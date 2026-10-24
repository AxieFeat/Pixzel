package pixzel.dsl

import pixzel.component.Dropdown
import pixzel.component.MenuItem
import pixzel.component.Tabs
import pixzel.component.dropdown as componentDropdown

fun ContentScope.tabs(id: String = "", configure: Tabs.() -> Unit = {}): Tabs =
    insert(ui.components.tabs(id), configure)

fun <T> ContentScope.dropdown(items: List<MenuItem<T>>, id: String = "", configure: Dropdown<T>.() -> Unit = {}): Dropdown<T> =
    insert(componentDropdown(ui, items, id), configure)
