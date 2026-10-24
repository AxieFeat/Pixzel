package pixzel.state

import pixzel.resource.Subscription

/** Shared implementation; no host, renderer, frame polling, or global service lookup is needed. */
internal class SimpleStateRuntime : StateRuntime {

    private val ownerThread = Thread.currentThread()
    private var nextId = 0L
    private var transactionDepth = 0
    private var flushing = false
    private val pending = linkedMapOf<SimpleState<*>, Any?>()
    private val dirty = linkedSetOf<SimpleBinding<*>>()
    private val evaluationStack = mutableListOf<SimpleBinding<*>>()
    private var collector: MutableSet<SimpleState<*>>? = null

    override fun <T> stateOf(initial: T, name: String?): MutableState<T> {
        checkThread()
        return SimpleMutableState(this, initial, name ?: "state#${++nextId}")
    }

    override fun <T> bind(name: String?, expression: () -> T): Binding<T> {
        checkThread()
        val binding = SimpleBinding(this, name ?: "binding#${++nextId}", expression)
        try {
            binding.refresh()
        } catch (failure: Throwable) {
            binding.close()
            throw failure
        }
        return binding
    }

    override fun <T> transaction(block: () -> T): T {
        checkThread()
        transactionDepth++
        var bodyFailure: Throwable? = null
        try {
            return block()
        } catch (failure: Throwable) {
            bodyFailure = failure
            throw failure
        } finally {
            transactionDepth--
            if (transactionDepth == 0) {
                try {
                    flush()
                } catch (notificationFailure: Throwable) {
                    if (bodyFailure != null) bodyFailure.addSuppressed(notificationFailure)
                    else throw notificationFailure
                }
            }
        }
    }

    internal fun checkThread() {
        check(Thread.currentThread() === ownerThread) {
            "State runtime belongs to '${ownerThread.name}'; post mutations to its owning UI thread."
        }
    }

    internal fun checkCanWrite() {
        checkThread()
        val evaluating = activeEvaluation.get()
        check(evaluating == null) {
            "State cannot be mutated while evaluating binding '${evaluating?.evaluationStack?.lastOrNull()?.name}'."
        }
    }

    internal fun read(state: SimpleState<*>) {
        checkThread()
        val activeRuntime = activeEvaluation.get()
        check(activeRuntime == null || activeRuntime === this) {
            "Binding dependencies must belong to the same StateRuntime."
        }
        collector?.add(state)
    }

    internal fun changed(state: SimpleState<*>, previous: Any?) {
        if (!pending.containsKey(state)) pending[state] = previous
        state.dependents.toList().forEach { invalidate(it) }
        if (transactionDepth == 0 && evaluationStack.isEmpty()) flush()
    }

    private fun invalidate(binding: SimpleBinding<*>) {
        if (binding.isClosed) return
        if (binding.isDirty) {
            dirty += binding
            return
        }
        binding.isDirty = true
        dirty += binding
        binding.dependents.toList().forEach { invalidate(it) }
    }

    internal fun <T> evaluate(binding: SimpleBinding<T>, expression: () -> T): T {
        val repeatedAt = evaluationStack.indexOf(binding)
        check(repeatedAt < 0) {
            "Binding dependency cycle: " +
                    (evaluationStack.drop(repeatedAt.coerceAtLeast(0)) + binding).joinToString(" -> ") { it.name }
        }
        val previousCollector = collector
        val previousRuntime = activeEvaluation.get()
        val reads = linkedSetOf<SimpleState<*>>()
        evaluationStack += binding
        collector = reads
        activeEvaluation.set(this)
        try {
            return expression()
        } finally {
            // Attempted reads are retained even after failure, so a source change can recover it.
            binding.replaceDependencies(reads)
            collector = previousCollector
            evaluationStack.removeAt(evaluationStack.lastIndex)
            activeEvaluation.set(previousRuntime)
            dirty -= binding
        }
    }

    internal fun remove(binding: SimpleBinding<*>) {
        dirty -= binding
        pending -= binding
    }

    private fun flush() {
        if (flushing || transactionDepth != 0) return
        flushing = true
        var failure: Throwable? = null
        fun record(problem: Throwable) {
            if (failure == null) failure = problem else if (failure !== problem) failure!!.addSuppressed(problem)
        }
        try {
            var waves = 0
            while (dirty.isNotEmpty() || pending.isNotEmpty()) {
                check(++waves <= MAX_NOTIFICATION_WAVES) {
                    "State notification cycle exceeded $MAX_NOTIFICATION_WAVES waves; " +
                            "pending: ${pending.keys.joinToString { it.name }}"
                }
                // Resolve all derivations before any observer sees this wave (including diamonds).
                while (dirty.isNotEmpty()) {
                    val binding = dirty.first()
                    try {
                        binding.refresh()
                    } catch (problem: Throwable) {
                        record(problem)
                    } finally {
                        dirty -= binding
                    }
                }
                val wave = pending.toList()
                pending.clear()
                wave.forEach { (state, oldValue) ->
                    if (!state.sameAsSnapshot(oldValue)) state.notifyObservers(::record)
                }
            }
        } catch (problem: Throwable) {
            record(problem)
            // A feedback loop must not leave the scheduler permanently blocked.
            pending.clear()
            dirty.clear()
        } finally {
            flushing = false
        }
        failure?.let { throw it }
    }

    private companion object {

        const val MAX_NOTIFICATION_WAVES = 1_000
        val activeEvaluation = ThreadLocal<SimpleStateRuntime?>()
    }
}

internal abstract class SimpleState<T>(
    protected val runtime: SimpleStateRuntime,
    internal val name: String
) : State<T> {

    internal val dependents = linkedSetOf<SimpleBinding<*>>()
    private val listeners = linkedMapOf<Any, (T) -> Unit>()
    protected abstract fun snapshot(): T

    override fun observe(listener: (T) -> Unit): Subscription {
        runtime.checkThread()
        val key = Any()
        listeners[key] = listener
        return Subscription {
            runtime.checkThread()
            listeners.remove(key)
        }
    }

    internal fun sameAsSnapshot(other: Any?): Boolean = snapshot() == other

    internal fun notifyObservers(recordFailure: (Throwable) -> Unit) {
        val value = snapshot()
        listeners.toList().forEach { (key, listener) ->
            if (listeners[key] === listener) {
                try {
                    listener(value)
                } catch (failure: Throwable) {
                    recordFailure(failure)
                }
            }
        }
    }

    protected fun clearObservers() = listeners.clear()
}

internal class SimpleMutableState<T>(runtime: SimpleStateRuntime, initial: T, name: String) :
    SimpleState<T>(runtime, name), MutableState<T> {

    private var current = initial
    override var value: T
        get() {
            runtime.read(this)
            return current
        }
        set(value) {
            runtime.checkCanWrite()
            if (current == value) return
            val previous = current
            current = value
            runtime.changed(this, previous)
        }

    override fun snapshot(): T = current
}

internal class SimpleBinding<T>(runtime: SimpleStateRuntime, name: String, expression: () -> T) :
    SimpleState<T>(runtime, name), Binding<T> {

    private var expression: (() -> T)? = expression
    private var initialized = false
    private var current: Any? = null
    private var failure: Throwable? = null
    private var dependencies = emptySet<SimpleState<*>>()
    internal var isDirty = true
    override var isClosed: Boolean = false
        private set

    override val value: T
        get() {
            runtime.read(this)
            if (isDirty && !isClosed) refresh()
            failure?.let { throw it }
            return snapshot()
        }

    override fun observe(listener: (T) -> Unit): Subscription {
        check(!isClosed) { "Cannot observe closed binding '$name'." }
        return super.observe(listener)
    }

    @Suppress("UNCHECKED_CAST")
    override fun snapshot(): T = current as T

    internal fun refresh() {
        if (!isDirty || isClosed) return
        runtime.checkThread()
        try {
            val next = runtime.evaluate(this, checkNotNull(expression))
            val previous = current
            val wasInitialized = initialized
            current = next
            initialized = true
            isDirty = false
            failure = null
            if (wasInitialized && previous != next) runtime.changed(this, previous)
        } catch (problem: Throwable) {
            isDirty = false
            failure = problem
            throw problem
        }
    }

    internal fun replaceDependencies(next: Set<SimpleState<*>>) {
        (dependencies - next).forEach { it.dependents -= this }
        (next - dependencies).forEach { it.dependents += this }
        dependencies = next
    }

    override fun close() {
        runtime.checkThread()
        if (isClosed) return
        isClosed = true
        isDirty = false
        expression = null
        failure = null
        replaceDependencies(emptySet())
        clearObservers()
        dependents.clear()
        runtime.remove(this)
    }
}
