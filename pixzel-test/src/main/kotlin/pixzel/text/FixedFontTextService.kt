package pixzel.text

import pixzel.surface.Rect
import pixzel.surface.Size
import kotlin.math.max

/**
 * Synthetic monospace fixture: one glyph per grapheme, not a TTF/OTF font or production shaper.
 * It has no complex-script shaping/bidi or real kerning. Metrics scale from a 16sp reference.
 */
class FixedFontTextService private constructor(
    private val glyphWidth: Float,
    private val referenceLineHeight: Float
) : TextService {

    override val capabilities = TextCapabilities("pixzel.fixed-test", scripts = setOf("Latn", "Cyrl"))
    var layoutCount: Long = 0
        private set

    override fun canDisplay(text: String, style: TextStyle) = true

    override fun layout(request: TextRequest): TextLayout {
        require(request.spans.isEmpty()) { "FixedFontTextService is a plain-text fixture; styled spans require a capable provider" }
        layoutCount++
        val scale = request.style.size.value / 16f * request.textScale
        val advance = glyphWidth * scale
        val lineHeight = request.style.lineHeight?.times(request.textScale) ?: (referenceLineHeight * scale)
        val metrics = FontMetrics(lineHeight * 0.75f, lineHeight * 0.25f, 0f)
        val ranges = GRAPHEME.findAll(request.text).map { TextRange(it.range.first, it.range.last + 1) }.toList()

        data class LogicalLine(val start: Int, val end: Int, val ranges: List<TextRange>)

        val logical = mutableListOf<LogicalLine>()
        var current = mutableListOf<TextRange>()
        var lineStart = 0
        var cursor = 0f
        fun widthOf(range: TextRange, x: Float): Float {
            if (request.text.substring(range.start, range.end) != "\t") return max(0f, advance + request.style.letterSpacing)
            val tab = advance * request.tabSize
            return if (tab == 0f) 0f else tab - x % tab
        }

        fun finish(end: Int) {
            logical += LogicalLine(lineStart, end, current.toList())
            current = mutableListOf()
            cursor = 0f
        }
        for (range in ranges) {
            val content = request.text.substring(range.start, range.end)
            if (content == "\n" || content == "\r" || content == "\r\n") {
                finish(range.start)
                lineStart = range.end
            } else {
                var clusterWidth = widthOf(range, cursor)
                if (request.wrap && request.width.isFinite() && current.isNotEmpty() && cursor + clusterWidth > request.width) {
                    finish(range.start)
                    lineStart = range.start
                    clusterWidth = widthOf(range, 0f)
                }
                current += range
                cursor += clusterWidth
            }
        }
        finish(request.text.length)
        val visibleLines = logical.take(request.maxLines)
        val lines = mutableListOf<TextLine>()
        val clusters = mutableListOf<TextCluster>()
        val glyphs = mutableListOf<PositionedGlyph>()
        val direction = if (request.direction == TextDirection.RTL) TextDirection.RTL else TextDirection.LTR
        var maxWidth = 0f
        for ((lineIndex, line) in visibleLines.withIndex()) {
            var end = line.end
            val visibleRanges = if (request.trimTrailingWhitespace) line.ranges.dropLastWhile {
                request.text.substring(it.start, it.end).all(Char::isWhitespace)
            }.also { end = it.lastOrNull()?.end ?: line.start } else line.ranges
            var x = 0f
            val widths = visibleRanges.map { range -> widthOf(range, x).also { x += it } }
            val lineWidth = x
            maxWidth = max(maxWidth, lineWidth)
            val top = lineIndex * lineHeight
            val baseline = top + metrics.ascent
            lines += TextLine(line.start, end, Rect(0f, top, lineWidth, lineHeight), baseline, metrics)
            x = if (direction == TextDirection.RTL) lineWidth else 0f
            for ((index, range) in visibleRanges.withIndex()) {
                val clusterWidth = widths[index]
                val left = if (direction == TextDirection.RTL) x - clusterWidth else x
                val bounds = Rect(left, top, clusterWidth, lineHeight)
                clusters += TextCluster(
                    range.start, range.end, lineIndex, bounds,
                    if (direction == TextDirection.RTL) bounds.right else bounds.x,
                    if (direction == TextDirection.RTL) bounds.x else bounds.right
                )
                glyphs += PositionedGlyph(
                    request.text.codePointAt(range.start), left, baseline, clusterWidth,
                    clusterStart = range.start, clusterEnd = range.end
                )
                x += if (direction == TextDirection.RTL) -clusterWidth else clusterWidth
            }
        }
        return TextLayout(
            request.text, Size(maxWidth, visibleLines.size * lineHeight), lines,
            listOf(
                GlyphRun(
                    request.style.family.faces.first(), glyphs, direction,
                    (request.style.size.value * request.textScale).coerceAtLeast(0.001f)
                )
            ), clusters,
            direction, capabilities.providerId, logical.size > request.maxLines
        )
    }

    companion object {

        private val GRAPHEME = Regex("\\X")
        fun of(glyphWidth: Float = 8f, lineHeight: Float = 16f): FixedFontTextService {
            require(glyphWidth.isFinite() && glyphWidth >= 0)
            require(lineHeight.isFinite() && lineHeight >= 0)
            return FixedFontTextService(glyphWidth, lineHeight)
        }
    }
}
