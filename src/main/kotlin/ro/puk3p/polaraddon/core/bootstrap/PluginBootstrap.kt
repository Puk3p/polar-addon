package ro.puk3p.polaraddon.core.bootstrap

import org.bukkit.plugin.java.JavaPlugin
import ro.puk3p.polaraddon.application.usecase.KnockbackPlayerUseCase
import ro.puk3p.polaraddon.application.usecase.RotatePlayerUseCase
import ro.puk3p.polaraddon.infrastructure.bukkit.adapter.BukkitPlayerKnocker
import ro.puk3p.polaraddon.infrastructure.bukkit.adapter.BukkitPlayerLookup
import ro.puk3p.polaraddon.infrastructure.bukkit.adapter.BukkitPlayerRotator
import ro.puk3p.polaraddon.infrastructure.bukkit.command.PolarCommand

class PluginBootstrap(private val plugin: JavaPlugin) {
    fun start() {
        val playerLookup = BukkitPlayerLookup()
        val playerRotator = BukkitPlayerRotator()
        val playerKnocker = BukkitPlayerKnocker()

        val rotateUseCase = RotatePlayerUseCase(playerLookup, playerRotator)
        val knockbackUseCase = KnockbackPlayerUseCase(playerLookup, playerKnocker)

        val polarCommand = PolarCommand(plugin, rotateUseCase, knockbackUseCase)
        plugin.getCommand("polar")?.let {
            it.setExecutor(polarCommand)
            it.tabCompleter = polarCommand
        }

        plugin.logger.info("Commands registered.")
    }
}
