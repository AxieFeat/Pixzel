package pixzel.component

import pixzel.Ui
import pixzel.input.EventPhase
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.resource.Subscription

internal class SimpleOverlayManager(private val owner: Ui) : OverlayManager {
    private data class Entry(val element: Element, val escape: Boolean, val outside: Boolean, val dismiss: () -> Unit)

    private val entries = mutableListOf<Entry>()
    private var closed = false
    private fun active(entry: Entry) =
        generateSequence(entry.element as Element?) { it.parent }.all { it.ui === owner && !it.isDisposed && it.visibility == Visibility.VISIBLE }

    private val listener = owner.root.onEvent(EventPhase.CAPTURE) { event ->
        val top = entries.lastOrNull(::active) ?: return@onEvent
        val key = event.event as? KeyEvent
        val pointer = event.event as? PointerEvent
        val outside = pointer?.action == PointerAction.DOWN && generateSequence(event.target as Element?) { it.parent }.none { it === top.element }
        if (top.escape && key?.key == Key.ESCAPE && key.action == KeyAction.DOWN || top.outside && outside) {
            event.consume()
            event.preventDefault()
            event.stopPropagation()
            top.dismiss()
        }
    }

    override fun register(element: Element, dismissOnEscape: Boolean, dismissOnOutside: Boolean, dismiss: () -> Unit): Subscription {
        owner.assertThread()
        check(!closed)
        val entry = Entry(element, dismissOnEscape, dismissOnOutside, dismiss)
        entries += entry
        return Subscription { entries.remove(entry) }
    }

    override fun dismissTop(): Boolean {
        owner.assertThread()
        val top = entries.lastOrNull(::active) ?: return false
        top.dismiss()
        return true
    }

    override fun close() {
        if (closed) return
        closed = true
        listener.close()
        entries.clear()
    }
}
