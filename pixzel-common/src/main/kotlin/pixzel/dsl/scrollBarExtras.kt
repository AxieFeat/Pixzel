package pixzel.dsl

import pixzel.component.ScrollBar
import pixzel.component.ScrollView

fun ContentScope.scrollBar(view: ScrollView? = null, id: String = "", configure: ScrollBar.() -> Unit = {}): ScrollBar =
    insert(ui.components.scrollBar(id)) {
        this.view = view
        configure()
    }
