package pixzel.layout

import pixzel.component.Invalidation
import pixzel.layout.unit.Dp
import pixzel.layout.unit.SizeSpec
import pixzel.layout.unit.dp
import pixzel.layout.unit.wrapContent
import pixzel.surface.Insets
import pixzel.surface.Placement
import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.surface.Vec2
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

data class Constraints(
    val minWidth: Float = 0f,
    val maxWidth: Float = Float.POSITIVE_INFINITY,
    val minHeight: Float = 0f,
    val maxHeight: Float = Float.POSITIVE_INFINITY
) {

    init {
        require(minWidth.isFinite() && minHeight.isFinite() && minWidth >= 0 && minHeight >= 0) { "Constraint minima must be finite and nonnegative" }
        require(!maxWidth.isNaN() && !maxHeight.isNaN() && maxWidth >= minWidth && maxHeight >= minHeight) { "Invalid constraint range" }
    }

    fun constrain(size: Size) = Size(size.width.coerceIn(minWidth, maxWidth), size.height.coerceIn(minHeight, maxHeight))
    fun inset(insets: Insets) = Constraints(
        (minWidth - insets.horizontal).coerceAtLeast(0f),
        (maxWidth - insets.horizontal).coerceAtLeast(0f),
        (minHeight - insets.vertical).coerceAtLeast(0f),
        (maxHeight - insets.vertical).coerceAtLeast(0f)
    )

    companion object {

        fun fixed(size: Size) = Constraints(size.width, size.width, size.height, size.height)
    }
}

/** Mutable configuration, with immediate owner invalidation. Computed Surface data is separate. */
class LayoutConfig(private val changed: (Invalidation) -> Unit = {}) {

    private fun <T> property(initial: T) = object : ReadWriteProperty<Any?, T> {
        private var value = initial
        override fun getValue(thisRef: Any?, property: KProperty<*>) = value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (this.value != value) {
                changed(Invalidation.MEASURE)
                this.value = value
            }
        }
    }

    var width: SizeSpec by property(wrapContent)
    var height: SizeSpec by property(wrapContent)
    var minWidth: Dp by property(0.dp)
    var minHeight: Dp by property(0.dp)
    var maxWidth: Dp? by property(null)
    var maxHeight: Dp? by property(null)
    var padding: Insets by property(Insets.ZERO)
    var margin: Insets by property(Insets.ZERO)
    var spacing: Dp by property(0.dp)
    var weight: Float by property(0f)
    var aspectRatio: Float? by property(null)
    var gridColumn: Int by property(0)
    var gridRow: Int by property(0)
}

data class LayoutEnvironment(
    val textScale: Float = 1f,
    val availableRoot: Size = Size.ZERO,
    val contentBox: Rect? = null,
    val borderBox: Rect? = null,
    val safeAreaBox: Rect? = null
)

interface Measurable {

    val id: String
    val layout: LayoutConfig
    val placement: Placement
    fun measure(constraints: Constraints): Placeable
}

interface Placeable {

    val size: Size
    val baseline: Float?

    /** Called once during arrange, in parent-local dp coordinates. */
    fun place(position: Vec2)
}

data class ChildPlacement(val child: Placeable, val position: Vec2)
data class LayoutPlan(val size: Size, val children: List<ChildPlacement> = emptyList(), val baseline: Float? = null)

/** Policies receive only public measurables. Measurement must not mutate the tree. */
fun interface LayoutPolicy {

    fun measure(children: List<Measurable>, constraints: Constraints, environment: LayoutEnvironment): LayoutPlan
}
