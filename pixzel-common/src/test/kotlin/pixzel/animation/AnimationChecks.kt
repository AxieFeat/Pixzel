package pixzel.animation

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun runAnimationChecks() {
    fun close(actual: Float, expected: Float) = check(abs(actual - expected) < 0.0001f) { "$actual != $expected" }
    val values = mutableListOf<Float>()
    var finished = 0
    val handle = animateFloat(0f, 10f, 1.seconds, onValue = { values += it }, onFinished = { finished++ })
    close(handle.advance(250.milliseconds), 2.5f)
    close(handle.advance(250.milliseconds), 5f)
    close(handle.advance(20.seconds), 10f)
    check(handle.elapsed == 1.seconds && !handle.isRunning && finished == 1)
    handle.finish()
    handle.close()
    check(finished == 1 && values == listOf(0f, 2.5f, 5f, 10f))

    val cancelled = animateFloat(0f, 10f, 1.seconds, onFinished = { error("Cancellation completed animation") })
    cancelled.advance(100.milliseconds)
    cancelled.close()
    cancelled.close()
    close(cancelled.advance(1.seconds), 1f)
    check(cancelled.isCancelled && !cancelled.isRunning)

    val reduced = animateFloat(2f, 8f, Duration.ZERO)
    check(reduced.value == 8f && !reduced.isRunning)
    val eased = animateFloat(0f, 8f, 1.seconds, Easings.EASE_IN)
    close(eased.advance(500.milliseconds), 2f)

    val frames = mutableListOf(Keyframe(200.milliseconds, 8f), Keyframe(600.milliseconds, 4f))
    val immutable = KeyframeSpec(1.seconds, frames)
    frames.clear()
    val keyed = startAnimation(0f, 0f, immutable, Interpolators.FLOAT)
    close(keyed.advance(100.milliseconds), 4f)
    close(keyed.advance(300.milliseconds), 6f)
    close(keyed.advance(400.milliseconds), 2f)
    close(keyed.advance(200.milliseconds), 0f)

    // A nullable frame is a value, not an absent endpoint.
    val nullable = KeyframeSpec<String?>(1.seconds, listOf(Keyframe(500.milliseconds, null)))
    val chooseEnd = Interpolator<String?> { from, to, fraction -> if (fraction < 1f) from else to }
    check(nullable.sample("a", "b", 500.milliseconds, chooseEnd) == null)

    val controller = animationController(0f, Interpolators.FLOAT)
    controller.transitionTo(10f, tween(1.seconds))
    close(controller.advance(500.milliseconds), 5f)
    controller.transitionTo(20f, tween(1.seconds))
    close(controller.advance(500.milliseconds), 12.5f)
    controller.transitionTo(30f, tween(1.seconds), RetargetPolicy.FROM_START)
    close(controller.value, 5f)
    close(controller.advance(500.milliseconds), 17.5f)
    controller.transitionTo(40f, tween(1.seconds), RetargetPolicy.QUEUE)
    close(controller.advance(1.seconds), 35f)
    close(controller.advance(1.seconds), 40f)
    check(!controller.isRunning)
    controller.transitionTo(50f, tween(Duration.ZERO))
    close(controller.value, 50f)
    controller.close()
    controller.close()
    check(runCatching { controller.transitionTo(0f, tween(1.seconds)) }.isFailure)

    check(runCatching { tween<Float>((-1).milliseconds) }.isFailure)
    check(runCatching { tween<Float>(Duration.INFINITE) }.isFailure)
    check(runCatching { handle.advance((-1).milliseconds) }.isFailure)
    var threadFailure: Throwable? = null
    val worker = Thread { threadFailure = runCatching { handle.advance(1.milliseconds) }.exceptionOrNull() }
    worker.start()
    worker.join()
    check(threadFailure is IllegalStateException)
    check(runCatching { KeyframeSpec(1.seconds, listOf(Keyframe(Duration.ZERO, 1f))) }.isFailure)
    check(runCatching { KeyframeSpec(1.seconds, listOf(Keyframe(500.milliseconds, 1f), Keyframe(400.milliseconds, 2f))) }.isFailure)

    var completionAfterFailure = false
    val exceptional = animateFloat(
        0f, 1f, 1.seconds, onValue = { if (it == 1f) error("paint callback") },
        onFinished = { completionAfterFailure = true })
    check(runCatching { exceptional.advance(1.seconds) }.isFailure)
    check(!exceptional.isRunning && exceptional.value == 1f && completionAfterFailure)
}

class AnimationTest {

    @Test
    fun transitionsAndCancellation() = runAnimationChecks()
}

fun main() {
    runAnimationChecks()
    println("Animation checks passed")
}
