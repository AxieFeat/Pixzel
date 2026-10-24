package pixzel.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pixzel.Ui
import pixzel.graphics.RecordingBackend
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.WheelEvent
import pixzel.input.WindowFocus
import pixzel.layout.unit.dp
import pixzel.platform.TestHost
import pixzel.surface.Placement
import pixzel.surface.Rect
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import pixzel.ui
import kotlin.time.Duration

class ScrollBarTest {
    private class Fixture : AutoCloseable {

        val host = TestHost.of(500, 250)
        val ui: Ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val view = ScrollView.of(ui.components).also {
            it.layout.width = 200.dp
            it.layout.height = 100.dp
        }
        val content = ui.components.surface().also {
            it.layout.width = 160.dp
            it.layout.height = 500.dp
        }
        val bar = ScrollBar.of(ui.components).also {
            it.layout.height = 100.dp
            it.placement = Placement(offset = Vec2(210f, 0f))
            it.view = view
        }

        init {
            view.add(content)
            ui.root.add(view)
            ui.root.add(bar)
            frame()
        }

        fun frame() = ui.frame(Duration.ZERO)
        fun pointer(action: PointerAction, y: Float, button: Int = 0, x: Float = 217f) {
            host.queue(PointerEvent(action, Vec2(x, y), button = button))
            frame()
        }

        fun key(key: Key, action: KeyAction = KeyAction.DOWN) {
            host.queue(KeyEvent(key, action))
            frame()
        }

        override fun close() {
            ui.close()
            host.close()
        }
    }

    @Test
    fun `thumb derives from viewport extent and updates without remeasuring the bar`() {
        Fixture().use { f ->
            assertTrue(f.bar.canScroll)
            assertEquals(Rect(2f, 0f, 10f, 20f), f.bar.thumbBounds)
            val measured = (f.bar as AbstractElement).measureCount
            f.view.scrollOffset = Vec2(0f, 200f)
            f.frame()
            assertEquals(Rect(2f, 40f, 10f, 20f), f.bar.thumbBounds)
            assertEquals(measured, f.bar.measureCount)
            assertEquals("200.0", f.bar.semantics.value)
            f.content.layout.height = 100.dp
            f.frame()
            assertFalse(f.bar.canScroll)
            assertEquals(100f, f.bar.thumbBounds.height)
            assertEquals(Vec2.ZERO, f.view.scrollOffset)
        }
    }

    @Test
    fun `primary thumb capture drags beyond bounds secondary release does not end drag`() {
        Fixture().use { f ->
            f.pointer(PointerAction.DOWN, 10f, button = 1)
            assertTrue(f.ui.capturedPointers.isEmpty())
            assertEquals(Vec2.ZERO, f.view.scrollOffset)
            f.pointer(PointerAction.DOWN, 10f)
            assertSame(f.bar, f.ui.capturedPointers[0])
            assertTrue(f.bar.state.dragging)
            f.pointer(PointerAction.UP, 10f, button = 1)
            assertTrue(f.bar.state.dragging)
            assertSame(f.bar, f.ui.capturedPointers[0])
            f.pointer(PointerAction.MOVE, 400f)
            assertEquals(400f, f.view.scrollOffset.y)
            f.pointer(PointerAction.UP, 400f)
            assertFalse(f.bar.state.dragging)
            assertTrue(f.ui.capturedPointers.isEmpty())
            f.key(Key.HOME)
            f.pointer(PointerAction.DOWN, 10f)
            f.host.queue(WindowFocus(false))
            f.frame()
            assertFalse(f.bar.state.dragging)
            assertTrue(f.ui.capturedPointers.isEmpty())
        }
    }

    @Test
    fun `track pages keyboard repeats and wheel clamp at both ends and disabled blocks input`() {
        Fixture().use { f ->
            f.pointer(PointerAction.DOWN, 80f)
            f.pointer(PointerAction.UP, 80f)
            assertEquals(100f, f.view.scrollOffset.y)
            f.key(Key.END)
            assertEquals(400f, f.view.scrollOffset.y)
            f.key(Key.UP)
            f.key(Key.UP, KeyAction.REPEAT)
            assertEquals(336f, f.view.scrollOffset.y)
            f.key(Key.HOME)
            assertEquals(0f, f.view.scrollOffset.y)
            f.host.queue(WheelEvent(Vec2(217f, 10f), Vec2(0f, -2f)))
            f.frame()
            assertEquals(64f, f.view.scrollOffset.y)
            f.bar.enabled = false
            f.pointer(PointerAction.DOWN, 80f)
            f.pointer(PointerAction.UP, 80f)
            f.key(Key.END)
            assertEquals(64f, f.view.scrollOffset.y)
            assertFalse(f.bar.canScroll)
        }
    }

    @Test
    fun `horizontal bar clamps oversized thumb and detached or incompatible views are inert`() {
        Fixture().use { f ->
            f.view.axes = ScrollAxes.HORIZONTAL
            f.view.layout.width = 100.dp
            f.view.layout.height = 80.dp
            f.content.layout.width = 500.dp
            f.content.layout.height = 50.dp
            f.bar.orientation = ScrollBarOrientation.HORIZONTAL
            f.bar.layout.width = 100.dp
            f.bar.layout.height = 14.dp
            f.bar.placement = Placement(offset = Vec2(0f, 90f))
            f.frame()
            assertEquals(Rect(0f, 2f, 20f, 10f), f.bar.thumbBounds)
            f.pointer(PointerAction.DOWN, 97f, x = 10f)
            f.pointer(PointerAction.MOVE, 97f, x = 90f)
            f.pointer(PointerAction.UP, 97f, x = 90f)
            assertEquals(400f, f.view.scrollOffset.x)
            f.bar.minThumbSize = 300f
            f.frame()
            assertEquals(100f, f.bar.thumbBounds.width)
            f.key(Key.HOME)
            assertEquals(0f, f.view.scrollOffset.x)
            f.view.axes = ScrollAxes.VERTICAL
            f.frame()
            assertFalse(f.bar.canScroll)
            f.view.axes = ScrollAxes.HORIZONTAL
            f.ui.root.remove(f.view)
            f.frame()
            assertFalse(f.bar.canScroll)
            assertEquals(100f, f.bar.thumbBounds.width)
            f.bar.view = null
            f.frame()
            assertFalse(f.bar.canScroll)
            assertThrows(IllegalArgumentException::class.java) { f.bar.lineStep = Float.NaN }
            assertThrows(IllegalArgumentException::class.java) { f.bar.minThumbSize = -1f }
        }
    }
}
