package pixzel.component

import pixzel.resource.Subscription
import kotlin.time.Duration

/** Owner-scoped callbacks for public behaviors and transitions, advanced by the UI clock. */
class FrameUpdates(private val checkOwner: () -> Unit = {}) : AutoCloseable {

    private val callbacks = linkedMapOf<Any, (Duration) -> Boolean>()
    private var closed = false
    val isEmpty get() = callbacks.isEmpty()
    fun add(callback: (Duration) -> Boolean): Subscription {
        checkOwner()
        check(!closed)
        val token = Any()
        callbacks[token] = callback
        return Subscription {
            checkOwner()
            callbacks.remove(token)
        }
    }

    fun advance(delta: Duration): Boolean {
        checkOwner()
        var changed = false
        var failure: Throwable? = null
        callbacks.toList().forEach { (token, callback) ->
            if (callbacks[token] === callback) try {
                if (callback(delta)) changed = true
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
        }
        failure?.let { throw it }
        return changed
    }

    override fun close() {
        if (closed) return
        closed = true
        callbacks.clear()
    }
}
