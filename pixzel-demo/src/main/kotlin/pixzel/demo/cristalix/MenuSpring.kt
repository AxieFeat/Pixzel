package pixzel.demo.cristalix

import kotlin.math.abs
import kotlin.math.exp

/**
 * Exact critically damped motion stays stable when frames arrive at different intervals.
 */
internal class MenuSpring(var value: Float = 0f, private val frequency: Float = 16f) {

    var target = value
    private var velocity = 0f

    fun step(seconds: Float): Boolean {
        if (abs(value - target) < SETTLE_EPSILON && abs(velocity) < SETTLE_EPSILON) {
            value = target
            velocity = 0f
            return false
        }

        val dt = seconds.coerceIn(0f, MAXIMUM_STEP)
        val offset = value - target
        // Solve the critically damped spring analytically instead of accumulating Euler integration error.
        // The exponential term makes small and large frame intervals converge to the same trajectory.
        val combined = velocity + frequency * offset
        val decay = exp(-frequency * dt)
        value = target + (offset + combined * dt) * decay
        velocity = (velocity - frequency * combined * dt) * decay
        return true
    }

    fun snap(target: Float = this.target) {
        this.target = target
        value = target
        velocity = 0f
    }

    companion object {

        private const val SETTLE_EPSILON = 0.001f
        private const val MAXIMUM_STEP = 0.05f
    }
}
