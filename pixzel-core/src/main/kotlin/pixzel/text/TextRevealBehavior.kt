package pixzel.text

import pixzel.surface.Size
import kotlin.time.Duration

enum class RevealPhase {
    IDLE,
    WAIT,
    FORWARD,
    END_PAUSE,
    BACKWARD,
    START_PAUSE,
    COMPLETE
}
enum class RevealTrigger {
    HOVER,
    FOCUS,
    HOVER_OR_FOCUS,
    ALWAYS,
    EXPLICIT
}
enum class RevealMode {
    PING_PONG,
    ONE_WAY
}
enum class RevealExit {
    RESET,
    RETAIN,
    FINISH_SEGMENT,
    RETURN
}
enum class RevealReducedMotion {
    STATIC,
    ALLOW
}

@JvmInline
value class DpPerSecond(val value: Float) {

    init {
        require(value.isFinite() && value >= 0) { "Reveal speed must be finite and nonnegative" }
    }
}

val Int.dpPerSecond get() = DpPerSecond(toFloat())
val Float.dpPerSecond get() = DpPerSecond(this)

data class TextRevealContext(
    val full: TextLayout,
    val static: TextOverflowResult,
    val viewport: Size,
    val delta: Duration,
    val hovered: Boolean = false,
    val focused: Boolean = false,
    val explicitActive: Boolean = false,
    val reducedMotion: Boolean = false,
    val enabled: Boolean = true
) {

    init {
        require(delta.isFinite() && !delta.isNegative()) { "Frame delta must be finite and nonnegative" }
    }
}

data class TextRevealFrame(
    val layout: TextLayout,
    val offsetX: Float = 0f,
    val clip: Boolean = true,
    val needsUpdate: Boolean = false,
    val phase: RevealPhase = RevealPhase.IDLE
)

/** Immutable/shareable strategy. Each Text owns a distinct runtime created by this factory. */
fun interface TextRevealBehavior {

    fun createRuntime(): TextRevealRuntime
}

/** Runtime state is never stored in shared themes. reset is also used on detach/disposal. */
interface TextRevealRuntime {

    fun update(context: TextRevealContext): TextRevealFrame
    fun reset()
}
