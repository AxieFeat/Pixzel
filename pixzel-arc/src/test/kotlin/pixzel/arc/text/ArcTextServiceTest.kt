package pixzel.arc.text

import arc.font.NativeFontProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pixzel.layout.unit.Sp
import pixzel.surface.Vec2
import pixzel.text.FontFace
import pixzel.text.FontFamily
import pixzel.text.TextDirection
import pixzel.text.TextLayout
import pixzel.text.TextRequest
import pixzel.text.TextSpan
import pixzel.text.TextSpans
import pixzel.text.TextStyle
import java.nio.file.Files
import java.nio.file.Path

class ArcTextServiceTest {

    private fun fonts() = Path.of(System.getProperty("pixzel.fonts", "../assets/fonts"))

    private fun withText(block: (ArcTextService) -> Unit) {
        NativeFontProvider.of().use { provider ->
            val latin = provider.load("noto", Files.readAllBytes(fonts().resolve("NotoSans-Regular.ttf")))
            val bold = provider.load("noto-bold", Files.readAllBytes(fonts().resolve("NotoSans-Bold.ttf")))
            val arabic = provider.load("arabic", Files.readAllBytes(fonts().resolve("NotoSansArabic-Regular.ttf")))
            block(
                ArcTextService.from(
                    listOf(
                        ArcFontRegistration(latin, "sans"),
                        ArcFontRegistration(bold, "sans", 700), ArcFontRegistration(arabic)
                    )
                )
            )
        }
    }

    private val family = FontFamily(listOf(FontFace("noto"), FontFace("arabic")))
    private fun request(text: String) = TextRequest(text, TextStyle(family = family))

    @Test
    fun `real Latin Cyrillic ligatures fallback and source maps are retained`() = withText { text ->
        val layout = text.layout(request("AV ffi Привет مرحبا A\u0301 \uD83D\uDC69\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"))
        assertTrue(layout.size.width > 0 && layout.size.height > 0)
        assertTrue(layout.runs.any { it.face.id == "arabic" })
        assertTrue(layout.runs.any { it.face.id == "noto" })
        assertTrue(layout.runs.flatMap { it.glyphs }.all { it.glyphId > 0 })
        val sourceRanges = Regex("\\X").findAll(layout.text).map { it.range.first to it.range.last + 1 }.toSet()
        assertEquals(sourceRanges, layout.clusters.map { it.start to it.end }.toSet())
        assertTrue(layout.runs.flatMap { it.glyphs }.any { it.clusterEnd - it.clusterStart > 1 })
        assertTrue(text.canDisplay("Привет مرحبا", request("").style))
        assertFalse(text.canDisplay(String(Character.toChars(0x10FFFF)), request("").style))
        val replacement = text.layout(request("x" + String(Character.toChars(0x10FFFF)) + "y"))
        assertTrue(replacement.runs.flatMap { it.glyphs }.all { it.glyphId > 0 })
        assertTrue(replacement.clusters.any { it.start == 1 && it.end == 3 })
    }

    @Test
    fun `bidi visual ordering and legal caret edges work for mixed Arabic numbers and Cyrillic`() = withText { text ->
        val layout = text.layout(request("مرحبا 123 Привет"))
        assertEquals(TextDirection.RTL, layout.direction)
        assertTrue(layout.runs.any { it.direction == TextDirection.RTL })
        assertTrue(layout.runs.any { it.direction == TextDirection.LTR })
        val arabic = layout.clusters.filter { it.start < 5 }.sortedBy { it.start }
        assertTrue(arabic.first().leadingX > arabic.last().leadingX)
        assertTrue(arabic.all { it.leadingX >= it.trailingX })
        val legal = layout.clusters.flatMap { listOf(it.start, it.end) }.toSet()
        for (x in 0..layout.size.width.toInt()) assertTrue(layout.hitTest(Vec2(x.toFloat(), 5f)).index in legal)
    }

    @Test
    fun `wrapped source never splits graphemes and max lines reports hidden content`() = withText { text ->
        val source = "AVAV word A\u0301word Привет мир\nnext\tcolumn\n"
        val wrapped = text.layout(request(source).copy(width = 65f, wrap = true, maxLines = 3))
        assertEquals(3, wrapped.lines.size)
        assertTrue(wrapped.isTruncated)
        val boundaries = Regex("\\X").findAll(source).map { it.range.last + 1 }.toSet() + 0
        assertTrue(wrapped.lines.all { it.start in boundaries && it.end in boundaries })
        assertTrue(wrapped.lines.all { it.bounds.width <= 65.001f })
        val emptyLine = text.layout(request("one\n"))
        assertEquals(2, emptyLine.lines.size)
        assertEquals(4, emptyLine.caret(4).index)
        assertEquals(1, emptyLine.caret(4).lineIndex)
    }

    @Test
    fun `measured glyph IDs are rasterized at requested independent density`() = withText { text ->
        val layout = text.layout(request("ЖAV"))
        val run = layout.runs.first()
        val glyph = run.glyphs.first()
        val one = text.rasterize(run.face, glyph.glyphId, run.fontSize, 1f)
        val two = text.rasterize(run.face, glyph.glyphId, run.fontSize, 2f)
        assertTrue(one.alpha.any { it.toInt() and 255 > 0 })
        assertTrue(two.width > one.width && two.height > one.height)
        assertEquals(layout.size, text.layout(request("ЖAV")).size)
        assertThrows(IllegalArgumentException::class.java) { text.rasterize(run.face.copy(version = 1), glyph.glyphId, run.fontSize) }
    }

    @Test
    fun `registered real bold variant is selected and produces different metrics and coverage`() = withText { text ->
        val regularRequest = request("HHH Привет").copy(style = request("").style.copy(size = Sp(32f)))
        val regular = text.layout(regularRequest)
        val bold = text.layout(regularRequest.copy(style = regularRequest.style.copy(weight = 700)))
        assertTrue(regular.runs.all { it.face.id == "noto" })
        assertTrue(bold.runs.all { it.face.id == "noto-bold" })
        assertNotEquals(regular.size.width, bold.size.width)
        fun coverage(layout: TextLayout): Int {
            val run = layout.runs.first()
            val glyph = run.glyphs.first()
            return text.rasterize(run.face, glyph.glyphId, run.fontSize).alpha.sumOf { it.toInt() and 255 }
        }
        assertTrue(coverage(bold) > coverage(regular))
        val nearest = text.layout(regularRequest.copy(style = regularRequest.style.copy(weight = 650)))
        assertTrue(nearest.runs.all { it.face.id == "noto-bold" })
        val familyName = regularRequest.style.copy(family = FontFamily(listOf(FontFace("sans"))), weight = 700)
        assertTrue(text.layout(regularRequest.copy(style = familyName)).runs.all { it.face.id == "noto-bold" })
    }

    @Test
    fun `recreated services and revised font bytes cannot silently reuse stale layouts`() {
        val regular = Files.readAllBytes(fonts().resolve("NotoSans-Regular.ttf"))
        val bold = Files.readAllBytes(fonts().resolve("NotoSans-Bold.ttf"))
        val first = NativeFontProvider.of()
        val old = ArcTextService.of(listOf(first.load("same", regular, version = 1)))
        val request = TextRequest("HHH", TextStyle(family = FontFamily(listOf(FontFace("same", 1)))))
        val layout = old.layout(request)
        first.close()
        NativeFontProvider.of().use { second ->
            val replacement = ArcTextService.of(listOf(second.load("same", bold, version = 2)))
            assertNotEquals(old.capabilities.providerId, replacement.capabilities.providerId)
            assertThrows(IllegalArgumentException::class.java) { replacement.layout(request) }
            val run = layout.runs.single()
            val glyph = run.glyphs.first()
            assertThrows(IllegalArgumentException::class.java) { replacement.rasterize(run.face, glyph.glyphId, run.fontSize) }
            val updated = replacement.layout(request.copy(style = request.style.copy(family = FontFamily(listOf(FontFace("same", 2))))))
            assertEquals(2L, updated.runs.single().face.version)
            assertNotEquals(layout.size.width, updated.size.width)
        }
    }

    @Test
    fun `styled spans preserve source clusters mixed sizes fallback bidi and immutable keys`() = withText { text ->
        val source = "A\u0301V مرحبا end"
        val regular = request(source)
        val spans = mutableListOf(
            TextSpan(0, 3, regular.style.copy(size = Sp(32f), weight = 700)),
            TextSpan(4, 9, regular.style.copy(size = Sp(24f)))
        )
        val snapshot = TextSpans.from(spans)
        val styledRequest = regular.copy(spans = snapshot)
        spans.clear()
        val styled = text.layout(styledRequest)
        assertTrue(text.capabilities.styledSpans)
        assertTrue(styled.runs.any { it.face.id == "noto-bold" && it.fontSize == 32f })
        assertTrue(styled.runs.any { it.face.id == "arabic" && it.fontSize == 24f })
        assertTrue(styled.size.height > text.layout(regular).size.height)
        val ranges = Regex("\\X").findAll(source).map { it.range.first to it.range.last + 1 }.toSet()
        assertEquals(ranges, styled.clusters.map { it.start to it.end }.toSet())
        assertEquals(0, styled.caret(1).index)
        val wrapping = text.layout(styledRequest.copy(width = 65f, wrap = true))
        assertTrue(wrapping.lines.size > 1)
        assertTrue(wrapping.lines.all { it.end in ranges.map { range -> range.second } })
        val illegal = regular.copy(spans = TextSpans.of(TextSpan(1, 3, regular.style)))
        assertThrows(IllegalArgumentException::class.java) { text.layout(illegal) }
        val overlapping = styledRequest.copy(
            spans = TextSpans.of(
                TextSpan(0, 3, regular.style.copy(weight = 700)),
                TextSpan(0, 3, regular.style)
            )
        )
        assertEquals("noto", text.layout(overlapping).runs.first().face.id)
    }
}
