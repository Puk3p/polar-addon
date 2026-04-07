package ro.puk3p.polaraddon.infrastructure.bukkit.command

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import ro.puk3p.polaraddon.application.result.UseCaseResult
import ro.puk3p.polaraddon.application.usecase.KnockbackPlayerUseCase
import ro.puk3p.polaraddon.application.usecase.RotatePlayerUseCase
import ro.puk3p.polaraddon.domain.model.RotationTarget
import kotlin.math.cos
import kotlin.math.sin

class PolarCommand(
    private val plugin: JavaPlugin,
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
            SUB_TEST -> handleTest(sender, args)
            SUB_SUMMON -> handleSummon(sender, args)
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
        if (args.size != 2) {
            sender.sendMessage("$PREFIX§cUsage: /polar rotate <player>")
            return
        }

        val playerName = args[1]

        when (rotateUseCase.execute(playerName, randomRotationTarget())) {
            UseCaseResult.Ok ->
                sender.sendMessage(
                    "$PREFIX§aRandomly rotated §e$playerName§a.",
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
        if (args.size != 2) {
            sender.sendMessage("$PREFIX§cUsage: /polar knockback <player>")
            return
        }

        val playerName = args[1]

        when (knockbackUseCase.execute(playerName, DEFAULT_STRENGTH)) {
            UseCaseResult.Ok ->
                sender.sendMessage(
                    "$PREFIX§aKnocked back §e$playerName§a.",
                )
            UseCaseResult.PlayerNotFound ->
                sender.sendMessage("$PREFIX§cPlayer §e$playerName §cis not online.")
        }
    }

    private fun handleTest(
        sender: CommandSender,
        args: Array<out String>,
    ) {
        if (!sender.hasPermission(PERM_TEST)) {
            sender.sendMessage("$PREFIX§cYou don't have permission to use this command.")
            return
        }
        if (args.size != 2) {
            sender.sendMessage("$PREFIX§cUsage: /polar test <player>")
            return
        }

        val playerName = args[1]
        when (rotateUseCase.execute(playerName, randomRotationTarget())) {
            UseCaseResult.Ok ->
                when (knockbackUseCase.execute(playerName, DEFAULT_STRENGTH)) {
                    UseCaseResult.Ok ->
                        sender.sendMessage("$PREFIX§aTested §e$playerName §awith random rotation and knockback.")
                    UseCaseResult.PlayerNotFound ->
                        sender.sendMessage("$PREFIX§cPlayer §e$playerName §cis not online.")
                }
            UseCaseResult.PlayerNotFound ->
                sender.sendMessage("$PREFIX§cPlayer §e$playerName §cis not online.")
        }
    }

    private fun handleSummon(
        sender: CommandSender,
        args: Array<out String>,
    ) {
        if (!sender.hasPermission(PERM_SUMMON)) {
            sender.sendMessage("$PREFIX§cYou don't have permission to use this command.")
            return
        }
        if (args.size != 3) {
            sender.sendMessage("$PREFIX§cUsage: /polar summon <player> <mob>")
            return
        }

        val playerName = args[1]
        val mobName = args[2].lowercase()
        val entityType =
            SUMMON_ENTITY_TYPES[mobName]
                ?: run {
                    sender.sendMessage("$PREFIX§cMob must be one of: §e${SUMMON_ENTITY_TYPES.keys.joinToString(", ")}§c.")
                    return
                }

        val player =
            Bukkit.getPlayerExact(playerName)
                ?: run {
                    sender.sendMessage("$PREFIX§cPlayer §e$playerName §cis not online.")
                    return
                }

        val spawnLocation =
            findSideOrBehindLocation(player)
                ?: run {
                    sender.sendMessage("$PREFIX§cCould not find a safe left, right, or behind space near §e$playerName§c.")
                    return
                }

        val entity = player.world.spawnEntity(spawnLocation, entityType)
        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable {
                if (!entity.isDead) {
                    entity.remove()
                }
            },
            SUMMON_DESPAWN_TICKS,
        )

        sender.sendMessage("$PREFIX§aSpawned §e$mobName §anear §e$playerName §afor 3 seconds.")
    }

    private fun findSideOrBehindLocation(player: Player): Location? {
        val yawRad = Math.toRadians(player.location.yaw.toDouble())
        val forward = Vector(sin(yawRad), 0.0, -cos(yawRad))
        val left = Vector(forward.z, 0.0, -forward.x)
        val right = Vector(-forward.z, 0.0, forward.x)
        val behind = forward.clone().multiply(-1.0)

        val candidates =
            listOf(left, right, behind)
                .shuffled()
                .flatMap { direction ->
                    SUMMON_DISTANCES.map { distance ->
                        player.location.clone().add(direction.clone().normalize().multiply(distance))
                    }
                }

        return candidates.firstOrNull(::isSpawnSpaceClear)
    }

    private fun isSpawnSpaceClear(location: Location): Boolean {
        val feet = location.block
        val body = location.clone().add(0.0, 1.0, 0.0).block
        val ground = location.clone().add(0.0, -1.0, 0.0).block
        return !feet.type.isSolid && !body.type.isSolid && ground.type.isSolid
    }

    private fun randomRotationTarget(): RotationTarget {
        return RotationTarget(
            yaw = kotlin.random.Random.nextFloat() * FULL_ROTATION_DEGREES,
            pitch = DEFAULT_PITCH,
        )
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage("$PREFIX§7/polar rotate <player>")
        sender.sendMessage("$PREFIX§7/polar knockback <player>")
        sender.sendMessage("$PREFIX§7/polar test <player>")
        sender.sendMessage("$PREFIX§7/polar summon <player> <mob>")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        return when (args.size) {
            1 ->
                availableSubcommands(sender)
                    .filter { it.startsWith(args[0].lowercase()) }
            2 -> {
                val sub = args[0].lowercase()
                val hasPermission =
                    (sub == SUB_ROTATE && sender.hasPermission(PERM_ROTATE)) ||
                        (sub == SUB_KNOCKBACK && sender.hasPermission(PERM_KNOCKBACK)) ||
                        (sub == SUB_KB && sender.hasPermission(PERM_KNOCKBACK)) ||
                        (sub == SUB_TEST && sender.hasPermission(PERM_TEST)) ||
                        (sub == SUB_SUMMON && sender.hasPermission(PERM_SUMMON))
                if (hasPermission) {
                    Bukkit.getOnlinePlayers()
                        .map { it.name }
                        .filter { it.lowercase().startsWith(args[1].lowercase()) }
                } else {
                    emptyList()
                }
            }
            3 ->
                if (args[0].equals(SUB_SUMMON, ignoreCase = true) && sender.hasPermission(PERM_SUMMON)) {
                    SUMMON_ENTITY_TYPES.keys
                        .filter { it.startsWith(args[2].lowercase()) }
                } else {
                    emptyList()
                }
            else -> emptyList()
        }
    }

    private fun availableSubcommands(sender: CommandSender): List<String> {
        return buildList {
            if (sender.hasPermission(PERM_ROTATE)) {
                add(SUB_ROTATE)
            }
            if (sender.hasPermission(PERM_KNOCKBACK)) {
                add(SUB_KNOCKBACK)
                add(SUB_KB)
            }
            if (sender.hasPermission(PERM_TEST)) {
                add(SUB_TEST)
            }
            if (sender.hasPermission(PERM_SUMMON)) {
                add(SUB_SUMMON)
            }
        }
    }

    private companion object {
        const val PREFIX = "§8[§bPolarAddon§8] §r"
        const val PERM_ROTATE = "polaraddon.rotate"
        const val PERM_KNOCKBACK = "polaraddon.knockback"
        const val PERM_TEST = "polaraddon.test"
        const val PERM_SUMMON = "polaraddon.summon"
        const val SUB_ROTATE = "rotate"
        const val SUB_KNOCKBACK = "knockback"
        const val SUB_KB = "kb"
        const val SUB_TEST = "test"
        const val SUB_SUMMON = "summon"
        const val FULL_ROTATION_DEGREES = 360f
        const val DEFAULT_PITCH = 0f
        const val DEFAULT_STRENGTH = 1.6
        const val SUMMON_DESPAWN_TICKS = 60L
        val SUMMON_DISTANCES = doubleArrayOf(2.0, 3.0)
        val SUMMON_ENTITY_TYPES =
            linkedMapOf(
                "blaze" to EntityType.BLAZE,
                "silverfish" to EntityType.SILVERFISH,
                "zombie" to EntityType.ZOMBIE,
                "skeleton" to EntityType.SKELETON,
                "witch" to EntityType.WITCH,
                "creeper" to EntityType.CREEPER,
                "enderman" to EntityType.ENDERMAN,
            )
    }
}
