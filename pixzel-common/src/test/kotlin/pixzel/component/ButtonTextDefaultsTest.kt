package pixzel.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pixzel.graphics.DrawText
import pixzel.graphics.RecordingBackend
import pixzel.graphics.group
import pixzel.layout.unit.dp
import pixzel.platform.TestHost
import pixzel.style.ButtonTextDefaults
import pixzel.style.StyleScope
import pixzel.style.StyleTokens
import pixzel.style.Theme
import pixzel.surface.Placement
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import pixzel.text.TextLayout
import pixzel.text.TextOverflow
import pixzel.text.TextRequest
import pixzel.text.TextReveal
import pixzel.text.TextRevealBehavior
import pixzel.text.TextRevealContext
import pixzel.text.TextRevealFrame
import pixzel.text.TextRevealRuntime
import pixzel.text.TextService
import pixzel.text.hoverReveal
import pixzel.ui
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ButtonTextDefaultsTest {

    @Test
    fun `leading and trailing text updates preserve the complete primary button semantics`() {
        val host = TestHost.of(400, 240)
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        try {
            val button = ui.components.button("button")
            val label = button.setLabel("Complete primary label")
            val leading = ui.components.text("status").apply { value = "Offline" }
            val trailing = ui.components.text("count").apply { value = "1" }
            button.setLeading(leading)
            button.setTrailing(trailing)
            ui.root.add(button)
            ui.frame(Duration.ZERO)
            label.value = "Updated complete primary label"
            leading.value = "Online"
            trailing.value = "99"
            ui.frame(Duration.ZERO)
            assertEquals("Updated complete primary label", button.semantics.label)
            assertEquals("Online", leading.semantics.label)
            assertEquals("99", trailing.semantics.label)
            assertEquals("Updated complete primary label", ui.semanticsTree().children.single().label)
        } finally {
            ui.close()
            host.close()
        }
    }

    private class TimedReveal : TextRevealBehavior {

        var runtimes = 0
        override fun createRuntime(): TextRevealRuntime {
            runtimes++
            return object : TextRevealRuntime {
                private var elapsed = Duration.ZERO
                override fun reset() {
                    elapsed = Duration.ZERO
                }

                override fun update(context: TextRevealContext): TextRevealFrame {
                    elapsed += context.delta
                    return TextRevealFrame(context.full, -(elapsed / 1.seconds).toFloat(), needsUpdate = true)
                }
            }
        }
    }

    @Test
    fun `theme and component scope label defaults merge while explicit instance values win`() {
        val host = TestHost.of(400, 240)
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        try {
            ui.theme = Theme.DARK.copy(buttonText = ButtonTextDefaults(TextOverflow.VISIBLE, TextReveal.NONE))
            val group = ui.components.surface()
            ui.root.add(group)
            val button = ui.components.button()
            val label = button.setLabel("Long server label")
            group.add(button)
            ui.frame(Duration.ZERO)
            assertSame(TextOverflow.VISIBLE, label.overflow)
            assertSame(TextReveal.NONE, label.reveal)
            group.styleScope = StyleScope(StyleTokens(buttonText = ButtonTextDefaults(overflow = TextOverflow.CLIP)))
            ui.frame(Duration.ZERO)
            assertSame(TextOverflow.CLIP, label.overflow)
            assertSame(TextReveal.NONE, label.reveal)
            val custom = TimedReveal()
            label.overflow = TextOverflow.ELLIPSIS_START
            label.reveal = custom
            ui.theme = Theme.LIGHT.copy(buttonText = ButtonTextDefaults(TextOverflow.ELLIPSIS_MIDDLE, hoverReveal()))
            ui.frame(Duration.ZERO)
            assertSame(TextOverflow.ELLIPSIS_START, label.overflow)
            assertSame(custom, label.reveal)
            val plain = ui.components.text()
            group.add(plain)
            assertSame(TextOverflow.CLIP, plain.overflow)
            assertSame(TextReveal.NONE, plain.reveal)
        } finally {
            ui.close()
            host.close()
        }
    }

    @Test
    fun `shared theme reveal has independent per-label clocks and NONE disables existing motion`() {
        val host = TestHost.of(400, 240)
        val backend = RecordingBackend.of()
        val ui = ui(host, backend, FixedFontTextService.of())
        try {
            val reveal = TimedReveal()
            ui.theme = Theme.DARK.copy(buttonText = ButtonTextDefaults(TextOverflow.CLIP, reveal))
            val first = ui.components.button().apply {
                layout.width = 100.dp
                setLabel("First long server")
            }
            ui.root.add(first)
            ui.frame(Duration.ZERO)
            ui.frame(1.seconds)
            val second = ui.components.button().apply {
                layout.width = 100.dp
                placement = Placement(offset = Vec2(0f, 60f))
                setLabel("Second long server")
            }
            ui.root.add(second)
            ui.frame(Duration.ZERO)
            ui.frame(1.seconds)
            val draws = backend.lastFrame!!.commands.filterIsInstance<DrawText>()
            assertEquals(2, reveal.runtimes)
            assertEquals(-2f, draws.first { it.layout.text.startsWith("First") }.origin.x)
            assertEquals(-1f, draws.first { it.layout.text.startsWith("Second") }.origin.x)
            ui.theme = ui.theme.copy(buttonText = ButtonTextDefaults(TextOverflow.CLIP, TextReveal.NONE))
            ui.frame(Duration.ZERO)
            assertTrue(backend.lastFrame!!.commands.filterIsInstance<DrawText>().all { it.origin.x == 0f })
        } finally {
            ui.close()
            host.close()
        }
    }

    @Test
    fun `policy-only theme updates preserve full layout measurements and shaping cache`() {
        val host = TestHost.of(400, 240)
        val font = FixedFontTextService.of()
        var shapes = 0
        val text = object : TextService by font {
            override fun layout(request: TextRequest): TextLayout {
                shapes++
                return font.layout(request)
            }
        }
        val ui = ui(host, RecordingBackend.of(), text)
        try {
            ui.theme = Theme.DARK.copy(buttonText = ButtonTextDefaults(TextOverflow.CLIP, TextReveal.NONE))
            val button = ui.components.button()
            val label = button.setLabel("Server name")
            ui.root.add(button)
            ui.frame(Duration.ZERO)
            val before = shapes
            val measures = (label as AbstractElement).measureCount
            ui.theme = Theme.LIGHT.copy(buttonText = ButtonTextDefaults(TextOverflow.VISIBLE, TimedReveal()))
            ui.frame(Duration.ZERO)
            assertEquals(before, shapes)
            assertEquals(measures, label.measureCount)
            assertEquals(0, ui.statistics.measuredNodes)
        } finally {
            ui.close()
            host.close()
        }
    }
}
