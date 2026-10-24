package pixzel.dsl

import pixzel.Ui
import pixzel.component.Button
import pixzel.component.Element
import pixzel.component.HitShape
import pixzel.component.Painter
import pixzel.component.Text
import pixzel.component.TextField
import pixzel.graphics.Brush
import pixzel.graphics.RoundedRectangle
import pixzel.layout.CrossAxisAlignment
import pixzel.layout.GridTrack
import pixzel.layout.columnLayout
import pixzel.layout.flowLayout
import pixzel.layout.gridLayout
import pixzel.layout.rowLayout
import pixzel.layout.stackLayout
import pixzel.layout.unit.Dp
import pixzel.layout.unit.dp
import pixzel.state.Binding
import pixzel.state.MutableState
import pixzel.state.State
import pixzel.state.StateKey
import pixzel.state.getValue
import pixzel.state.stateOf
import pixzel.surface.Placement
import pixzel.surface.Vec2
import pixzel.text.TextAlignment
import pixzel.text.TextOverflowPolicy
import pixzel.text.TextRevealBehavior
import pixzel.text.TextSpans
import pixzel.text.TextStyle
import pixzel.text.VerticalAlignment

@DslMarker
annotation class PixzelDsl
@PixzelDsl
open class ContentScope(val ui: Ui, val parent: Element) {

    fun <T> state(key: StateKey<T>, initial: () -> T): MutableState<T> = parent.state(key, initial)
    fun <T : Element> insert(element: T, configure: T.() -> Unit = {}): T {
        parent.add(element)
        element.configure()
        return element
    }

    fun surface(id: String = "", configure: SurfaceScope.() -> Unit = {}): Element = ui.components.surface(id).also {
        parent.add(it)
        SurfaceScope(ui, it).configure()
    }

    fun row(
        id: String = "",
        spacing: Dp = 0.dp,
        alignment: CrossAxisAlignment = CrossAxisAlignment.START,
        configure: RowScope.() -> Unit = {}
    ): Element =
        ui.components.surface(id).also {
            it.layoutPolicy = rowLayout(spacing, alignment)
            parent.add(it)
            RowScope(ui, it).configure()
        }

    fun column(
        id: String = "",
        spacing: Dp = 0.dp,
        alignment: CrossAxisAlignment = CrossAxisAlignment.START,
        configure: ColumnScope.() -> Unit = {}
    ): Element =
        ui.components.surface(id).also {
            it.layoutPolicy = columnLayout(spacing, alignment)
            parent.add(it)
            ColumnScope(ui, it).configure()
        }

    fun stack(id: String = "", configure: SurfaceScope.() -> Unit = {}): Element = surface(id) {
        element.layoutPolicy = stackLayout()
        configure()
    }

    fun flow(id: String = "", spacing: Dp = 0.dp, configure: SurfaceScope.() -> Unit = {}): Element = surface(id) {
        element.layoutPolicy = flowLayout(spacing)
        configure()
    }

    fun grid(columns: List<GridTrack>, id: String = "", spacing: Dp = 0.dp, configure: GridScope.() -> Unit = {}): Element =
        ui.components.surface(id).also {
            it.layoutPolicy = gridLayout(columns, spacing)
            parent.add(it)
            GridScope(ui, it, columns.size).configure()
        }

    fun text(value: String = "", id: String = "", configure: TextScope.() -> Unit = {}): Text = ui.components.text(id).also {
        parent.add(it)
        it.value = value
        TextScope(ui, it).configure()
    }

    fun button(id: String = "", configure: ButtonScope.() -> Unit = {}): Button = ui.components.button(id).also {
        parent.add(it)
        ButtonScope(ui, it).configure()
    }

    fun textField(value: String = "", id: String = "", configure: TextField.() -> Unit = {}): TextField = ui.components.textField(id).also {
        parent.add(it)
        it.value = value
        it.configure()
    }

    fun spacer(width: Dp = 0.dp, height: Dp = 0.dp) = surface {
        layout {
            this.width = width
            this.height = height
        }
    }
}

@PixzelDsl
open class ElementScope(ui: Ui, val element: Element) : ContentScope(ui, element) {

    fun layout(configure: LayoutScope.() -> Unit) {
        LayoutScope(element.layout).configure()
    }

    fun placement(configure: PlacementScope.() -> Unit) {
        element.placement = PlacementScope(element.placement).apply(configure).build()
    }

    fun appearance(configure: AppearanceScope.() -> Unit) {
        AppearanceScope(element).configure()
    }

    var hitShape: HitShape
        get() = element.hitShape
        set(value) {
            element.hitShape = value
        }

    open fun content(configure: ContentScope.() -> Unit) {
        element.children.toList().forEach { element.remove(it) }
        ContentScope(ui, element).configure()
    }
}

/**
 * Generic Surface configuration has no Row/Column weight or Grid-cell parent-data helpers.
 */
@PixzelDsl
open class SurfaceScope(ui: Ui, element: Element) : ElementScope(ui, element)

/**
 * Parent data is valid only for a direct child of this linear container.
 */
@PixzelDsl
abstract class LinearScope(ui: Ui, element: Element) : SurfaceScope(ui, element) {

    fun <T : Element> weight(child: T, value: Float): T {
        require(value.isFinite() && value >= 0f) { "Child weight must be finite and nonnegative" }
        require(child.parent === element && !child.isDisposed) { "Weighted element '${child.id}' must be a direct child of '${element.id}'" }
        child.layout.weight = value
        return child
    }

    fun <T : Element> T.weighted(value: Float): T = weight(this, value)
}

@PixzelDsl
class RowScope(ui: Ui, element: Element) : LinearScope(ui, element)
@PixzelDsl
class ColumnScope(ui: Ui, element: Element) : LinearScope(ui, element)

@PixzelDsl
class GridScope(ui: Ui, element: Element, private val columnCount: Int) : SurfaceScope(ui, element) {

    fun <T : Element> cell(child: T, row: Int, column: Int): T {
        require(row >= 0 && column in 0 until columnCount) { "Grid cell is outside the configured rows/columns" }
        require(child.parent === element && !child.isDisposed) { "Grid element '${child.id}' must be a direct child of '${element.id}'" }
        child.layout.gridRow = row
        child.layout.gridColumn = column
        return child
    }

    fun <T : Element> T.inCell(row: Int, column: Int): T = cell(this, row, column)
}

@PixzelDsl
class ButtonScope(ui: Ui, val button: Button) : ElementScope(ui, button) {

    fun label(value: String = "", configure: TextScope.() -> Unit = {}): Text = button.setLabel(value).also { TextScope(ui, it).configure() }
    private fun slot(set: (Element) -> Unit, configure: ContentScope.() -> Unit) {
        val holder = ui.components.surface()
        try {
            ContentScope(ui, holder).configure()
            set(holder)
        } catch (error: Throwable) {
            try {
                holder.close()
            } catch (cleanup: Throwable) {
                if (cleanup !== error) error.addSuppressed(cleanup)
            }
            throw error
        }
    }

    fun leading(configure: ContentScope.() -> Unit) = slot({ button.setLeading(it) }, configure)
    fun trailing(configure: ContentScope.() -> Unit) = slot({ button.setTrailing(it) }, configure)
    override fun content(configure: ContentScope.() -> Unit) = slot(button::setContent, configure)
    fun onClick(handler: () -> Unit) {
        button.resources.own(button.onClick(handler))
    }
}

@PixzelDsl
class TextScope(ui: Ui, val text: Text) : ElementScope(ui, text) {

    fun value(value: String) {
        text.value = value
    }

    fun value(binding: Binding<String>) {
        text.bindValue(binding)
    }

    fun typography(configure: TypographyScope.() -> Unit) {
        text.style = TypographyScope(text.style).apply(configure).build()
    }

    var overflow: TextOverflowPolicy
        get() = text.overflow
        set(value) {
            text.overflow = value
        }
    var spans: TextSpans
        get() = text.spans
        set(value) {
            text.spans = value
        }
    var reveal: TextRevealBehavior
        get() = text.reveal
        set(value) {
            text.reveal = value
        }
    var ellipsisMarker: String
        get() = text.ellipsisMarker
        set(value) {
            text.ellipsisMarker = value
        }
    var maxLines: Int
        get() = text.maxLines
        set(value) {
            text.maxLines = value
        }
    var horizontalAlignment: TextAlignment
        get() = text.horizontalAlignment
        set(value) {
            text.horizontalAlignment = value
        }
    var verticalAlignment: VerticalAlignment
        get() = text.verticalAlignment
        set(value) {
            text.verticalAlignment = value
        }
}

class TypographyScope(style: TextStyle) {

    var size = style.size
    var family = style.family
    var weight = style.weight
    var slant = style.slant
    var letterSpacing = style.letterSpacing
    var lineHeight = style.lineHeight
    fun build() = TextStyle(family, size, weight, slant, letterSpacing, lineHeight)
}

class PlacementScope(placement: Placement) {

    var anchor = placement.anchor
    var pivot = placement.pivot
    var offset = placement.offset
    var referenceBox = placement.referenceBox
    fun offset(x: Dp, y: Dp) {
        offset = Vec2(x.value, y.value)
    }

    fun build() = Placement(anchor, pivot, offset, referenceBox)
}

class AppearanceScope(private val element: Element) {

    private var brush: Brush? = null
    private var radius = 0.dp
    var background: Brush?
        get() = brush
        set(value) {
            brush = value
            apply()
        }
    var corners: Dp
        get() = radius
        set(value) {
            radius = value
            apply()
        }
    var painter: Painter?
        get() = element.background
        set(value) {
            element.background = value
        }

    private fun apply() {
        val b = brush
        val r = radius.value
        element.background = b?.let { Painter { c, ctx -> c.fill(RoundedRectangle(r), ctx.bounds, it) } }
    }
}

/**
 * Replaces root content, disposing the previous tree and its owned observations.
 */
fun Ui.content(configure: ContentScope.() -> Unit) {
    assertThread()
    root.children.toList().forEach { root.remove(it) }
    ContentScope(this, root).configure()
}

/**
 * Reconciles one root per key. Reused handlers read the current item from State<T>.
 */
class KeyedItems<T, K>(
    private val ui: Ui,
    private val parent: Element,
    private val key: (T) -> K,
    private val create: ContentScope.(State<T>) -> Unit
) : AutoCloseable {

    private data class Entry<T>(val element: Element, val item: MutableState<T>)

    private val entries = linkedMapOf<K, Entry<T>>()
    private var nextId = 0L
    private var closed = false
    fun update(items: List<T>) {
        ui.assertThread()
        check(!closed) { "Keyed scope is closed" }
        val keys = items.map(key)
        require(keys.toSet().size == keys.size) { "Duplicate item key under '${parent.id}'" }
        (entries.keys - keys.toSet()).forEach { entries.remove(it)?.let { e -> parent.remove(e.element) } }
        items.forEach { item ->
            val k = key(item)
            val entry = entries[k]
            if (entry == null) {
                val e = ui.components.surface("item-${++nextId}")
                try {
                    parent.add(e)
                    val current = stateOf(item)
                    ContentScope(ui, e).create(current)
                    entries[k] = Entry(e, current)
                } catch (error: Throwable) {
                    try {
                        e.close()
                    } catch (cleanup: Throwable) {
                        if (cleanup !== error) error.addSuppressed(cleanup)
                    }
                    throw error
                }
            } else entry.item.value = item
        }
        parent.reorder(keys.map { entries.getValue(it).element })
    }

    override fun close() {
        ui.assertThread()
        if (closed) return
        closed = true
        var failure: Throwable? = null
        entries.values.toList().forEach {
            try {
                it.element.close()
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
        }
        entries.clear()
        failure?.let { throw it }
    }
}

fun <T, K> ContentScope.items(items: State<List<T>>, key: (T) -> K, create: ContentScope.(State<T>) -> Unit): Element {
    val holder = surface()
    val managed = KeyedItems(ui, holder, key, create)
    holder.resources.own(managed)
    managed.update(items.value)
    holder.resources.own(items.observe { managed.update(it) })
    return holder
}

fun <T> ContentScope.dynamic(binding: Binding<T>, create: ContentScope.(T) -> Unit): Element {
    val holder = surface()
    fun replace(value: T) {
        holder.children.toList().forEach { holder.remove(it) }
        ContentScope(ui, holder).create(value)
    }
    replace(binding.value)
    holder.resources.own(binding)
    holder.resources.own(binding.observe(::replace))
    return holder
}
