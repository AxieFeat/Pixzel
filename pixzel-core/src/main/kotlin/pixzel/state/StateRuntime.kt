package pixzel.state

/**
 * A thread-confined state graph. A Ui can inject its own runtime; externally created states may
 * be borrowed by consumers. Runtime membership is independent of subscription ownership.
 */
interface StateRuntime {

    fun <T> stateOf(initial: T, name: String? = null): MutableState<T>

    /** Tracks the library states read by [expression], including changing conditional reads. */
    fun <T> bind(name: String? = null, expression: () -> T): Binding<T>

    /**
     * Batches nested writes into one consistent notification wave. Writes are not rolled back.
     * If the body throws, pending notifications still complete. Notification failures are
     * suppressed on that original exception; subsequent transactions remain usable.
     */
    fun <T> transaction(block: () -> T): T

    fun interface Factory {

        fun create(): StateRuntime
    }

    companion object {

        fun of(factory: Factory): StateRuntime = factory.create()
    }
}
