package ro.puk3p.polaraddon.application.usecase

import ro.puk3p.polaraddon.application.result.UseCaseResult
import ro.puk3p.polaraddon.domain.model.RotationTarget
import ro.puk3p.polaraddon.domain.port.PlayerLookup
import ro.puk3p.polaraddon.domain.port.PlayerRotationPort

class RotatePlayerUseCase(
    private val playerLookup: PlayerLookup,
    private val rotationPort: PlayerRotationPort,
) {
    fun execute(
        playerName: String,
        target: RotationTarget,
    ): UseCaseResult {
        val player = playerLookup.find(playerName) ?: return UseCaseResult.PlayerNotFound
        rotationPort.rotate(player, target)
        return UseCaseResult.Ok
    }
}
