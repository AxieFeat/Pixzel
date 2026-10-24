package pixzel.resource

/** Factories reopen on attachment and close on detach; registration lifetime ends on disposal. */
class AttachmentScope(private val checkOwner: () -> Unit = {}) : AutoCloseable {

    private class Entry(val open: () -> AutoCloseable, var lease: AutoCloseable? = null, var opening: Boolean = false)

    private val entries = mutableListOf<Entry>()
    private var attached = false
    private var closed = false
    private fun open(entry: Entry) {
        if (closed || !attached || entry !in entries || entry.opening || entry.lease != null) return
        entry.opening = true
        val lease = try {
            entry.open()
        } finally {
            entry.opening = false
        }
        // The factory may detach, unregister or close this scope synchronously.
        if (!closed && attached && entry in entries) entry.lease = lease else lease.close()
    }

    fun bind(open: () -> AutoCloseable): Subscription {
        checkOwner()
        check(!closed)
        val entry = Entry(open)
        entries += entry
        try {
            this.open(entry)
        } catch (error: Throwable) {
            entries.remove(entry)
            throw error
        }
        return Subscription {
            checkOwner()
            if (entries.remove(entry)) {
                val lease = entry.lease
                entry.lease = null
                lease?.close()
            }
        }
    }

    fun setAttached(value: Boolean) {
        checkOwner()
        if (closed || attached == value) return
        attached = value
        var failure: Throwable? = null
        entries.toList().forEach { entry ->
            try {
                if (attached && !closed) {
                    open(entry)
                } else {
                    val lease = entry.lease
                    entry.lease = null
                    lease?.close()
                }
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    override fun close() {
        if (closed) return
        checkOwner()
        closed = true
        attached = false
        var failure: Throwable? = null
        entries.toList().forEach { entry ->
            val lease = entry.lease
            entry.lease = null
            try {
                lease?.close()
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
        }
        entries.clear()
        failure?.let { throw it }
    }
}
