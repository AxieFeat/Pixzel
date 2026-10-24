package pixzel.animation

import kotlin.time.Duration

/** Monotonic simulation time, advanced explicitly and frozen during suspension. */
class FakeClock {

    var elapsed: Duration = Duration.ZERO
        private set
    var isPaused: Boolean = false
    fun advance(delta: Duration): Duration {
        require(delta.isFinite() && delta >= Duration.ZERO) { "Clock delta must be finite and nonnegative." }
        if (!isPaused) {
            val next = elapsed + delta
            require(next.isFinite()) { "Clock elapsed time overflowed." }
            elapsed = next
        }
        return elapsed
    }

    companion object {

        fun of() = FakeClock()
    }
}
