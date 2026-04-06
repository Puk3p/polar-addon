package ro.puk3p.polaraddon.polar

import ro.puk3p.polaraddon.PolarAddonPlugin
import top.polar.api.PolarApiAccessor
import top.polar.api.event.listener.repository.EventListenerRepository
import top.polar.api.user.event.DetectionAlertEvent
import top.polar.api.user.event.MitigationEvent
import top.polar.api.user.event.PunishmentEvent

object PolarApiHook {
    fun init() {
        val plugin = PolarAddonPlugin.instance
        plugin.logger.info("Polar API initialised — registering event listeners.")

        val api =
            PolarApiAccessor.access().get()
                ?: run {
                    plugin.logger.severe("Polar API is unavailable after init callback — aborting.")
                    return
                }

        registerListeners(api.events().repository())
    }

    private fun registerListeners(repo: EventListenerRepository) {
        val plugin = PolarAddonPlugin.instance

        repo.registerListener(DetectionAlertEvent::class.java) { event ->
            plugin.logger.info(
                "[Polar] Detection alert: player=${event.user().username()}, " +
                    "check=${event.check().name()}, vl=${event.check().violationLevel()}",
            )
        }

        repo.registerListener(MitigationEvent::class.java) { event ->
            plugin.logger.info(
                "[Polar] Mitigation: player=${event.user().username()}, " +
                    "check=${event.check().name()}",
            )
        }

        repo.registerListener(PunishmentEvent::class.java) { event ->
            plugin.logger.info(
                "[Polar] Punishment: player=${event.user().username()}",
            )
        }
    }
}
