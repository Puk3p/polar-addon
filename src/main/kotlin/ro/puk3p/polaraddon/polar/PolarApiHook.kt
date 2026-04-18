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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

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
            val violationLevel = event.check().violationLevel()
            val aggregateKey = "detection|$playerName|$checkName"
            val avatarUrl = resolvePlayerAvatarUrl(playerName, bridge)

            if (bridge.detectionEnabled) {
                queueAggregatedAlert(
                    aggregateKey = aggregateKey,
                    windowMillis = bridge.detectionAggregateWindowMillis,
                    violationLevel = violationLevel,
                    template =
                        AlertTemplate(
                            title = "\u26a0\ufe0f Polar Detection",
                            description = "Suspicious behavior detected by Polar.",
                            color = COLOR_DETECTION,
                            fields =
                                listOf(
                                    DiscordField("Player", "`$playerName`"),
                                    DiscordField("Check", "`$checkName`"),
                                ),
                            thumbnailUrl = avatarUrl,
                            authorName = "Polar Anticheat",
                            authorIconUrl = avatarUrl,
                            footerText = "PolarAddon • Detection",
                            includeViolationLevel = true,
                        ),
                    logMessage = "Detection alert: player=$playerName, check=$checkName",
                )
            } else {
                PolarAddonPlugin.instance.logger.info(
                    "[Polar] Detection alert: player=$playerName, check=$checkName, vl=${formatViolationLevel(violationLevel)}",
                )
            }
        }

        repo.registerListener(MitigationEvent::class.java) { event ->
            val bridge = discordBridge
            val playerName = event.user().username()
            val checkName = event.check().name()
            val aggregateKey = "mitigation|$playerName|$checkName"
            val avatarUrl = resolvePlayerAvatarUrl(playerName, bridge)

            if (bridge.mitigationEnabled) {
                queueAggregatedAlert(
                    aggregateKey = aggregateKey,
                    windowMillis = bridge.mitigationAggregateWindowMillis,
                    template =
                        AlertTemplate(
                            title = "\ud83d\udee1\ufe0f Polar Mitigation",
                            description = "Polar mitigated a suspicious action.",
                            color = COLOR_MITIGATION,
                            fields =
                                listOf(
                                    DiscordField("Player", "`$playerName`"),
                                    DiscordField("Check", "`$checkName`"),
                                ),
                            thumbnailUrl = avatarUrl,
                            authorName = "Polar Anticheat",
                            authorIconUrl = avatarUrl,
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
            val avatarUrl = resolvePlayerAvatarUrl(playerName, bridge)

            if (bridge.punishmentEnabled) {
                queueAggregatedAlert(
                    aggregateKey = aggregateKey,
                    windowMillis = bridge.punishmentAggregateWindowMillis,
                    template =
                        AlertTemplate(
                            title = "\u26d4 Polar Punishment",
                            description = "Polar issued a punishment.",
                            color = COLOR_PUNISHMENT,
                            fields =
                                listOf(
                                    DiscordField("Player", "`$playerName`"),
                                ),
                            thumbnailUrl = avatarUrl,
                            authorName = "Polar Anticheat",
                            authorIconUrl = avatarUrl,
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
        aggregateKey: String,
        windowMillis: Long,
        template: AlertTemplate,
        logMessage: String,
        violationLevel: Double? = null,
    ) {
        val plugin = PolarAddonPlugin.instance
        val shouldSchedule =
            synchronized(ALERT_AGGREGATION_LOCK) {
                val existing = pendingAlerts[aggregateKey]
                if (existing != null) {
                    existing.count += 1
                    if (violationLevel != null) {
                        existing.latestViolationLevel = violationLevel
                        existing.maxViolationLevel = max(existing.maxViolationLevel ?: violationLevel, violationLevel)
                    }
                    false
                } else {
                    pendingAlerts[aggregateKey] =
                        PendingAlert(
                            template = template,
                            logMessage = logMessage,
                            latestViolationLevel = violationLevel,
                            maxViolationLevel = violationLevel,
                        )
                    true
                }
            }

        if (!shouldSchedule) {
            return
        }

        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable {
                flushAggregatedAlert(aggregateKey)
            },
            millisToTicks(windowMillis),
        )
    }

    private fun flushAggregatedAlert(aggregateKey: String) {
        val pending =
            synchronized(ALERT_AGGREGATION_LOCK) {
                pendingAlerts.remove(aggregateKey)
            } ?: return

        val bridge = discordBridge
        val countSuffix = if (pending.count > 1) " x${pending.count}" else ""
        val violationSuffix =
            if (pending.template.includeViolationLevel && pending.maxViolationLevel != null) {
                ", vl=${formatViolationLevel(pending.maxViolationLevel)}"
            } else {
                ""
            }
        PolarAddonPlugin.instance.logger.info("[Polar] ${pending.logMessage}$violationSuffix$countSuffix")

        val embed =
            DiscordEmbed(
                title = pending.template.title + countSuffix,
                description = pending.template.description,
                color = pending.template.color,
                fields =
                    buildList {
                        addAll(pending.template.fields)
                        if (pending.template.includeViolationLevel && pending.maxViolationLevel != null) {
                            add(
                                DiscordField(
                                    "Max Violation Level",
                                    "`${formatViolationLevel(pending.maxViolationLevel)}`",
                                ),
                            )
                        }
                        if (pending.count > 1) {
                            add(DiscordField("Occurrences", "`x${pending.count}`"))
                        }
                    },
                thumbnailUrl = pending.template.thumbnailUrl,
                authorName = pending.template.authorName,
                authorIconUrl = pending.template.authorIconUrl,
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

    private fun resolvePlayerAvatarUrl(
        playerName: String,
        bridge: DiscordBridge,
    ): String {
        if (!bridge.premiumSkinLookupEnabled) {
            return playerSkinAvatarUrl(playerName)
        }

        return if (isPremiumPlayer(playerName, bridge)) {
            playerSkinAvatarUrl(playerName)
        } else {
            bridge.fallbackPlayerAvatarUrl
        }
    }

    private fun isPremiumPlayer(
        playerName: String,
        bridge: DiscordBridge,
    ): Boolean {
        val now = System.currentTimeMillis()
        premiumPlayerCache[playerName.lowercase()]?.let { cached ->
            if (cached.expiresAtMillis > now) {
                return cached.isPremium
            }
        }

        val isPremium = fetchPremiumStatus(playerName, bridge.premiumSkinLookupTimeoutMillis)
        premiumPlayerCache[playerName.lowercase()] =
            PremiumCacheEntry(
                isPremium = isPremium,
                expiresAtMillis = now + bridge.premiumSkinLookupCacheMillis,
            )
        return isPremium
    }

    private fun fetchPremiumStatus(
        playerName: String,
        timeoutMillis: Int,
    ): Boolean {
        val encoded = URLEncoder.encode(playerName, StandardCharsets.UTF_8.name())
        val connection = URL("https://api.mojang.com/users/profiles/minecraft/$encoded").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", PREMIUM_LOOKUP_USER_AGENT)
            connection.responseCode == HttpURLConnection.HTTP_OK
        } catch (_: IOException) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun formatViolationLevel(violationLevel: Double?): String {
        return if (violationLevel == null) {
            "n/a"
        } else {
            String.format("%.2f", violationLevel)
        }
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
        val detectionAggregateWindowMillis =
            config.getLong("discord.aggregate-window-detection-millis", 2500L)
        val mitigationAggregateWindowMillis =
            config.getLong("discord.aggregate-window-mitigation-millis", aggregateWindowMillis)
        val punishmentAggregateWindowMillis =
            config.getLong("discord.aggregate-window-punishment-millis", aggregateWindowMillis)
        val premiumSkinLookupEnabled = config.getBoolean("discord.player-avatar.premium-skin-lookup-enabled", true)
        val premiumSkinLookupTimeoutMillis =
            config.getInt("discord.player-avatar.premium-skin-lookup-timeout-millis", 1200).coerceAtLeast(250)
        val premiumSkinLookupCacheMinutes =
            config.getInt("discord.player-avatar.premium-skin-lookup-cache-minutes", 60).coerceAtLeast(1)
        val fallbackPlayerAvatarUrl =
            config.getString("discord.player-avatar.fallback-url", "https://mc-heads.net/avatar/Steve/128")
                .orEmpty()
                .trim()
                .ifBlank { "https://mc-heads.net/avatar/Steve/128" }

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
                detectionAggregateWindowMillis = detectionAggregateWindowMillis.coerceAtLeast(0L),
                mitigationAggregateWindowMillis = mitigationAggregateWindowMillis.coerceAtLeast(0L),
                punishmentAggregateWindowMillis = punishmentAggregateWindowMillis.coerceAtLeast(0L),
                fallbackPlayerAvatarUrl = fallbackPlayerAvatarUrl,
                premiumSkinLookupEnabled = premiumSkinLookupEnabled,
                premiumSkinLookupTimeoutMillis = premiumSkinLookupTimeoutMillis,
                premiumSkinLookupCacheMillis = premiumSkinLookupCacheMinutes * 60_000L,
            )
        }

        plugin.logger.info("Discord webhook alerts are enabled.")
        return DiscordBridge(
            notifier = DiscordWebhookNotifier(plugin, webhookUrl, username, avatarUrl),
            detectionEnabled = detectionEnabled,
            mitigationEnabled = mitigationEnabled,
            punishmentEnabled = punishmentEnabled,
            detectionAggregateWindowMillis = detectionAggregateWindowMillis.coerceAtLeast(0L),
            mitigationAggregateWindowMillis = mitigationAggregateWindowMillis.coerceAtLeast(0L),
            punishmentAggregateWindowMillis = punishmentAggregateWindowMillis.coerceAtLeast(0L),
            fallbackPlayerAvatarUrl = fallbackPlayerAvatarUrl,
            premiumSkinLookupEnabled = premiumSkinLookupEnabled,
            premiumSkinLookupTimeoutMillis = premiumSkinLookupTimeoutMillis,
            premiumSkinLookupCacheMillis = premiumSkinLookupCacheMinutes * 60_000L,
        )
    }

    private data class PendingAlert(
        val template: AlertTemplate,
        val logMessage: String,
        var count: Int = 1,
        var latestViolationLevel: Double? = null,
        var maxViolationLevel: Double? = null,
    )

    private data class AlertTemplate(
        val title: String,
        val description: String,
        val color: Int,
        val fields: List<DiscordField>,
        val thumbnailUrl: String?,
        val authorName: String?,
        val authorIconUrl: String?,
        val footerText: String,
        val includeViolationLevel: Boolean = false,
    )

    private data class DiscordBridge(
        val notifier: DiscordWebhookNotifier?,
        val detectionEnabled: Boolean,
        val mitigationEnabled: Boolean,
        val punishmentEnabled: Boolean,
        val detectionAggregateWindowMillis: Long,
        val mitigationAggregateWindowMillis: Long,
        val punishmentAggregateWindowMillis: Long,
        val fallbackPlayerAvatarUrl: String,
        val premiumSkinLookupEnabled: Boolean,
        val premiumSkinLookupTimeoutMillis: Int,
        val premiumSkinLookupCacheMillis: Long,
    ) {
        companion object {
            fun disabled(): DiscordBridge {
                return DiscordBridge(
                    notifier = null,
                    detectionEnabled = false,
                    mitigationEnabled = false,
                    punishmentEnabled = false,
                    detectionAggregateWindowMillis = 2500L,
                    mitigationAggregateWindowMillis = 1250L,
                    punishmentAggregateWindowMillis = 1250L,
                    fallbackPlayerAvatarUrl = "https://mc-heads.net/avatar/Steve/128",
                    premiumSkinLookupEnabled = true,
                    premiumSkinLookupTimeoutMillis = 1200,
                    premiumSkinLookupCacheMillis = 3_600_000L,
                )
            }
        }
    }

    private data class PremiumCacheEntry(
        val isPremium: Boolean,
        val expiresAtMillis: Long,
    )

    private const val COLOR_DETECTION = 15158332
    private const val COLOR_MITIGATION = 16776960
    private const val COLOR_PUNISHMENT = 10038562

    private val ALERT_AGGREGATION_LOCK = Any()
    private val LISTENER_LOCK = Any()
    private val pendingAlerts = ConcurrentHashMap<String, PendingAlert>()
    private val premiumPlayerCache = ConcurrentHashMap<String, PremiumCacheEntry>()

    @Volatile private var listenersRegistered = false

    @Volatile private var discordBridge: DiscordBridge = DiscordBridge.disabled()

    private const val PREMIUM_LOOKUP_USER_AGENT =
        "Mozilla/5.0 (compatible; PolarAddonPremiumLookup/1.0; +https://github.com/Puk3p/polar-addon)"
}
