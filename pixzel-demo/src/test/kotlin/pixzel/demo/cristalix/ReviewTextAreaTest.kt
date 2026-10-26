package pixzel.demo.cristalix

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import pixzel.graphics.RecordingBackend
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.Modifiers
import pixzel.input.TextCommit
import pixzel.layout.unit.dp
import pixzel.platform.TestHost
import pixzel.text.FixedFontTextService
import pixzel.ui
import kotlin.time.Duration

class ReviewTextAreaTest {

    @Test
    fun editsAndRestoresMultiline() {
        val host = TestHost.of()
        ui(host, RecordingBackend.of(), FixedFontTextService.of()).use { ui ->
            val field = ReviewTextArea(MenuModel()).apply {
                layout.width = 300.dp
                layout.height = 150.dp
            }
            ui.root.add(field)
            ui.frame(Duration.ZERO)
            ui.focus.request(field)
            host.queue(TextCommit("Первая строка"))
            host.queue(KeyEvent(Key.ENTER, KeyAction.DOWN))
            host.queue(TextCommit("Вторая строка"))
            ui.frame(Duration.ZERO)
            assertEquals("Первая строка\nВторая строка", field.value)
            host.queue(KeyEvent(Key.A, KeyAction.DOWN, Modifiers(control = true)))
            host.queue(TextCommit("Замена"))
            ui.frame(Duration.ZERO)
            assertEquals("Замена", field.value)
            host.queue(KeyEvent(Key.Z, KeyAction.DOWN, Modifiers(control = true)))
            ui.frame(Duration.ZERO)
            assertEquals("Первая строка\nВторая строка", field.value)
            host.queue(KeyEvent(Key.Y, KeyAction.DOWN, Modifiers(control = true)))
            ui.frame(Duration.ZERO)
            assertEquals("Замена", field.value)
        }
        host.close()
    }

    @Test
    fun keepsWholeGraphemes() {
        val field = ReviewTextArea(MenuModel())
        field.insert("a".repeat(499))
        field.insert("👩‍💻")
        assertEquals(499, field.value.length)
        field.insert("b")
        field.insert("c")
        assertEquals("a".repeat(499) + "b", field.value)
        assertFalse(field.value.any { it.isSurrogate() })
        field.close()
    }

    @Test
    fun deletesAndPastesGraphemes() {
        val host = TestHost.of()
        ui(host, RecordingBackend.of(), FixedFontTextService.of()).use { ui ->
            val field = ReviewTextArea(MenuModel()).apply {
                layout.width = 300.dp
                layout.height = 150.dp
            }
            ui.root.add(field)
            ui.frame(Duration.ZERO)
            ui.focus.request(field)
            host.queue(TextCommit("a👩‍💻"))
            host.queue(KeyEvent(Key.BACKSPACE, KeyAction.DOWN))
            ui.frame(Duration.ZERO)
            assertEquals("a", field.value)
            host.writeClipboard("\nВставка\nтекста")
            host.queue(KeyEvent(Key.V, KeyAction.DOWN, Modifiers(control = true)))
            ui.frame(Duration.ZERO)
            assertEquals("a\nВставка\nтекста", field.value)
        }
        host.close()
    }
}
