package ro.puk3p.polaraddon.application.usecase

import ro.puk3p.polaraddon.application.result.UseCaseResult
import ro.puk3p.polaraddon.domain.port.PlayerKnockbackPort
import ro.puk3p.polaraddon.domain.port.PlayerLookup

class KnockbackPlayerUseCase(
    private val playerLookup: PlayerLookup,
    private val knockbackPort: PlayerKnockbackPort,
) {
    fun execute(
        playerName: String,
        strength: Double,
    ): UseCaseResult {
        val player = playerLookup.find(playerName) ?: return UseCaseResult.PlayerNotFound
        knockbackPort.knockback(player, strength)
        return UseCaseResult.Ok
    }
}
