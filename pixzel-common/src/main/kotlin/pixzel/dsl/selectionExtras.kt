package pixzel.dsl

import pixzel.component.Checkbox
import pixzel.component.Radio
import pixzel.component.Toggle

fun ContentScope.checkbox(id: String = "", configure: Checkbox.() -> Unit = {}): Checkbox =
    insert(ui.components.checkbox(id), configure)

fun ContentScope.toggle(id: String = "", configure: Toggle.() -> Unit = {}): Toggle =
    insert(ui.components.toggle(id), configure)

fun ContentScope.radio(id: String = "", configure: Radio.() -> Unit = {}): Radio =
    insert(ui.components.radio(id), configure)
