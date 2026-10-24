package pixzel.animation

import kotlin.time.Duration

/**
 * Shareable timing strategy. Input is clamped to 0..1; custom output may overshoot.
 */
fun interface Easing {

    fun transform(fraction: Float): Float
}

/**
 * Interpolates a property without prescribing its unit or rendering representation.
 */
fun interface Interpolator<T> {

    fun interpolate(from: T, to: T, fraction: Float): T
}

/**
 * Immutable/shareable configuration; a running cursor belongs to [AnimationHandle].
 */
interface AnimationSpec<T> {

    val duration: Duration

    /**
     * [elapsed] is clamped by the runner to 0..duration. Endpoints must be preserved.
     */
    fun sample(from: T, to: T, elapsed: Duration, interpolator: Interpolator<T>): T
}

/**
 * A deterministic, owner-cancellable transition. Only [advance] moves its clock; suspension is
 * implemented by withholding delta. Close retains the current value and invokes no completion.
 * Mutations belong to the thread which created the handle; callbacks release on completion/close.
 */
interface AnimationHandle<out T> : AutoCloseable {

    val value: T
    val elapsed: Duration
    val duration: Duration
    val isRunning: Boolean
    val isCancelled: Boolean
    fun advance(delta: Duration): T
    fun finish()
    fun cancel()
    override fun close() = cancel()
}

enum class RetargetPolicy {
    /**
     * Cancel the previous transition and start at its current displayed value.
     */
    FROM_CURRENT,

    /**
     * Cancel the previous transition and start at its original starting value.
     */
    FROM_START,

    /**
     * Complete prior requests before beginning this request.
     */
    QUEUE
}

/**
 * A property timeline which can replace or queue transitions, also closed by its owner.
 */
interface AnimationController<T> : AutoCloseable {

    val value: T
    val isRunning: Boolean
    fun transitionTo(target: T, spec: AnimationSpec<T>, policy: RetargetPolicy = RetargetPolicy.FROM_CURRENT)
    fun advance(delta: Duration): T
    fun cancel()
    override fun close()
}
