package pixzel.component

import pixzel.Ui
import pixzel.surface.Rect

fun Ui.semanticsTree(): SemanticsNode {
    assertThread()
    fun snapshot(element: Element, parentEnabled: Boolean): SemanticsNode {
        val semantics = element.semantics
        val enabled = parentEnabled && element.enabled
        return SemanticsNode(
            element.id,
            element.generation,
            semantics.role,
            semantics.label,
            semantics.value,
            enabled,
            semantics.checked,
            semantics.selected,
            element.focusable,
            element.state.focused,
            if (element.surface.isAttached) element.surface.mapRect(
                Rect(
                    0f,
                    0f,
                    element.surface.bounds.width,
                    element.surface.bounds.height
                )
            ) else Rect.ZERO,
            semantics.actions.keys.toSet(),
            element.children.filter { it.visibility == Visibility.VISIBLE }.map { snapshot(it, enabled) })
    }
    return snapshot(root, true)
}

fun Ui.performSemanticsAction(element: Element, action: String): Result<Unit> = runCatching {
    assertThread()
    require(
        generateSequence(element as Element?) { it.parent }.all {
            it.ui === this && it.enabled && !it.isDisposed && it.visibility == Visibility.VISIBLE
        }
    ) { "Accessibility action target is inactive" }
    val callback = element.semantics.actions[action] ?: throw UnsupportedOperationException("Action '$action' is unavailable for '${element.id}'")
    callback()
}
