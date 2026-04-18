package ro.puk3p.polaraddon.polar

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

object CombatContextService {
    fun recordHit(
        attacker: Player,
        victim: Player,
    ) {
        val now = System.currentTimeMillis()
        attackTimestamps.computeIfAbsent(attacker.uniqueId) { ConcurrentLinkedDeque() }.addLast(now)
        trimOldHits(attacker.uniqueId, now)

        lastCombatTarget[attacker.uniqueId] = TargetSnapshot(victim.uniqueId)
        lastCombatTarget[victim.uniqueId] = TargetSnapshot(attacker.uniqueId)
        lastCombatActivity[attacker.uniqueId] = now
        lastCombatActivity[victim.uniqueId] = now
    }

    fun snapshotFor(
        playerName: String,
        checkName: String,
    ): CombatContextSnapshot? {
        if (!isCombatCheck(checkName)) {
            return null
        }

        val player = Bukkit.getPlayerExact(playerName) ?: return null
        val now = System.currentTimeMillis()
        trimOldHits(player.uniqueId, now)

        val inCombat = isPlayerInCombat(player, now)
        if (!inCombat) {
            return null
        }

        val targetName = resolveTargetName(player.uniqueId)
        val distance = resolveDistance(player, targetName)
        val cps = computeCps(player.uniqueId)
        val ping = resolvePing(player)
        val tps = resolveServerTps()

        return CombatContextSnapshot(
            targetName = targetName ?: "Unknown",
            distanceBlocks = distance,
            cpsWindowSeconds = CPS_WINDOW_MILLIS / 1000.0,
            cps = cps,
            pingMillis = ping,
            tps = tps,
        )
    }

    private fun isCombatCheck(checkName: String): Boolean {
        val normalized = checkName.lowercase()
        return normalized.contains("reach") || normalized.contains("killaura") || normalized.contains("kill aura")
    }

    private fun isPlayerInCombat(
        player: Player,
        now: Long,
    ): Boolean {
        val combatLogXState = queryCombatLogXInCombat(player)
        if (combatLogXState != CombatState.UNKNOWN) {
            return combatLogXState == CombatState.IN_COMBAT
        }

        val lastActivity = lastCombatActivity[player.uniqueId] ?: return false
        return now - lastActivity <= COMBAT_GRACE_MILLIS
    }

    private fun queryCombatLogXInCombat(player: Player): CombatState {
        val plugin = Bukkit.getPluginManager().getPlugin("CombatLogX") ?: return CombatState.UNKNOWN

        return try {
            val pluginClass = plugin.javaClass
            val getApiMethod = findMethod(pluginClass, "getCombatManager", "getApi", "getAPI")
            val api = getApiMethod?.invoke(plugin) ?: return CombatState.UNKNOWN
            val apiClass = api.javaClass
            val isInCombatMethod =
                findMethod(apiClass, "isInCombat")
                    ?: findMethod(apiClass, "isPlayerInCombat")
                    ?: return CombatState.UNKNOWN

            val params = isInCombatMethod.parameterTypes
            when {
                params.size == 1 && params[0] == UUID::class.java ->
                    toCombatState(isInCombatMethod.invoke(api, player.uniqueId))

                params.size == 1 && Player::class.java.isAssignableFrom(params[0]) ->
                    toCombatState(isInCombatMethod.invoke(api, player))

                else -> CombatState.UNKNOWN
            }
        } catch (_: ReflectiveOperationException) {
            CombatState.UNKNOWN
        } catch (_: SecurityException) {
            CombatState.UNKNOWN
        } catch (_: IllegalArgumentException) {
            CombatState.UNKNOWN
        } catch (_: ClassCastException) {
            CombatState.UNKNOWN
        }
    }

    private fun toCombatState(rawValue: Any?): CombatState {
        val inCombat = rawValue as? Boolean ?: return CombatState.UNKNOWN
        return if (inCombat) {
            CombatState.IN_COMBAT
        } else {
            CombatState.NOT_IN_COMBAT
        }
    }

    private fun findMethod(
        type: Class<*>,
        vararg names: String,
    ): Method? {
        return type.methods.firstOrNull { method ->
            names.any { it.equals(method.name, ignoreCase = true) }
        }
    }

    private fun resolveTargetName(attackerId: UUID): String? {
        val snapshot = lastCombatTarget[attackerId] ?: return null
        val player = Bukkit.getPlayer(snapshot.targetId) ?: return null
        return player.name
    }

    private fun resolveDistance(
        player: Player,
        targetName: String?,
    ): Double? {
        if (targetName.isNullOrBlank()) {
            return null
        }
        val target = Bukkit.getPlayerExact(targetName) ?: return null
        if (target.world.uid != player.world.uid) {
            return null
        }
        return player.location.distance(target.location)
    }

    private fun computeCps(playerId: UUID): Double {
        val timestamps = attackTimestamps[playerId] ?: return 0.0
        val count = timestamps.size
        if (count == 0) {
            return 0.0
        }
        return count / (CPS_WINDOW_MILLIS / 1000.0)
    }

    private fun trimOldHits(
        playerId: UUID,
        now: Long,
    ) {
        val timestamps = attackTimestamps[playerId] ?: return
        val cutoff = now - CPS_WINDOW_MILLIS
        while (true) {
            val head = timestamps.peekFirst() ?: break
            if (head >= cutoff) {
                break
            }
            timestamps.pollFirst()
        }
    }

    private fun resolvePing(player: Player): Int? {
        return try {
            val handleMethod = player.javaClass.getMethod("getHandle")
            val handle = handleMethod.invoke(player)
            val pingField = handle.javaClass.getField("ping")
            pingField.getInt(handle)
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun resolveServerTps(): Double? {
        return try {
            val spigotMethod = Bukkit::class.java.getMethod("spigot")
            val spigot = spigotMethod.invoke(null)
            val getTpsMethod = spigot.javaClass.getMethod("getTPS")
            val tpsArray = getTpsMethod.invoke(spigot) as? DoubleArray ?: return null
            tpsArray.firstOrNull()
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: ClassCastException) {
            null
        }
    }

    data class CombatContextSnapshot(
        val targetName: String,
        val distanceBlocks: Double?,
        val cpsWindowSeconds: Double,
        val cps: Double,
        val pingMillis: Int?,
        val tps: Double?,
    )

    private data class TargetSnapshot(
        val targetId: UUID,
    )

    private enum class CombatState {
        IN_COMBAT,
        NOT_IN_COMBAT,
        UNKNOWN,
    }

    private val attackTimestamps = ConcurrentHashMap<UUID, ConcurrentLinkedDeque<Long>>()
    private val lastCombatTarget = ConcurrentHashMap<UUID, TargetSnapshot>()
    private val lastCombatActivity = ConcurrentHashMap<UUID, Long>()

    private const val CPS_WINDOW_MILLIS = 2000L
    private const val COMBAT_GRACE_MILLIS = 10_000L
}
