package pixzel.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pixzel.surface.Size
import pixzel.surface.Vec2
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TextPipelineTest {

    private val font = FixedFontTextService.of()
    private fun context(text: String, width: Float, marker: String = "\u2026"): TextOverflowContext {
        val request = TextRequest(text)
        return TextOverflowContext(font, request, font.layout(request), Size(width, 16f), marker)
    }

    @Test
    fun `extended grapheme boundaries retain emoji combining flags CRLF and Hangul`() {
        val pieces = listOf(
            "A\u0301", "\uD83D\uDC69\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66",
            "\uD83C\uDDF7\uD83C\uDDFA", "\uD83D\uDC4D\uD83C\uDFFD", "\r\n", "\u1100\u1161\u11A8"
        )
        val source = pieces.joinToString("")
        val ranges = UnicodeGraphemeSegmenter.segment(source)
        assertEquals(pieces, ranges.map { source.substring(it.start, it.end) })
        assertEquals(0, UnicodeGraphemeSegmenter.previousBoundary(source, 1))
        assertEquals(2, UnicodeGraphemeSegmenter.nextBoundary(source, 0))
        assertEquals(source.length, ranges.last().end)
    }

    @Test
    fun `ellipsis cuts only clusters at all three positions`() {
        val source = "A\u0301\uD83D\uDC69\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66\uD83C\uDDF7\uD83C\uDDFAZ"
        val c = context(source, 24f)
        val clusters = UnicodeGraphemeSegmenter.segment(source).map { source.substring(it.start, it.end) }
        assertEquals(clusters[0] + clusters[1] + "\u2026", TextOverflow.ELLIPSIS.resolve(c).layout.text)
        assertEquals("\u2026" + clusters[2] + clusters[3], TextOverflow.ELLIPSIS_START.resolve(c).layout.text)
        assertEquals(clusters[0] + "\u2026" + clusters[3], TextOverflow.ELLIPSIS_MIDDLE.resolve(c).layout.text)
        for (policy in listOf(TextOverflow.ELLIPSIS, TextOverflow.ELLIPSIS_START, TextOverflow.ELLIPSIS_MIDDLE)) {
            assertTrue(policy.resolve(c).layout.size.width <= 24f)
        }
    }

    @Test
    fun `exact fit empty and too narrow marker have explicit behavior`() {
        val exact = context("абв", 24f)
        assertSame(exact.full, TextOverflow.ELLIPSIS.resolve(exact).layout)
        assertFalse(TextOverflow.ELLIPSIS.resolve(exact).overflowed)
        assertEquals("", TextOverflow.ELLIPSIS.resolve(context("", 0f)).layout.text)
        val narrow = context("long label", 4f, "...")
        val clipped = TextOverflow.ELLIPSIS.resolve(narrow)
        assertEquals("...", clipped.layout.text)
        assertTrue(clipped.clip)
        assertEquals("", TextOverflow.ELLIPSIS.resolve(narrow.copy(tooNarrow = TooNarrowPolicy.EMPTY)).layout.text)
        assertEquals("...", TextOverflow.ELLIPSIS.resolve(narrow.copy(viewport = Size.ZERO)).layout.text)
    }

    @Test
    fun `marker fallback and custom policy use public contracts`() {
        val service = object : TextService by font {
            override fun canDisplay(text: String, style: TextStyle) = text != "\u2026"
        }
        val c = context("long label", 40f).copy(service = service)
        assertEquals("lo...", TextOverflow.ELLIPSIS.resolve(c).layout.text)
        val custom = TextOverflowPolicy { it.service.layout(it.request.copy(text = "OK")).let { l -> TextOverflowResult(l, false, true) } }
        assertEquals("OK", custom.resolve(c).layout.text)
        assertFalse(custom.resolve(c).clip)
    }

    @Test
    fun `candidate text is reshaped instead of summing original advances`() {
        val seen = mutableListOf<String>()
        val service = object : TextService by font {
            override fun layout(request: TextRequest): TextLayout {
                seen += request.text
                // Real providers may change kerning/ligatures in the shortened context.
                return font.layout(request)
            }
        }
        val c = context("AVAVAVAV", 32f).copy(service = service)
        val result = TextOverflow.ELLIPSIS.resolve(c)
        assertTrue(seen.size > 1)
        assertTrue(seen.contains(result.layout.text))
        assertTrue(seen.any { it.endsWith("\u2026") && it != "\u2026" })
        assertEquals(result.layout.size.width, font.layout(TextRequest(result.layout.text)).size.width)
    }

    @Test
    fun `wrap max lines ellipsizes final visible line`() {
        val c = context("abcdefghij", 32f).let { it.copy(request = it.request.copy(maxLines = 2), viewport = Size(32f, 32f)) }
        val result = TextOverflow.WRAP.resolve(c)
        assertTrue(result.overflowed)
        assertEquals(2, result.layout.lines.size)
        assertTrue(result.layout.text.endsWith("\u2026"))
        assertTrue(result.layout.size.width <= 32f)
        val explicit = font.layout(TextRequest("one\ntwo\n", width = 32f, wrap = true))
        assertEquals(3, explicit.lines.size)
        assertEquals(48f, explicit.size.height)
    }

    @Test
    fun `caret hit and deletion boundaries never enter a cluster`() {
        val source = "a\u0301\uD83D\uDE00z"
        val layout = font.layout(TextRequest(source))
        assertEquals(0, layout.caret(1).index)
        assertEquals(2, layout.caret(3).index)
        assertEquals(4, layout.hitTest(Vec2(15f, 8f)).index)
        assertEquals(2, UnicodeGraphemeSegmenter.previousBoundary(source, 4))
        assertEquals(4, UnicodeGraphemeSegmenter.nextBoundary(source, 2))
    }

    private class RevealFixture(val service: FixedFontTextService = FixedFontTextService.of()) {

        val request = TextRequest("abcdefghij")
        val full = service.layout(request)
        val viewport = Size(40f, 16f)
        val static = TextOverflow.ELLIPSIS.resolve(TextOverflowContext(service, request, full, viewport))
        fun frame(delta: Duration, hovered: Boolean = true, reducedMotion: Boolean = false) =
            TextRevealContext(full, static, viewport, delta, hovered = hovered, reducedMotion = reducedMotion)
    }

    @Test
    fun `default reveal preserves delay both pauses directions and static exit`() {
        val fixture = RevealFixture()
        val runtime = hoverReveal().createRuntime()
        assertEquals(RevealPhase.IDLE, runtime.update(fixture.frame(Duration.ZERO, hovered = false)).phase)
        val wait = runtime.update(fixture.frame(599.milliseconds))
        assertEquals(RevealPhase.WAIT, wait.phase)
        assertSame(fixture.static.layout, wait.layout)
        val first = runtime.update(fixture.frame(1.milliseconds))
        assertEquals(RevealPhase.FORWARD, first.phase)
        assertSame(fixture.full, first.layout)
        assertEquals(0f, first.offsetX, 0.0001f)
        assertEquals(-16f, runtime.update(fixture.frame(500.milliseconds)).offsetX, 0.0001f)
        val tail = runtime.update(fixture.frame(751.milliseconds))
        assertEquals(RevealPhase.END_PAUSE, tail.phase)
        assertEquals(-40f, tail.offsetX, 0.0001f)
        val backward = runtime.update(fixture.frame(700.milliseconds))
        assertEquals(RevealPhase.BACKWARD, backward.phase)
        assertEquals(-39.968f, backward.offsetX, 0.001f)
        assertEquals(-19.968f, runtime.update(fixture.frame(625.milliseconds)).offsetX, 0.001f)
        val leading = runtime.update(fixture.frame(625.milliseconds))
        assertEquals(RevealPhase.START_PAUSE, leading.phase)
        assertEquals(0f, leading.offsetX, 0.0001f)
        assertEquals(RevealPhase.FORWARD, runtime.update(fixture.frame(700.milliseconds)).phase)
        val exit = runtime.update(fixture.frame(Duration.ZERO, hovered = false))
        assertEquals(RevealPhase.IDLE, exit.phase)
        assertSame(fixture.static.layout, exit.layout)
    }

    @Test
    fun `animation performs no shaping and large delta skips periods in constant time`() {
        val fixture = RevealFixture()
        val runtime = hoverReveal().createRuntime()
        val count = fixture.service.layoutCount
        repeat(500) { runtime.update(fixture.frame(16.milliseconds)) }
        assertEquals(count, fixture.service.layoutCount)
        runtime.reset()
        val enormous = runtime.update(fixture.frame(1_000_000.days))
        assertTrue(enormous.offsetX in -40f..0f)
        assertTrue(enormous.offsetX.isFinite())
        assertEquals(count, fixture.service.layoutCount)
    }

    @Test
    fun `shared config has independent runtime reduced motion zero speed resize and focus`() {
        val fixture = RevealFixture()
        val behavior = hoverReveal { trigger = RevealTrigger.HOVER_OR_FOCUS }
        val first = behavior.createRuntime()
        val second = behavior.createRuntime()
        assertEquals(-16f, first.update(fixture.frame(1100.milliseconds)).offsetX, 0.001f)
        assertEquals(RevealPhase.WAIT, second.update(fixture.frame(Duration.ZERO)).phase)
        assertEquals(RevealPhase.IDLE, first.update(fixture.frame(Duration.ZERO, reducedMotion = true)).phase)
        assertEquals(RevealPhase.WAIT, second.update(fixture.frame(Duration.ZERO).copy(viewport = Size(48f, 16f))).phase)
        assertEquals(
            RevealPhase.FORWARD,
            behavior.createRuntime().update(fixture.frame(600.milliseconds, hovered = false).copy(focused = true)).phase
        )
        val stopped = hoverReveal { speed = 0.dpPerSecond }.createRuntime().update(fixture.frame(10.seconds))
        assertFalse(stopped.needsUpdate)
        assertSame(fixture.static.layout, stopped.layout)
        assertThrows(IllegalArgumentException::class.java) { DpPerSecond(-1f) }
    }

    @Test
    fun `reveal exit policies and repeat count have observable results`() {
        val fixture = RevealFixture()
        val retain = hoverReveal { onDeactivate = RevealExit.RETAIN }.createRuntime()
        retain.update(fixture.frame(1100.milliseconds))
        val retained = retain.update(fixture.frame(10.seconds, hovered = false))
        assertEquals(-16f, retained.offsetX, 0.001f)
        assertFalse(retained.needsUpdate)
        val returning = hoverReveal { onDeactivate = RevealExit.RETURN }.createRuntime()
        returning.update(fixture.frame(1100.milliseconds))
        assertEquals(-8f, returning.update(fixture.frame(250.milliseconds, hovered = false)).offsetX, 0.001f)
        assertEquals(RevealPhase.IDLE, returning.update(fixture.frame(250.milliseconds, hovered = false)).phase)
        val once = hoverReveal { repeatCount = 1 }.createRuntime().update(fixture.frame(10.seconds))
        assertEquals(RevealPhase.COMPLETE, once.phase)
        assertFalse(once.needsUpdate)
    }

    @Test
    fun `cache is LRU bounded by entries and bytes and uses font generations`() {
        val cache = CachingTextService.of(font, maxEntries = 2, maxBytes = 100_000)
        val a = TextRequest("a")
        val b = TextRequest("b")
        val c = TextRequest("c")
        val first = cache.layout(a)
        cache.layout(b)
        assertSame(first, cache.layout(a))
        cache.layout(c)
        val calls = font.layoutCount
        cache.layout(b)
        assertEquals(calls + 1, font.layoutCount)
        assertEquals(2, cache.statistics.entries)
        assertTrue(cache.statistics.estimatedBytes <= 100_000)
        val replacement = a.copy(style = a.style.copy(family = FontFamily(listOf(FontFace("default", 1)))))
        assertNotSame(first, cache.layout(replacement))
        val tiny = CachingTextService.of(font, maxBytes = 1)
        tiny.layout(a)
        assertEquals(0, tiny.statistics.entries)
        cache.clear()
        assertEquals(0L, cache.statistics.estimatedBytes)
    }

    @Test
    fun `overflow rebases immutable span ranges at prefix suffix middle and final wrapped line`() {
        val seen = mutableListOf<TextRequest>()
        val service = object : TextService by font {
            override val capabilities = font.capabilities.copy(styledSpans = true)
            override fun layout(request: TextRequest): TextLayout {
                seen += request
                // This adapter test inspects range composition; native tests verify styled metrics.
                return font.layout(request.copy(spans = TextSpans.EMPTY))
            }
        }
        val style = TextStyle(weight = 700)
        val input = mutableListOf(TextSpan(1, 7, style))
        val request = TextRequest("abcdefgh", spans = TextSpans.from(input))
        input.clear()
        assertEquals(1, request.spans.size)
        val full = service.layout(request)
        val c = TextOverflowContext(service, request, full, Size(32f, 32f))
        for ((policy, expected, range) in listOf(
            Triple(TextOverflow.ELLIPSIS, "abc\u2026", TextRange(1, 3)),
            Triple(TextOverflow.ELLIPSIS_START, "\u2026fgh", TextRange(1, 3)),
            Triple(TextOverflow.ELLIPSIS_MIDDLE, "ab\u2026h", TextRange(1, 2))
        )) {
            seen.clear()
            assertEquals(expected, policy.resolve(c).layout.text)
            val finalRequest = seen.last { it.text == expected }
            assertEquals(listOf(range), finalRequest.spans.map { it.range })
        }
        val wrapped = c.copy(request = request.copy(maxLines = 1))
        assertTrue(TextOverflow.WRAP.resolve(wrapped).layout.text.endsWith("\u2026"))
        assertTrue(seen.all { candidate -> candidate.spans.all { it.range.end <= candidate.text.length } })
        assertThrows(IllegalArgumentException::class.java) { font.layout(request) }
    }

    @Test
    fun `truncating AUTO text retains original paragraph direction when suffix script differs`() {
        val request = TextRequest("مرحبا ABC")
        val full = font.layout(request.copy(direction = TextDirection.RTL))
        val context = TextOverflowContext(font, request, full, Size(32f, 16f))
        val shortened = TextOverflow.ELLIPSIS_START.resolve(context).layout
        assertEquals("\u2026ABC", shortened.text)
        assertEquals(TextDirection.RTL, shortened.direction)
        val marker = shortened.clusters.first { it.start == 0 }
        assertEquals(shortened.size.width, marker.leadingX)
    }
}
