package ro.puk3p.polaraddon.infrastructure.bukkit.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import ro.puk3p.polaraddon.application.result.UseCaseResult
import ro.puk3p.polaraddon.application.usecase.KnockbackPlayerUseCase
import ro.puk3p.polaraddon.application.usecase.RotatePlayerUseCase
import ro.puk3p.polaraddon.domain.model.RotationTarget

class PolarCommand(
    private val rotateUseCase: RotatePlayerUseCase,
    private val knockbackUseCase: KnockbackPlayerUseCase,
) : CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        if (args.isEmpty()) {
            sendUsage(sender)
            return true
        }

        when (args[0].lowercase()) {
            SUB_ROTATE -> handleRotate(sender, args)
            SUB_KNOCKBACK, SUB_KB -> handleKnockback(sender, args)
            else -> sendUsage(sender)
        }
        return true
    }

    private fun handleRotate(
        sender: CommandSender,
        args: Array<out String>,
    ) {
        if (!sender.hasPermission(PERM_ROTATE)) {
            sender.sendMessage("$PREFIX§cYou don't have permission to use this command.")
            return
        }
        if (args.size < 2) {
            sender.sendMessage("$PREFIX§cUsage: /polar rotate <player> [yaw] [pitch]")
            return
        }

        val playerName = args[1]
        val yaw = args.getOrNull(2)?.toFloatOrNull() ?: (kotlin.random.Random.nextFloat() * 360f)
        val pitch = args.getOrNull(3)?.toFloatOrNull() ?: 0f
        val target = RotationTarget(yaw, pitch)

        when (rotateUseCase.execute(playerName, target)) {
            UseCaseResult.Ok ->
                sender.sendMessage(
                    "$PREFIX§aRotated §e$playerName §ato " +
                        "yaw=§f${"%.1f".format(yaw)}§a pitch=§f${"%.1f".format(pitch)}§a.",
                )
            UseCaseResult.PlayerNotFound ->
                sender.sendMessage("$PREFIX§cPlayer §e$playerName §cis not online.")
        }
    }

    private fun handleKnockback(
        sender: CommandSender,
        args: Array<out String>,
    ) {
        if (!sender.hasPermission(PERM_KNOCKBACK)) {
            sender.sendMessage("$PREFIX§cYou don't have permission to use this command.")
            return
        }
        if (args.size < 2) {
            sender.sendMessage("$PREFIX§cUsage: /polar knockback <player> [strength]")
            return
        }

        val playerName = args[1]
        val strength = args.getOrNull(2)?.toDoubleOrNull()?.coerceIn(MIN_STRENGTH, MAX_STRENGTH) ?: DEFAULT_STRENGTH

        when (knockbackUseCase.execute(playerName, strength)) {
            UseCaseResult.Ok ->
                sender.sendMessage(
                    "$PREFIX§aKnocked back §e$playerName §awith strength §f${"%.1f".format(strength)}§a.",
                )
            UseCaseResult.PlayerNotFound ->
                sender.sendMessage("$PREFIX§cPlayer §e$playerName §cis not online.")
        }
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage("$PREFIX§7/polar rotate <player> [yaw] [pitch]")
        sender.sendMessage("$PREFIX§7/polar knockback <player> [strength]")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        return when (args.size) {
            1 ->
                listOf(SUB_ROTATE, SUB_KNOCKBACK)
                    .filter { it.startsWith(args[0].lowercase()) }
            2 -> {
                val sub = args[0].lowercase()
                val hasPermission =
                    (sub == SUB_ROTATE && sender.hasPermission(PERM_ROTATE)) ||
                        (sub == SUB_KNOCKBACK && sender.hasPermission(PERM_KNOCKBACK)) ||
                        (sub == SUB_KB && sender.hasPermission(PERM_KNOCKBACK))
                if (hasPermission) {
                    Bukkit.getOnlinePlayers()
                        .map { it.name }
                        .filter { it.lowercase().startsWith(args[1].lowercase()) }
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private companion object {
        const val PREFIX = "§8[§bPolarAddon§8] §r"
        const val PERM_ROTATE = "polaraddon.rotate"
        const val PERM_KNOCKBACK = "polaraddon.knockback"
        const val SUB_ROTATE = "rotate"
        const val SUB_KNOCKBACK = "knockback"
        const val SUB_KB = "kb"
        const val DEFAULT_STRENGTH = 1.0
        const val MIN_STRENGTH = 0.1
        const val MAX_STRENGTH = 5.0
    }
}
