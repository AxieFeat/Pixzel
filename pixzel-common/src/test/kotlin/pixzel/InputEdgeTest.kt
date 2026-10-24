package pixzel

import org.junit.jupiter.api.Test
import pixzel.component.Popup
import pixzel.graphics.RecordingBackend
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.platform.TestHost
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import kotlin.time.Duration

class InputEdgeTest {

    @Test
    fun secondaryReleaseDoesNotActivateOrReleasePrimaryCapture() {
        val host = TestHost.of()
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val button = ui.components.button()
        var clicks = 0
        button.onClick { clicks++ }
        ui.root.add(button)
        ui.frame(Duration.ZERO)
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(10f, 10f), button = 0))
        host.queue(PointerEvent(PointerAction.UP, Vec2(10f, 10f), button = 1))
        ui.frame(Duration.ZERO)
        check(clicks == 0 && button.state.pressed)
        host.queue(PointerEvent(PointerAction.UP, Vec2(1000f, 500f), button = 0))
        ui.frame(Duration.ZERO)
        check(clicks == 0 && !button.state.pressed)
        ui.close()
    }

    @Test
    fun blankOutsideAndUnfocusedEscapeDismissOnlyTopPopup() {
        val host = TestHost.of(800, 600)
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val anchor = ui.components.button()
        ui.root.add(anchor)
        ui.frame(Duration.ZERO)
        val one = Popup.of(ui, anchor).also { it.content { text("One") } }
        val two = Popup.of(ui, anchor).also { it.content { text("Two") } }
        ui.frame(Duration.ZERO)
        ui.focus.clear()
        host.queue(KeyEvent(Key.ESCAPE, KeyAction.DOWN))
        ui.frame(Duration.ZERO)
        check(two.element.isDisposed && !one.element.isDisposed)
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(790f, 590f)))
        ui.frame(Duration.ZERO)
        check(one.element.isDisposed)
        val detached = Popup.of(ui, anchor).also { it.content { text("Detached") } }
        ui.frame(Duration.ZERO)
        ui.root.remove(anchor, false)
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(790f, 590f)))
        ui.frame(Duration.ZERO)
        check(!detached.element.isDisposed)
        anchor.close()
        ui.close()
    }
}
