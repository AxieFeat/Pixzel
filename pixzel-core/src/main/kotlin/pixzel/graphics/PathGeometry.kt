package pixzel.graphics

import pixzel.surface.Vec2
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Shared, Arc-independent curve flattening and fill geometry for paint and positional input. */
object PathGeometry {

    const val DEFAULT_TOLERANCE = .01f

    /** Open subpaths stay open here; fill operations close them implicitly, while strokes preserve their caps. */
    fun flatten(path: VectorPath, tolerance: Float = DEFAULT_TOLERANCE): List<List<Vec2>> {
        require(tolerance.isFinite() && tolerance > 0) { "Path tolerance must be positive and finite" }
        val result = mutableListOf<List<Vec2>>()
        var current = mutableListOf<Vec2>()
        var total = 0

        fun append(point: Vec2) {
            require(current.size < 65536) { "A path contour exceeds 65536 flattened points" }
            if (current.lastOrNull() != point) {
                require(total < 65536) { "A path exceeds 65536 flattened points" }
                total++
                current += point
            }
        }

        fun finish() {
            if (current.size >= 2) result += current.toList()
            current = mutableListOf()
        }

        path.segments.forEach { segment ->
            when (segment) {
                is MoveTo -> {
                    finish()
                    append(segment.point)
                }

                is LineTo -> {
                    require(current.isNotEmpty()) { "A path starts with MoveTo" }
                    append(segment.point)
                }

                is QuadTo -> {
                    require(current.isNotEmpty()) { "A path starts with MoveTo" }
                    quad(current.last(), segment.control, segment.end, tolerance, ::append, 0)
                }

                is CubicTo -> {
                    require(current.isNotEmpty()) { "A path starts with MoveTo" }
                    cubic(current.last(), segment.first, segment.second, segment.end, tolerance, ::append, 0)
                }

                ClosePath -> {
                    if (current.isNotEmpty()) append(current.first())
                    finish()
                }

                else -> throw UnsupportedOperationException("Unsupported path segment ${segment::class.qualifiedName}")
            }
        }

        finish()
        return result
    }

    /** Closed-fill hit test. Boundary points are included; crossings use the same nonzero/even-odd rules as paint. */
    fun contains(contours: List<List<Vec2>>, point: Vec2, fillRule: FillRule): Boolean {
        var winding = 0
        contours.forEach { contour ->
            if (contour.size < 3) return@forEach
            for (index in contour.indices) {
                val a = contour[index]
                val b = contour[(index + 1) % contour.size]
                val dx = b.x.toDouble() - a.x
                val dy = b.y.toDouble() - a.y
                val px = point.x.toDouble() - a.x
                val py = point.y.toDouble() - a.y
                val length = hypot(dx, dy)
                if (length > 0 && abs(dx * py - dy * px) <= 1e-6 * length && px * dx + py * dy >= 0 && px * dx + py * dy <= length * length) return true
                if ((a.y <= point.y && b.y > point.y) || (b.y <= point.y && a.y > point.y)) {
                    val x = a.x + (point.y.toDouble() - a.y) * dx / dy
                    if (x > point.x) winding += if (fillRule == FillRule.EVEN_ODD) 1 else if (b.y > a.y) 1 else -1
                }
            }
        }
        return if (fillRule == FillRule.EVEN_ODD) winding % 2 != 0 else winding != 0
    }

    private fun midpoint(a: Vec2, b: Vec2) = Vec2(a.x / 2 + b.x / 2, a.y / 2 + b.y / 2)
    private fun distance(p: Vec2, a: Vec2, b: Vec2): Double {
        val dx = b.x.toDouble() - a.x
        val dy = b.y.toDouble() - a.y
        val length = hypot(dx, dy)
        return if (length == 0.0) hypot(p.x.toDouble() - a.x, p.y.toDouble() - a.y)
        else abs(dx * (p.y.toDouble() - a.y) - dy * (p.x.toDouble() - a.x)) / length
    }

    private fun quad(a: Vec2, b: Vec2, c: Vec2, t: Float, append: (Vec2) -> Unit, depth: Int) {
        // Subdivide at the midpoint until the control point is close enough to the chord.
        if (depth >= 16 || distance(b, a, c) <= t) {
            append(c)
            return
        }

        val ab = midpoint(a, b)
        val bc = midpoint(b, c)
        val m = midpoint(ab, bc)
        quad(a, ab, m, t, append, depth + 1)
        quad(m, bc, c, t, append, depth + 1)
    }

    private fun cubic(a: Vec2, b: Vec2, c: Vec2, d: Vec2, t: Float, append: (Vec2) -> Unit, depth: Int) {
        if (depth >= 16 || max(distance(b, a, d), distance(c, a, d)) <= t) {
            append(d)
            return
        }
        val ab = midpoint(a, b)
        val bc = midpoint(b, c)
        val cd = midpoint(c, d)
        val x = midpoint(ab, bc)
        val y = midpoint(bc, cd)
        val m = midpoint(x, y)
        cubic(a, ab, x, m, t, append, depth + 1)
        cubic(m, y, cd, d, t, append, depth + 1)
    }
}
