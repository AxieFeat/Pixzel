package pixzel.arc.graphics

import pixzel.graphics.CornerRadii
import pixzel.graphics.Ellipse
import pixzel.graphics.FillRule
import pixzel.graphics.PathGeometry
import pixzel.graphics.Rectangle
import pixzel.graphics.RoundedCorners
import pixzel.graphics.RoundedRectangle
import pixzel.graphics.Shape
import pixzel.graphics.StrokeCap
import pixzel.graphics.StrokeJoin
import pixzel.graphics.VectorPath
import pixzel.surface.Rect
import pixzel.surface.Vec2
import java.util.TreeSet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** CPU flattening and scan conversion produce non-overlapping triangles, including holes and intersections. */
internal object ShapeTessellator {

    const val MAX_VERTICES = 262144
    private const val MAX_LEVELS = 4096
    private const val MAX_SCAN_WORK = 4_194_304L
    fun triangles(
        shape: Shape, bounds: Rect, stroke: Float? = null, tolerance: Float = .2f,
        join: StrokeJoin = StrokeJoin.ROUND, cap: StrokeCap = StrokeCap.ROUND, miterLimit: Float = 4f
    ): List<Vec2> {
        val contours = contours(shape, bounds, tolerance)
        require(contours.sumOf { it.size.toLong() } <= 8192) { "A shape exceeds its 8192 flattened-point budget" }
        if (stroke == null) return triangulate(contours, (shape as? VectorPath)?.fillRule ?: FillRule.NON_ZERO)
        require(stroke.isFinite() && stroke >= 0) { "Stroke width must be finite and nonnegative" }
        if (stroke == 0f) return emptyList()
        val radius = stroke / 2
        val outlines = mutableListOf<List<Vec2>>()
        var outlinePoints = 0
        fun outline(points: List<Vec2>) {
            outlinePoints = Math.addExact(outlinePoints, points.size)
            require(outlinePoints <= 8192) { "A stroke exceeds its 8192 outline-point budget" }
            outlines += points
        }
        contours.forEach { contour ->
            if (contour.size < 2) return@forEach
            val closed = contour.first() == contour.last()
            val points = if (closed) contour.dropLast(1) else contour
            val pairs = if (closed) points.indices.map { points[it] to points[(it + 1) % points.size] } else points.zipWithNext()
            pairs.forEachIndexed { index, (start, end) ->
                var a = start
                var b = end
                val dx = b.x - a.x
                val dy = b.y - a.y
                val length = hypot(dx, dy)
                if (length > 1e-6f) {
                    if (!closed && cap == StrokeCap.SQUARE) {
                        if (index == 0) a = Vec2(a.x - dx / length * radius, a.y - dy / length * radius)
                        if (index == pairs.lastIndex) b = Vec2(b.x + dx / length * radius, b.y + dy / length * radius)
                    }
                    val normal = Vec2(-dy / length * radius, dx / length * radius)
                    outline(listOf(a - normal, b - normal, b + normal, a + normal, a - normal))
                }
            }
            val indices = if (closed) points.indices else 1 until points.lastIndex
            indices.forEach { index ->
                val p = points[index]
                val previous = points[(index - 1 + points.size) % points.size]
                val next = points[(index + 1) % points.size]
                val ax = p.x - previous.x
                val ay = p.y - previous.y
                val bx = next.x - p.x
                val by = next.y - p.y
                val al = hypot(ax, ay)
                val bl = hypot(bx, by)
                if (al <= 1e-6f || bl <= 1e-6f) return@forEach
                val cross = ax / al * by / bl - ay / al * bx / bl
                if (abs(cross) < 1e-6f) return@forEach
                val side = if (cross > 0) -1f else 1f
                val first = Vec2(p.x - ay / al * radius * side, p.y + ax / al * radius * side)
                val last = Vec2(p.x - by / bl * radius * side, p.y + bx / bl * radius * side)
                val wedge = mutableListOf(p, first)
                when (join) {
                    StrokeJoin.BEVEL -> Unit
                    StrokeJoin.MITER -> {
                        val denominator = ax * by - ay * bx
                        val t = ((last.x - first.x) * by - (last.y - first.y) * bx) / denominator
                        val intersection = Vec2(first.x + ax * t, first.y + ay * t)
                        if (hypot(intersection.x - p.x, intersection.y - p.y) <= miterLimit * radius) wedge += intersection
                    }

                    StrokeJoin.ROUND -> {
                        val start = atan2(first.y - p.y, first.x - p.x)
                        var delta = atan2(last.y - p.y, last.x - p.x) - start
                        if (cross > 0 && delta < 0) delta += (2 * PI).toFloat()
                        if (cross < 0 && delta > 0) delta -= (2 * PI).toFloat()
                        val steps = ceil(abs(delta) * sqrt(radius / max(.001f, tolerance))).toInt().coerceIn(1, 128)
                        for (step in 1 until steps) {
                            val angle = start + delta * step / steps
                            wedge += Vec2(p.x + cos(angle) * radius, p.y + sin(angle) * radius)
                        }
                    }
                }
                wedge += last
                wedge += p
                outline(wedge)
            }
            if (!closed && cap == StrokeCap.ROUND) listOf(points.first(), points.last()).forEach { center ->
                outline(ellipse(Rect(center.x - radius, center.y - radius, stroke, stroke), tolerance))
            }
        }
        // All stroke pieces share positive winding: union coverage is drawn once, even at joins.
        return triangulate(outlines.map { if (area(it) < 0) it.asReversed() else it }, FillRule.NON_ZERO)
    }

    private fun contours(shape: Shape, r: Rect, tolerance: Float): List<List<Vec2>> = when (shape) {
        Rectangle -> listOf(listOf(Vec2(r.x, r.y), Vec2(r.right, r.y), Vec2(r.right, r.bottom), Vec2(r.x, r.bottom), Vec2(r.x, r.y)))
        Ellipse -> listOf(ellipse(r, tolerance))
        is RoundedRectangle -> rounded(CornerRadii.all(shape.radius), r, tolerance)
        is RoundedCorners -> rounded(shape.radii, r, tolerance)
        is VectorPath -> PathGeometry.flatten(shape, min(tolerance, PathGeometry.DEFAULT_TOLERANCE))
        else -> throw UnsupportedOperationException("Arc renderer needs geometry for shape ${shape::class.qualifiedName}")
    }

    private fun rounded(radii: CornerRadii, r: Rect, tolerance: Float): List<List<Vec2>> {
        val fitted = radii.fit(r.size)
        if (listOf(fitted.topLeft, fitted.topRight, fitted.bottomRight, fitted.bottomLeft).all { it == 0f }) return contours(Rectangle, r, tolerance)
        val points = mutableListOf<Vec2>()

        data class Corner(val x: Float, val y: Float, val start: Double, val radius: Float)
        listOf(
            Corner(r.right - fitted.topRight, r.y + fitted.topRight, -PI / 2, fitted.topRight),
            Corner(r.right - fitted.bottomRight, r.bottom - fitted.bottomRight, 0.0, fitted.bottomRight),
            Corner(r.x + fitted.bottomLeft, r.bottom - fitted.bottomLeft, PI / 2, fitted.bottomLeft),
            Corner(r.x + fitted.topLeft, r.y + fitted.topLeft, PI, fitted.topLeft)
        ).forEach { (x, y, start, radius) ->
            val count = max(1, ceil(PI * sqrt(radius / max(.001f, tolerance)) / 4).toInt()).coerceAtMost(128)
            for (i in 0..count) {
                val angle = start + PI / 2 * i / count
                points += Vec2(x + cos(angle).toFloat() * radius, y + sin(angle).toFloat() * radius)
            }
        }
        return listOf(points + points.first())
    }

    private fun ellipse(r: Rect, tolerance: Float): List<Vec2> {
        if (r.width <= 0 || r.height <= 0) return emptyList()
        val count = ceil(PI * sqrt(max(r.width, r.height) / max(.001f, tolerance))).toInt().coerceIn(16, 512)
        return (0..count).map { index ->
            val angle = 2 * PI * index / count
            Vec2(r.x + r.width / 2 + cos(angle).toFloat() * r.width / 2, r.y + r.height / 2 + sin(angle).toFloat() * r.height / 2)
        }
    }

    private data class Edge(val a: Vec2, val b: Vec2) {

        val minimum get() = min(a.y, b.y)
        val maximum get() = max(a.y, b.y)
        val winding get() = if (b.y > a.y) 1 else -1
        fun x(y: Float) = a.x + (y - a.y) * (b.x - a.x) / (b.y - a.y)
    }

    private fun triangulate(contours: List<List<Vec2>>, rule: FillRule): List<Vec2> {
        val edges = contours.flatMap { points ->
            val closed = if (points.size > 2 && points.first() != points.last()) points + points.first() else points
            closed.zipWithNext().filter { abs(it.first.y - it.second.y) > 1e-7f }.map { Edge(it.first, it.second) }
        }
        if (edges.isEmpty()) return emptyList()
        require(edges.size <= 4096) { "A shape exceeds the 4096 flattened-edge budget" }
        val levels = TreeSet<Float>()
        fun level(y: Float) {
            levels += y
            require(levels.size <= MAX_LEVELS) { "A shape exceeds its $MAX_LEVELS tessellation-level budget" }
            require(levels.size.toLong() * edges.size <= MAX_SCAN_WORK) { "A shape exceeds its tessellation scan-work budget" }
        }
        edges.forEach {
            level(it.a.y)
            level(it.b.y)
        }
        // Intersection levels ensure that edge order remains stable inside every strip.
        for (i in edges.indices) for (j in i + 1 until edges.size) {
            val e = edges[i]
            val f = edges[j]
            val low = max(e.minimum, f.minimum)
            val high = min(e.maximum, f.maximum)
            if (high - low <= 1e-6f) continue
            val eSlope = (e.b.x - e.a.x) / (e.b.y - e.a.y)
            val fSlope = (f.b.x - f.a.x) / (f.b.y - f.a.y)
            if (abs(eSlope - fSlope) < 1e-7f) continue
            val y = (f.a.x - f.a.y * fSlope - e.a.x + e.a.y * eSlope) / (eSlope - fSlope)
            if (y > low + 1e-6f && y < high - 1e-6f) level(y)
        }
        val output = mutableListOf<Vec2>()
        fun triangle(a: Vec2, b: Vec2, c: Vec2) {
            require(output.size + 3 <= MAX_VERTICES) { "A shape exceeds its $MAX_VERTICES tessellation-vertex budget" }
            output.add(a)
            output.add(b)
            output.add(c)
        }
        levels.zipWithNext().forEach { (top, bottom) ->
            if (bottom - top < 1e-6f) return@forEach
            val middle = (top + bottom) / 2
            val active = edges.filter { middle > it.minimum && middle < it.maximum }.sortedBy { it.x(middle) }
            var winding = 0
            var left: Edge? = null
            active.forEach { edge ->
                val before = if (rule == FillRule.EVEN_ODD) winding % 2 != 0 else winding != 0
                winding += if (rule == FillRule.EVEN_ODD) 1 else edge.winding
                val after = if (rule == FillRule.EVEN_ODD) winding % 2 != 0 else winding != 0
                if (!before && after) left = edge
                if (before && !after) left?.let { first ->
                    val a = Vec2(first.x(top), top)
                    val b = Vec2(edge.x(top), top)
                    val c = Vec2(edge.x(bottom), bottom)
                    val d = Vec2(first.x(bottom), bottom)
                    if (abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)) > 1e-7f) triangle(a, b, c)
                    if (abs((c.x - a.x) * (d.y - a.y) - (c.y - a.y) * (d.x - a.x)) > 1e-7f) triangle(a, c, d)
                    left = null
                }
            }
        }
        return output
    }

    private fun area(points: List<Vec2>) = points.zipWithNext().sumOf { (a, b) -> (a.x * b.y - a.y * b.x).toDouble() }.toFloat() / 2
}
