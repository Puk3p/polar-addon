package ro.puk3p.polaraddon.infrastructure.bukkit.adapter

import org.bukkit.entity.Player
import org.bukkit.util.Vector
import ro.puk3p.polaraddon.domain.port.PlayerKnockbackPort
import kotlin.math.cos
import kotlin.math.sin

class BukkitPlayerKnocker : PlayerKnockbackPort {
    override fun knockback(
        player: Player,
        strength: Double,
    ) {
        val yawRad = Math.toRadians(player.location.yaw.toDouble())
        val horizontal = HORIZONTAL_BASE * strength
        val vertical = VERTICAL_BASE * strength
        val velocity = Vector(sin(yawRad) * horizontal, vertical, -cos(yawRad) * horizontal)

        if (isHorizontallyBlocked(player, velocity)) {
            velocity.x = 0.0
            velocity.z = 0.0
        }

        player.velocity = velocity
    }

    private fun isHorizontallyBlocked(
        player: Player,
        velocity: Vector,
    ): Boolean {
        val direction = velocity.clone().setY(0.0)
        if (direction.lengthSquared() == 0.0) {
            return false
        }

        direction.normalize().multiply(COLLISION_PROBE_DISTANCE)
        val target = player.location.clone().add(direction)
        return COLLISION_PROBE_HEIGHTS.any { yOffset ->
            target.clone().add(0.0, yOffset, 0.0).block.type.isSolid
        }
    }

    private companion object {
        const val HORIZONTAL_BASE = 0.40
        const val VERTICAL_BASE = 0.36
        const val COLLISION_PROBE_DISTANCE = 0.65
        val COLLISION_PROBE_HEIGHTS = doubleArrayOf(0.1, 1.0)
    }
}
