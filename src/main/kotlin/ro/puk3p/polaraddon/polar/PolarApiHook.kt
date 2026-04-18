package ro.puk3p.polaraddon.polar

import org.bukkit.Bukkit
import ro.puk3p.polaraddon.PolarAddonPlugin
import ro.puk3p.polaraddon.infrastructure.discord.DiscordWebhookNotifier
import ro.puk3p.polaraddon.infrastructure.discord.DiscordWebhookNotifier.DiscordEmbed
import ro.puk3p.polaraddon.infrastructure.discord.DiscordWebhookNotifier.DiscordField
import top.polar.api.PolarApiAccessor
import top.polar.api.event.listener.repository.EventListenerRepository
import top.polar.api.user.event.DetectionAlertEvent
import top.polar.api.user.event.MitigationEvent
import top.polar.api.user.event.PunishmentEvent
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object PolarApiHook {
    fun init() {
        val plugin = PolarAddonPlugin.instance
        val loadedBridge = loadDiscordBridge(plugin)
        discordBridge = loadedBridge

        synchronized(LISTENER_LOCK) {
            if (listenersRegistered) {
                plugin.logger.info("Polar API listeners are already registered; skipping duplicate registration.")
                return
            }
        }

        plugin.logger.info("Polar API initialised — registering event listeners.")

        val api =
            PolarApiAccessor.access().get()
                ?: run {
                    plugin.logger.severe("Polar API is unavailable after init callback — aborting.")
                    return
                }

        registerListeners(api.events().repository())

        synchronized(LISTENER_LOCK) {
            listenersRegistered = true
        }
    }

    fun reset() {
        synchronized(LISTENER_LOCK) {
            listenersRegistered = false
            pendingAlerts.clear()
        }
        discordBridge = DiscordBridge.disabled()
    }

    fun reloadConfig() {
        val plugin = PolarAddonPlugin.instance
        val loadedBridge = loadDiscordBridge(plugin)
        synchronized(ALERT_AGGREGATION_LOCK) {
            pendingAlerts.clear()
        }
        discordBridge = loadedBridge
        plugin.logger.info("Polar alert bridge reloaded from config.")
    }

    private fun registerListeners(repo: EventListenerRepository) {
        repo.registerListener(DetectionAlertEvent::class.java) { event ->
            val bridge = discordBridge
            val playerName = event.user().username()
            val checkName = event.check().name()
            val violationLevel = event.check().violationLevel().toString()
            val aggregateKey = "detection|$playerName|$checkName|$violationLevel"

            if (bridge.detectionEnabled) {
                queueAggregatedAlert(
                    bridge = bridge,
                    aggregateKey = aggregateKey,
                    template =
                        AlertTemplate(
                            title = "Polar Detection Alert",
                            description = "Suspicious behavior detected by Polar.",
                            color = COLOR_DETECTION,
                            fields =
                                listOf(
                                    DiscordField("Player", "`$playerName`"),
                                    DiscordField("Check", "`$checkName`"),
                                    DiscordField("Violation Level", "`$violationLevel`"),
                                ),
                            thumbnailUrl = playerSkinAvatarUrl(playerName),
                            footerText = "PolarAddon • Detection",
                        ),
                    logMessage = "Detection alert: player=$playerName, check=$checkName, vl=$violationLevel",
                )
            } else {
                PolarAddonPlugin.instance.logger.info(
                    "[Polar] Detection alert: player=$playerName, check=$checkName, vl=$violationLevel",
                )
            }
        }

        repo.registerListener(MitigationEvent::class.java) { event ->
            val bridge = discordBridge
            val playerName = event.user().username()
            val checkName = event.check().name()
            val aggregateKey = "mitigation|$playerName|$checkName"

            if (bridge.mitigationEnabled) {
                queueAggregatedAlert(
                    bridge = bridge,
                    aggregateKey = aggregateKey,
                    template =
                        AlertTemplate(
                            title = "Polar Mitigation",
                            description = "Polar mitigated a suspicious action.",
                            color = COLOR_MITIGATION,
                            fields =
                                listOf(
                                    DiscordField("Player", "`$playerName`"),
                                    DiscordField("Check", "`$checkName`"),
                                ),
                            thumbnailUrl = playerSkinAvatarUrl(playerName),
                            footerText = "PolarAddon • Mitigation",
                        ),
                    logMessage = "Mitigation: player=$playerName, check=$checkName",
                )
            } else {
                PolarAddonPlugin.instance.logger.info(
                    "[Polar] Mitigation: player=$playerName, check=$checkName",
                )
            }
        }

        repo.registerListener(PunishmentEvent::class.java) { event ->
            val bridge = discordBridge
            val playerName = event.user().username()
            val aggregateKey = "punishment|$playerName"

            if (bridge.punishmentEnabled) {
                queueAggregatedAlert(
                    bridge = bridge,
                    aggregateKey = aggregateKey,
                    template =
                        AlertTemplate(
                            title = "Polar Punishment",
                            description = "Polar issued a punishment.",
                            color = COLOR_PUNISHMENT,
                            fields =
                                listOf(
                                    DiscordField("Player", "`$playerName`"),
                                ),
                            thumbnailUrl = playerSkinAvatarUrl(playerName),
                            footerText = "PolarAddon • Punishment",
                        ),
                    logMessage = "Punishment: player=$playerName",
                )
            } else {
                PolarAddonPlugin.instance.logger.info(
                    "[Polar] Punishment: player=$playerName",
                )
            }
        }
    }

    private fun queueAggregatedAlert(
        bridge: DiscordBridge,
        aggregateKey: String,
        template: AlertTemplate,
        logMessage: String,
    ) {
        val plugin = PolarAddonPlugin.instance
        val shouldSchedule =
            synchronized(ALERT_AGGREGATION_LOCK) {
                val existing = pendingAlerts[aggregateKey]
                if (existing != null) {
                    existing.count += 1
                    false
                } else {
                    pendingAlerts[aggregateKey] = PendingAlert(template = template, logMessage = logMessage)
                    true
                }
            }

        if (!shouldSchedule) {
            return
        }

        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable {
                flushAggregatedAlert(aggregateKey, bridge)
            },
            millisToTicks(bridge.aggregateWindowMillis),
        )
    }

    private fun flushAggregatedAlert(
        aggregateKey: String,
        bridge: DiscordBridge,
    ) {
        val pending =
            synchronized(ALERT_AGGREGATION_LOCK) {
                pendingAlerts.remove(aggregateKey)
            } ?: return

        val countSuffix = if (pending.count > 1) " x${pending.count}" else ""
        PolarAddonPlugin.instance.logger.info("[Polar] ${pending.logMessage}$countSuffix")

        val embed =
            DiscordEmbed(
                title = pending.template.title + countSuffix,
                description = pending.template.description,
                color = pending.template.color,
                fields =
                    buildList {
                        addAll(pending.template.fields)
                        if (pending.count > 1) {
                            add(DiscordField("Occurrences", "`x${pending.count}`"))
                        }
                    },
                thumbnailUrl = pending.template.thumbnailUrl,
                footerText = pending.template.footerText,
                timestampIso8601 = Instant.now().toString(),
            )

        bridge.notifier?.sendEmbed(embed)
    }

    private fun millisToTicks(millis: Long): Long {
        val bounded = millis.coerceAtLeast(0L)
        val ticks = (bounded + 49L) / 50L
        return ticks.coerceAtLeast(1L)
    }

    private fun playerSkinAvatarUrl(playerName: String): String {
        val encoded = URLEncoder.encode(playerName, StandardCharsets.UTF_8.name())
        return "https://mc-heads.net/avatar/$encoded/128"
    }

    private fun loadDiscordBridge(plugin: PolarAddonPlugin): DiscordBridge {
        val config = plugin.config
        val webhookUrl = config.getString("discord.webhook-url", "").orEmpty().trim()
        val username = config.getString("discord.username", "PolarAddon").orEmpty()
        val avatarUrl = config.getString("discord.avatar-url", "").orEmpty().trim().ifBlank { null }
        val aggregateWindowMillis =
            if (config.isSet("discord.aggregate-window-millis")) {
                config.getLong("discord.aggregate-window-millis", 1250L)
            } else {
                config.getLong("discord.dedup-window-millis", 1250L)
            }

        val detectionEnabled = config.getBoolean("discord.alerts.detection", true)
        val mitigationEnabled = config.getBoolean("discord.alerts.mitigation", true)
        val punishmentEnabled = config.getBoolean("discord.alerts.punishment", true)

        if (webhookUrl.isBlank()) {
            plugin.logger.info("Discord webhook alerts are disabled (discord.webhook-url is empty).")
            return DiscordBridge(
                notifier = null,
                detectionEnabled = detectionEnabled,
                mitigationEnabled = mitigationEnabled,
                punishmentEnabled = punishmentEnabled,
                aggregateWindowMillis = aggregateWindowMillis.coerceAtLeast(0L),
            )
        }

        plugin.logger.info("Discord webhook alerts are enabled.")
        return DiscordBridge(
            notifier = DiscordWebhookNotifier(plugin, webhookUrl, username, avatarUrl),
            detectionEnabled = detectionEnabled,
            mitigationEnabled = mitigationEnabled,
            punishmentEnabled = punishmentEnabled,
            aggregateWindowMillis = aggregateWindowMillis.coerceAtLeast(0L),
        )
    }

    private data class PendingAlert(
        val template: AlertTemplate,
        val logMessage: String,
        var count: Int = 1,
    )

    private data class AlertTemplate(
        val title: String,
        val description: String,
        val color: Int,
        val fields: List<DiscordField>,
        val thumbnailUrl: String?,
        val footerText: String,
    )

    private data class DiscordBridge(
        val notifier: DiscordWebhookNotifier?,
        val detectionEnabled: Boolean,
        val mitigationEnabled: Boolean,
        val punishmentEnabled: Boolean,
        val aggregateWindowMillis: Long,
    ) {
        companion object {
            fun disabled(): DiscordBridge {
                return DiscordBridge(
                    notifier = null,
                    detectionEnabled = false,
                    mitigationEnabled = false,
                    punishmentEnabled = false,
                    aggregateWindowMillis = 1250L,
                )
            }
        }
    }

    private const val COLOR_DETECTION = 15158332
    private const val COLOR_MITIGATION = 16776960
    private const val COLOR_PUNISHMENT = 10038562

    private val ALERT_AGGREGATION_LOCK = Any()
    private val LISTENER_LOCK = Any()
    private val pendingAlerts = ConcurrentHashMap<String, PendingAlert>()

    @Volatile private var listenersRegistered = false

    @Volatile private var discordBridge: DiscordBridge = DiscordBridge.disabled()
}
