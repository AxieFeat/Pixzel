// Pixel geometry, palette and motion timings are authored as visual design data.
@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.component.AbstractElement
import pixzel.component.Role
import pixzel.component.Semantics
import pixzel.graphics.Canvas
import pixzel.graphics.ClosePath
import pixzel.graphics.Color
import pixzel.graphics.FillRule
import pixzel.graphics.LineTo
import pixzel.graphics.MoveTo
import pixzel.graphics.Rectangle
import pixzel.graphics.Solid
import pixzel.graphics.VectorPath
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.input.InputBehavior
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.PointerPolicy
import pixzel.layout.ChildPlacement
import pixzel.layout.Constraints
import pixzel.layout.LayoutPlan
import pixzel.layout.LayoutPolicy
import pixzel.layout.unit.sp
import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.surface.Vec2
import pixzel.text.TextAlignment
import pixzel.text.TextLayout
import pixzel.text.TextRequest
import pixzel.text.TextStyle
import pixzel.text.VerticalAlignment
import pixzel.text.FontFamily
import pixzel.text.FontFace
import pixzel.text.alignedOrigin
import kotlin.math.roundToInt
import kotlin.time.Duration

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

internal open class MenuElement(id: String, protected val model: MenuModel) : AbstractElement(id) {

    protected val bounds get() = Rect(0f, 0f, surface.bounds.width, surface.bounds.height)

    protected fun Canvas.smallLabel(value: String, x: Float, y: Float, size: Float = 11f, color: Color = Color.WHITE) {
        val owner = ui ?: return
        val shaped = owner.text.layout(
            TextRequest(
                value,
                TextStyle(family = FontFamily(listOf(FontFace("minecraft-five"))), size = size.sp)
            )
        )
        text(shaped, Vec2(x, y), color)
    }

    @Suppress("LongParameterList")
    protected fun Canvas.label(
        value: String, x: Float, y: Float, size: Float = 20f, color: Color = Color.WHITE,
        centered: Boolean = false, width: Float = Float.POSITIVE_INFINITY, shadow: Boolean = false
    ) {
        val service = ui?.text ?: return
        val shaped = service.layout(
            TextRequest(
                value, TextStyle(size = size.sp), width = width, wrap = width.isFinite(), textScale = ui!!.settings.textScale
            )
        )
        val left = if (centered) x - shaped.size.width / 2 else x
        drawLabel(shaped, Vec2(left, y), color, shadow)
    }

    @Suppress("LongParameterList")
    protected fun Canvas.label(
        value: String, bounds: Rect, size: Float = 20f, color: Color = Color.WHITE,
        horizontal: TextAlignment = TextAlignment.CENTER, shadow: Boolean = false
    ) {
        val owner = ui ?: return
        val shaped = owner.text.layout(TextRequest(value, TextStyle(size = size.sp), textScale = owner.settings.textScale))
        drawLabel(shaped, shaped.alignedOrigin(bounds, horizontal, VerticalAlignment.CENTER), color, shadow)
    }

    private fun Canvas.drawLabel(shaped: TextLayout, origin: Vec2, color: Color, shadow: Boolean) {
        if (shadow) text(shaped, origin + Vec2(1f, 2f), 0x00000080.rgba)
        text(shaped, origin, color)
    }
}

internal open class MenuButton(
    id: String, model: MenuModel, title: String = "", var action: () -> Unit = {}
) : MenuElement(id, model) {

    var title = title
        set(value) {
            if (field == value) return
            field = value
            semantics = semantics.copy(label = value)
            invalidate()
        }

    val hover = MenuSpring()
    protected val press = MenuSpring()
    var purple = false
    var active = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    private var ripple = 1f
    private var rippleOrigin = Vec2.ZERO
    private var armed = false

    init {
        pointerPolicy = PointerPolicy.AUTO
        focusable = true
        semantics = Semantics(Role.BUTTON, title, actions = mapOf("activate" to { activate(null) }))
        behavior = InputBehavior { _, routed ->
            when (val input = routed.event) {
                is PointerEvent -> if (input.button == 0) {
                    when (input.action) {
                        PointerAction.DOWN -> {
                            armed = true
                            ui?.focus?.request(this, false)
                            routed.capturePointer()
                            ripple = 0f
                            rippleOrigin = routed.localPosition ?: Vec2.ZERO
                            routed.consume()
                        }

                        PointerAction.UP -> {
                            if (armed && routed.localPosition?.let(bounds::contains) == true) activate(routed.localPosition)
                            armed = false
                            routed.consume()
                        }

                        PointerAction.CANCEL -> {
                            armed = false
                        }

                        else -> {}
                    }
                }

                is KeyEvent -> if (input.action == KeyAction.DOWN && input.key in listOf(Key.ENTER, Key.SPACE)) {
                    activate(null)
                    ripple = 0f
                    routed.consume()
                }
            }
        }
    }

    protected open fun activate(position: Vec2?) {
        action()
    }

    override fun update(delta: Duration): Boolean {
        val dt = delta.inWholeNanoseconds / 1_000_000_000f
        hover.target = if (state.hovered || state.focusVisible || active) 1f else 0f
        press.target = if (state.pressed) 1f else 0f
        if (model.reducedMotion) {
            hover.snap()
            press.snap()
            ripple = 1f
        }

        val changed = hover.step(dt) or press.step(dt)
        if (ripple < 1f) ripple = (ripple + dt * 3f).coerceAtMost(1f)
        if (changed || ripple < 1f) invalidate()
        return changed
    }

    override fun paint(canvas: Canvas) {
        val base = if (purple) 0x5C30CE.rgb else 0x2362BE.rgb
        val edge = if (purple) 0x9365EB.rgb else 0x5899ED.rgb
        canvas.pixelPanel(bounds, base.shade(1f + hover.value * 0.2f - press.value * 0.15f), edge, 4f, 2f)
        if (state.focusVisible) canvas.stroke(Rectangle, Rect(4f, 4f, bounds.width - 8f, bounds.height - 8f), Solid(0xC8E8FFFF.rgba), 1f)
        if (ripple < 1f) {
            val band = bounds.width * ripple
            canvas.rect(
                (rippleOrigin.x - band / 2).coerceAtLeast(4f), 4f,
                band.coerceAtMost(bounds.width - 8f), bounds.height - 8f, Color.WHITE.alpha((1f - ripple) * 0.12f)
            )
        }
        val measured = ui!!.text.layout(TextRequest(title, TextStyle(size = 20.sp), textScale = ui!!.settings.textScale)).size.width
        val fontSize = 20f * ((bounds.width - 18f) / measured.coerceAtLeast(1f)).coerceAtMost(1f)
        with(canvas) {
            label(title, Rect(9f, press.value * 2f, bounds.width - 18f, bounds.height), fontSize, shadow = true)
        }
    }
}

/**
 * Absolute placement is confined to this demo's composition; every item remains a retained Pixzel element.
 */
internal fun positionedLayout(rectangle: (String, Size) -> Rect) = LayoutPolicy { children, constraints, _ ->
    val size = Size(constraints.maxWidth, constraints.maxHeight)
    LayoutPlan(size, children.map { child ->
        val rect = rectangle(child.id, size)
        ChildPlacement(child.measure(Constraints.fixed(rect.size)), Vec2(rect.x, rect.y))
    })
}
