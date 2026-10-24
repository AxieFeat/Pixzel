package pixzel.arc.text

import arc.font.FontDirection
import arc.font.RasterGlyph
import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.text.FontFace
import pixzel.text.FontMetrics
import pixzel.text.FontSlant
import pixzel.text.GlyphRun
import pixzel.text.PositionedGlyph
import pixzel.text.TextCapabilities
import pixzel.text.TextCluster
import pixzel.text.TextDirection
import pixzel.text.TextLayout
import pixzel.text.TextLine
import pixzel.text.TextRange
import pixzel.text.TextRequest
import pixzel.text.TextService
import pixzel.text.TextStyle
import java.text.Bidi
import java.text.BreakIterator
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import arc.font.FontFace as ArcFontFace
import arc.font.FontMetrics as ArcFontMetrics

/** Explicit variant metadata; a family can register several real faces without synthetic styling. */
data class ArcFontRegistration(
    val face: ArcFontFace,
    val familyId: String = face.id,
    val weight: Int = 400,
    val slant: FontSlant = FontSlant.NORMAL
) {

    init {
        require(familyId.isNotBlank() && weight in 1..1000)
    }
}

/**
 * Font layout through Arc's neutral shaping/raster contracts. Faces are borrowed and must outlive
 * this service and its renderer. Native font providers impose their creating-thread ownership.
 * Family order defines fallback; unregistered faces are configuration errors. Missing clusters
 * become a replacement glyph, preserving their original UTF-16 source range.
 */
class ArcTextService private constructor(private val registrations: List<ArcFontRegistration>, override val capabilities: TextCapabilities) :
    TextService {

    private val faces = registrations.map { it.face }.associateBy { it.id }
    private val default = registrations.first().face
    var layoutCount: Long = 0
        private set

    private fun family(style: TextStyle): List<ArcFontFace> = style.family.faces.map { face ->
        val exact = registrations.firstOrNull { it.face.id == face.id }
        val familyId = exact?.familyId ?: if (face.id == "default") registrations.first().familyId else face.id
        val variants = registrations.filter { it.familyId == familyId }
        require(variants.isNotEmpty()) { "Font face or family '${face.id}' is not registered" }
        // Prefer the requested slant, then the nearest actual weight. Registration order breaks ties.
        val resolved = variants.minWith(compareBy<ArcFontRegistration> { if (it.slant == style.slant) 0 else 1 }
            .thenBy { abs(it.weight - style.weight) }).face
        require(face.version == (exact?.face ?: resolved).version) { "Font face '${face.id}' generation ${face.version} is unavailable" }
        resolved
    }.distinct()

    override fun canDisplay(text: String, style: TextStyle): Boolean {
        val family = family(style)
        return GRAPHEME.findAll(text).all { match ->
            val codepoints = match.value.codePoints().toArray().filterNot(::isFormatting)
            family.any { face -> codepoints.all { face.contains(it) } }
        }
    }

    /** Raster data is CPU-owned. The backend owns any derived atlas/texture and its budget. */
    fun rasterize(face: FontFace, glyphId: Int, fontSize: Float, pixelsPerDp: Float = 1f): RasterGlyph {
        val resolved = faces[face.id] ?: if (face.id == "default") default else
            throw IllegalArgumentException("Font face '${face.id}' is not registered")
        require(face.version == resolved.version) { "Stale glyph run for '${face.id}'" }
        return resolved.rasterize(glyphId, fontSize, pixelsPerDp)
    }

    private data class Cell(
        val start: Int, val end: Int, val face: ArcFontFace, val display: String,
        val script: Character.UnicodeScript, val style: TextStyle, val size: Float
    )

    private data class Part(val cells: List<Cell>, val level: Byte, val tab: Boolean = false)
    private data class LocalGlyph(val glyph: PositionedGlyph, val face: ArcFontFace, val penX: Float, val size: Float)
    private data class LocalLine(
        val start: Int, val end: Int, val width: Float,
        val metrics: FontMetrics, val height: Float, val baselineOffset: Float,
        val glyphs: List<LocalGlyph>, val clusters: List<TextCluster>, val direction: TextDirection
    )

    override fun layout(request: TextRequest): TextLayout {
        layoutCount++
        val size = request.style.size.value * request.textScale
        val family = family(request.style)
        val allRanges = GRAPHEME.findAll(request.text).map { TextRange(it.range.first, it.range.last + 1) }.toList()
        val boundaries = allRanges.map { it.end }.toSet() + 0
        require(request.spans.all { it.range.start in boundaries && it.range.end in boundaries }) {
            "Text spans must begin and end at extended grapheme boundaries"
        }
        if (size == 0f && request.spans.all { it.style.size.value == 0f }) return zeroLayout(request)
        var previousScript = Character.UnicodeScript.COMMON
        val cells = allRanges.filterNot { isNewline(request.text.substring(it.start, it.end)) }.map { range ->
            val source = request.text.substring(range.start, range.end)
            val style = request.spans.lastOrNull { it.range.start <= range.start && it.range.end >= range.end }?.style ?: request.style
            val family = family(style)
            val codepoints = source.codePoints().toArray().filterNot(::isFormatting)
            val selected = family.firstOrNull { face -> codepoints.all { face.contains(it) } }
            val face = selected ?: family.firstOrNull { it.contains(0xFFFD) } ?: family.first()
            val script = codepoints.asSequence().map(Character.UnicodeScript::of)
                .firstOrNull { it != Character.UnicodeScript.COMMON && it != Character.UnicodeScript.INHERITED }
                ?: previousScript
            previousScript = script
            Cell(
                range.start, range.end, face, if (selected != null || source == "\t") source else "\uFFFD", script,
                style, style.size.value * request.textScale
            )
        }
        val newline = NEWLINE.findAll(request.text).toList()
        val paragraphs = mutableListOf<TextRange>()
        var start = 0
        for (match in newline) {
            paragraphs += TextRange(start, match.range.first)
            start = match.range.last + 1
        }
        paragraphs += TextRange(start, request.text.length)
        val lines = mutableListOf<LocalLine>()
        var truncated = false
        outer@ for (paragraph in paragraphs) {
            var cursor = paragraph.start
            val paragraphCells = cells.filter { it.start >= paragraph.start && it.end <= paragraph.end }
            val paragraphText = request.text.substring(paragraph.start, paragraph.end)
            val bidi = Bidi(paragraphText, bidiFlag(request.direction))
            val boundaries = if (request.wrap) lineBreaks(paragraphText, request.locale).map { it + paragraph.start }.toSet() else emptySet()
            do {
                if (lines.size >= request.maxLines) {
                    truncated = true
                    break@outer
                }
                var end = paragraph.end
                var line = shapeLine(
                    request,
                    paragraphCells.filter { it.start >= cursor && it.end <= end },
                    cursor,
                    end,
                    paragraph.start,
                    bidi,
                    family,
                    size
                )
                if (request.wrap && request.width.isFinite() && line.width > request.width && cursor < end) {
                    val remaining = paragraphCells.filter { it.start >= cursor }
                    var low = 1
                    var high = remaining.size
                    var count = 1
                    while (low <= high) {
                        val middle = low + (high - low) / 2
                        val candidateEnd = remaining[middle - 1].end
                        val candidate = shapeLine(request, remaining.take(middle), cursor, candidateEnd, paragraph.start, bidi, family, size)
                        if (candidate.width <= request.width || middle == 1) {
                            count = middle
                            low = middle + 1
                        } else high = middle - 1
                    }
                    end = remaining[count - 1].end
                    val wordEnd =
                        boundaries.filter { boundary -> boundary > cursor && boundary <= end && remaining.any { it.end == boundary } }.maxOrNull()
                    if (wordEnd != null) end = wordEnd
                    line = shapeLine(request, remaining.filter { it.end <= end }, cursor, end, paragraph.start, bidi, family, size)
                }
                lines += line
                cursor = end
            } while (cursor < paragraph.end)
        }
        val outputLines = mutableListOf<TextLine>()
        val outputClusters = mutableListOf<TextCluster>()
        val outputRuns = mutableListOf<GlyphRun>()
        var top = 0f
        var maxWidth = 0f
        for ((lineIndex, line) in lines.withIndex()) {
            val metrics = line.metrics
            val height = line.height
            val baseline = top + line.baselineOffset
            outputLines += TextLine(line.start, line.end, Rect(0f, top, line.width, height), baseline, metrics)
            outputClusters += line.clusters.map { cluster ->
                cluster.copy(
                    lineIndex = lineIndex,
                    bounds = Rect(cluster.bounds.x, top, cluster.bounds.width, height)
                )
            }
            var activeFace: ArcFontFace? = null
            var activeDirection = TextDirection.LTR
            var activeSize = size
            var glyphs = mutableListOf<PositionedGlyph>()
            fun flush() {
                val face = activeFace ?: return
                if (glyphs.isNotEmpty()) outputRuns += GlyphRun(FontFace(face.id, face.version), glyphs, activeDirection, activeSize)
                glyphs = mutableListOf()
            }
            for (item in line.glyphs) {
                val direction = line.clusters.firstOrNull { item.glyph.clusterStart >= it.start && item.glyph.clusterStart < it.end }
                    ?.let { if (it.leadingX > it.trailingX) TextDirection.RTL else TextDirection.LTR } ?: line.direction
                if (activeFace !== item.face || activeDirection != direction || activeSize != item.size) {
                    flush()
                    activeFace = item.face
                    activeDirection = direction
                    activeSize = item.size
                }
                glyphs += item.glyph.copy(y = item.glyph.y + baseline)
            }
            flush()
            maxWidth = max(maxWidth, line.width)
            top += height
        }
        val direction = lines.firstOrNull()?.direction ?: if (request.direction == TextDirection.RTL) TextDirection.RTL else TextDirection.LTR
        return TextLayout(
            request.text, Size(maxWidth, top), outputLines, outputRuns, outputClusters,
            direction, capabilities.providerId, truncated
        )
    }

    private fun shapeLine(
        request: TextRequest, input: List<Cell>, start: Int, end: Int, paragraphStart: Int,
        paragraphBidi: Bidi, family: List<ArcFontFace>, size: Float
    ): LocalLine {
        val cells = if (request.trimTrailingWhitespace) input.dropLastWhile { cell ->
            request.text.substring(cell.start, cell.end).all(Char::isWhitespace)
        } else input
        val bidi = if (start < end) paragraphBidi.createLineBidi(start - paragraphStart, end - paragraphStart) else null
        val baseDirection = if (paragraphBidi.baseIsLeftToRight()) TextDirection.LTR else TextDirection.RTL
        val parts = mutableListOf<Part>()
        var group = mutableListOf<Cell>()
        var groupLevel: Byte = 0
        fun flush() {
            if (group.isNotEmpty()) {
                parts += Part(group.toList(), groupLevel)
                group = mutableListOf()
            }
        }
        for (cell in cells) {
            val level = (bidi?.getLevelAt(cell.start - start) ?: 0).toByte()
            if (cell.display == "\t") {
                flush()
                parts += Part(listOf(cell), level, true)
            } else {
                if (group.isNotEmpty() && (cell.face !== group.last().face || cell.script != group.last().script ||
                            cell.style != group.last().style || level != groupLevel)
                ) flush()
                groupLevel = level
                group += cell
            }
        }
        flush()
        val levels = parts.map { it.level }.toByteArray()
        val visual = parts.toTypedArray<Any>()
        if (visual.isNotEmpty()) Bidi.reorderVisually(levels, 0, visual, 0, visual.size)
        val glyphs = mutableListOf<LocalGlyph>()
        val clusters = mutableListOf<TextCluster>()
        var x = 0f
        var ascent = 0f
        var descent = 0f
        var gap = 0f
        val primaryMetrics = if (size > 0f) family.first().metrics(size) else ArcFontMetrics(0f, 0f, 0f)
        ascent = primaryMetrics.ascent
        descent = primaryMetrics.descent
        gap = primaryMetrics.lineGap
        val baseHeight = request.style.lineHeight?.times(request.textScale) ?: (ascent + descent + gap)
        var aboveBaseline = ascent + (baseHeight - ascent - descent) / 2
        var belowBaseline = descent + (baseHeight - ascent - descent) / 2
        for (value in visual) {
            val part = value as Part
            val rtl = part.level.toInt() and 1 != 0
            val face = part.cells.first().face
            val size = part.cells.first().size
            val style = part.cells.first().style
            val spacing = style.letterSpacing * request.textScale
            val direction = if (rtl) FontDirection.RTL else FontDirection.LTR
            if (size == 0f) {
                part.cells.forEach { cell -> clusters += TextCluster(cell.start, cell.end, 0, Rect(x, 0f, 0f, 0f), x, x) }
                continue
            }
            val partMetrics = face.metrics(size)
            ascent = max(ascent, partMetrics.ascent)
            descent = max(descent, partMetrics.descent)
            gap = max(gap, partMetrics.lineGap)
            val partHeight = style.lineHeight?.times(request.textScale) ?: (partMetrics.ascent + partMetrics.descent + partMetrics.lineGap)
            val leading = (partHeight - partMetrics.ascent - partMetrics.descent) / 2
            aboveBaseline = max(aboveBaseline, partMetrics.ascent + leading)
            belowBaseline = max(belowBaseline, partMetrics.descent + leading)
            if (part.tab) {
                val space = face.shape(" ", size, FontDirection.LTR, request.locale).glyphs.sumOf { it.advanceX.toDouble() }.toFloat()
                val tab = max(0.001f, space * request.tabSize)
                val width = tab - x % tab
                val cell = part.cells.first()
                clusters += TextCluster(
                    cell.start, cell.end, 0, Rect(x, 0f, width, 0f),
                    if (rtl) x + width else x, if (rtl) x else x + width
                )
                x += width
                continue
            }
            val display = part.cells.joinToString("") { it.display }
            val displayStarts = mutableListOf<Int>()
            var displayCursor = 0
            part.cells.forEach {
                displayStarts += displayCursor
                displayCursor += it.display.length
            }
            val shaped = face.shape(display, size, direction, request.locale)
            ascent = max(ascent, shaped.metrics.ascent)
            descent = max(descent, shaped.metrics.descent)
            gap = max(gap, shaped.metrics.lineGap)
            var pen = x
            for ((glyphIndex, glyph) in shaped.glyphs.withIndex()) {
                val firstCell = displayStarts.indexOfLast { it <= glyph.clusterStart }.coerceAtLeast(0)
                val lastCell = displayStarts.indexOfLast { it < glyph.clusterEnd }.coerceAtLeast(firstCell)
                val sourceStart = part.cells[firstCell].start
                val sourceEnd = part.cells[lastCell].end
                val groupLast = shaped.glyphs.getOrNull(glyphIndex + 1)?.clusterStart != glyph.clusterStart
                val extra = if (groupLast) spacing * (lastCell - firstCell + 1) else 0f
                glyphs += LocalGlyph(
                    PositionedGlyph(
                        glyph.glyphId, pen + glyph.offsetX, glyph.offsetY,
                        glyph.advanceX + extra, glyph.advanceY, sourceStart, sourceEnd
                    ), face, pen, size
                )
                pen += glyph.advanceX + extra
            }
            // Build caret edges per extended grapheme, subdividing ligature advance safely.
            val partGlyphs =
                glyphs.filter { it.face === face && it.glyph.clusterStart >= part.cells.first().start && it.glyph.clusterEnd <= part.cells.last().end }
            val grouped = partGlyphs.groupBy { it.glyph.clusterStart to it.glyph.clusterEnd }
            for ((range, items) in grouped) {
                val covered = part.cells.filter { it.start >= range.first && it.end <= range.second }
                val groupLeft = items.minOf { it.penX }
                val advance = items.sumOf { it.glyph.advanceX.toDouble() }.toFloat().coerceAtLeast(0f)
                for ((index, cell) in covered.withIndex()) {
                    val width = if (covered.isEmpty()) 0f else advance / covered.size
                    val left = groupLeft + if (rtl) (covered.size - index - 1) * width else index * width
                    clusters += TextCluster(
                        cell.start, cell.end, 0, Rect(left, 0f, width, 0f),
                        if (rtl) left + width else left, if (rtl) left else left + width
                    )
                }
            }
            x = max(x, pen)
        }
        return LocalLine(
            start, end, max(0f, x), FontMetrics(ascent, descent, gap), aboveBaseline + belowBaseline,
            aboveBaseline, glyphs, clusters, baseDirection
        )
    }

    private fun zeroLayout(request: TextRequest): TextLayout {
        val metrics = FontMetrics(0f, 0f, 0f)
        val ranges = GRAPHEME.findAll(request.text).map { TextRange(it.range.first, it.range.last + 1) }.toList()
        val lines = mutableListOf<TextLine>()
        val clusters = mutableListOf<TextCluster>()
        var start = 0
        var lineIndex = 0
        for (range in ranges) {
            if (lineIndex >= request.maxLines) break
            if (isNewline(request.text.substring(range.start, range.end))) {
                lines += TextLine(start, range.start, Rect.ZERO, 0f, metrics)
                lineIndex++
                start = range.end
            } else clusters += TextCluster(range.start, range.end, lineIndex, Rect.ZERO)
        }
        if (lineIndex < request.maxLines) lines += TextLine(start, request.text.length, Rect.ZERO, 0f, metrics)
        val direction = if (Bidi(request.text, bidiFlag(request.direction)).baseIsLeftToRight()) TextDirection.LTR else TextDirection.RTL
        return TextLayout(
            request.text, Size.ZERO, lines, emptyList(), clusters, direction,
            providerId = capabilities.providerId, isTruncated = lineIndex >= request.maxLines
        )
    }

    companion object {

        private val identities = AtomicLong()
        private fun nextIdentity() = "pixzel.arc.font:${identities.incrementAndGet()}"
        private val GRAPHEME = Regex("\\X")
        private val NEWLINE = Regex("\r\n|\r|\n")

        /** Default provider identity is unique per service; explicit IDs assert compatible font resources. */
        fun of(faces: List<ArcFontFace>, providerId: String = nextIdentity()): ArcTextService {
            return from(faces.map { ArcFontRegistration(it) }, providerId)
        }

        fun from(registrations: List<ArcFontRegistration>, providerId: String = nextIdentity()): ArcTextService {
            require(registrations.isNotEmpty() && registrations.map { it.face.id }.distinct().size == registrations.size)
            require(providerId.isNotBlank())
            return ArcTextService(
                registrations.toList(), TextCapabilities(
                    providerId, kerning = true, ligatures = true,
                    bidi = true, fallback = true, scripts = setOf("Latn", "Cyrl", "Arab"), styledSpans = true
                )
            )
        }

        private fun isFormatting(codepoint: Int) = codepoint == 0x200D || codepoint in 0xFE00..0xFE0F ||
                codepoint in 0xE0100..0xE01EF || codepoint == 0x09 || codepoint == 0x0A || codepoint == 0x0D

        private fun isNewline(text: String) = text == "\n" || text == "\r" || text == "\r\n"
        private fun bidiFlag(direction: TextDirection) = when (direction) {
            TextDirection.AUTO -> Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT
            TextDirection.LTR -> Bidi.DIRECTION_LEFT_TO_RIGHT
            TextDirection.RTL -> Bidi.DIRECTION_RIGHT_TO_LEFT
        }

        private fun lineBreaks(text: String, locale: String): List<Int> {
            val iterator = BreakIterator.getLineInstance(Locale.forLanguageTag(locale))
            iterator.setText(text)
            val result = mutableListOf<Int>()
            var boundary = iterator.first()
            while (boundary != BreakIterator.DONE) {
                result += boundary
                boundary = iterator.next()
            }
            return result
        }
    }
}
