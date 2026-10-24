package pixzel

import org.junit.jupiter.api.Test
import pixzel.graphics.RecordingBackend
import pixzel.input.Composition
import pixzel.input.DragDropManager
import pixzel.input.DragType
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.WindowFocus
import pixzel.platform.TestHost
import pixzel.surface.Placement
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import kotlin.time.Duration

class PlatformBehaviorTest {

    @Test
    fun compositionPreeditIsSeparateAndCancellationFocusLossAndCommitWork() {
        val host = TestHost.of()
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val field = ui.components.textField()
        field.value = "ab"
        ui.root.add(field)
        ui.frame(Duration.ZERO)
        ui.focus.request(field)
        field.selectionStart = 1
        field.selectionEnd = 1
        host.queue(Composition("е́", 2, 2))
        ui.frame(Duration.ZERO)
        check(field.value == "ab" && field.composition?.preedit == "е́" && host.imeCaret != null)
        host.queue(Composition("е́", 2, 2, cancelled = true))
        ui.frame(Duration.ZERO)
        check(field.value == "ab" && field.composition == null)
        host.queue(Composition("Ж", 1, 1))
        host.queue(Composition("Ж", 1, 1, committed = true))
        ui.frame(Duration.ZERO)
        check(field.value == "aЖb" && field.composition == null)
        host.queue(Composition("x", 1, 1))
        host.queue(WindowFocus(false))
        ui.frame(Duration.ZERO)
        check(field.value == "aЖb" && field.composition == null)
        ui.close()
    }

    @Test
    fun typedDragThresholdDropCancelAndKeyboardShareAcceptance() {
        val host = TestHost.of()
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val source = ui.components.button("source")
        val target = ui.components.button("target").apply { placement = Placement(offset = Vec2(300f, 0f)) }
        ui.root.add(source)
        ui.root.add(target)
        val type = DragType<String>("item")
        val manager = DragDropManager(ui)
        var dropped = ""
        var cancelled = false
        manager.target(target, type, { it.startsWith("ok") }) { dropped = it }
        manager.source(source, type, { "ok-value" }, preview = { ui.components.text().apply { value = it } }, complete = { cancelled = it.cancelled })
        ui.frame(Duration.ZERO)
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(10f, 10f)))
        host.queue(PointerEvent(PointerAction.MOVE, Vec2(20f, 10f)))
        host.queue(PointerEvent(PointerAction.MOVE, Vec2(310f, 10f)))
        host.queue(PointerEvent(PointerAction.UP, Vec2(310f, 10f)))
        ui.frame(Duration.ZERO)
        check(dropped == "ok-value" && !source.state.dragging)
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(10f, 10f)))
        host.queue(PointerEvent(PointerAction.MOVE, Vec2(20f, 10f)))
        host.queue(WindowFocus(false))
        ui.frame(Duration.ZERO)
        check(cancelled)
        check(manager.perform(source, target, type, "ok-keyboard"))
        check(dropped == "ok-keyboard")
        check(!manager.perform(source, target, type, "bad"))
        ui.close()
    }
}
