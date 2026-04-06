package ro.puk3p.polaraddon.domain.model

import kotlin.random.Random

data class RotationTarget(val yaw: Float, val pitch: Float) {
    companion object {
        fun random() =
            RotationTarget(
                yaw = Random.nextFloat() * 360f,
                pitch = 0f,
            )
    }
}
