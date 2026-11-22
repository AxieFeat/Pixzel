// Pixel geometry, palette and motion timings are authored as visual design data.
@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.Ui

import pixzel.graphics.Canvas
import pixzel.graphics.ClosePath
import pixzel.graphics.Color
import pixzel.graphics.FillRule
import pixzel.graphics.LineTo
import pixzel.graphics.MoveTo
import pixzel.graphics.Rectangle
import pixzel.graphics.Solid
import pixzel.graphics.VectorPath
import pixzel.graphics.rgba
import pixzel.layout.unit.sp
import pixzel.surface.Rect
import pixzel.surface.Vec2
import pixzel.text.TextAlignment
import pixzel.text.TextLayout
import pixzel.text.TextRequest
import pixzel.text.TextStyle
import pixzel.text.VerticalAlignment
import pixzel.text.alignedOrigin
import kotlin.math.roundToInt

internal fun Color.alpha(opacity: Float) = Color((rgba and 0xFFFFFF00L) or (alpha * opacity.coerceIn(0f, 1f)).roundToInt().toLong())

internal fun Color.shade(factor: Float) = Color(
    ((red * factor).toInt().coerceIn(0, 255).toLong() shl 24) or
            ((green * factor).toInt().coerceIn(0, 255).toLong() shl 16) or
            ((blue * factor).toInt().coerceIn(0, 255).toLong() shl 8) or alpha.toLong()
)

internal fun Canvas.rect(x: Float, y: Float, width: Float, height: Float, color: Color) {
    if (width > 0f && height > 0f && color.alpha > 0) fill(Rectangle, Rect(x, y, width, height), Solid(color))
}

internal fun Canvas.polygon(points: List<Vec2>, color: Color) {
    val path = VectorPath(listOf(MoveTo(points.first())) + points.drop(1).map(::LineTo) + ClosePath)
    fill(path, Rect.ZERO, Solid(color))
}

@Suppress("LongParameterList")
internal fun Canvas.pixelPanel(bounds: Rect, color: Color, edge: Color = color.shade(1.45f), cut: Float = 8f, borderWidth: Float = 3f) {
    require(cut.isFinite() && cut >= 0f)
    val x = bounds.x
    val y = bounds.y
    val w = bounds.width
    val h = bounds.height
    if (w == 0f || h == 0f) return
    val border = minOf(borderWidth, w / 2f, h / 2f)
    val corner = cut.coerceAtMost((minOf(w, h) / 2f - border).coerceAtLeast(0f))
    fun outline(inset: Float): VectorPath {
        val left = x + inset
        val top = y + inset
        val right = x + w - inset
        val bottom = y + h - inset
        val step = corner + inset
        val points = listOf(
            Vec2(x + step, top), Vec2(x + w - step, top), Vec2(x + w - step, y + step),
            Vec2(right, y + step), Vec2(right, y + h - step), Vec2(x + w - step, y + h - step),
            Vec2(x + w - step, bottom), Vec2(x + step, bottom), Vec2(x + step, y + h - step),
            Vec2(left, y + h - step), Vec2(left, y + step), Vec2(x + step, y + step)
        )
        return VectorPath(listOf(MoveTo(points.first())) + points.drop(1).map(::LineTo) + ClosePath)
    }

    val outer = outline(0f)
    if (w <= border * 2f || h <= border * 2f) {
        fill(outer, Rect.ZERO, Solid(edge))
        return
    }
    val inner = outline(border)
    // Separate coverage keeps translucent fills from painting over the border or its corners.
    fill(VectorPath(outer.segments + inner.segments, FillRule.EVEN_ODD), Rect.ZERO, Solid(edge))
    fill(inner, Rect.ZERO, Solid(color))
    if (borderWidth >= 3f && h - border * 2f >= 2f) {
        val start = x + corner + border
        val width = w - 2f * (corner + border)
        rect(start, y + border, width, 1f, Color.WHITE.alpha(0.12f))
        rect(start, y + h - border - 1f, width, 1f, 0x00000050.rgba)
    }
}

internal fun Canvas.star(x: Float, y: Float, size: Float, color: Color) {
    val rows = listOf(
        "...#...",
        "...#...",
        "#######",
        ".##.##.",
        "..###..",
        ".##.##.",
        ".#...#."
    )
    val pixel = size / 7f
    rows.forEachIndexed { row, line ->
        line.forEachIndexed { column, cell ->
            if (cell == '#') rect(x + column * pixel, y + row * pixel, pixel, pixel, color)
        }
    }
}

internal class PixelCanvas(val ui: Ui, val bounds: Rect, canvas: Canvas) : Canvas by canvas {

    fun Canvas.smallLabel(value: String, x: Float, y: Float, size: Float = 11f, color: Color = Color.WHITE) {
        val owner = ui
        val shaped = owner.text.layout(TextRequest(value, TextStyle(family = SMALL_FONT, size = size.sp)))
        text(shaped, Vec2(x, y), color)
    }

    @Suppress("LongParameterList")
    fun Canvas.label(
        value: String, x: Float, y: Float, size: Float = 20f, color: Color = Color.WHITE,
        centered: Boolean = false, width: Float = Float.POSITIVE_INFINITY, shadow: Boolean = false
    ) {
        val service = ui.text
        val shaped = service.layout(
            TextRequest(
                value, TextStyle(size = size.sp), width = width, wrap = width.isFinite(), textScale = ui.settings.textScale
            )
        )
        val left = if (centered) x - shaped.size.width / 2 else x
        drawLabel(shaped, Vec2(left, y), color, shadow)
    }

    @Suppress("LongParameterList")
    fun Canvas.label(
        value: String, bounds: Rect, size: Float = 20f, color: Color = Color.WHITE,
        horizontal: TextAlignment = TextAlignment.CENTER, shadow: Boolean = false
    ) {
        val owner = ui
        val shaped = owner.text.layout(TextRequest(value, TextStyle(size = size.sp), textScale = owner.settings.textScale))
        drawLabel(shaped, shaped.alignedOrigin(bounds, horizontal, VerticalAlignment.CENTER), color, shadow)
    }

    private fun Canvas.drawLabel(shaped: TextLayout, origin: Vec2, color: Color, shadow: Boolean) {
        if (shadow) text(shaped, origin + Vec2(1f, 2f), 0x00000080.rgba)
        text(shaped, origin, color)
    }
}
