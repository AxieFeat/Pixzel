package pixzel.text

import pixzel.surface.Rect
import pixzel.surface.Vec2

/**
 * Aligns the measured line box inside [bounds], preserving the provider's baseline and leading.
 * Overflow starts at the top-left edge so clipping and horizontal scrolling remain usable.
 */
fun TextLayout.alignedOrigin(
    bounds: Rect,
    horizontal: TextAlignment = TextAlignment.START,
    vertical: VerticalAlignment = VerticalAlignment.TOP
): Vec2 {
    val width = (bounds.width - size.width).coerceAtLeast(0f)
    val height = (bounds.height - size.height).coerceAtLeast(0f)
    val x = when (horizontal) {
        TextAlignment.CENTER -> width / 2f
        TextAlignment.RIGHT -> width
        TextAlignment.END -> if (direction == TextDirection.RTL) 0f else width
        TextAlignment.START -> if (direction == TextDirection.RTL) width else 0f
        TextAlignment.LEFT -> 0f
    }
    val y = when (vertical) {
        VerticalAlignment.TOP -> 0f
        VerticalAlignment.CENTER -> height / 2f
        VerticalAlignment.BOTTOM -> height
    }
    return Vec2(bounds.x + x, bounds.y + y)
}
