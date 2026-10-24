package pixzel.text

import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

object TextReveal {

    val NONE: TextRevealBehavior = TextRevealBehavior {
        object : TextRevealRuntime {
            override fun update(context: TextRevealContext) = TextRevealFrame(context.static.layout, clip = context.static.clip)
            override fun reset() = Unit
        }
    }
}

data class HoverRevealConfiguration(
    val trigger: RevealTrigger = RevealTrigger.HOVER,
    val delay: Duration = 600.milliseconds,
    val speed: DpPerSecond = DpPerSecond(32f),
    val pauseAtEnd: Duration = 700.milliseconds,
    val pauseAtStart: Duration = 700.milliseconds,
    val mode: RevealMode = RevealMode.PING_PONG,
    val onDeactivate: RevealExit = RevealExit.RESET,
    val reducedMotion: RevealReducedMotion = RevealReducedMotion.STATIC,
    val repeatCount: Int? = null,
    val minimumOverflow: Float = 0.001f
) {

    init {
        require(listOf(delay, pauseAtEnd, pauseAtStart).all { it.isFinite() && !it.isNegative() })
        require(repeatCount == null || repeatCount > 0)
        require(minimumOverflow.isFinite() && minimumOverflow >= 0)
    }
}

class HoverRevealScope {

    var trigger = RevealTrigger.HOVER
    var delay = 600.milliseconds
    var speed = DpPerSecond(32f)
    var pauseAtEnd = 700.milliseconds
    var pauseAtStart = 700.milliseconds
    var mode = RevealMode.PING_PONG
    var onDeactivate = RevealExit.RESET
    var reducedMotion = RevealReducedMotion.STATIC
    var repeatCount: Int? = null
    var minimumOverflow = 0.001f
    internal fun configuration() = HoverRevealConfiguration(
        trigger, delay, speed, pauseAtEnd, pauseAtStart,
        mode, onDeactivate, reducedMotion, repeatCount, minimumOverflow
    )
}

fun hoverReveal(configure: HoverRevealScope.() -> Unit = {}): TextRevealBehavior =
    hoverReveal(HoverRevealScope().apply(configure).configuration())

fun hoverReveal(configuration: HoverRevealConfiguration): TextRevealBehavior =
    TextRevealBehavior { SimpleHoverRevealRuntime(configuration) }

private class SimpleHoverRevealRuntime(private val configuration: HoverRevealConfiguration) : TextRevealRuntime {

    private var elapsed = 0.0
    private var layout: TextLayout? = null
    private var width = -1f
    private var height = -1f
    private var wasActive = false
    private var lastFrame: TextRevealFrame? = null
    private var exitStart: Float? = null
    private var exitTarget = 0f
    private var exitElapsed = 0.0

    override fun reset() {
        elapsed = 0.0
        wasActive = false
        lastFrame = null
        exitStart = null
        exitElapsed = 0.0
        layout = null
        width = -1f
        height = -1f
    }

    override fun update(context: TextRevealContext): TextRevealFrame {
        if (layout !== context.full || width != context.viewport.width || height != context.viewport.height) {
            reset()
            layout = context.full
            width = context.viewport.width
            height = context.viewport.height
        }
        val distance = max(0f, context.full.size.width - context.viewport.width)
        val staticFrame = TextRevealFrame(context.static.layout, clip = context.static.clip)
        val speed = configuration.speed.value
        if (!context.enabled || distance <= configuration.minimumOverflow || speed == 0f ||
            context.full.lines.size != 1 || context.reducedMotion && configuration.reducedMotion == RevealReducedMotion.STATIC
        ) {
            elapsed = 0.0
            wasActive = false
            exitStart = null
            lastFrame = staticFrame
            return staticFrame
        }
        val active = when (configuration.trigger) {
            RevealTrigger.HOVER -> context.hovered
            RevealTrigger.FOCUS -> context.focused
            RevealTrigger.HOVER_OR_FOCUS -> context.hovered || context.focused
            RevealTrigger.ALWAYS -> true
            RevealTrigger.EXPLICIT -> context.explicitActive
        }
        val dt = context.delta.toDouble(DurationUnit.SECONDS)
        val rtl = context.full.direction == TextDirection.RTL
        val leading = if (rtl) -distance else 0f
        val trailing = if (rtl) 0f else -distance
        if (!active) {
            val previous = lastFrame
            when (configuration.onDeactivate) {
                RevealExit.RESET -> {
                    elapsed = 0.0
                    exitStart = null
                    wasActive = false
                    lastFrame = staticFrame
                    return staticFrame
                }

                RevealExit.RETAIN -> {
                    wasActive = false
                    return (previous ?: staticFrame).copy(needsUpdate = false).also { lastFrame = it }
                }

                RevealExit.FINISH_SEGMENT, RevealExit.RETURN -> {
                    if (wasActive && previous != null && previous.phase != RevealPhase.IDLE && previous.phase != RevealPhase.WAIT) {
                        exitStart = previous.offsetX
                        exitTarget =
                            if (configuration.onDeactivate == RevealExit.FINISH_SEGMENT && previous.phase == RevealPhase.FORWARD) trailing else leading
                        exitElapsed = 0.0
                    }
                    wasActive = false
                    val start = exitStart
                    if (start == null || abs(start - exitTarget) < 0.0001f) {
                        elapsed = 0.0
                        exitStart = null
                        lastFrame = staticFrame
                        return staticFrame
                    }
                    exitElapsed += dt
                    val duration = abs(start - exitTarget).toDouble() / speed
                    if (exitElapsed >= duration) {
                        elapsed = 0.0
                        exitStart = null
                        lastFrame = staticFrame
                        return staticFrame
                    }
                    return TextRevealFrame(
                        context.full, start + (exitTarget - start) * (exitElapsed / duration).toFloat(),
                        needsUpdate = true, phase = if (exitTarget == trailing) RevealPhase.FORWARD else RevealPhase.BACKWARD
                    ).also { lastFrame = it }
                }
            }
        }
        if (exitStart != null) {
            elapsed = 0.0
            exitStart = null
        }
        wasActive = true
        elapsed += dt
        val delay = configuration.delay.toDouble(DurationUnit.SECONDS)
        if (elapsed < delay) return staticFrame.copy(needsUpdate = true, phase = RevealPhase.WAIT).also { lastFrame = it }
        val travel = distance.toDouble() / speed
        val endPause = configuration.pauseAtEnd.toDouble(DurationUnit.SECONDS)
        val startPause = configuration.pauseAtStart.toDouble(DurationUnit.SECONDS)
        val cycle = travel * (if (configuration.mode == RevealMode.PING_PONG) 2 else 1) + endPause + startPause
        var movement = elapsed - delay
        val repeats = configuration.repeatCount
        if (repeats != null && movement >= cycle * repeats) {
            val end = if (configuration.mode == RevealMode.PING_PONG) leading else trailing
            return TextRevealFrame(context.full, end, phase = RevealPhase.COMPLETE).also { lastFrame = it }
        }
        // Constant-time phase selection, even after years of skipped time.
        if (repeats == null && movement >= cycle) {
            movement %= cycle
            elapsed = delay + movement
        } else if (movement >= cycle) movement %= cycle
        val frame = when {
            movement < travel -> TextRevealFrame(
                context.full,
                leading + (trailing - leading) * (movement / travel).toFloat(), needsUpdate = true, phase = RevealPhase.FORWARD
            )

            movement < travel + endPause -> TextRevealFrame(context.full, trailing, needsUpdate = true, phase = RevealPhase.END_PAUSE)
            configuration.mode == RevealMode.PING_PONG && movement < travel * 2 + endPause -> TextRevealFrame(
                context.full,
                trailing + (leading - trailing) * ((movement - travel - endPause) / travel).toFloat(),
                needsUpdate = true, phase = RevealPhase.BACKWARD
            )

            else -> TextRevealFrame(context.full, leading, needsUpdate = true, phase = RevealPhase.START_PAUSE)
        }
        lastFrame = frame
        return frame
    }
}
