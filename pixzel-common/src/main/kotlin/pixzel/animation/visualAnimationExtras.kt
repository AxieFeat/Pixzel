package pixzel.animation

import pixzel.component.Element
import pixzel.graphics.Color
import pixzel.surface.Transform
import kotlin.time.Duration

fun animateColor(
    from: Color, to: Color, duration: Duration, easing: Easing = Easings.LINEAR,
    onValue: (Color) -> Unit = {}, onFinished: () -> Unit = {}
): AnimationHandle<Color> =
    startAnimation(from, to, tween(duration, easing), Interpolators.COLOR, onValue, onFinished)

fun animateOpacity(
    from: Float, to: Float, duration: Duration, easing: Easing = Easings.LINEAR,
    onValue: (Float) -> Unit = {}, onFinished: () -> Unit = {}
): AnimationHandle<Float> {
    require(from.isFinite() && to.isFinite() && from in 0f..1f && to in 0f..1f)
    return startAnimation(from, to, tween(duration, easing), Interpolators.OPACITY, onValue, onFinished)
}

fun animateTransform(
    from: Transform, to: Transform, duration: Duration, easing: Easing = Easings.LINEAR,
    interpolator: Interpolator<Transform> = Interpolators.TRANSFORM,
    onValue: (Transform) -> Unit = {}, onFinished: () -> Unit = {}
): AnimationHandle<Transform> =
    startAnimation(from, to, tween(duration, easing), interpolator, onValue, onFinished)

/**
 * Creates one reusable property controller owned by this Element. Its Ui clock advances it while visible
 * and attached; detachment/hidden visibility pauses it. Reduced motion immediately selects the latest
 * requested target, including queued targets. Disposal cancels the controller and removes its callback.
 * [apply] receives [initial] immediately and then changed values. It should mutate a paint property;
 * layout mutation is an explicit caller choice.
 */
fun <T> Element.propertyTransition(initial: T, interpolator: Interpolator<T>, apply: Element.(T) -> Unit): AnimationController<T> {
    ui?.assertThread()
    check(!isDisposed) { "Element is disposed" }
    apply(initial)
    check(!isDisposed) { "Property initialization disposed its Element" }
    return resources.own(ElementAnimationController(this, initial, interpolator, { apply(it) }))
}

fun Element.opacityTransition(): AnimationController<Float> {
    ui?.assertThread()
    check(!isDisposed) { "Element is disposed" }
    return resources.own(ElementAnimationController(this, opacity, Interpolators.OPACITY, { opacity = it }, {
        require(it.isFinite() && it in 0f..1f) { "Opacity must be in 0..1" }
    }))
}

fun Element.transformTransition(interpolator: Interpolator<Transform> = Interpolators.TRANSFORM): AnimationController<Transform> =
    propertyTransition(paintTransform, interpolator) { paintTransform = it }

fun Element.colorTransition(initial: Color, apply: Element.(Color) -> Unit): AnimationController<Color> =
    propertyTransition(initial, Interpolators.COLOR, apply)

private class ElementAnimationController<T>(
    private val owner: Element,
    initial: T,
    interpolator: Interpolator<T>,
    apply: (T) -> Unit,
    private val validate: (T) -> Unit = {}
) : AnimationController<T> {

    private val thread = Thread.currentThread()
    private val delegate = animationController(initial, interpolator, apply)
    private var latestTarget = initial
    private var closed = false
    private val tick = owner.frameUpdates.add { delta ->
        val previous = value
        if (isRunning) advance(delta)
        previous != value
    }
    override val value: T get() = delegate.value
    override val isRunning: Boolean get() = delegate.isRunning
    private fun checkOwner() {
        check(Thread.currentThread() === thread)
        check(!closed) { "Animation controller is closed" }
    }

    override fun transitionTo(target: T, spec: AnimationSpec<T>, policy: RetargetPolicy) {
        checkOwner()
        requireValidDuration(spec.duration)
        validate(target)
        latestTarget = target
        delegate.transitionTo(
            target, if (owner.ui?.settings?.isReducedMotion == true) tween(Duration.ZERO) else spec,
            if (owner.ui?.settings?.isReducedMotion == true) RetargetPolicy.FROM_CURRENT else policy
        )
    }

    override fun advance(delta: Duration): T {
        checkOwner()
        requireValidDelta(delta)
        if (isRunning && owner.ui?.settings?.isReducedMotion == true) delegate.transitionTo(latestTarget, tween(Duration.ZERO))
        else delegate.advance(delta)
        return value
    }

    override fun cancel() {
        checkOwner()
        delegate.cancel()
    }

    override fun close() {
        check(Thread.currentThread() === thread)
        if (closed) return
        closed = true
        tick.close()
        delegate.close()
    }
}
