@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.Ui
import pixzel.component.Invalidation
import pixzel.component.ScrollView
import pixzel.component.Element
import pixzel.input.EventPhase
import pixzel.input.WheelEvent
import pixzel.input.WheelUnit
import pixzel.layout.ChildPlacement
import pixzel.layout.Constraints
import pixzel.layout.LayoutPlan
import pixzel.layout.LayoutPolicy
import pixzel.layout.unit.fillAvailable
import pixzel.surface.Size
import pixzel.surface.Vec2

internal data class TileView(val root: Element, val size: () -> TileSize)

/**
 * Composes a native ScrollView with a data-driven masonry layout.
 */
internal class TileBoard(ui: Ui, reducedMotion: () -> Boolean) {

    val root: ScrollView = ui.components.scrollView("review-board")
    private val content = ui.compose("review-tiles")
    private val items = mutableListOf<TileView>()
    private val scroll = MenuSpring(frequency = 20f)
    val empty get() = items.isEmpty()

    init {
        content.layout.width = fillAvailable
        root.add(content)
        content.layoutPolicy = LayoutPolicy { children, constraints, _ ->
            val width = constraints.maxWidth
            val rectangles = masonry(width, if (width < 700f) 1 else 6, 20f, items.map { it.size() })
            val height = rectangles.maxOfOrNull { it.bottom } ?: 0f
            LayoutPlan(Size(width, height), children.zip(rectangles).map { (child, rect) ->
                ChildPlacement(child.measure(Constraints.fixed(rect.size)), Vec2(rect.x, rect.y))
            })
        }
        root.onEvent(EventPhase.CAPTURE) { routed ->
            (routed.event as? WheelEvent)?.let { wheel ->
                scrollBy(-wheel.delta.y * if (wheel.unit == WheelUnit.LINE) 65f else 1f)
                routed.consume()
                routed.preventDefault()
            }
        }
        root.onFrame { seconds ->
            if (reducedMotion()) scroll.snap()
            val moving = scroll.step(seconds)
            root.scrollOffset = Vec2(0f, scroll.value)
            items.forEach { tile ->
                val rect = tile.root.surface.bounds
                tile.root.opacity = if (rect.bottom > scroll.value && rect.y < scroll.value + root.surface.bounds.height) 1f else 0f
            }
            moving
        }
    }

    fun replace(views: List<TileView>) {
        content.children.toList().forEach { content.remove(it) }
        items.clear()
        items.addAll(views)
        items.forEach { content.add(it.root) }
        scroll.snap(0f)
        root.scrollOffset = Vec2.ZERO
    }

    fun reflow() = content.invalidate(Invalidation.MEASURE)
    fun scrollBy(amount: Float) {
        scroll.target = (scroll.target + amount).coerceIn(0f, (root.extent.height - root.surface.bounds.height).coerceAtLeast(0f))
    }
}
