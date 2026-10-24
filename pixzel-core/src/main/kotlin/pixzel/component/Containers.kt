package pixzel.component

import pixzel.graphics.Color
import pixzel.graphics.ImageHandle
import pixzel.graphics.Sampling
import pixzel.state.MutableState
import pixzel.surface.Size
import pixzel.surface.Vec2

interface ScrollView : Element {

    var axes: ScrollAxes
    var scrollOffset: Vec2
    val extent: Size

    /**
     * Returns the unconsumed remainder in dp.
     */
    fun scrollBy(delta: Vec2): Vec2
    fun scrollIntoView(element: Element)

    companion object {

        fun of(components: ComponentFactory, id: String = "") = components.scrollView(id)
    }
}

enum class ScrollAxes {
    VERTICAL,
    HORIZONTAL,
    BOTH
}
interface Image : Element {

    var image: ImageHandle?
    var tint: Color
    var sampling: Sampling
    var fit: ImageFit

    companion object {

        fun of(components: ComponentFactory, id: String = "") = components.image(id)
    }
}

enum class ImageFit {
    STRETCH,
    CONTAIN,
    COVER,
    CROP
}
interface VirtualList<T, K> : ScrollView {

    var items: List<T>
    val materializedCount: Int
    fun scrollToKey(key: K)
    fun <V> itemState(key: K, slot: StateSlot<V>, initial: () -> V): MutableState<V>
}

class StateSlot<T>(val name: String)
