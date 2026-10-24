package pixzel.state

import pixzel.resource.Subscription
import kotlin.reflect.KProperty

/**
 * A structurally compared observable value. Observe does not deliver an initial value.
 */
interface State<out T> {

    val value: T

    /**
     * Returns an idempotent handle. Listeners run on the state's owning thread.
     */
    fun observe(listener: (@UnsafeVariance T) -> Unit): Subscription
}

/**
 * Replacing the value notifies observers; mutating a collection in place does not.
 */
interface MutableState<T> : State<T> {

    override var value: T
}

/**
 * A tracked expression owned by its caller. Closing disconnects its dependencies and listeners;
 * its last successful value remains available as a snapshot. Close the handle with its owner.
 */
interface Binding<out T> : State<T>, AutoCloseable {

    val isClosed: Boolean
    override fun close()
}

operator fun <T> State<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value

operator fun <T> MutableState<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    this.value = value
}
