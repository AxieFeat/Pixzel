package pixzel.surface

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class Vec2(val x: Float, val y: Float) {
    init {
        require(x.isFinite() && y.isFinite()) { "Coordinates must be finite" }
    }

    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)

    companion object {

        val ZERO = Vec2(0f, 0f)
    }
}

data class Size(val width: Float, val height: Float) {
    init {
        require(width.isFinite() && height.isFinite() && width >= 0 && height >= 0) { "Size must be nonnegative and finite: $width x $height" }
    }

    companion object {

        val ZERO = Size(0f, 0f)
    }
}

data class Rect(val x: Float, val y: Float, val width: Float, val height: Float) {
    init {
        require(x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite() && width >= 0 && height >= 0)
    }

    val right get() = x + width
    val bottom get() = y + height
    val size get() = Size(width, height)
    fun contains(p: Vec2) = width > 0 && height > 0 && p.x >= x && p.y >= y && p.x < right && p.y < bottom
    fun intersect(other: Rect): Rect {
        val left = max(x, other.x)
        val top = max(y, other.y)
        return Rect(left, top, max(0f, min(right, other.right) - left), max(0f, min(bottom, other.bottom) - top))
    }

    companion object {

        val ZERO = Rect(0f, 0f, 0f, 0f)
    }
}

/** Column-vector affine transform, multiplication applies the right operand first. */
data class Transform(val a: Float = 1f, val b: Float = 0f, val c: Float = 0f, val d: Float = 1f, val tx: Float = 0f, val ty: Float = 0f) {

    init {
        require(a.isFinite() && b.isFinite() && c.isFinite() && d.isFinite() && tx.isFinite() && ty.isFinite())
    }

    fun map(p: Vec2) = Vec2(a * p.x + c * p.y + tx, b * p.x + d * p.y + ty)
    operator fun times(r: Transform) =
        Transform(a * r.a + c * r.b, b * r.a + d * r.b, a * r.c + c * r.d, b * r.c + d * r.d, a * r.tx + c * r.ty + tx, b * r.tx + d * r.ty + ty)

    fun inverse(): Transform? {
        val det = a * d - b * c
        if (abs(det) < 1e-8f) return null
        return Transform(d / det, -b / det, -c / det, a / det, (c * ty - d * tx) / det, (b * tx - a * ty) / det)
    }

    fun mapRect(r: Rect): Rect {
        val p = listOf(map(Vec2(r.x, r.y)), map(Vec2(r.right, r.y)), map(Vec2(r.x, r.bottom)), map(Vec2(r.right, r.bottom)))
        val x = p.minOf { it.x }
        val y = p.minOf { it.y }
        return Rect(x, y, p.maxOf { it.x } - x, p.maxOf { it.y } - y)
    }

    companion object {

        val IDENTITY = Transform()
        fun translation(x: Float, y: Float) = Transform(tx = x, ty = y)
        fun scale(x: Float, y: Float = x) = Transform(a = x, d = y)
        fun rotation(radians: Float) = Transform(cos(radians), sin(radians), -sin(radians), cos(radians))
    }
}
