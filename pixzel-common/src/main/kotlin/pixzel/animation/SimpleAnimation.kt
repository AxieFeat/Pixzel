package pixzel.animation

import kotlin.time.Duration

internal class SimpleAnimation<T>(
    internal val from: T,
    private val to: T,
    private val spec: AnimationSpec<T>,
    private val interpolator: Interpolator<T>,
    onValue: (T) -> Unit,
    onFinished: () -> Unit
) : AnimationHandle<T> {

    private val ownerThread = Thread.currentThread()
    private var valueListener: ((T) -> Unit)? = onValue
    private var completionListener: (() -> Unit)? = onFinished
    override val duration: Duration = spec.duration
    override var value: T = if (duration == Duration.ZERO) to else from
        private set
    override var elapsed: Duration = Duration.ZERO
        private set
    override var isRunning: Boolean = duration > Duration.ZERO
        private set
    override var isCancelled: Boolean = false
        private set

    init {
        requireValidDuration(duration)
    }

    internal fun deliverInitial() {
        checkThread()
        try {
            valueListener?.invoke(value)
        } finally {
            if (!isRunning) {
                val finished = completionListener
                releaseListeners()
                finished?.invoke()
            }
        }
    }

    override fun advance(delta: Duration): T {
        checkThread()
        requireValidDelta(delta)
        if (!isRunning || delta == Duration.ZERO) return value
        elapsed = minOf(duration, elapsed + delta)
        if (elapsed == duration) {
            complete()
        } else {
            deliver(spec.sample(from, to, elapsed, interpolator))
        }
        return value
    }

    override fun finish() {
        checkThread()
        if (!isRunning) return
        elapsed = duration
        complete()
    }

    private fun complete() {
        isRunning = false
        val finished = completionListener
        try {
            deliver(to)
        } finally {
            releaseListeners()
            finished?.invoke()
        }
    }

    private fun deliver(next: T) {
        if (next == value) return
        value = next
        valueListener?.invoke(next)
    }

    override fun cancel() {
        checkThread()
        if (isRunning) {
            isRunning = false
            isCancelled = true
        }
        releaseListeners()
    }

    private fun releaseListeners() {
        valueListener = null
        completionListener = null
    }

    private fun checkThread() {
        check(Thread.currentThread() === ownerThread) { "Animation mutations belong to the owning UI thread." }
    }
}

internal class SimpleAnimationController<T>(
    initial: T,
    private val interpolator: Interpolator<T>,
    onValue: (T) -> Unit
) : AnimationController<T> {

    private data class Request<T>(val target: T, val spec: AnimationSpec<T>)

    override var value: T = initial
        private set
    override val isRunning: Boolean get() = active?.isRunning == true || queue.isNotEmpty()
    private var active: SimpleAnimation<T>? = null
    private val queue = ArrayDeque<Request<T>>()
    private var isClosed = false
    private val ownerThread = Thread.currentThread()
    private var valueListener: ((T) -> Unit)? = onValue
    private fun checkThread() {
        check(Thread.currentThread() === ownerThread) { "Animation controller mutations belong to the owning UI thread." }
    }

    override fun transitionTo(target: T, spec: AnimationSpec<T>, policy: RetargetPolicy) {
        checkThread()
        check(!isClosed) { "Animation controller is closed." }
        requireValidDuration(spec.duration)
        if (policy == RetargetPolicy.QUEUE && active?.isRunning == true) {
            queue += Request(target, spec)
            return
        }
        val previous = active
        val from = if (policy == RetargetPolicy.FROM_START && previous != null) previous.from else value
        active?.cancel()
        queue.clear()
        start(from, Request(target, spec))
    }

    private fun start(from: T, request: Request<T>) {
        val animation = SimpleAnimation(from, request.target, request.spec, interpolator, { next ->
            if (value != next) {
                value = next
                valueListener?.invoke(next)
            }
        }, {})
        active = animation
        animation.deliverInitial()
    }

    override fun advance(delta: Duration): T {
        checkThread()
        check(!isClosed) { "Animation controller is closed." }
        requireValidDelta(delta)
        var remaining = delta
        while (true) {
            val running = active ?: break
            val consumed = minOf(remaining, running.duration - running.elapsed)
            running.advance(consumed)
            if (active !== running || isClosed) break
            remaining -= consumed
            if (running.isRunning) break
            if (queue.isEmpty()) break
            start(value, queue.removeFirst())
            if (remaining == Duration.ZERO && active?.isRunning == true) break
        }
        return value
    }

    override fun cancel() {
        checkThread()
        active?.cancel()
        queue.clear()
    }

    override fun close() {
        checkThread()
        if (isClosed) return
        cancel()
        isClosed = true
        valueListener = null
    }
}
