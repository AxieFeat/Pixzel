package pixzel.demo.cristalix

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pixzel.component.Button
import pixzel.component.Text
import pixzel.graphics.RecordingBackend
import pixzel.layout.unit.dp
import pixzel.platform.TestHost
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import pixzel.text.TextAlignment
import pixzel.text.VerticalAlignment
import pixzel.ui
import kotlin.time.Duration

class MenuTextAlignmentTest {

    @Test
    fun usesNativeTextLayout() {
        val host = TestHost.of(800, 600)
        ui(host, RecordingBackend.of(), FixedFontTextService.of()).use { ui ->
            val button = ui.pixelButton("test", "Избранное")
            button.layout.width = 136.dp
            button.layout.height = 38.dp
            ui.root.add(button)
            ui.frame(Duration.ZERO)
            val text = button.content as Text
            assertEquals(TextAlignment.CENTER, text.horizontalAlignment)
            assertEquals(VerticalAlignment.CENTER, text.verticalAlignment)
            assertEquals(118f, text.surface.bounds.width)
            assertEquals(38f, text.surface.bounds.height)
            button.title = "Отзывы"
            assertEquals("Отзывы", text.value)
            assertEquals("Отзывы", button.semantics.label)
            val close = ui.pixelButton("close", "×", PixelButtonStyle.REVIEW).apply {
                layout.width = 21.dp
                layout.height = 21.dp
            }
            ui.root.add(close)
            ui.frame(Duration.ZERO)
            assertEquals(15f, close.content!!.surface.bounds.width)

        }
        host.close()
    }

    @Test
    fun composesPopupRows() {
        val host = TestHost.of(800, 600)
        ui(host, RecordingBackend.of(), FixedFontTextService.of()).use { ui ->
            val popup = ChoicePopup(ui)
            popup.root.layout.width = 166.dp
            popup.root.layout.height = 144.dp
            ui.root.add(popup.root)
            var selected = ""
            popup.show(listOf("First", "Second", "Third"), "Second", Vec2.ZERO, 166f) { selected = it }
            ui.frame(Duration.ZERO)
            assertTrue(popup.root.children.all { it is Button })
            assertTrue(popup.root.children.all { it.surface.bounds.height == 48f })
            popup.move(1)
            popup.choose()
            assertEquals("Third", selected)
            assertTrue(!popup.open)
        }
        host.close()
    }
}
