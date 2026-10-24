package pixzel.layout

import pixzel.layout.unit.Dp
import pixzel.layout.unit.FillAvailable
import pixzel.layout.unit.Percent
import pixzel.layout.unit.SizeSpec
import pixzel.layout.unit.WrapContent
import pixzel.layout.unit.dp
import pixzel.surface.Anchor
import pixzel.surface.Pivot
import pixzel.surface.Placement
import pixzel.surface.Rect
import pixzel.surface.ReferenceBox
import pixzel.surface.Size
import pixzel.surface.Vec2
import kotlin.math.max

private fun requireFinite(value: Float, axis: String): Float {
    require(value.isFinite()) { "$axis requires a finite reference size (fill/percentage inside an unbounded parent)" }
    return value
}

private fun requireSpacing(spacing: Dp) {
    require(spacing.value >= 0f) { "Layout spacing must be nonnegative." }
}

/** Sizing consumes finite maxima for fill/percentage; wrap never uses an implicit infinity. */
fun resolveSize(spec: SizeSpec, intrinsic: Float, available: Float): Float = when (spec) {
    is Dp -> spec.value.also { require(it >= 0f) { "Size cannot be negative." } }
    is Percent -> requireFinite(available, "Percentage") * spec.value
    FillAvailable -> requireFinite(available, "fillAvailable")
    WrapContent -> intrinsic
    else -> error("Unknown SizeSpec ${spec::class.simpleName}; supply a custom layout policy.")
}

private fun referenceBox(placement: Placement, size: Size, environment: LayoutEnvironment): Rect =
    when (placement.referenceBox) {
        ReferenceBox.CONTENT -> environment.contentBox
        ReferenceBox.BORDER -> environment.borderBox
        ReferenceBox.SAFE_AREA -> environment.safeAreaBox ?: environment.contentBox
    } ?: Rect(0f, 0f, size.width, size.height)

private fun anchored(child: Measurable, placeable: Placeable, box: Rect): Vec2 {
    val p = child.placement
    val m = child.layout.margin
    if (p.anchor == Anchor.TOP_LEFT && p.pivot == Pivot.TOP_LEFT && m.left == 0f && m.top == 0f && box.x == 0f && box.y == 0f) return p.offset
    val availableWidth = (box.width - m.horizontal).coerceAtLeast(0f)
    val availableHeight = (box.height - m.vertical).coerceAtLeast(0f)
    return Vec2(
        box.x + m.left + p.anchor.x * availableWidth - p.pivot.x * placeable.size.width + p.offset.x,
        box.y + m.top + p.anchor.y * availableHeight - p.pivot.y * placeable.size.height + p.offset.y
    )
}

/** Uses each child's selected reference box for both available size and anchor placement. */
fun overlayLayout(): LayoutPolicy = LayoutPolicy { children, constraints, environment ->
    val ordinary = Constraints(
        maxWidth = environment.contentBox?.width ?: constraints.maxWidth,
        maxHeight = environment.contentBox?.height ?: constraints.maxHeight
    )

    fun measure(child: Measurable): Placeable {
        val box = when (child.placement.referenceBox) {
            ReferenceBox.CONTENT -> environment.contentBox
            ReferenceBox.BORDER -> environment.borderBox
            ReferenceBox.SAFE_AREA -> environment.safeAreaBox ?: environment.contentBox
        }
        val margin = child.layout.margin
        val width = box?.width ?: constraints.maxWidth
        val height = box?.height ?: constraints.maxHeight
        val available =
            if (margin.horizontal == 0f && margin.vertical == 0f && width == ordinary.maxWidth && height == ordinary.maxHeight) ordinary else Constraints(
                maxWidth = (width - margin.horizontal).coerceAtLeast(0f),
                maxHeight = (height - margin.vertical).coerceAtLeast(0f)
            )
        return child.measure(available)
    }
    if (constraints.minWidth == constraints.maxWidth && constraints.minHeight == constraints.maxHeight) {
        val size = Size(constraints.maxWidth, constraints.maxHeight)
        LayoutPlan(size, children.map { child ->
            val p = measure(child)
            ChildPlacement(p, anchored(child, p, referenceBox(child.placement, size, environment)))
        })
    } else {
        val measured = children.map { child ->
            // An intrinsic pass may have no boxes yet; unbounded wrap measurement remains valid.
            val box = when (child.placement.referenceBox) {
                ReferenceBox.CONTENT -> environment.contentBox
                ReferenceBox.BORDER -> environment.borderBox
                ReferenceBox.SAFE_AREA -> environment.safeAreaBox ?: environment.contentBox
            }
            val margin = child.layout.margin
            child to child.measure(
                Constraints(
                    maxWidth = ((box?.width ?: constraints.maxWidth) - margin.horizontal).coerceAtLeast(0f),
                    maxHeight = ((box?.height ?: constraints.maxHeight) - margin.vertical).coerceAtLeast(0f)
                )
            )
        }
        val desired = Size(
            measured.maxOfOrNull { it.second.size.width + it.first.layout.margin.horizontal } ?: 0f,
            measured.maxOfOrNull { it.second.size.height + it.first.layout.margin.vertical } ?: 0f
        )
        val size = constraints.constrain(desired)
        LayoutPlan(size, measured.map { (child, placeable) ->
            ChildPlacement(placeable, anchored(child, placeable, referenceBox(child.placement, size, environment)))
        })
    }
}

/**
 * Fixed children, margins, spacing and flexible minima reserve space first. Flexible children
 * divide the remainder by their positive weight (implicit fill weight is one), capped by their
 * configured maxima. If minima exceed the parent, children overflow without negative sizing.
 * The parent's own constrained size is preserved; clipping is an independent element policy.
 */
enum class CrossAxisAlignment {

    START,
    CENTER,
    END,
    BASELINE
}

private fun linearLayout(horizontal: Boolean, spacing: Dp, alignment: CrossAxisAlignment): LayoutPolicy {
    requireSpacing(spacing)
    require(horizontal || alignment != CrossAxisAlignment.BASELINE) { "Baseline alignment applies to horizontal rows" }
    return LayoutPolicy { children, constraints, _ ->
        val available = if (horizontal) constraints.maxWidth else constraints.maxHeight
        val cross = if (horizontal) constraints.maxHeight else constraints.maxWidth
        val measured = arrayOfNulls<Placeable>(children.size)
        fun main(placeable: Placeable) = if (horizontal) placeable.size.width else placeable.size.height
        fun mainMargin(child: Measurable) = if (horizontal) child.layout.margin.horizontal else child.layout.margin.vertical
        fun crossMargin(child: Measurable) = if (horizontal) child.layout.margin.vertical else child.layout.margin.horizontal
        fun minimum(child: Measurable) = if (horizontal) child.layout.minWidth.value else child.layout.minHeight.value
        fun crossMinimum(child: Measurable) = if (horizontal) child.layout.minHeight.value else child.layout.minWidth.value
        fun maximum(child: Measurable) = if (horizontal) child.layout.maxWidth?.value else child.layout.maxHeight?.value
        fun flexible(child: Measurable) = child.layout.weight > 0f ||
                (if (horizontal) child.layout.width else child.layout.height) == FillAvailable

        fun weight(child: Measurable) = if (child.layout.weight > 0f) child.layout.weight.toDouble() else 1.0
        fun measure(child: Measurable, minMain: Float, maxMain: Float): Placeable {
            val maxCross = max(crossMinimum(child), (cross - crossMargin(child)).coerceAtLeast(0f))
            return child.measure(
                if (horizontal) Constraints(minMain, maxMain, 0f, maxCross)
                else Constraints(0f, maxCross, minMain, maxMain)
            )
        }

        children.forEach { child ->
            require(child.layout.weight.isFinite() && child.layout.weight >= 0f) { "Weight at '${child.id}' must be finite and nonnegative." }
            require(minimum(child) >= 0f && crossMinimum(child) >= 0f) { "Minimum size at '${child.id}' must be nonnegative." }
            require(maximum(child) == null || maximum(child)!! >= minimum(child)) { "Maximum size at '${child.id}' is below its minimum." }
        }
        val flexibleIndices = children.indices.filter { flexible(children[it]) }
        if (flexibleIndices.isNotEmpty()) requireFinite(available, "Weighted layout")
        val gaps = spacing.value * (children.size - 1).coerceAtLeast(0)
        var used = gaps.toDouble() + children.sumOf { mainMargin(it).toDouble() }
        children.forEachIndexed { index, child ->
            if (flexible(child)) {
                used += minimum(child)
            } else {
                val maxMain = max(minimum(child), (available - mainMargin(child)).coerceAtLeast(0f))
                measured[index] = measure(child, 0f, maxMain)
                used += main(measured[index]!!)
            }
        }
        val allocations = DoubleArray(children.size) { minimum(children[it]).toDouble() }
        var remaining = (available.toDouble() - used).coerceAtLeast(0.0)
        val open = flexibleIndices.toMutableList()
        while (open.isNotEmpty() && remaining > 0.0) {
            val totalWeight = open.sumOf { weight(children[it]) }
            val saturated = open.filter { index ->
                val maximum = maximum(children[index])?.toDouble() ?: Double.POSITIVE_INFINITY
                allocations[index] + remaining * weight(children[index]) / totalWeight > maximum
            }
            if (saturated.isEmpty()) {
                open.forEach { allocations[it] += remaining * weight(children[it]) / totalWeight }
                break
            }
            saturated.forEach { index ->
                val maximum = maximum(children[index])!!.toDouble()
                remaining -= (maximum - allocations[index])
                allocations[index] = maximum
            }
            open.removeAll(saturated.toSet())
        }
        flexibleIndices.forEach { index ->
            val allocation = allocations[index].toFloat()
            require(allocation.isFinite()) { "Weighted allocation at '${children[index].id}' exceeds finite layout coordinates." }
            measured[index] = measure(children[index], allocation, allocation)
        }
        val mainSize = measured.filterNotNull().sumOf { main(it).toDouble() }.toFloat() +
                children.sumOf { mainMargin(it).toDouble() }.toFloat() + gaps
        val baseline = if (alignment == CrossAxisAlignment.BASELINE) children.indices.maxOfOrNull { index ->
            children[index].layout.margin.top + (measured[index]!!.baseline ?: measured[index]!!.size.height)
        } else null
        val crossSize = if (baseline != null) baseline + (children.indices.maxOfOrNull { index ->
            val p = measured[index]!!
            p.size.height - (p.baseline ?: p.size.height) + children[index].layout.margin.bottom
        } ?: 0f) else children.indices.maxOfOrNull { index ->
            (if (horizontal) measured[index]!!.size.height else measured[index]!!.size.width) + crossMargin(children[index])
        } ?: 0f
        val size = constraints.constrain(if (horizontal) Size(mainSize, crossSize) else Size(crossSize, mainSize))
        var cursor = 0f
        val placements = children.mapIndexed { index, child ->
            val placeable = measured[index]!!
            val margin = child.layout.margin
            val allocatedCross = if (horizontal) size.height else size.width
            val childCross = if (horizontal) placeable.size.height else placeable.size.width
            val crossOffset = when (alignment) {
                CrossAxisAlignment.START -> if (horizontal) margin.top else margin.left
                CrossAxisAlignment.CENTER -> (allocatedCross - childCross - crossMargin(child)) / 2 + (if (horizontal) margin.top else margin.left)
                CrossAxisAlignment.END -> allocatedCross - childCross - (if (horizontal) margin.bottom else margin.right)
                CrossAxisAlignment.BASELINE -> baseline!! - (placeable.baseline ?: placeable.size.height)
            }
            val position = if (horizontal) Vec2(cursor + margin.left, crossOffset) else Vec2(crossOffset, cursor + margin.top)
            cursor += main(placeable) + mainMargin(child) + spacing.value
            ChildPlacement(placeable, position)
        }
        LayoutPlan(size, placements, baseline)
    }
}

fun rowLayout(spacing: Dp = 0.dp, alignment: CrossAxisAlignment = CrossAxisAlignment.START): LayoutPolicy = linearLayout(true, spacing, alignment)
fun columnLayout(spacing: Dp = 0.dp, alignment: CrossAxisAlignment = CrossAxisAlignment.START): LayoutPolicy = linearLayout(false, spacing, alignment)
fun stackLayout(): LayoutPolicy = overlayLayout()

fun flowLayout(spacing: Dp = 0.dp): LayoutPolicy {
    requireSpacing(spacing)
    return LayoutPolicy { children, constraints, _ ->
        require(constraints.maxWidth.isFinite()) { "Flow requires a finite width." }
        var x = 0f
        var y = 0f
        var rowHeight = 0f
        var width = 0f
        val placed = children.map { child ->
            val margin = child.layout.margin
            val placeable = child.measure(
                Constraints(
                    maxWidth = (constraints.maxWidth - margin.horizontal).coerceAtLeast(0f),
                    maxHeight = (constraints.maxHeight - margin.vertical).coerceAtLeast(0f)
                )
            )
            if (x > 0f && x + placeable.size.width + margin.horizontal > constraints.maxWidth) {
                x = 0f
                y += rowHeight + spacing.value
                rowHeight = 0f
            }
            val position = Vec2(x + margin.left, y + margin.top)
            x += placeable.size.width + margin.horizontal + spacing.value
            width = max(width, x - spacing.value)
            rowHeight = max(rowHeight, placeable.size.height + margin.vertical)
            ChildPlacement(placeable, position)
        }
        LayoutPlan(constraints.constrain(Size(width, y + rowHeight)), placed)
    }
}

interface GridTrack
data class FixedTrack(val size: Dp) : GridTrack {
    init {
        require(size.value >= 0f) { "Fixed track size must be nonnegative." }
    }
}

data class WeightedTrack(val weight: Float = 1f) : GridTrack {
    init {
        require(weight.isFinite() && weight > 0f) { "Track weight must be finite and positive." }
    }
}

data object ContentTrack : GridTrack

/** Column tracks with content-height rows. Cell margins belong inside their allocated track. */
fun gridLayout(columns: List<GridTrack>, spacing: Dp = 0.dp): LayoutPolicy {
    requireSpacing(spacing)
    require(columns.isNotEmpty()) { "Grid requires at least one column." }
    val tracks = columns.toList()
    tracks.forEach { require(it is FixedTrack || it is WeightedTrack || it === ContentTrack) { "Unsupported grid track ${it::class.simpleName}." } }
    return LayoutPolicy { children, constraints, _ ->
        children.forEach { child ->
            require(child.layout.gridColumn in tracks.indices && child.layout.gridRow >= 0) {
                "Invalid grid cell at '${child.id}': row=${child.layout.gridRow}, column=${child.layout.gridColumn}."
            }
        }
        val widths = FloatArray(tracks.size)
        tracks.forEachIndexed { index, track ->
            widths[index] = when (track) {
                is FixedTrack -> track.size.value
                ContentTrack -> children.filter { it.layout.gridColumn == index }.maxOfOrNull { child ->
                    val margin = child.layout.margin
                    child.measure(
                        Constraints(
                            maxWidth = (constraints.maxWidth - margin.horizontal).coerceAtLeast(0f),
                            maxHeight = (constraints.maxHeight - margin.vertical).coerceAtLeast(0f)
                        )
                    ).size.width + margin.horizontal
                } ?: 0f

                is WeightedTrack -> 0f
                else -> error("Unsupported grid track.")
            }
        }
        val totalWeight = tracks.filterIsInstance<WeightedTrack>().sumOf { it.weight.toDouble() }
        if (totalWeight > 0.0) {
            requireFinite(constraints.maxWidth, "Weighted grid")
            val remaining = max(0.0, constraints.maxWidth.toDouble() - widths.sum() - spacing.value * (tracks.size - 1))
            tracks.forEachIndexed { index, track ->
                if (track is WeightedTrack) widths[index] = (remaining * track.weight / totalWeight).toFloat()
            }
        }
        val rowHeights = mutableMapOf<Int, Float>()
        val measured = children.map { child ->
            val margin = child.layout.margin
            val width = (widths[child.layout.gridColumn] - margin.horizontal).coerceAtLeast(0f)
            child.measure(Constraints(width, width, 0f, (constraints.maxHeight - margin.vertical).coerceAtLeast(0f)))
        }
        children.forEachIndexed { index, child ->
            rowHeights[child.layout.gridRow] = max(rowHeights[child.layout.gridRow] ?: 0f, measured[index].size.height + child.layout.margin.vertical)
        }
        val columnPositions = FloatArray(tracks.size)
        for (index in 1 until tracks.size) columnPositions[index] = columnPositions[index - 1] + widths[index - 1] + spacing.value
        var accumulatedHeight = 0f
        val rowPositions = rowHeights.keys.sorted().associateWith { row ->
            (accumulatedHeight + spacing.value * row).also { accumulatedHeight += rowHeights.getValue(row) }
        }
        val placed = children.mapIndexed { index, child ->
            ChildPlacement(
                measured[index], Vec2(
                    columnPositions[child.layout.gridColumn] + child.layout.margin.left,
                    rowPositions.getValue(child.layout.gridRow) + child.layout.margin.top
                )
            )
        }
        val lastRow = rowHeights.keys.maxOrNull() ?: 0
        val size = Size(widths.sum() + spacing.value * (tracks.size - 1), accumulatedHeight + spacing.value * lastRow)
        LayoutPlan(constraints.constrain(size), placed)
    }
}
