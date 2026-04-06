package ro.puk3p.polaraddon.infrastructure.bukkit.adapter

import org.bukkit.entity.Player
import ro.puk3p.polaraddon.domain.model.RotationTarget
import ro.puk3p.polaraddon.domain.port.PlayerRotationPort

class BukkitPlayerRotator : PlayerRotationPort {
    override fun rotate(
        player: Player,
        target: RotationTarget,
    ) {
        val loc = player.location.clone()
        loc.yaw = target.yaw
        loc.pitch = target.pitch
        player.teleport(loc)
    }
}
