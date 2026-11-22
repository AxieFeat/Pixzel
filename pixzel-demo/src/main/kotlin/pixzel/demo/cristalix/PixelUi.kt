@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.Ui
import pixzel.component.Button
import pixzel.component.Element
import pixzel.component.Invalidation
import pixzel.component.Painter
import pixzel.component.Text
import pixzel.component.Visibility
import pixzel.dsl.ContentScope
import pixzel.graphics.Color
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.input.PointerPolicy
import pixzel.layout.ChildPlacement
import pixzel.layout.Constraints
import pixzel.layout.LayoutPlan
import pixzel.layout.LayoutPolicy
import pixzel.layout.unit.dp
import pixzel.layout.unit.sp
import pixzel.resource.Subscription
import pixzel.state.StateKey
import pixzel.surface.Insets
import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.text.FontFace
import pixzel.text.FontFamily
import pixzel.text.TextStyle
import pixzel.text.TextOverflow
import pixzel.text.TextOverflowPolicy
import java.util.IdentityHashMap

/**
 * Reusable pixel skin for ordinary Pixzel buttons. Input, capture, focus and semantics stay in Pixzel.
 */
internal data class PixelButtonStyle(
    val fill: Color = 0x2362BE.rgb,
    val edge: Color = 0x5899ED.rgb,
    val text: Color = Color.WHITE,
    val fontSize: Float = 20f,
    val cut: Float = 4f,
    val border: Float = 2f,
    val flat: Boolean = false,
    val smallFont: Boolean = false,
    val padding: Float = 9f
) {

    companion object {

        val BLUE = PixelButtonStyle()
        val PURPLE = PixelButtonStyle(fill = 0x5C30CE.rgb, edge = 0x9365EB.rgb)
        val REVIEW = PixelButtonStyle(
            fill = 0x17171770.rgba, edge = 0x99999980.rgba, fontSize = 11f,
            cut = 2f, border = 1f, smallFont = true, padding = 3f
        )
        val REVIEW_BLUE = REVIEW.copy(fill = 0x2464BF.rgb, edge = 0x629FEB.rgb)
    }
}

private val buttonMotionKey = StateKey<MenuSpring>("pixel-button-motion")
private val Button.hoverMotion get() = state(buttonMotionKey) { MenuSpring() }.value

// The containing viewport already clips the page. A label needs its own GPU clip only when it overflows.
internal val clippedText = TextOverflowPolicy { context ->
    TextOverflow.CLIP.resolve(context).let { it.copy(clip = it.overflowed) }
}
private val buttonText = TextOverflowPolicy { context ->
    TextOverflow.ELLIPSIS.resolve(context).let { it.copy(clip = it.overflowed) }
}

internal var Button.title: String
    get() = (content as? Text)?.value ?: semantics.label
    set(value) {
        if (title == value) return
        (content as? Text)?.let { it.value = value } ?: setLabel(value)
        semantics = semantics.copy(label = value)
    }

internal var Button.selected: Boolean
    get() = state.selected
    set(value) {
        if (state.selected != value) setInteractionState(state.copy(selected = value))
    }

internal val Element.bounds get() = Rect(0f, 0f, surface.bounds.width, surface.bounds.height)

internal fun Ui.compose(id: String, build: ContentScope.() -> Unit = {}): Element =
    components.surface(id).also { ContentScope(this, it).build() }

internal fun Element.draw(owner: Ui, paint: PixelCanvas.() -> Unit) {
    background = Painter { canvas, context -> PixelCanvas(owner, context.bounds, canvas).paint() }
}

internal fun Element.onFrame(update: (Float) -> Boolean): Subscription =
    frameUpdates.add { update(it.inWholeNanoseconds / 1_000_000_000f) }

internal fun <T> Element.observe(value: () -> T, changed: (T) -> Unit) {
    var initialized = false
    var previous: T? = null
    onFrame {
        val next = value()
        if (!initialized || previous != next) {
            initialized = true
            previous = next
            changed(next)
        }
        false
    }
}

internal fun Element.showIf(show: Boolean) {
    visibility = if (show) Visibility.VISIBLE else Visibility.HIDDEN
}

internal fun Ui.pixelButton(
    id: String, title: String = "", style: PixelButtonStyle = PixelButtonStyle.BLUE,
    reducedMotion: () -> Boolean = { false }, clicked: () -> Unit = {}
): Button = components.button(id).also { button ->
    button.layout.padding = Insets.horizontal(style.padding.dp)
    button.foreground = null
    button.setLabel(title).apply {
        this.style = TextStyle(size = style.fontSize.sp, family = if (style.smallFont) SMALL_FONT else FontFamily.DEFAULT)
        color = style.text
        overflow = buttonText
    }
    button.draw(this) {
        val hover = button.hoverMotion.value
        if (!style.flat) pixelPanel(
            bounds,
            if (button.selected) 0xEEEEEE.rgb else style.fill.shade(1f + hover * .2f - if (button.state.pressed) .15f else 0f),
            style.edge, style.cut, style.border
        )
    }
    button.onClick(clicked)
    button.onFrame { seconds ->
        val motion = button.hoverMotion
        motion.target = if (button.state.hovered || button.state.focusVisible || button.selected) 1f else 0f
        if (reducedMotion()) motion.snap()
        (button.content as? Text)?.color = if (button.selected) 0x181818.rgb else style.text
        motion.step(seconds)
    }
}

internal fun ContentScope.action(
    id: String, title: String, style: PixelButtonStyle = PixelButtonStyle.BLUE, clicked: () -> Unit
): Button = insert(ui.pixelButton(id, title, style, clicked = clicked))

/**
 * Typed child placement: IDs are debug labels, never layout selectors.
 */
internal class ViewPlacement {

    private val entries = IdentityHashMap<Element, (Size) -> Rect>()
    fun place(child: Element, rectangle: (Size) -> Rect) {
        entries[child] = rectangle
    }

    fun build() = LayoutPolicy { children, constraints, _ ->
        val size = Size(constraints.maxWidth, constraints.maxHeight)
        LayoutPlan(size, children.map { child ->
            val rect = entries[child]?.invoke(size) ?: Rect(0f, 0f, size.width, size.height)
            ChildPlacement(child.measure(Constraints.fixed(rect.size)), pixzel.surface.Vec2(rect.x, rect.y))
        })
    }
}

internal fun Element.arrange(build: ViewPlacement.() -> Unit) {
    layoutPolicy = ViewPlacement().apply(build).build()
}

/**
 * Shared overlay ownership and focus. A hidden overlay has no active input or frame work.
 */
internal class Overlay(val root: Element, private val focus: () -> Element? = { null }) {

    var open = false
        private set
    private var scope: Subscription? = null

    init {
        root.showIf(false)
        root.pointerPolicy = PointerPolicy.AUTO
        root.focusable = true
        root.resources.own(Subscription { scope?.close() })
    }

    fun show() {
        root.showIf(true)
        open = true
        scope?.close()
        scope = root.ui?.focus?.pushScope(root)
        root.ui?.focus?.request(focus() ?: root)
        root.invalidate(Invalidation.MEASURE)
    }

    fun dismiss() {
        open = false
        scope?.close()
        scope = null
        root.showIf(false)
    }
}

internal val SMALL_FONT = FontFamily(listOf(FontFace("minecraft-five")))
