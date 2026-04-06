package ro.puk3p.polaraddon.domain.port

import org.bukkit.entity.Player
import ro.puk3p.polaraddon.domain.model.RotationTarget

interface PlayerRotationPort {
    fun rotate(
        player: Player,
        target: RotationTarget,
    )
}
