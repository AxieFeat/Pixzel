package pixzel.component

import pixzel.input.InputBehavior
import pixzel.input.PointerPolicy
import pixzel.input.WheelEvent
import pixzel.input.WheelUnit
import pixzel.layout.ChildPlacement
import pixzel.layout.Constraints
import pixzel.layout.LayoutPlan
import pixzel.layout.LayoutPolicy
import pixzel.layout.unit.dp
import pixzel.layout.unit.fillAvailable
import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.surface.Vec2

internal open class SimpleScrollView(id: String) : AbstractElement(id), ScrollView {

    private var offset = Vec2.ZERO
    override var axes: ScrollAxes by property(ScrollAxes.VERTICAL, Invalidation.MEASURE)
    override var scrollOffset: Vec2
        get() = offset
        set(value) {
            check(!isDisposed) { "ScrollView is disposed" }
            ui?.assertThread()
            val viewport = surface.contentBox.size
            val bounded = boundedOffset(value, viewport)
            if (offset != bounded) {
                invalidate(Invalidation.ARRANGE)
                offset = bounded
            }
        }
    override var extent = Size.ZERO
        protected set

    init {
        layout.width = fillAvailable
        layout.height = 240.dp
        clipPolicy = ClipPolicy.CONTENT
        pointerPolicy = PointerPolicy.AUTO
        layoutPolicy = LayoutPolicy { children, c, e ->
            val measured = children.map { child ->
                val margin = child.layout.margin
                child.measure(
                    Constraints(
                        maxWidth = if (axes == ScrollAxes.VERTICAL) (c.maxWidth - margin.horizontal).coerceAtLeast(0f) else Float.POSITIVE_INFINITY,
                        maxHeight = if (axes == ScrollAxes.HORIZONTAL) (c.maxHeight - margin.vertical).coerceAtLeast(0f) else Float.POSITIVE_INFINITY
                    )
                )
            }
            extent =
                if (axes == ScrollAxes.HORIZONTAL) Size(children.indices.sumOf { (measured[it].size.width + children[it].layout.margin.horizontal).toDouble() }
                    .toFloat(), children.indices.maxOfOrNull { measured[it].size.height + children[it].layout.margin.vertical } ?: 0f)
                else Size(
                    children.indices.maxOfOrNull { measured[it].size.width + children[it].layout.margin.horizontal } ?: 0f,
                    children.indices.sumOf { (measured[it].size.height + children[it].layout.margin.vertical).toDouble() }.toFloat()
                )
            val viewport = c.constrain(Size(extent.width, extent.height))
            offset = boundedOffset(offset, viewport)
            var cursor = 0f
            LayoutPlan(viewport, measured.mapIndexed { index, placeable ->
                val margin = children[index].layout.margin
                val p = ChildPlacement(
                    placeable,
                    if (axes == ScrollAxes.HORIZONTAL) Vec2(cursor + margin.left - offset.x, margin.top - offset.y) else Vec2(
                        margin.left - offset.x,
                        cursor + margin.top - offset.y
                    )
                )
                cursor += if (axes == ScrollAxes.HORIZONTAL) placeable.size.width + margin.horizontal else placeable.size.height + margin.vertical
                p
            })
        }
        behavior = InputBehavior { _, event ->
            if (event.event is WheelEvent) {
                val wheel = event.event as WheelEvent
                val delta = event.scrollRemaining ?: wheel.delta
                val scale = when (wheel.unit) {
                    WheelUnit.LINE -> Vec2(32f, 32f)
                    WheelUnit.DP -> Vec2(1f, 1f)
                    WheelUnit.PAGE -> Vec2(surface.contentBox.width.coerceAtLeast(1f), surface.contentBox.height.coerceAtLeast(1f))
                }
                val remaining = scrollBy(Vec2(-delta.x * scale.x, -delta.y * scale.y))
                event.consumeScroll(Vec2(delta.x + remaining.x / scale.x, delta.y + remaining.y / scale.y))
            }
        }
    }

    private fun boundedOffset(value: Vec2, viewport: Size) = Vec2(
        if (axes == ScrollAxes.VERTICAL) 0f else value.x.coerceIn(0f, (extent.width - viewport.width).coerceAtLeast(0f)),
        if (axes == ScrollAxes.HORIZONTAL) 0f else value.y.coerceIn(0f, (extent.height - viewport.height).coerceAtLeast(0f))
    )

    override fun scrollBy(delta: Vec2): Vec2 {
        val before = scrollOffset
        scrollOffset = before + delta
        return delta - (scrollOffset - before)
    }

    override fun scrollIntoView(element: Element) {
        check(!isDisposed)
        ui?.assertThread()
        require(generateSequence(element.portalTarget ?: element.parent) {
            it.portalTarget ?: it.parent
        }.any { it === this }) { "Element is not geometrically inside this ScrollView" }
        val inverse = surface.localToRoot.inverse() ?: error("A singular ScrollView transform cannot reveal a descendant")
        val bounds = (inverse * element.surface.localToRoot).mapRect(Rect(0f, 0f, element.surface.bounds.width, element.surface.bounds.height))
        val box = surface.contentBox
        val dx = when {
            bounds.x < box.x -> bounds.x - box.x
            bounds.right > box.right -> bounds.right - box.right
            else -> 0f
        }
        val dy = when {
            bounds.y < box.y -> bounds.y - box.y
            bounds.bottom > box.bottom -> bounds.bottom - box.bottom
            else -> 0f
        }
        scrollBy(Vec2(dx, dy))
    }
}
