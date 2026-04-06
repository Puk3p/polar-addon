package ro.puk3p.polaraddon

import org.bukkit.plugin.java.JavaPlugin
import ro.puk3p.polaraddon.core.bootstrap.PluginBootstrap
import ro.puk3p.polaraddon.polar.PolarApiHook

class PolarAddonPlugin : JavaPlugin() {
    companion object {
        lateinit var instance: PolarAddonPlugin
            private set
    }

    override fun onLoad() {
        instance = this
        top.polar.api.loader.LoaderApi.registerEnableCallback(PolarApiHook::init)
    }

    override fun onEnable() {
        PluginBootstrap(this).start()
        logger.info("PolarAddon enabled.")
    }

    override fun onDisable() {
        logger.info("PolarAddon disabled.")
    }
}
