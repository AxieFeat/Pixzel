package pixzel.dsl

import pixzel.component.ContextMenu
import pixzel.component.MenuItem

/** Adds a right-click/explicit-keyboard menu to this scope's element without a permanent node. */
fun <T> ContentScope.contextMenu(items: List<MenuItem<T>>, pointerButton: Int = 1, onSelect: (MenuItem<T>) -> Unit): ContextMenu<T> =
    ContextMenu.of(ui, parent, items, pointerButton, onSelect)
