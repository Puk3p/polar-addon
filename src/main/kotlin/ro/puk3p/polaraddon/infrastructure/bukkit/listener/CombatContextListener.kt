package ro.puk3p.polaraddon.infrastructure.bukkit.listener

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import ro.puk3p.polaraddon.polar.CombatContextService

class CombatContextListener : Listener {
    @EventHandler
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return
        CombatContextService.recordHit(attacker, victim)
    }
}
