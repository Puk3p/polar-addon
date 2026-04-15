package ro.puk3p.polaraddon.polar

import ro.puk3p.polaraddon.PolarAddonPlugin
import ro.puk3p.polaraddon.infrastructure.discord.DiscordWebhookNotifier
import ro.puk3p.polaraddon.infrastructure.discord.DiscordWebhookNotifier.DiscordField
import top.polar.api.PolarApiAccessor
import top.polar.api.event.listener.repository.EventListenerRepository
import top.polar.api.user.event.DetectionAlertEvent
import top.polar.api.user.event.MitigationEvent
import top.polar.api.user.event.PunishmentEvent

object PolarApiHook {
    fun init() {
        val plugin = PolarAddonPlugin.instance
        plugin.logger.info("Polar API initialised — registering event listeners.")
        val discordBridge = loadDiscordBridge(plugin)

        val api =
            PolarApiAccessor.access().get()
                ?: run {
                    plugin.logger.severe("Polar API is unavailable after init callback — aborting.")
                    return
                }

        registerListeners(api.events().repository(), discordBridge)
    }

    private fun registerListeners(
        repo: EventListenerRepository,
        discordBridge: DiscordBridge,
    ) {
        val plugin = PolarAddonPlugin.instance

        repo.registerListener(DetectionAlertEvent::class.java) { event ->
            val playerName = event.user().username()
            val checkName = event.check().name()
            val violationLevel = event.check().violationLevel().toString()

            plugin.logger.info(
                "[Polar] Detection alert: player=$playerName, " +
                    "check=$checkName, vl=$violationLevel",
            )

            if (discordBridge.detectionEnabled) {
                discordBridge.notifier?.sendEmbed(
                    title = "Polar Detection Alert",
                    description = "A detection alert was triggered.",
                    color = COLOR_DETECTION,
                    fields =
                        listOf(
                            DiscordField("Player", playerName),
                            DiscordField("Check", checkName),
                            DiscordField("Violation Level", violationLevel),
                        ),
                )
            }
        }

        repo.registerListener(MitigationEvent::class.java) { event ->
            val playerName = event.user().username()
            val checkName = event.check().name()

            plugin.logger.info(
                "[Polar] Mitigation: player=$playerName, check=$checkName",
            )

            if (discordBridge.mitigationEnabled) {
                discordBridge.notifier?.sendEmbed(
                    title = "Polar Mitigation",
                    description = "Polar mitigated a suspicious action.",
                    color = COLOR_MITIGATION,
                    fields =
                        listOf(
                            DiscordField("Player", playerName),
                            DiscordField("Check", checkName),
                        ),
                )
            }
        }

        repo.registerListener(PunishmentEvent::class.java) { event ->
            val playerName = event.user().username()

            plugin.logger.info(
                "[Polar] Punishment: player=$playerName",
            )

            if (discordBridge.punishmentEnabled) {
                discordBridge.notifier?.sendEmbed(
                    title = "Polar Punishment",
                    description = "Polar issued a punishment.",
                    color = COLOR_PUNISHMENT,
                    fields =
                        listOf(
                            DiscordField("Player", playerName),
                        ),
                )
            }
        }
    }

    private fun loadDiscordBridge(plugin: PolarAddonPlugin): DiscordBridge {
        val config = plugin.config
        val webhookUrl = config.getString("discord.webhook-url", "").orEmpty().trim()
        val username = config.getString("discord.username", "PolarAddon").orEmpty()
        val avatarUrl = config.getString("discord.avatar-url", "").orEmpty().trim().ifBlank { null }

        val detectionEnabled = config.getBoolean("discord.alerts.detection", true)
        val mitigationEnabled = config.getBoolean("discord.alerts.mitigation", true)
        val punishmentEnabled = config.getBoolean("discord.alerts.punishment", true)

        if (webhookUrl.isBlank()) {
            plugin.logger.info("Discord webhook alerts are disabled (discord.webhook-url is empty).")
            return DiscordBridge(null, detectionEnabled, mitigationEnabled, punishmentEnabled)
        }

        plugin.logger.info("Discord webhook alerts are enabled.")
        return DiscordBridge(
            notifier = DiscordWebhookNotifier(plugin, webhookUrl, username, avatarUrl),
            detectionEnabled = detectionEnabled,
            mitigationEnabled = mitigationEnabled,
            punishmentEnabled = punishmentEnabled,
        )
    }

    private data class DiscordBridge(
        val notifier: DiscordWebhookNotifier?,
        val detectionEnabled: Boolean,
        val mitigationEnabled: Boolean,
        val punishmentEnabled: Boolean,
    )

    private const val COLOR_DETECTION = 15158332
    private const val COLOR_MITIGATION = 16776960
    private const val COLOR_PUNISHMENT = 10038562
}
