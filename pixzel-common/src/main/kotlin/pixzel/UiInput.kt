package pixzel

import pixzel.component.ClipPolicy
import pixzel.component.Element
import pixzel.graphics.Rectangle
import pixzel.input.Composition
import pixzel.input.EventPhase
import pixzel.input.InputEvent
import pixzel.input.InputMode
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.NavigationCommand
import pixzel.input.NavigationDirection
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.PointerPolicy
import pixzel.input.RoutedEvent
import pixzel.input.RoutingFlags
import pixzel.input.TextCommit
import pixzel.input.WheelEvent
import pixzel.input.WindowFocus
import pixzel.surface.Rect
import pixzel.surface.Vec2
import kotlin.math.abs

/** Owns pointer capture, hover and routed gestures; the Ui owns frame and layout progress. */
internal class UiInput(private val ui: SimpleUi) {

    private var pointer: Vec2? = null
    var inputMode = InputMode.POINTER
        private set
    private val captures = mutableMapOf<Int, Element>()
    val capturedPointers: Map<Int, Element> get() = captures.toMap()
    private val captureButtons = mutableMapOf<Int, Int>()
    private val consumedKeys = mutableSetOf<Pair<Key, Int>>()
    private val hovered = mutableSetOf<Element>()

    private fun hit(element: Element, position: Vec2, includeDisabled: Boolean = false): Element? {
        if (!ui.visibleAttached(element) || element.pointerPolicy == PointerPolicy.NONE) return null
        val enabled = includeDisabled || ui.effective(element)
        val local = element.surface.fromRoot(position) ?: return null
        val bounds = Rect(0f, 0f, element.surface.bounds.width, element.surface.bounds.height)
        val clip = when (element.clipPolicy) {
            ClipPolicy.BOUNDS -> Rectangle.contains(local, bounds)
            ClipPolicy.CONTENT -> Rectangle.contains(local, element.surface.contentBox)
            ClipPolicy.SHAPE -> element.clipShape.contains(local, bounds)
            else -> true
        }
        if (!clip) return null
        ui.sorted(element).asReversed().forEach { hit(it, position, includeDisabled)?.let { return it } }
        return if (enabled && element.pointerPolicy == PointerPolicy.AUTO && element.hitShape.contains(local, bounds.size)) element else null
    }

    fun hitTest(point: Vec2): Element? {
        ui.assertThread()
        ui.layoutIfNeeded()
        return hit(ui.root, point)
    }

    fun hasPointerCapture(element: Element) = captures.containsValue(element)

    fun releasePointerCapture(element: Element) {
        ui.assertMutable()
        cancelCaptures { it === element }
    }

    fun navigate(command: NavigationCommand): Boolean {
        ui.assertMutable()
        if (ui.lifecycle == UiLifecycle.CLOSED || ui.closing) return false
        inputMode = InputMode.NAVIGATION
        ui.layoutIfNeeded()
        return when (command) {
            NavigationCommand.NEXT -> ui.focus.move(true)
            NavigationCommand.PREVIOUS -> ui.focus.move(false)
            NavigationCommand.LEFT -> ui.focus.move(NavigationDirection.LEFT)
            NavigationCommand.RIGHT -> ui.focus.move(NavigationDirection.RIGHT)
            NavigationCommand.UP -> ui.focus.move(NavigationDirection.UP)
            NavigationCommand.DOWN -> ui.focus.move(NavigationDirection.DOWN)
            NavigationCommand.ACTIVATE -> {
                val target = ui.focus.focused?.takeIf(ui::effective)
                val action = target?.semantics?.actions?.get("activate")
                if (action != null) {
                    action()
                    true
                } else false
            }

            NavigationCommand.CANCEL -> ui.overlays.dismissTop()
        }
    }

    fun hover() {
        val target = pointer?.let { hit(ui.root, it, includeDisabled = true) }
        val next = generateSequence(target) { it.parent }.toSet()
        val leaving = (hovered - next).filter { !it.isDisposed }
        val entering = (next - hovered).toList()
        hovered.clear()
        hovered.addAll(next)
        var failure: Throwable? = null
        fun update(element: Element, value: Boolean) {
            try {
                element.setInteractionState(element.state.copy(hovered = value))
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
        }
        leaving.forEach { update(it, false) }
        entering.forEach { update(it, true) }
        failure?.let { throw it }
    }

    fun dispatch(original: InputEvent): Boolean {
        ui.assertThread()
        if (ui.lifecycle == UiLifecycle.CLOSED || ui.closing) return false
        when (original) {
            is PointerEvent, is WheelEvent -> inputMode = InputMode.POINTER
            is KeyEvent, is TextCommit, is Composition -> inputMode = InputMode.KEYBOARD
        }
        ui.layoutIfNeeded()
        cancelCaptures { !ui.effective(it) }
        val event = when (original) {
            is PointerEvent -> original.copy(position = Vec2(original.position.x / ui.settings.uiScale, original.position.y / ui.settings.uiScale))
            is WheelEvent -> original.copy(position = Vec2(original.position.x / ui.settings.uiScale, original.position.y / ui.settings.uiScale))
            else -> original
        }
        if (event is WindowFocus && !event.focused) {
            consumedKeys.clear()
            var failure: Throwable? = null
            try {
                pointer = null
                hover()
            } catch (error: Throwable) {
                failure = error
            }
            try {
                cancelCaptures()
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
            try {
                ui.focus.clear()
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
            failure?.let { throw it }
            return false
        }
        if (event is KeyEvent && event.key == Key.TAB) {
            if (event.action == KeyAction.DOWN) ui.focus.move(!event.modifiers.shift)
            return true
        }
        if (event is PointerEvent) {
            pointer = if (event.action == PointerAction.LEAVE) null else event.position
            hover()
        }
        val position = when (event) {
            is PointerEvent -> event.position
            is WheelEvent -> event.position
            else -> null
        }
        val target = when (event) {
            is PointerEvent -> captures[event.pointerId]?.takeIf(ui::effective) ?: hit(ui.root, event.position)
            is WheelEvent -> hit(ui.root, event.position)
            else -> ui.focus.focused?.takeIf(ui::effective)
        } ?: ui.root

        // Portals change geometry, but event capture/bubble still follows logical ownership.
        val route = generateSequence(target as Element?) { it.parent }.toList().asReversed()
        val keyIdentity = (event as? KeyEvent)?.let { it.key to it.physicalCode }
        val flags = RoutingFlags().apply {
            if (event is WheelEvent) scrollRemainder = event.delta
            if (event is KeyEvent && event.action == KeyAction.UP && consumedKeys.remove(checkNotNull(keyIdentity))) consumed = true
        }

        fun dispatch(node: Element, phase: EventPhase) {
            if (ui.effective(node)) {
                node.dispatch(RoutedEvent(event, target, node, phase, position?.let { node.surface.fromRoot(it) }, flags))
            }
        }
        route.forEach { if (!flags.stopped) dispatch(it, EventPhase.CAPTURE) }
        if (!flags.stopped) dispatch(target, EventPhase.TARGET)
        route.asReversed().forEach { if (!flags.stopped) dispatch(it, EventPhase.BUBBLE) }
        if (ui.lifecycle == UiLifecycle.CLOSED || ui.closing) return flags.consumed

        if (event is WheelEvent) {
            for (node in route.asReversed()) {
                val remainder = flags.scrollRemainder ?: event.delta
                if (flags.prevented || flags.stopped || (abs(remainder.x) < .00001f && abs(remainder.y) < .00001f)) break
                if (ui.effective(node)) node.behavior?.handle(
                    node,
                    RoutedEvent(event, target, node, EventPhase.TARGET, position?.let { node.surface.fromRoot(it) }, flags)
                )
                if (ui.lifecycle == UiLifecycle.CLOSED || ui.closing) return flags.consumed
            }
        } else if (!flags.prevented && ui.effective(target)) target.behavior?.handle(
            target,
            RoutedEvent(event, target, target, EventPhase.TARGET, position?.let { target.surface.fromRoot(it) }, flags)
        )
        if (ui.lifecycle == UiLifecycle.CLOSED || ui.closing) return flags.consumed
        if (event is KeyEvent && event.action == KeyAction.DOWN && !flags.consumed && !flags.prevented) {
            val direction = when (event.key) {
                Key.LEFT -> NavigationDirection.LEFT
                Key.RIGHT -> NavigationDirection.RIGHT
                Key.UP -> NavigationDirection.UP
                Key.DOWN -> NavigationDirection.DOWN
                else -> null
            }
            if (direction != null && ui.focus.move(direction)) flags.consumed = true
        }

        if (event is PointerEvent) {
            flags.capture?.takeIf(ui::effective)?.let {
                captures[event.pointerId] = it
                if (event.action == PointerAction.DOWN) captureButtons[event.pointerId] = event.button
                it.setInteractionState(it.state.copy(pressed = true))
            }
            if (
                event.action == PointerAction.CANCEL ||
                event.action == PointerAction.UP &&
                (captureButtons[event.pointerId] == null || captureButtons[event.pointerId] == event.button)
            ) {
                captureButtons.remove(event.pointerId)
                captures.remove(event.pointerId)?.takeIf { !it.isDisposed }?.let { it.setInteractionState(it.state.copy(pressed = false)) }
            }
        }

        if (event is KeyEvent && event.action != KeyAction.UP && flags.consumed) consumedKeys += checkNotNull(keyIdentity)
        return flags.consumed
    }

    fun cancelCaptures(predicate: (Element) -> Boolean = { true }) {
        var failure: Throwable? = null
        captures.toMap().filterValues(predicate).forEach { (id, node) ->
            captures.remove(id)
            captureButtons.remove(id)
            if (!node.isDisposed) {
                try {
                    val flags = RoutingFlags()
                    val event = PointerEvent(PointerAction.CANCEL, pointer ?: Vec2.ZERO, pointerId = id)
                    val route = generateSequence(node as Element?) { it.parent }.toList().asReversed()
                    fun deliver(current: Element, phase: EventPhase) {
                        if (!current.isDisposed) current.dispatch(
                            RoutedEvent(
                                event,
                                node,
                                current,
                                phase,
                                if (current.surface.isAttached) current.surface.fromRoot(event.position) else null,
                                flags
                            )
                        )
                    }
                    route.forEach { if (!flags.stopped) deliver(it, EventPhase.CAPTURE) }
                    if (!flags.stopped) deliver(node, EventPhase.TARGET)
                    route.asReversed().forEach { if (!flags.stopped) deliver(it, EventPhase.BUBBLE) }
                    if (!node.isDisposed) node.behavior?.handle(node, RoutedEvent(event, node, node, EventPhase.TARGET, null, flags))
                } catch (error: Throwable) {
                    if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
                } finally {
                    try {
                        if (!node.isDisposed) node.setInteractionState(node.state.copy(pressed = false))
                    } catch (error: Throwable) {
                        if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
                    }
                }
            }
        }
        failure?.let { throw it }
    }

    fun detached(element: Element) {
        ui.assertMutable()
        fun owned(node: Element) = generateSequence(node as Element?) { it.parent }.any { it === element }
        var failure: Throwable? = null
        try {
            cancelCaptures(::owned)
        } catch (error: Throwable) {
            failure = error
        }
        try {
            ui.focus.detached(element)
        } catch (error: Throwable) {
            if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
        }
        val leaving = hovered.filter(::owned)
        hovered.removeAll(leaving.toSet())
        leaving.filter { !it.isDisposed }.forEach { node ->
            try {
                node.setInteractionState(node.state.copy(hovered = false, pressed = false))
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    fun clearState() {
        captures.clear()
        captureButtons.clear()
        consumedKeys.clear()
        hovered.clear()
    }
}
