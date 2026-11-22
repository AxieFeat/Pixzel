package pixzel.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pixzel.UiSettings
import pixzel.graphics.Concat
import pixzel.graphics.DrawText
import pixzel.graphics.Fill
import pixzel.graphics.RecordingBackend
import pixzel.input.Composition
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.layout.unit.dp
import pixzel.layout.unit.sp
import pixzel.platform.TestHost
import pixzel.surface.Insets
import pixzel.surface.Rect
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import pixzel.text.TextStyle
import pixzel.text.TextAlignment
import pixzel.text.TextDirection
import pixzel.text.VerticalAlignment
import pixzel.ui
import kotlin.time.Duration

class TextFieldAlignmentTest {

    @Test
    fun alignsHorizontalInput() {
        val host = TestHost.of()
        val backend = RecordingBackend.of()
        val ui = ui(host, backend, FixedFontTextService.of())
        try {
            val field = ui.components.textField().apply {
                value = "abc"
                layout.width = 100.dp
                layout.height = 40.dp
                layout.padding = Insets.horizontal(10.dp)
            }
            ui.root.add(field)
            for (direction in listOf(TextDirection.LTR, TextDirection.RTL)) {
                field.direction = direction
                for ((alignment, x) in listOf(
                    TextAlignment.LEFT to 10f,
                    TextAlignment.CENTER to 38f,
                    TextAlignment.RIGHT to 66f,
                    TextAlignment.START to if (direction == TextDirection.RTL) 66f else 10f,
                    TextAlignment.END to if (direction == TextDirection.RTL) 10f else 66f
                )) {
                    field.horizontalAlignment = alignment
                    ui.frame(Duration.ZERO)
                    assertEquals(Vec2(x, 12f), backend.lastFrame!!.commands.filterIsInstance<Concat>().last().transform.map(Vec2.ZERO))
                    ui.focus.request(field)
                    field.selectionEnd = 1
                    ui.frame(Duration.ZERO)
                    val caretX = if (direction == TextDirection.RTL) 16f else 8f
                    assertEquals(Rect(x + caretX, 12f, 1f, 16f), host.imeCaret)
                }
            }
            field.value = "a".repeat(30)
            field.direction = TextDirection.LTR
            field.selectionStart = field.value.length
            field.selectionEnd = field.value.length
            ui.frame(Duration.ZERO)
            assertEquals(89f, host.imeCaret!!.x)
            field.selectionEnd = 0
            ui.frame(Duration.ZERO)
            assertEquals(10f, host.imeCaret!!.x)
        } finally {
            ui.close()
            host.close()
        }
    }

    @Test
    fun alignsPlaceholderAndInput() {
        val host = TestHost.of()
        val backend = RecordingBackend.of()
        val ui = ui(host, backend, FixedFontTextService.of())
        try {
            val field = ui.components.textField().apply {
                placeholder = "Поиск игры"
                style = TextStyle(size = 19.sp)
                layout.height = 38.dp
                layout.padding = Insets.horizontal(12.dp)
            }
            ui.root.add(field)
            for (value in listOf("", "Игра")) {
                field.value = value
                for ((alignment, y) in listOf(
                    VerticalAlignment.TOP to 0f,
                    VerticalAlignment.CENTER to 9.5f,
                    VerticalAlignment.BOTTOM to 19f
                )) {
                    field.verticalAlignment = alignment
                    ui.frame(Duration.ZERO)
                    val commands = backend.lastFrame!!.commands
                    val origin = commands.takeWhile { it !is DrawText }.filterIsInstance<Concat>().last().transform
                    assertEquals(Vec2(12f, y), origin.map(Vec2.ZERO))
                }
            }
            field.layout.padding = Insets(12f, 4f, 12f, 2f)
            field.verticalAlignment = VerticalAlignment.CENTER
            ui.frame(Duration.ZERO)
            assertEquals(10.5f, backend.lastFrame!!.commands.filterIsInstance<Concat>().last().transform.ty)
            field.layout.height = 16.dp
            ui.frame(Duration.ZERO)
            assertEquals(4f, backend.lastFrame!!.commands.filterIsInstance<Concat>().last().transform.ty)
        } finally {
            ui.close()
            host.close()
        }
    }

    @Test
    fun alignsEditingCoordinates() {
        val host = TestHost.of()
        val backend = RecordingBackend.of()
        val ui = ui(host, backend, FixedFontTextService.of(), UiSettings(uiScale = 2f))
        try {
            val field = ui.components.textField().apply {
                value = "abc"
                style = TextStyle(size = 16.sp)
                verticalAlignment = VerticalAlignment.CENTER
                layout.height = 40.dp
                layout.padding = Insets.horizontal(12.dp)
                background = null
                foreground = null
            }
            ui.root.add(field)
            ui.frame(Duration.ZERO)
            ui.focus.request(field)
            field.selectionStart = 0
            field.selectionEnd = 2
            ui.frame(Duration.ZERO)
            val fills = backend.lastFrame!!.commands.filterIsInstance<Fill>()
            assertEquals(Rect(0f, 0f, 16f, 16f), fills.first().bounds)
            assertEquals(Rect(16f, 0f, 1f, 16f), fills.last().bounds)
            assertEquals(Rect(56f, 24f, 2f, 32f), host.imeCaret)

            host.queue(Composition("x", 1, 1))
            ui.frame(Duration.ZERO)
            assertEquals(Rect(0f, 15f, 8f, 1f), backend.lastFrame!!.commands.filterIsInstance<Fill>().first().bounds)
            assertEquals(Rect(40f, 24f, 2f, 32f), host.imeCaret)

            host.queue(PointerEvent(PointerAction.DOWN, Vec2(40f, 40f)))
            host.queue(PointerEvent(PointerAction.MOVE, Vec2(56f, 40f)))
            host.queue(PointerEvent(PointerAction.UP, Vec2(56f, 40f)))
            ui.frame(Duration.ZERO)
            assertEquals(1, field.selectionStart)
            assertEquals(2, field.selectionEnd)
        } finally {
            ui.close()
            host.close()
        }
    }
}
