package pixzel

import org.junit.jupiter.api.Test
import pixzel.component.ClipPolicy
import pixzel.component.Modal
import pixzel.graphics.RecordingBackend
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.layout.unit.dp
import pixzel.platform.TestHost
import pixzel.surface.Placement
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import kotlin.time.Duration

class OverlayTest {

    @Test
    fun modalTrapsAndRestoresFocusConsumesBlankAndOutside() {
        val host = TestHost.of(800, 600)
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val first = ui.components.button("outside")
        ui.root.add(first)
        ui.frame(Duration.ZERO)
        ui.focus.request(first)
        val modal = Modal.of(ui)
        val inside = ui.components.button("inside")
        modal.body.add(inside)
        ui.focus.request(inside)
        ui.frame(Duration.ZERO)
        check(!ui.focus.request(first))
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(790f, 590f)))
        host.queue(PointerEvent(PointerAction.UP, Vec2(790f, 590f)))
        ui.frame(Duration.ZERO)
        check(host.gameplayEvents.isEmpty())
        host.queue(KeyEvent(Key.ESCAPE, KeyAction.DOWN))
        ui.frame(Duration.ZERO)
        check(modal.element.isDisposed && ui.focus.focused === first)
        ui.close()
    }

    @Test
    fun portalEscapesOriginalClipButKeepsLogicalRouteAndCurrentTarget() {
        val host = TestHost.of(800, 600)
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val source = ui.components.surface("source").apply {
            layout.width = 20.dp
            layout.height = 20.dp
            clipPolicy = ClipPolicy.BOUNDS
        }
        val later = ui.components.surface("overlay").apply {
            layout.width = 400.dp
            layout.height = 300.dp
            placement = Placement(offset = Vec2(200f, 100f))
        }
        val portal = ui.components.button("portal").apply {
            layout.width = 100.dp
            layout.height = 40.dp
            portalTarget = later
            placement = Placement.center()
        }
        source.add(portal)
        ui.root.add(source)
        ui.root.add(later)
        var sourceSaw = false
        source.onEvent { if (it.event is PointerEvent) sourceSaw = true }
        var clicks = 0
        portal.onClick { clicks++ }
        ui.frame(Duration.ZERO)
        check(portal.surface.toRoot(Vec2.ZERO) == Vec2(350f, 230f))
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(360f, 240f)))
        host.queue(PointerEvent(PointerAction.UP, Vec2(360f, 240f)))
        ui.frame(Duration.ZERO)
        check(clicks == 1 && sourceSaw)
        later.placement = Placement(offset = Vec2(100f, 50f))
        ui.frame(Duration.ZERO)
        check(portal.surface.toRoot(Vec2.ZERO) == Vec2(250f, 180f))
        ui.close()
    }
}
