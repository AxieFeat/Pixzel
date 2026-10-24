package pixzel.component

import pixzel.surface.Rect

enum class ScrollBarOrientation {
    HORIZONTAL,
    VERTICAL
}

/** A borrowed ScrollView controls the thumb. An absent/non-scrollable view is a disabled bar. */
interface ScrollBar : Element {

    var view: ScrollView?
    var orientation: ScrollBarOrientation
    var minThumbSize: Float
    var lineStep: Float
    val thumbBounds: Rect
    val canScroll: Boolean

    companion object {

        fun of(components: ComponentFactory, id: String = "") = components.scrollBar(id)
    }
}
