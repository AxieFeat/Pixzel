package pixzel.text

import pixzel.layout.unit.Sp
import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.surface.Vec2
import kotlin.math.abs

/**
 * Stable font resource identity. Changing its data requires a new [version].
 */
data class FontFace(val id: String, val version: Long = 0) {

    init {
        require(id.isNotBlank())
        require(version >= 0)
    }
}

class FontFamily(faces: List<FontFace>) {

    val faces: List<FontFace> = faces.toList()

    init {
        require(this.faces.isNotEmpty()) { "A font family needs at least one face" }
    }

    override fun equals(other: Any?) = other is FontFamily && faces == other.faces
    override fun hashCode() = faces.hashCode()

    companion object {

        val DEFAULT = FontFamily(listOf(FontFace("default")))
    }
}

enum class FontSlant {
    NORMAL,
    ITALIC,
    OBLIQUE
}
enum class TextDirection {
    AUTO,
    LTR,
    RTL
}
enum class TextAlignment {
    START,
    CENTER,
    END,
    LEFT,
    RIGHT
}
enum class VerticalAlignment {
    TOP,
    CENTER,
    BOTTOM
}

/**
 * Sp is resolved to logical dp with TextRequest.textScale, independently of raster DPI.
 */
data class TextStyle(
    val family: FontFamily = FontFamily.DEFAULT,
    val size: Sp = Sp(16f),
    val weight: Int = 400,
    val slant: FontSlant = FontSlant.NORMAL,
    val letterSpacing: Float = 0f,
    val lineHeight: Float? = null
) {

    init {
        require(weight in 1..1000)
        require(letterSpacing.isFinite())
        require(lineHeight == null || lineHeight.isFinite() && lineHeight >= 0)
    }
}

/**
 * A complete style replacement over a nonempty UTF-16 range. Later spans override earlier ones.
 */
data class TextSpan(val range: TextRange, val style: TextStyle) {

    init {
        require(range.end > range.start) { "Text spans must be nonempty" }
    }

    constructor(start: Int, end: Int, style: TextStyle) : this(TextRange(start, end), style)
}

/**
 * Immutable span snapshot, safe to include in layout-cache keys.
 */
class TextSpans private constructor(private val values: List<TextSpan>) : AbstractList<TextSpan>() {

    override val size: Int get() = values.size
    override fun get(index: Int) = values[index]

    companion object {

        val EMPTY = TextSpans(emptyList())
        fun of(vararg spans: TextSpan) = TextSpans(spans.toList())
        fun from(spans: Iterable<TextSpan>) = TextSpans(spans.toList())
    }
}

/**
 * All string indices in this package are UTF-16 code-unit offsets, with an exclusive end.
 */
data class TextRequest(
    val text: String,
    val style: TextStyle = TextStyle(),
    val width: Float = Float.POSITIVE_INFINITY,
    val wrap: Boolean = false,
    val maxLines: Int = Int.MAX_VALUE,
    val direction: TextDirection = TextDirection.AUTO,
    val locale: String = "und",
    val textScale: Float = 1f,
    val trimTrailingWhitespace: Boolean = false,
    val tabSize: Int = 4,
    val spans: TextSpans = TextSpans.EMPTY
) {

    init {
        require(!width.isNaN() && width >= 0)
        require(maxLines > 0)
        require(textScale.isFinite() && textScale > 0)
        require(tabSize > 0)
        require(spans.all { it.range.end <= text.length }) { "Text span exceeds source text" }
    }
}

data class FontMetrics(val ascent: Float, val descent: Float, val lineGap: Float) {
    init {
        require(listOf(ascent, descent, lineGap).all { it.isFinite() && it >= 0 })
    }

    val height get() = ascent + descent + lineGap
}

data class PositionedGlyph(
    val glyphId: Int,
    val x: Float,
    val y: Float,
    val advanceX: Float,
    val advanceY: Float = 0f,
    val clusterStart: Int,
    val clusterEnd: Int
) {

    init {
        require(glyphId >= 0 && clusterStart >= 0 && clusterEnd >= clusterStart)
        require(listOf(x, y, advanceX, advanceY).all(Float::isFinite))
    }
}

/**
 * Glyph coordinates are absolute within the measured layout, with y at the glyph baseline.
 */
class GlyphRun(val face: FontFace, glyphs: List<PositionedGlyph>, val direction: TextDirection, val fontSize: Float = 16f) {

    init {
        require(fontSize.isFinite() && fontSize > 0) { "Glyph run size must be positive and finite" }
    }

    val glyphs: List<PositionedGlyph> = glyphs.toList()
}

data class TextLine(
    val start: Int,
    val end: Int,
    val bounds: Rect,
    val baseline: Float,
    val metrics: FontMetrics
)

/**
 * Visual bounds and both logical caret edges of one extended grapheme cluster.
 */
data class TextCluster(
    val start: Int,
    val end: Int,
    val lineIndex: Int,
    val bounds: Rect,
    val leadingX: Float = bounds.x,
    val trailingX: Float = bounds.right
)

data class TextCaret(val index: Int, val position: Vec2, val height: Float, val lineIndex: Int)

/**
 * Immutable measured drawing data. Renderers consume these exact positioned runs.
 */
class TextLayout(
    val text: String,
    val size: Size,
    lines: List<TextLine>,
    runs: List<GlyphRun>,
    clusters: List<TextCluster>,
    val direction: TextDirection = TextDirection.LTR,
    val providerId: String,
    val isTruncated: Boolean = false
) {

    val lines: List<TextLine> = lines.toList()
    val runs: List<GlyphRun> = runs.toList()
    val clusters: List<TextCluster> = clusters.toList()
    val baseline: Float get() = lines.firstOrNull()?.baseline ?: 0f

    /**
     * Chooses a legal cluster edge, including visual RTL edges.
     */
    fun hitTest(point: Vec2): TextCaret {
        if (lines.isEmpty()) return TextCaret(0, Vec2.ZERO, 0f, 0)
        val lineIndex = lines.indices.firstOrNull { point.y >= lines[it].bounds.y && point.y < lines[it].bounds.bottom }
            ?: lines.indices.minBy { index ->
                val bounds = lines[index].bounds
                when {
                    point.y < bounds.y -> bounds.y - point.y
                    point.y > bounds.bottom -> point.y - bounds.bottom
                    else -> 0f
                }
            }
        val line = lines[lineIndex]
        val candidates = clusters.asSequence().filter { it.lineIndex == lineIndex }.flatMap {
            sequenceOf(it.start to it.leadingX, it.end to it.trailingX)
        }.toList()
        val edge = candidates.minByOrNull { abs(point.x - it.second) }
        return TextCaret(edge?.first ?: line.start, Vec2(edge?.second ?: line.bounds.x, line.bounds.y), line.bounds.height, lineIndex)
    }

    /**
     * An interior UTF-16 index snaps to the leading edge of its grapheme.
     */
    fun caret(index: Int): TextCaret {
        require(index in 0..text.length)
        val lineIndex = lines.indexOfLast { index >= it.start && index <= it.end }.coerceAtLeast(0)
        val cluster = clusters.firstOrNull { it.lineIndex == lineIndex && index >= it.start && index < it.end }
            ?: clusters.lastOrNull { it.lineIndex == lineIndex && it.end <= index }
        if (cluster == null) {
            val line = lines.getOrNull(lineIndex)
            return TextCaret(
                line?.start ?: 0, Vec2(line?.bounds?.x ?: 0f, line?.bounds?.y ?: 0f),
                line?.bounds?.height ?: 0f, lineIndex
            )
        }
        val atEnd = index >= cluster.end
        return TextCaret(
            if (atEnd) cluster.end else cluster.start,
            Vec2(if (atEnd) cluster.trailingX else cluster.leadingX, cluster.bounds.y), cluster.bounds.height, cluster.lineIndex
        )
    }
}

data class TextCapabilities(
    val providerId: String,
    val kerning: Boolean = false,
    val ligatures: Boolean = false,
    val bidi: Boolean = false,
    val fallback: Boolean = false,
    val unicodeClusters: Boolean = true,
    val scripts: Set<String> = emptySet(),
    val styledSpans: Boolean = false
)

/**
 * Thread/lifetime policy belongs to the provider. Ui borrows this service by default.
 */
interface TextService {

    val capabilities: TextCapabilities
    fun layout(request: TextRequest): TextLayout

    /**
     * Used to select a marker fallback. Providers must include family fallback in this check.
     */
    fun canDisplay(text: String, style: TextStyle): Boolean
}
