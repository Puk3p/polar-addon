package ro.puk3p.polaraddon.application.result

sealed class UseCaseResult {
    object Ok : UseCaseResult()

    object PlayerNotFound : UseCaseResult()
}
