package pixzel

import org.junit.jupiter.api.Test
import pixzel.component.ClipPolicy
import pixzel.graphics.ClosePath
import pixzel.graphics.CornerRadii
import pixzel.graphics.CubicTo
import pixzel.graphics.FillRule
import pixzel.graphics.LineTo
import pixzel.graphics.MoveTo
import pixzel.graphics.PathGeometry
import pixzel.graphics.QuadTo
import pixzel.graphics.RecordingBackend
import pixzel.graphics.RoundedCorners
import pixzel.graphics.VectorPath
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.layout.unit.dp
import pixzel.platform.TestHost
import pixzel.surface.Placement
import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.surface.Transform
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import kotlin.time.Duration

class PathGeometryTest {

    private val bounds = Rect(0f, 0f, 100f, 100f)
    private fun square(x: Float, y: Float, size: Float) = listOf(
        MoveTo(Vec2(x, y)), LineTo(Vec2(x + size, y)),
        LineTo(Vec2(x + size, y + size)), LineTo(Vec2(x, y + size)), ClosePath
    )

    @Test
    fun `fill rules include boundaries holes and intersections`() {
        val segments = square(0f, 0f, 100f) + square(30f, 30f, 40f)
        val evenOdd = VectorPath(segments, FillRule.EVEN_ODD)
        val nonZero = VectorPath(segments, FillRule.NON_ZERO)
        check(evenOdd.contains(Vec2(10f, 50f), bounds))
        check(!evenOdd.contains(Vec2(50f, 50f), bounds))
        check(nonZero.contains(Vec2(50f, 50f), bounds))
        check(evenOdd.contains(Vec2(30f, 50f), bounds))
        val opposite = square(0f, 0f, 100f) + listOf(
            MoveTo(Vec2(30f, 30f)), LineTo(Vec2(30f, 70f)),
            LineTo(Vec2(70f, 70f)), LineTo(Vec2(70f, 30f)), ClosePath
        )
        check(!VectorPath(opposite).contains(Vec2(50f, 50f), bounds))
        val crossing = VectorPath(listOf(MoveTo(Vec2(0f, 0f)), LineTo(Vec2(100f, 100f)), LineTo(Vec2(0f, 100f)), LineTo(Vec2(100f, 0f)), ClosePath))
        check(crossing.contains(Vec2(50f, 15f), bounds))
        check(crossing.contains(Vec2(50f, 85f), bounds))
        check(!crossing.contains(Vec2(10f, 50f), bounds))
    }

    @Test
    fun `quadratic cubic and open fill contours match shared geometry`() {
        val quad = VectorPath(
            listOf(
                MoveTo(Vec2(0f, 0f)), QuadTo(Vec2(50f, -50f), Vec2(100f, 0f)),
                LineTo(Vec2(100f, 100f)), LineTo(Vec2(0f, 100f))
            )
        )
        check(quad.contains(Vec2(50f, -20f), bounds))
        check(!quad.contains(Vec2(50f, -30f), bounds))
        val cubic = VectorPath(
            listOf(
                MoveTo(Vec2(0f, 0f)), CubicTo(Vec2(0f, -50f), Vec2(100f, -50f), Vec2(100f, 0f)),
                LineTo(Vec2(100f, 100f)), LineTo(Vec2(0f, 100f)), ClosePath
            )
        )
        check(cubic.contains(Vec2(50f, -30f), bounds))
        check(!cubic.contains(Vec2(50f, -40f), bounds))
        val contours = PathGeometry.flatten(cubic)
        check(contours.first().first() == contours.first().last())
        check(PathGeometry.contains(contours, Vec2(10f, 10f), FillRule.NON_ZERO) == cubic.contains(Vec2(10f, 10f), bounds))
    }

    @Test
    fun `rotated path clipping excludes holes from pointer activation`() {
        val host = TestHost.of(640, 360)
        ui(host, RecordingBackend.of(), FixedFontTextService.of()).use { ui ->
            val parent = ui.components.surface("path-clip")
            parent.layout.width = 100.dp
            parent.layout.height = 100.dp
            parent.placement = Placement(offset = Vec2(180f, 60f))
            parent.paintTransform = Transform.rotation(.35f)
            parent.clipPolicy = ClipPolicy.SHAPE
            parent.clipShape = VectorPath(square(0f, 0f, 100f) + square(30f, 30f, 40f), FillRule.EVEN_ODD)
            val button = ui.components.button("action")
            button.layout.width = 100.dp
            button.layout.height = 100.dp
            var clicks = 0
            button.onClick { clicks++ }
            parent.add(button)
            ui.root.add(parent)
            ui.frame(Duration.ZERO)
            fun click(point: Vec2) {
                val root = parent.surface.toRoot(point)
                host.queue(PointerEvent(PointerAction.DOWN, root))
                host.queue(PointerEvent(PointerAction.UP, root))
                ui.frame(Duration.ZERO)
            }
            click(Vec2(50f, 50f))
            check(clicks == 0)
            click(Vec2(10f, 50f))
            check(clicks == 1)
        }
    }

    @Test
    fun `independent radii retain their distinct hit boundaries and fit adjacent edges`() {
        val shape = RoundedCorners(CornerRadii(0f, 30f, 8f, 16f))
        check(shape.contains(Vec2(1f, 1f), bounds))
        check(!shape.contains(Vec2(99f, 1f), bounds))
        check(!shape.contains(Vec2(1f, 99f), bounds))
        check(shape.contains(Vec2(50f, 50f), bounds))
        val fitted = CornerRadii(100f, 100f, 0f, 0f).fit(Size(100f, 100f))
        check(fitted.topLeft == 50f && fitted.topRight == 50f)
    }
}
