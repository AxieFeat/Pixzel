package pixzel.state

/** Creates an independent runtime confined to the calling thread. */
fun stateRuntime(): StateRuntime = SimpleStateRuntime()

private val threadRuntime = ThreadLocal.withInitial<StateRuntime> { SimpleStateRuntime() }

/**
 * Convenience graph for external model state on the calling thread. It does not own a Ui or any
 * binding. Pass [runtime] explicitly for a Ui-local graph and close every binding with its owner.
 */
fun <T> stateOf(initial: T, runtime: StateRuntime = threadRuntime.get(), name: String? = null): MutableState<T> =
    runtime.stateOf(initial, name)

fun <T> bind(
    runtime: StateRuntime = threadRuntime.get(),
    name: String? = null,
    expression: () -> T
): Binding<T> = runtime.bind(name, expression)

/** A read-only derived value with explicit ownership, using the same tracked graph as [bind]. */
fun <T> derivedStateOf(
    runtime: StateRuntime = threadRuntime.get(),
    name: String? = null,
    expression: () -> T
): Binding<T> = runtime.bind(name, expression)

fun <T> transaction(runtime: StateRuntime = threadRuntime.get(), block: () -> T): T = runtime.transaction(block)
