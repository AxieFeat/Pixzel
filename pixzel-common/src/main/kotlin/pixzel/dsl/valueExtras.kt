package pixzel.dsl

import pixzel.component.Progress
import pixzel.component.Slider

fun ContentScope.slider(id: String = "", configure: Slider.() -> Unit = {}): Slider =
    insert(ui.components.slider(id), configure)

fun ContentScope.progress(id: String = "", configure: Progress.() -> Unit = {}): Progress =
    insert(ui.components.progress(id), configure)
