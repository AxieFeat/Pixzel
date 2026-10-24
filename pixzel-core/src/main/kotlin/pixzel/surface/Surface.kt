package pixzel.surface

import pixzel.layout.unit.Dp

data class Insets(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it >= 0 })
    }

    val horizontal get() = left + right
    val vertical get() = top + bottom
    fun inset(rect: Rect) = Rect(rect.x + left, rect.y + top, (rect.width - horizontal).coerceAtLeast(0f), (rect.height - vertical).coerceAtLeast(0f))

    companion object {

        val ZERO = Insets()
        fun all(dp: Dp) = Insets(dp.value, dp.value, dp.value, dp.value)
        fun horizontal(dp: Dp) = Insets(left = dp.value, right = dp.value)
    }
}

enum class Anchor(val x: Float, val y: Float) {
    TOP_LEFT(0f, 0f),
    TOP_CENTER(.5f, 0f),
    TOP_RIGHT(1f, 0f),
    CENTER_LEFT(0f, .5f),
    CENTER(.5f, .5f),
    CENTER_RIGHT(1f, .5f),
    BOTTOM_LEFT(0f, 1f),
    BOTTOM_CENTER(.5f, 1f),
    BOTTOM_RIGHT(1f, 1f)
}
typealias Pivot = Anchor

enum class ReferenceBox {
    CONTENT,
    BORDER,
    SAFE_AREA
}
data class Placement(
    val anchor: Anchor = Anchor.TOP_LEFT,
    val pivot: Pivot = Pivot.TOP_LEFT,
    val offset: Vec2 = Vec2.ZERO,
    val referenceBox: ReferenceBox = ReferenceBox.CONTENT
) {

    companion object {

        fun center() = Placement(Anchor.CENTER, Pivot.CENTER)
        fun of(anchor: Anchor = Anchor.TOP_LEFT, pivot: Pivot = Pivot.TOP_LEFT, offset: Vec2 = Vec2.ZERO) = Placement(anchor, pivot, offset)
    }
}

/** Computed geometry is read-only; coordinates require an attached, laid-out owner. */
interface Surface {

    val bounds: Rect
    val contentBox: Rect
    val localToParent: Transform
    val localToRoot: Transform
    val isAttached: Boolean
    fun toParent(point: Vec2): Vec2
    fun toRoot(point: Vec2): Vec2
    fun fromRoot(point: Vec2): Vec2?
    fun mapRect(rect: Rect): Rect
}
