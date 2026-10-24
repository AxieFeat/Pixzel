package pixzel.animation

import kotlin.time.Duration

object Easings {

    val LINEAR: Easing = Easing { it }
    val EASE_IN: Easing = Easing { it * it }
    val EASE_OUT: Easing = Easing { 1f - (1f - it) * (1f - it) }
    val EASE_IN_OUT: Easing = Easing { it * it * (3f - 2f * it) }
}

/**
 * A duration of zero immediately selects the target, useful for reduced-motion policies.
 */
class TweenSpec<T>(override val duration: Duration, val easing: Easing = Easings.LINEAR) : AnimationSpec<T> {

    init {
        requireValidDuration(duration)
    }

    override fun sample(from: T, to: T, elapsed: Duration, interpolator: Interpolator<T>): T {
        if (elapsed >= duration) return to
        if (elapsed <= Duration.ZERO) return from
        val fraction = (elapsed / duration).toFloat().coerceIn(0f, 1f)
        val eased = easing.transform(fraction)
        require(eased.isFinite()) { "Easing returned a non-finite fraction." }
        return interpolator.interpolate(from, to, eased)
    }
}

/**
 * [easing] applies to the segment ending at this frame.
 */
data class Keyframe<T>(val time: Duration, val value: T, val easing: Easing = Easings.LINEAR)

/**
 * Interior frames in strictly ascending time order. Start/end values come from the runner;
 * [endEasing] applies between the last interior frame and the target. Frames are copied.
 */
class KeyframeSpec<T>(
    override val duration: Duration,
    frames: List<Keyframe<T>>,
    val endEasing: Easing = Easings.LINEAR
) : AnimationSpec<T> {

    val frames: List<Keyframe<T>> = frames.toList()

    init {
        requireValidDuration(duration)
        var previous = Duration.ZERO
        this.frames.forEach { frame ->
            require(frame.time.isFinite() && frame.time > previous && frame.time < duration) {
                "Keyframe times must be strictly ascending, greater than zero, and below duration."
            }
            previous = frame.time
        }
    }

    override fun sample(from: T, to: T, elapsed: Duration, interpolator: Interpolator<T>): T {
        if (elapsed >= duration) return to
        if (elapsed <= Duration.ZERO) return from
        var low = 0
        var high = frames.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (frames[middle].time < elapsed) low = middle + 1 else high = middle
        }
        val left = frames.getOrNull(low - 1)
        val right = frames.getOrNull(low)
        val start = left?.time ?: Duration.ZERO
        val end = right?.time ?: duration
        val fraction = ((elapsed - start) / (end - start)).toFloat().coerceIn(0f, 1f)
        val eased = (right?.easing ?: endEasing).transform(fraction)
        require(eased.isFinite()) { "Easing returned a non-finite fraction." }
        return interpolator.interpolate(if (left == null) from else left.value, if (right == null) to else right.value, eased)
    }
}

fun <T> tween(duration: Duration, easing: Easing = Easings.LINEAR): AnimationSpec<T> = TweenSpec(duration, easing)

fun <T> keyframes(
    duration: Duration,
    frames: List<Keyframe<T>>,
    endEasing: Easing = Easings.LINEAR
): AnimationSpec<T> = KeyframeSpec(duration, frames, endEasing)

/**
 * Initial delivery happens immediately. Subsequent callbacks run only for structural changes.
 */
fun <T> startAnimation(
    from: T,
    to: T,
    spec: AnimationSpec<T>,
    interpolator: Interpolator<T>,
    onValue: (T) -> Unit = {},
    onFinished: () -> Unit = {}
): AnimationHandle<T> = SimpleAnimation(from, to, spec, interpolator, onValue, onFinished).also { it.deliverInitial() }

fun animateFloat(
    from: Float,
    to: Float,
    duration: Duration,
    easing: Easing = Easings.LINEAR,
    onValue: (Float) -> Unit = {},
    onFinished: () -> Unit = {}
): AnimationHandle<Float> = startAnimation(from, to, TweenSpec(duration, easing), Interpolators.FLOAT, onValue, onFinished)

fun <T> animationController(
    initial: T,
    interpolator: Interpolator<T>,
    onValue: (T) -> Unit = {}
): AnimationController<T> = SimpleAnimationController(initial, interpolator, onValue)

internal fun requireValidDuration(duration: Duration) {
    require(duration.isFinite() && duration >= Duration.ZERO) { "Animation duration must be finite and nonnegative." }
}

internal fun requireValidDelta(delta: Duration) {
    require(delta.isFinite() && delta >= Duration.ZERO) { "Animation delta must be finite and nonnegative." }
}
