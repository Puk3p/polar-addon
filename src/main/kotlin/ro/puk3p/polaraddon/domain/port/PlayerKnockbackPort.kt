package ro.puk3p.polaraddon.domain.port

import org.bukkit.entity.Player

interface PlayerKnockbackPort {
    fun knockback(
        player: Player,
        strength: Double,
    )
}
