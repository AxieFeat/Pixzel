package pixzel.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pixzel.Ui
import pixzel.UiSettings
import pixzel.graphics.Fill
import pixzel.graphics.RecordingBackend
import pixzel.graphics.group
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.WindowFocus
import pixzel.platform.TestHost
import pixzel.surface.Placement
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import pixzel.ui
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class SelectionValueTest {
    private class Fixture : AutoCloseable {

        val host = TestHost.of(640, 360)
        val backend = RecordingBackend.of()
        val ui: Ui = ui(host, backend, FixedFontTextService.of())
        fun <T : Element> mount(element: T, y: Float = 0f): T {
            element.placement = Placement(offset = Vec2(0f, y))
            ui.root.add(element)
            return element
        }

        fun frame() = ui.frame(Duration.ZERO)
        fun click(x: Float = 20f, y: Float = 18f) {
            host.queue(PointerEvent(PointerAction.DOWN, Vec2(x, y)))
            host.queue(PointerEvent(PointerAction.UP, Vec2(x, y)))
            frame()
        }

        fun key(key: Key) {
            host.queue(KeyEvent(key, KeyAction.DOWN))
            host.queue(KeyEvent(key, KeyAction.UP))
            frame()
        }

        override fun close() {
            ui.close()
            host.close()
        }
    }

    @Test
    fun `checkbox and toggle support pointer keyboard mixed state disabled and subscriptions`() {
        Fixture().use { f ->
            val checkbox = f.mount(Checkbox.of(f.ui.components))
            checkbox.setLabel("Enable sound")
            var changes = 0
            val subscription = checkbox.onCheckChange { changes++ }
            f.frame()
            f.click()
            assertEquals(CheckState.CHECKED, checkbox.checkState)
            assertEquals(true, checkbox.semantics.checked)
            f.key(Key.SPACE)
            assertEquals(CheckState.UNCHECKED, checkbox.checkState)
            checkbox.checkState = CheckState.INDETERMINATE
            assertTrue(checkbox.state.indeterminate)
            assertNull(checkbox.semantics.checked)
            f.key(Key.ENTER)
            assertEquals(CheckState.CHECKED, checkbox.checkState)
            checkbox.enabled = false
            f.click()
            assertEquals(CheckState.CHECKED, checkbox.checkState)
            subscription.close()
            subscription.close()
            val before = changes
            checkbox.checkState = CheckState.UNCHECKED
            assertEquals(before, changes)
            val toggle = f.mount(Toggle.of(f.ui.components), 60f)
            toggle.setLabel("Notifications")
            f.frame()
            f.click(y = 78f)
            assertTrue(toggle.checked)
            f.key(Key.SPACE)
            assertFalse(toggle.checked)
        }
    }

    @Test
    fun `radio group roves over enabled members and replaces a removed selection`() {
        Fixture().use { f ->
            val group = radioGroup()
            val first = f.mount(Radio.of(f.ui.components), 0f).also { it.group = group }
            val disabled = f.mount(Radio.of(f.ui.components), 45f).also {
                it.enabled = false
                it.group = group
            }
            val last = f.mount(Radio.of(f.ui.components), 90f).also { it.group = group }
            f.frame()
            assertSame(first, group.selected)
            assertTrue(first.focusable)
            assertFalse(disabled.focusable)
            assertFalse(last.focusable)
            assertTrue(f.ui.focus.request(first))
            f.key(Key.RIGHT)
            assertSame(last, group.selected)
            assertSame(last, f.ui.focus.focused)
            assertFalse(first.selected)
            assertTrue(last.selected)
            assertTrue(last.focusable)
            assertFalse(first.focusable)
            f.ui.root.remove(last)
            assertSame(first, group.selected)
            assertEquals(2, group.members.size)
            assertThrows(IllegalArgumentException::class.java) { group.select(null) }
            group.allowNone = true
            group.select(null)
            assertFalse(first.selected)
            assertNull(group.selected)
        }
    }

    @Test
    fun `radio reentrant selection callbacks keep one actual selected member`() {
        Fixture().use { f ->
            val group = radioGroup()
            val first = f.mount(Radio.of(f.ui.components)).also { it.group = group }
            val second = f.mount(Radio.of(f.ui.components)).also { it.group = group }
            val third = f.mount(Radio.of(f.ui.components)).also { it.group = group }
            second.onSelectedChange { if (it) group.select(third) }
            group.select(second)
            assertSame(third, group.selected)
            assertEquals(listOf(false, false, true), listOf(first.selected, second.selected, third.selected))
        }
    }

    @Test
    fun `slider range clamps snaps and keeps explicit endpoints for a nondividing step`() {
        Fixture().use { f ->
            val slider = Slider.of(f.ui.components)
            slider.setRange(-10f, 10f)
            slider.step = 3f
            slider.value = -4.8f
            assertEquals(-4f, slider.value)
            slider.value = 100f
            assertEquals(10f, slider.value)
            slider.value = -100f
            assertEquals(-10f, slider.value)
            slider.setRange(4f, 4f)
            slider.value = 9f
            assertEquals(4f, slider.value)
            assertThrows(IllegalArgumentException::class.java) { slider.setRange(2f, 1f) }
            assertThrows(IllegalArgumentException::class.java) { slider.step = -1f }
            assertThrows(IllegalArgumentException::class.java) { slider.value = Float.NaN }
            slider.setRange(-Float.MAX_VALUE, Float.MAX_VALUE)
            slider.step = 0f
            slider.value = 0f
            assertTrue(slider.value.isFinite())
            slider.close()
        }
    }

    @Test
    fun `slider capture drag keyboard repeat and focus loss have observable effects`() {
        Fixture().use { f ->
            val slider = f.mount(Slider.of(f.ui.components))
            slider.setRange(0f, 100f)
            slider.step = 10f
            var calls = 0
            val subscription = slider.onValueChange { calls++ }
            f.frame()
            f.host.queue(PointerEvent(PointerAction.DOWN, Vec2(100f, 16f)))
            f.host.queue(PointerEvent(PointerAction.MOVE, Vec2(400f, 16f)))
            f.host.queue(PointerEvent(PointerAction.UP, Vec2(400f, 16f)))
            f.frame()
            assertEquals(100f, slider.value)
            assertFalse(slider.state.dragging)
            assertTrue(calls > 0)
            f.key(Key.HOME)
            assertEquals(0f, slider.value)
            f.host.queue(KeyEvent(Key.RIGHT, KeyAction.DOWN))
            f.host.queue(KeyEvent(Key.RIGHT, KeyAction.REPEAT))
            f.host.queue(KeyEvent(Key.RIGHT, KeyAction.UP))
            f.frame()
            assertEquals(20f, slider.value)
            f.key(Key.END)
            assertEquals(100f, slider.value)
            f.host.queue(PointerEvent(PointerAction.DOWN, Vec2(80f, 16f)))
            f.frame()
            assertTrue(slider.state.dragging)
            f.host.queue(WindowFocus(false))
            f.frame()
            assertFalse(slider.state.dragging)
            subscription.close()
        }
    }

    @Test
    fun `indeterminate progress moves paint only and honors reduced motion`() {
        Fixture().use { f ->
            val progress = f.mount(Progress.of(f.ui.components))
            progress.indeterminate = true
            f.frame()
            val initial = f.backend.lastFrame!!.commands.filterIsInstance<Fill>().last().bounds
            val measured = (progress as AbstractElement).measureCount
            f.ui.frame(400.milliseconds)
            val moved = f.backend.lastFrame!!.commands.filterIsInstance<Fill>().last().bounds
            assertTrue(moved.x > initial.x)
            assertEquals(measured, progress.measureCount)
            f.ui.settings = UiSettings(isReducedMotion = true)
            f.frame()
            val reduced = f.backend.lastFrame!!.commands.filterIsInstance<Fill>().last().bounds
            f.ui.frame(400.milliseconds)
            assertEquals(reduced, f.backend.lastFrame!!.commands.filterIsInstance<Fill>().last().bounds)
            progress.setRange(2f, 2f)
            progress.indeterminate = false
            assertEquals(2f, progress.value)
        }
    }
}
