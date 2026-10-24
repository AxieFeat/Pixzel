package pixzel.component

import pixzel.Ui
import pixzel.input.EventPhase
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.PointerPolicy
import pixzel.input.WindowFocus
import pixzel.layout.unit.dp
import pixzel.resource.Subscription
import pixzel.surface.Placement
import pixzel.surface.Rect
import pixzel.surface.ReferenceBox
import pixzel.surface.Vec2

/**
 * An owner-scoped typed menu. Right-button presses open at root-dp pointer coordinates; [openFor]
 * offers the same actions to keyboard bindings. Menu rows retain the standard arrow navigation,
 * semantics, outside/Escape dismissal and focus restoration. Detach closes an open menu; disposal
 * also removes the opening handler. The borrowed [ui] is never closed by this controller.
 */
class ContextMenu<T> private constructor(
    private val ui: Ui,
    val owner: Element,
    initial: List<MenuItem<T>>,
    private val pointerButton: Int,
    private val onSelect: (MenuItem<T>) -> Unit
) : AutoCloseable {

    private var model = initial.toList()
    private var closed = false
    private var menu: Menu<T>? = null
    private var anchor: Element? = null
    private var update: Subscription? = null
    private var pointerHandler: Subscription? = null
    private var releaseHandler: Subscription? = null
    private var focusHandler: Subscription? = null
    private var attachment: Subscription? = null

    var items: List<MenuItem<T>>
        get() = model
        set(value) {
            ui.assertThread()
            check(!closed) { "ContextMenu is closed" }
            requireUnique(value)
            closeMenu()
            model = value.toList()
        }
    val isOpen: Boolean get() = menu?.isClosed == false
    val isClosed: Boolean get() = closed
    val element: Element? get() = menu?.takeUnless { it.isClosed }?.element

    init {
        ui.assertThread()
        require(pointerButton >= 0)
        require(!owner.isDisposed && (owner.ui == null || owner.ui === ui)) { "ContextMenu owner belongs to another Ui or is disposed" }
        requireUnique(model)
        try {
            pointerHandler = owner.onEvent(EventPhase.BUBBLE) { event ->
                val pointer = event.event as? PointerEvent ?: return@onEvent
                if (!closed && pointer.action == PointerAction.DOWN && pointer.button == pointerButton && usable(owner)) {
                    event.consume()
                    event.preventDefault()
                    event.stopPropagation()
                    if (openAt(pointer.position)) trackOpeningRelease(pointer.pointerId)
                }
            }
            attachment = owner.attachments.bind { Subscription { closeMenu() } }
            owner.resources.own(this)
        } catch (error: Throwable) {
            try {
                close()
            } catch (cleanup: Throwable) {
                if (cleanup !== error) error.addSuppressed(cleanup)
            }
            throw error
        }
    }

    private fun requireUnique(items: List<MenuItem<T>>) {
        require(items.map { it.key }.toSet().size == items.size) { "ContextMenu keys must be unique" }
    }

    private fun usable(element: Element) =
        generateSequence(element as Element?) { it.parent }.all { it.ui === ui && !it.isDisposed && it.enabled && it.visibility == Visibility.VISIBLE }

    private fun clearOpeningRelease() {
        val previous = releaseHandler
        releaseHandler = null
        previous?.close()
    }

    private fun trackOpeningRelease(pointerId: Int) {
        clearOpeningRelease()
        releaseHandler = ui.root.onEvent(EventPhase.CAPTURE) { event ->
            if (closed) return@onEvent
            val pointer = event.event as? PointerEvent ?: return@onEvent
            if (
                pointer.pointerId == pointerId &&
                (pointer.action == PointerAction.CANCEL || pointer.action == PointerAction.UP && pointer.button == pointerButton)
            ) {
                event.consume()
                event.preventDefault()
                event.stopPropagation()
                clearOpeningRelease()
            }
        }
    }

    /** Opens at a point already expressed in UI root dp, not logical-window or framebuffer pixels. */
    fun openAt(position: Vec2): Boolean = open({ position })

    /** Opens below a logical descendant's current root bounds, following resize/rearrangement. */
    fun openFor(element: Element = owner): Boolean {
        ui.assertThread()
        check(!closed) { "ContextMenu is closed" }
        require(generateSequence(element as Element?) { it.parent }.any { it === owner }) { "ContextMenu invocation target must belong to its owner" }
        return open({
            val bounds = element.surface.mapRect(Rect(0f, 0f, element.surface.bounds.width, element.surface.bounds.height))
            Vec2(bounds.x, bounds.bottom)
        }, element)
    }

    private fun open(position: () -> Vec2, target: Element = owner): Boolean {
        ui.assertThread()
        check(!closed) { "ContextMenu is closed" }
        if (!usable(owner) || !usable(target)) return false
        closeMenu()
        val pointAnchor = ui.components.surface().apply {
            layout.width = 0.dp
            layout.height = 0.dp
            portalTarget = this@ContextMenu.ui.root
            pointerPolicy = PointerPolicy.PASS_THROUGH
            placement = Placement(offset = position(), referenceBox = ReferenceBox.BORDER)
        }
        anchor = pointAnchor
        try {
            owner.add(pointAnchor)
            val opened = Menu.of(ui, pointAnchor, model) { item ->
                try {
                    onSelect(item)
                } finally {
                    closeMenu()
                }
            }
            if (closed || !usable(owner)) {
                opened.close()
                closeMenu()
                return false
            }
            menu = opened
            opened.element.resources.own(Subscription {
                if (menu === opened) menu = null
                if (anchor === pointAnchor) {
                    anchor = null
                    val previous = update
                    update = null
                    previous?.close()
                    val focus = focusHandler
                    focusHandler = null
                    focus?.close()
                    clearOpeningRelease()
                }
                if (!pointAnchor.isDisposed) pointAnchor.close()
            })
            // WindowFocus is handled before routed dispatch. Observe only while this menu is open.
            focusHandler = ui.host.subscribe { event ->
                if (event is WindowFocus && !event.focused) {
                    var failure: Throwable? = null
                    try {
                        closeMenu()
                    } catch (error: Throwable) {
                        failure = error
                    }
                    try {
                        ui.focus.clear()
                    } catch (error: Throwable) {
                        if (failure == null) failure = error else if (failure !== error) failure.addSuppressed(error)
                    }
                    failure?.let { throw it }
                }
                false
            }
            update = ui.root.frameUpdates.add {
                if (!usable(owner) || !usable(target)) {
                    closeMenu()
                    true
                } else {
                    val next = Placement(offset = position(), referenceBox = ReferenceBox.BORDER)
                    if (pointAnchor.placement != next) {
                        pointAnchor.placement = next
                        true
                    } else false
                }
            }
            return true
        } catch (error: Throwable) {
            try {
                closeMenu()
            } catch (cleanup: Throwable) {
                if (cleanup !== error) error.addSuppressed(cleanup)
            }
            throw error
        }
    }

    /** Dismisses the current popup while leaving the owner's invocation handler reusable. */
    fun closeMenu() {
        ui.assertThread()
        val previous = menu
        menu = null
        val pointAnchor = anchor
        anchor = null
        val callback = update
        update = null
        val focus = focusHandler
        focusHandler = null
        var failure: Throwable? = null
        fun attempt(action: () -> Unit) {
            try {
                action()
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
        }
        attempt { clearOpeningRelease() }
        attempt { focus?.close() }
        attempt { callback?.close() }
        attempt { previous?.close() }
        attempt { if (pointAnchor?.isDisposed == false) pointAnchor.close() }
        failure?.let { throw it }
    }

    override fun close() {
        ui.assertThread()
        if (closed) return
        closed = true
        var failure: Throwable? = null
        fun attempt(action: () -> Unit) {
            try {
                action()
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
        }
        attempt { pointerHandler?.close() }
        pointerHandler = null
        attempt { clearOpeningRelease() }
        attempt { attachment?.close() }
        attachment = null
        attempt { closeMenu() }
        failure?.let { throw it }
    }

    companion object {

        fun <T> of(ui: Ui, owner: Element, items: List<MenuItem<T>>, pointerButton: Int = 1, onSelect: (MenuItem<T>) -> Unit) =
            ContextMenu(ui, owner, items, pointerButton, onSelect)
    }
}
