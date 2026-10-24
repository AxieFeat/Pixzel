package pixzel.resource

/**
 * Idempotent cancellation of an observation or host listener.
 */
fun interface Subscription : AutoCloseable {

    override fun close()
}

enum class Ownership {
    OWNED,
    BORROWED
}

/**
 * Closes owned resources in reverse order, including when one cleanup fails.
 */
class ResourceScope : AutoCloseable {

    private val resources = mutableListOf<AutoCloseable>()
    var isClosed: Boolean = false
        private set

    fun <T : AutoCloseable> own(resource: T): T {
        check(!isClosed) { "Resource scope is closed" }
        resources += resource
        return resource
    }

    /**
     * Removes and closes one owned lease, allowing bounded replacement in a long-lived scope.
     */
    fun release(resource: AutoCloseable): Boolean {
        val index = resources.indexOfFirst { it === resource }
        if (index < 0) return false
        resources.removeAt(index)
        resource.close()
        return true
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        var failure: Throwable? = null
        resources.asReversed().forEach {
            try {
                it.close()
            } catch (e: Throwable) {
                if (failure == null) failure = e else failure!!.addSuppressed(e)
            }
        }
        resources.clear()
        failure?.let { throw it }
    }
}
