package pixzel.arc.graphics

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pixzel.graphics.ClosePath
import pixzel.graphics.FillRule
import pixzel.graphics.LineTo
import pixzel.graphics.MoveTo
import pixzel.graphics.VectorPath
import pixzel.surface.Rect
import pixzel.surface.Vec2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ShapeTessellatorTest {

    @Test
    fun `dense crossing paths fail within tessellation budget`() {
        val count = 181
        val points = (0 until count).map { index ->
            val angle = 2 * PI * ((index * 89) % count) / count
            Vec2(100f + 95 * cos(angle).toFloat(), 100f + 95 * sin(angle).toFloat())
        }
        val segments = listOf(MoveTo(points.first())) + points.drop(1).map(::LineTo) + ClosePath
        val failure = assertThrows(IllegalArgumentException::class.java) {
            ShapeTessellator.triangles(VectorPath(segments, FillRule.EVEN_ODD), Rect(0f, 0f, 200f, 200f))
        }
        assertTrue(failure.message!!.contains("budget"), failure.message)
    }

    @Test
    fun `ordinary intersecting path remains supported`() {
        val path = VectorPath(
            listOf(MoveTo(Vec2.ZERO), LineTo(Vec2(100f, 100f)), LineTo(Vec2(0f, 100f)), LineTo(Vec2(100f, 0f)), ClosePath),
            FillRule.EVEN_ODD
        )
        val triangles = ShapeTessellator.triangles(path, Rect(0f, 0f, 100f, 100f))
        assertTrue(triangles.isNotEmpty() && triangles.size <= ShapeTessellator.MAX_VERTICES)
    }
}
