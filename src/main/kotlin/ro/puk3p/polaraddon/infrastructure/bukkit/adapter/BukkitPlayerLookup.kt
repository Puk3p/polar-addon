package ro.puk3p.polaraddon.infrastructure.bukkit.adapter

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import ro.puk3p.polaraddon.domain.port.PlayerLookup

class BukkitPlayerLookup : PlayerLookup {
    override fun find(name: String): Player? = Bukkit.getPlayerExact(name)
}
