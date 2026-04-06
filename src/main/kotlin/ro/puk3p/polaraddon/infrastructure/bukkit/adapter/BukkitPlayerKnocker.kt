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
        player.velocity = Vector(sin(yawRad) * horizontal, vertical, -cos(yawRad) * horizontal)
    }

    private companion object {
        const val HORIZONTAL_BASE = 0.40
        const val VERTICAL_BASE = 0.36
    }
}
