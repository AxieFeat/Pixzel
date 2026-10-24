package pixzel.layout

import org.junit.jupiter.api.Test
import pixzel.component.AbstractElement
import pixzel.layout.unit.SizeSpec
import pixzel.layout.unit.dp
import pixzel.layout.unit.fillAvailable
import pixzel.layout.unit.percent
import pixzel.layout.unit.wrapContent
import pixzel.surface.Anchor
import pixzel.surface.Insets
import pixzel.surface.Placement
import pixzel.surface.ReferenceBox
import pixzel.surface.Size
import pixzel.surface.Vec2
import kotlin.math.abs

private class LayoutBox(id: String) : AbstractElement(id)

fun runLayoutChecks() {
    fun near(actual: Float, expected: Float) = check(abs(actual - expected) < 0.001f) { "$actual != $expected" }
    fun box(id: String, width: SizeSpec = wrapContent, height: SizeSpec = 10.dp) = LayoutBox(id).apply {
        layout.width = width
        layout.height = height
    }

    fun arrange(parent: LayoutBox, width: Float, height: Float) {
        parent.measure(Constraints.fixed(Size(width, height))).place(Vec2.ZERO)
    }

    val weighted = box("weighted").apply { layoutPolicy = rowLayout() }
    val quarter = box("quarter", fillAvailable).apply { layout.weight = 0.25f }
    val rest = box("rest", fillAvailable).apply { layout.weight = 0.75f }
    weighted.add(quarter)
    weighted.add(rest)
    arrange(weighted, 100f, 20f)
    near(quarter.surface.bounds.width, 25f)
    near(rest.surface.bounds.width, 75f)
    near(rest.surface.bounds.x, 25f)

    quarter.layout.weight = 0f
    rest.layout.weight = 0f
    quarter.layout.maxWidth = 20.dp
    arrange(weighted, 100f, 20f)
    near(quarter.surface.bounds.width, 20f)
    near(rest.surface.bounds.width, 80f)
    quarter.layout.maxWidth = null
    quarter.layout.minWidth = 60.dp
    rest.layout.minWidth = 50.dp
    arrange(weighted, 100f, 20f)
    near(weighted.surface.bounds.width, 100f)
    near(quarter.surface.bounds.width, 60f)
    near(rest.surface.bounds.width, 50f)
    near(rest.surface.bounds.right, 110f)

    val column = box("column").apply { layoutPolicy = columnLayout(5.dp) }
    val top = box("top", 10.dp, fillAvailable).apply { layout.weight = 0.2f }
    val bottom = box("bottom", 10.dp, fillAvailable).apply { layout.weight = 0.8f }
    column.add(top)
    column.add(bottom)
    arrange(column, 20f, 105f)
    near(top.surface.bounds.height, 20f)
    near(bottom.surface.bounds.height, 80f)
    near(bottom.surface.bounds.y, 25f)

    val parent = box("anchor-parent", 200.dp, 100.dp).apply { layout.padding = Insets(20f, 10f, 30f, 15f) }
    val anchored = box("anchored", 40.dp, 20.dp)
    parent.add(anchored)
    for (anchor in Anchor.entries) {
        anchored.placement = Placement(anchor, anchor)
        arrange(parent, 200f, 100f)
        near(anchored.surface.bounds.x, 20f + anchor.x * (150f - 40f))
        near(anchored.surface.bounds.y, 10f + anchor.y * (75f - 20f))
    }
    anchored.placement = Placement(referenceBox = ReferenceBox.BORDER)
    arrange(parent, 200f, 100f)
    near(anchored.surface.bounds.x, 0f)
    near(anchored.surface.bounds.y, 0f)
    anchored.placement = Placement(referenceBox = ReferenceBox.SAFE_AREA)
    arrange(parent, 200f, 100f)
    near(anchored.surface.bounds.x, 20f)
    near(anchored.surface.bounds.y, 10f)
    anchored.layout.width = 50.percent
    anchored.placement = Placement(referenceBox = ReferenceBox.BORDER)
    arrange(parent, 200f, 100f)
    near(anchored.surface.bounds.width, 100f)
    anchored.placement = Placement(referenceBox = ReferenceBox.CONTENT)
    arrange(parent, 200f, 100f)
    near(anchored.surface.bounds.width, 75f)

    val root = box("root", 1280.dp, 720.dp)
    val notification = box("notification", 320.dp, 72.dp).apply {
        placement = Placement(offset = Vec2(24f, 24f))
        layout.padding = Insets.all(12.dp)
    }
    val text = box("text", 200.dp, 24.dp).apply { placement = Placement.center() }
    notification.add(text)
    root.add(notification)
    arrange(root, 1280f, 720f)
    check(notification.surface.localToRoot.map(Vec2.ZERO) == Vec2(24f, 24f))
    check(text.surface.localToRoot.map(Vec2.ZERO) == Vec2(84f, 48f))

    val grid = box("grid").apply { layoutPolicy = gridLayout(listOf(FixedTrack(20.dp), WeightedTrack(0.25f), WeightedTrack(0.75f)), 5.dp) }
    val cells = (0..2).map { index -> box("cell$index", fillAvailable).apply { layout.gridColumn = index } }
    cells.forEach { grid.add(it) }
    arrange(grid, 130f, 20f)
    near(cells[0].surface.bounds.width, 20f)
    near(cells[1].surface.bounds.width, 25f)
    near(cells[2].surface.bounds.width, 75f)
    near(cells[2].surface.bounds.x, 55f)
    cells[2].layout.gridRow = 1_000_000
    arrange(grid, 130f, 20f)
    near(cells[2].surface.bounds.y, 5_000_010f)

    val contentGrid = box("content-grid").apply { layoutPolicy = gridLayout(listOf(ContentTrack, WeightedTrack()), 4.dp) }
    val content = box("intrinsic-cell", 20.dp).apply { layout.margin = Insets.all(2.dp) }
    val fill = box("fill-cell", fillAvailable).apply { layout.gridColumn = 1 }
    contentGrid.add(content)
    contentGrid.add(fill)
    arrange(contentGrid, 100f, 30f)
    near(content.surface.bounds.width, 20f)
    near(fill.surface.bounds.x, 28f)
    near(fill.surface.bounds.width, 72f)

    val flow = box("flow").apply { layoutPolicy = flowLayout(10.dp) }
    val flowCells = (0..2).map { box("flow$it", 40.dp).apply { layout.margin = Insets.all(2.dp) } }
    flowCells.forEach { flow.add(it) }
    arrange(flow, 100f, 50f)
    near(flowCells[1].surface.bounds.x, 56f)
    near(flowCells[2].surface.bounds.y, 26f)

    check(runCatching { rowLayout((-1).dp) }.isFailure)
    check(runCatching { flowLayout((-1).dp) }.isFailure)
    check(runCatching { gridLayout(listOf(ContentTrack), (-1).dp) }.isFailure)
    check(runCatching { FixedTrack((-1).dp) }.isFailure)
    check(runCatching { WeightedTrack(Float.NaN) }.isFailure)
    check(runCatching { WeightedTrack(0f) }.isFailure)
    check(runCatching { gridLayout(emptyList()) }.isFailure)
    val fillChild = box("unbounded-fill", fillAvailable)
    check(runCatching {
        rowLayout().measure(
            listOf(fillChild),
            Constraints(),
            LayoutEnvironment()
        )
    }.exceptionOrNull()?.message?.contains("finite reference") == true)
    check(runCatching { resolveSize(50.percent, 0f, Float.POSITIVE_INFINITY) }.isFailure)
    check(runCatching { gridLayout(listOf(WeightedTrack())).measure(emptyList(), Constraints(), LayoutEnvironment()) }.isFailure)
    check(runCatching { flowLayout().measure(emptyList(), Constraints(), LayoutEnvironment()) }.isFailure)
    content.layout.gridRow = -1
    check(runCatching { contentGrid.measure(Constraints.fixed(Size(100f, 30f))) }.exceptionOrNull()?.message?.contains("Invalid grid cell") == true)
}

class LayoutChecksTest {

    @Test
    fun layouts() = runLayoutChecks()
}

fun main() {
    runLayoutChecks()
    println("Layout checks passed")
}
