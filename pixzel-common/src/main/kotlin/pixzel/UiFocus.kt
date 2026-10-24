package pixzel

import pixzel.component.Element
import pixzel.input.FocusManager
import pixzel.input.NavigationDirection
import pixzel.resource.Subscription
import pixzel.surface.Rect
import kotlin.math.abs

internal class UiFocus(private val ui: SimpleUi) : FocusManager {

    private class FocusScope(val root: Element, val previous: Element?, val restore: Boolean)

    private val scopes = mutableListOf<FocusScope>()
    private fun inside(element: Element, scope: Element) = generateSequence(element as Element?) { it.parent }.any { it === scope }
    private fun permitted(element: Element) = scopes.lastOrNull()?.let { inside(element, it.root) } ?: true

    override var focused: Element? = null
        private set

    private fun unfocus(element: Element) {
        if (!element.isDisposed) element.setInteractionState(
            element.state.copy(
                focused = false,
                focusVisible = false,
                pressed = element.state.pressed && ui.hasPointerCapture(element)
            )
        )
    }

    override fun request(element: Element, visible: Boolean): Boolean {
        ui.assertMutable()
        if (ui.lifecycle == UiLifecycle.CLOSED || ui.closing || !element.focusable || !ui.effective(element) || !permitted(element)) return false

        if (focused !== element) ui.host.maskHeldInput()
        focused?.takeIf { it !== element }?.let(::unfocus)

        focused = element
        element.setInteractionState(element.state.copy(focused = true, focusVisible = visible))
        return true
    }

    override fun clear() {
        ui.assertMutable()
        val previous = focused
        focused = null
        previous?.let(::unfocus)
    }

    override fun move(forward: Boolean): Boolean {
        ui.assertMutable()
        val list = ui.nodes().filter { it.focusable && ui.effective(it) && it.tabIndex >= 0 && permitted(it) }.sortedBy { it.tabIndex }
        if (list.isEmpty()) {
            clear()
            return false
        }
        val index = list.indexOf(focused)
        return request(list[if (forward) (index + 1) % list.size else if (index <= 0) list.lastIndex else index - 1])
    }

    override fun move(direction: NavigationDirection): Boolean {
        ui.assertMutable()
        ui.layoutIfNeeded()
        val origin = focused?.takeIf(ui::effective) ?: return move(true)
        val from = origin.surface.mapRect(Rect(0f, 0f, origin.surface.bounds.width, origin.surface.bounds.height))
        val x = from.x + from.width / 2
        val y = from.y + from.height / 2
        val horizontal = direction == NavigationDirection.LEFT || direction == NavigationDirection.RIGHT
        val positive = direction == NavigationDirection.RIGHT || direction == NavigationDirection.DOWN

        // Prefer candidates aligned with the current control within the requested half-plane.
        val candidate = ui.nodes().asSequence().filter { it !== origin && it.focusable && it.tabIndex >= 0 && ui.effective(it) && permitted(it) }
            .mapNotNull { element ->
                val bounds = element.surface.mapRect(Rect(0f, 0f, element.surface.bounds.width, element.surface.bounds.height))
                val dx = bounds.x + bounds.width / 2 - x
                val dy = bounds.y + bounds.height / 2 - y
                val primary = (if (horizontal) dx else dy) * (if (positive) 1 else -1)
                if (primary <= .0001f) null else {
                    val perpendicular = abs(if (horizontal) dy else dx)
                    val overlap =
                        if (horizontal) bounds.y < from.bottom && bounds.bottom > from.y else bounds.x < from.right && bounds.right > from.x
                    element to (primary + perpendicular * 2 + (if (overlap) 0f else 100000f))
                }
            }.minByOrNull { it.second }?.first ?: return false
        return request(candidate)
    }

    override fun pushScope(root: Element, restoreFocus: Boolean): Subscription {
        ui.assertMutable()
        require(ui.effective(root)) { "Focus scope must be attached, visible and enabled." }
        ui.host.maskHeldInput()
        val scope = FocusScope(root, focused, restoreFocus)
        scopes += scope
        if (focused?.let { !permitted(it) } == true) clear()
        if (focused == null) move()
        return Subscription {
            ui.assertMutable()
            val wasTop = scopes.lastOrNull() === scope
            if (scopes.remove(scope) && wasTop && scope.restore) {
                val previous = scope.previous
                if (previous == null || !request(previous)) move()
            }
        }
    }

    internal fun detached(element: Element) {
        val removed = scopes.filter { inside(it.root, element) }
        val wasTop = scopes.lastOrNull() in removed
        scopes.removeAll(removed.toSet())
        if (focused?.let { inside(it, element) } == true) clear()
        if (wasTop) {
            val previous = removed.lastOrNull()?.takeIf { it.restore }?.previous
            if (previous != null && !inside(previous, element) && request(previous)) return
            if (!ui.closing) ui.nodes().filter { !inside(it, element) && it.focusable && ui.effective(it) && permitted(it) && it.tabIndex >= 0 }
                .minByOrNull { it.tabIndex }?.let { request(it) }
        }
    }
}
