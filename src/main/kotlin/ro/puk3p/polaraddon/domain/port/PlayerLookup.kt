package ro.puk3p.polaraddon.domain.port

import org.bukkit.entity.Player

interface PlayerLookup {
    fun find(name: String): Player?
}
