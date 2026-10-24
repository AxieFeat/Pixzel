package pixzel.animation

import pixzel.graphics.Color
import pixzel.surface.Transform
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Stateless, shareable strategies. Property ranges and interpolation spaces are explicit.
 */
object Interpolators {

    val FLOAT: Interpolator<Float> = Interpolator { from, to, fraction -> from + (to - from) * fraction }
    val DOUBLE: Interpolator<Double> = Interpolator { from, to, fraction -> from + (to - from) * fraction }

    /**
     * Unit opacity. Overshooting easing is clamped to the legal 0..1 property range.
     */
    val OPACITY: Interpolator<Float> = Interpolator { from, to, fraction ->
        require(from.isFinite() && to.isFinite() && from in 0f..1f && to in 0f..1f)
        require(fraction.isFinite())
        (from.toDouble() + (to.toDouble() - from) * fraction).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Blends premultiplied stored sRGB channels, then returns straight-alpha RRGGBBAA.
     * This avoids a transparent endpoint's hidden RGB tinting the fade. It is not linear-light blending.
     * Color cannot overshoot its byte range; fractions outside 0..1 select the nearest endpoint.
     */
    val COLOR: Interpolator<Color> = Interpolator { from, to, fraction ->
        require(fraction.isFinite())
        when {
            fraction <= 0f -> from
            fraction >= 1f -> to
            else -> {
                val f = fraction.toDouble()
                val alpha = mix(from.alpha.toDouble(), to.alpha.toDouble(), f)
                fun channel(a: Int, b: Int): Long = if (alpha == 0.0) 0 else
                    (mix(a * from.alpha.toDouble(), b * to.alpha.toDouble(), f) / alpha).roundToInt().coerceIn(0, 255).toLong()
                Color(
                    (channel(from.red, to.red) shl 24) or (channel(from.green, to.green) shl 16) or
                            (channel(from.blue, to.blue) shl 8) or alpha.roundToInt().coerceIn(0, 255).toLong()
                )
            }
        }
    }

    /**
     * Interpolates affine matrix coefficients directly. Rotation endpoints can cross a singular matrix;
     * use [TRANSFORM] when the intended motion is rotation/scale/translation rather than matrix morphing.
     */
    val AFFINE_TRANSFORM: Interpolator<Transform> = Interpolator { from, to, fraction ->
        require(fraction.isFinite())
        when (fraction) {
            0f -> from
            1f -> to
            else -> Transform(
                mix(from.a, to.a, fraction), mix(from.b, to.b, fraction),
                mix(from.c, to.c, fraction), mix(from.d, to.d, fraction),
                mix(from.tx, to.tx, fraction), mix(from.ty, to.ty, fraction)
            )
        }
    }

    /**
     * Decomposes each affine transform into translation, rotation, X scale, signed Y scale and shear.
     * Rotation takes the shortest arc; other components interpolate linearly. Reflections can cross
     * zero scale. A singular endpoint is supported, but hit testing remains unavailable at a singular frame.
     */
    val TRANSFORM: Interpolator<Transform> = Interpolator { from, to, fraction ->
        require(fraction.isFinite())
        when (fraction) {
            0f -> from
            1f -> to
            else -> {
                val first = decompose(from)
                val last = decompose(to)
                val f = fraction.toDouble()
                val delta = atan2(sin(last.rotation - first.rotation), cos(last.rotation - first.rotation))
                val angle = first.rotation + delta * f
                val x = mix(first.scaleX, last.scaleX, f)
                val y = mix(first.scaleY, last.scaleY, f)
                val shear = mix(first.shear, last.shear, f)
                val c = cos(angle)
                val s = sin(angle)
                Transform(
                    (c * x).toFloat(), (s * x).toFloat(), (c * shear - s * y).toFloat(),
                    (s * shear + c * y).toFloat(), mix(from.tx, to.tx, fraction), mix(from.ty, to.ty, fraction)
                )
            }
        }
    }

    private data class Parts(val rotation: Double, val scaleX: Double, val scaleY: Double, val shear: Double)

    private fun decompose(t: Transform): Parts {
        val x = hypot(t.a.toDouble(), t.b.toDouble())
        return if (x > 0.0) Parts(
            atan2(t.b.toDouble(), t.a.toDouble()), x,
            (t.a.toDouble() * t.d - t.b.toDouble() * t.c) / x,
            (t.a.toDouble() * t.c + t.b.toDouble() * t.d) / x
        )
        else Parts(atan2(-t.c.toDouble(), t.d.toDouble()), 0.0, hypot(t.c.toDouble(), t.d.toDouble()), 0.0)
    }

    private fun mix(from: Float, to: Float, fraction: Float) = mix(from.toDouble(), to.toDouble(), fraction.toDouble()).toFloat()
    private fun mix(from: Double, to: Double, fraction: Double) = from + (to - from) * fraction
}
