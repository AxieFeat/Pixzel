package pixzel.graphics

import pixzel.text.FontFace
import kotlin.math.ceil

/** Raster pixels and bearings are in pixels at the requested pixels-per-dp scale. */
class SoftwareGlyph(val width: Int, val height: Int, val left: Int, val top: Int, alpha: ByteArray) {

    val alpha: ByteArray = alpha.copyOf()

    init {
        require(width >= 0 && height >= 0 && width.toLong() * height == alpha.size.toLong())
    }
}

/** Explicit raster boundary. A demo can adapt a real font service without adding Arc here. */
interface SoftwareGlyphProvider {

    fun supports(providerId: String): Boolean

    /** Legacy shared glyph-ID namespace; providers with multiple namespaces override the overload below. */
    fun rasterize(face: FontFace, glyphId: Int, fontSize: Float, pixelsPerDp: Float): SoftwareGlyph
    fun rasterize(providerId: String, face: FontFace, glyphId: Int, fontSize: Float, pixelsPerDp: Float): SoftwareGlyph =
        rasterize(face, glyphId, fontSize, pixelsPerDp)
}

/**
 * Pinned 5x7 preview fixture compatible with FixedFontTextService only. ASCII letters/digits are
 * legible; lowercase uses uppercase forms and unsupported glyphs use a visible replacement box.
 * This is a deterministic bitmap fixture, not a production font or a Cyrillic font provider.
 */
class Bitmap5x7GlyphProvider : SoftwareGlyphProvider {

    override fun supports(providerId: String) = providerId == "pixzel.fixed-test"

    override fun rasterize(face: FontFace, glyphId: Int, fontSize: Float, pixelsPerDp: Float): SoftwareGlyph {
        require(fontSize.isFinite() && fontSize > 0f && pixelsPerDp.isFinite() && pixelsPerDp > 0f)
        if (glyphId == 32 || glyphId == 9) return SoftwareGlyph(0, 0, 0, 0, ByteArray(0))
        val character = if (glyphId <= Char.MAX_VALUE.code) glyphId.toChar().uppercaseChar() else '\uFFFD'
        val rows = GLYPHS[character] ?: REPLACEMENT
        val scale = fontSize / 16f * pixelsPerDp
        val width = ceil(5 * scale).toInt().coerceAtLeast(1)
        val height = ceil(7 * scale).toInt().coerceAtLeast(1)
        require(width.toLong() * height <= 16_777_216) { "Fixture glyph exceeds its raster pixel budget." }
        val alpha = ByteArray(width * height) { index ->
            val x = (index % width * 5 / width).coerceAtMost(4)
            val y = (index / width * 7 / height).coerceAtMost(6)
            if ((rows[y] and (1 shl (4 - x))) != 0) 255.toByte() else 0
        }
        return SoftwareGlyph(width, height, 0, height, alpha)
    }

    private companion object {

        val REPLACEMENT = intArrayOf(31, 17, 21, 21, 21, 17, 31)
        val GLYPHS = mapOf(
            'A' to intArrayOf(14, 17, 17, 31, 17, 17, 17), 'B' to intArrayOf(30, 17, 17, 30, 17, 17, 30),
            'C' to intArrayOf(14, 17, 16, 16, 16, 17, 14), 'D' to intArrayOf(30, 17, 17, 17, 17, 17, 30),
            'E' to intArrayOf(31, 16, 16, 30, 16, 16, 31), 'F' to intArrayOf(31, 16, 16, 30, 16, 16, 16),
            'G' to intArrayOf(14, 17, 16, 23, 17, 17, 15), 'H' to intArrayOf(17, 17, 17, 31, 17, 17, 17),
            'I' to intArrayOf(14, 4, 4, 4, 4, 4, 14), 'J' to intArrayOf(7, 2, 2, 2, 18, 18, 12),
            'K' to intArrayOf(17, 18, 20, 24, 20, 18, 17), 'L' to intArrayOf(16, 16, 16, 16, 16, 16, 31),
            'M' to intArrayOf(17, 27, 21, 21, 17, 17, 17), 'N' to intArrayOf(17, 25, 21, 19, 17, 17, 17),
            'O' to intArrayOf(14, 17, 17, 17, 17, 17, 14), 'P' to intArrayOf(30, 17, 17, 30, 16, 16, 16),
            'Q' to intArrayOf(14, 17, 17, 17, 21, 18, 13), 'R' to intArrayOf(30, 17, 17, 30, 20, 18, 17),
            'S' to intArrayOf(15, 16, 16, 14, 1, 1, 30), 'T' to intArrayOf(31, 4, 4, 4, 4, 4, 4),
            'U' to intArrayOf(17, 17, 17, 17, 17, 17, 14), 'V' to intArrayOf(17, 17, 17, 17, 17, 10, 4),
            'W' to intArrayOf(17, 17, 17, 21, 21, 21, 10), 'X' to intArrayOf(17, 17, 10, 4, 10, 17, 17),
            'Y' to intArrayOf(17, 17, 10, 4, 4, 4, 4), 'Z' to intArrayOf(31, 1, 2, 4, 8, 16, 31),
            '0' to intArrayOf(14, 17, 19, 21, 25, 17, 14), '1' to intArrayOf(4, 12, 4, 4, 4, 4, 14),
            '2' to intArrayOf(14, 17, 1, 2, 4, 8, 31), '3' to intArrayOf(30, 1, 1, 14, 1, 1, 30),
            '4' to intArrayOf(2, 6, 10, 18, 31, 2, 2), '5' to intArrayOf(31, 16, 16, 30, 1, 1, 30),
            '6' to intArrayOf(14, 16, 16, 30, 17, 17, 14), '7' to intArrayOf(31, 1, 2, 4, 8, 8, 8),
            '8' to intArrayOf(14, 17, 17, 14, 17, 17, 14), '9' to intArrayOf(14, 17, 17, 15, 1, 1, 14),
            '.' to intArrayOf(0, 0, 0, 0, 0, 6, 6), ':' to intArrayOf(0, 6, 6, 0, 6, 6, 0),
            '-' to intArrayOf(0, 0, 0, 31, 0, 0, 0), '+' to intArrayOf(0, 4, 4, 31, 4, 4, 0),
            '/' to intArrayOf(1, 1, 2, 4, 8, 16, 16), '!' to intArrayOf(4, 4, 4, 4, 4, 0, 4),
            '?' to intArrayOf(14, 17, 1, 2, 4, 0, 4), '\u2026' to intArrayOf(0, 0, 0, 0, 0, 0, 21)
        )
    }
}
