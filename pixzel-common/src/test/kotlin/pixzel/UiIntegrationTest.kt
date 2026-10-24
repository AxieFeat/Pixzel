package pixzel

import org.junit.jupiter.api.Test
import pixzel.component.Button
import pixzel.component.ClipPolicy
import pixzel.component.Element
import pixzel.component.Text
import pixzel.dsl.content
import pixzel.dsl.items
import pixzel.graphics.RecordingBackend
import pixzel.graphics.RoundedRectangle
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.Modifiers
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.TextCommit
import pixzel.layout.unit.dp
import pixzel.platform.TestHost
import pixzel.state.bind
import pixzel.state.stateOf
import pixzel.surface.Anchor
import pixzel.surface.Insets
import pixzel.surface.Pivot
import pixzel.surface.Placement
import pixzel.surface.Size
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class UiIntegrationTest {

    @Test
    fun nestedCoordinatesResizeAndStaticFrames() {
        val host = TestHost.of()
        val recording = RecordingBackend.of()
        val text = FixedFontTextService.of()
        val ui = ui(host, recording, text)
        lateinit var title: Text
        ui.content {
            surface("notification") {
                layout {
                    width = 320.dp
                    height = 72.dp
                    padding = Insets.all(12.dp)
                }
                placement { offset(24.dp, 24.dp) }
                title = text("Connection established") {
                    layout {
                        width = 200.dp
                        height = 24.dp
                    }
                    placement {
                        anchor = Anchor.CENTER
                        pivot = Pivot.CENTER
                    }
                }
            }
        }
        ui.frame(Duration.ZERO)
        check(title.surface.toRoot(Vec2.ZERO) == Vec2(84f, 48f))
        val count = text.layoutCount
        ui.frame(16.milliseconds)
        check(ui.statistics.measuredNodes == 0 && ui.statistics.paintedNodes == 0)
        check(text.layoutCount == count)
        host.resize(1920, 1080)
        ui.frame(Duration.ZERO)
        check(title.surface.toRoot(Vec2.ZERO) == Vec2(84f, 48f))
        ui.close()
        check(host.listenerCount == 0 && recording.activeSessions == 0)
        check(runCatching { ui.frame(Duration.ZERO) }.isFailure)
    }

    @Test
    fun pointerCaptureConsumptionRetinaAndKeyboard() {
        val host = TestHost.of(800, 600, 1600, 1200)
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of(), UiSettings(uiScale = 1.25f))
        var clicks = 0
        val button = ui.components.button("action")
        button.layout.width = 100.dp
        button.layout.height = 40.dp
        button.placement = Placement(offset = Vec2(100f, 100f))
        button.onClick { clicks++ }
        button.setLabel("Action")
        ui.root.add(button)
        ui.frame(Duration.ZERO)
        check(ui.root.surface.bounds.size == Size(640f, 480f))
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(130f, 130f)))
        ui.frame(Duration.ZERO)
        check(button.state.pressed && host.isPhysicalButtonDown(0) && !host.isGameplayButtonDown(0))
        host.queue(PointerEvent(PointerAction.UP, Vec2(130f, 130f)))
        ui.frame(Duration.ZERO)
        check(clicks == 1 && !button.state.pressed)
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(130f, 130f)))
        host.queue(PointerEvent(PointerAction.UP, Vec2(600f, 500f)))
        ui.frame(Duration.ZERO)
        check(clicks == 1)
        ui.focus.request(button)
        host.queue(KeyEvent(Key.SPACE, KeyAction.DOWN))
        host.queue(KeyEvent(Key.SPACE, KeyAction.REPEAT))
        host.queue(KeyEvent(Key.SPACE, KeyAction.UP))
        ui.frame(Duration.ZERO)
        check(clicks == 2)
        ui.close()
    }

    @Test
    fun keyedReorderKeepsNodeFocusAndCurrentData() {
        val host = TestHost.of()
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())

        data class Item(val key: Int, val title: String)

        val state = stateOf(listOf(Item(1, "One"), Item(2, "Two")))
        var result = ""
        lateinit var holder: Element
        ui.content {
            holder = items(state, { it.key }) { item ->
                button("button") {
                    label { value(bind { item.value.title }) }
                    onClick { result = item.value.title }
                }
            }
        }
        ui.frame(Duration.ZERO)
        val first = holder.children[0].children[0] as Button
        ui.focus.request(first)
        state.value = listOf(Item(2, "Second"), Item(1, "First"))
        ui.frame(Duration.ZERO)
        check(holder.children[1].children[0] === first && ui.focus.focused === first)
        first.activate()
        check(result == "First")
        ui.close()
    }

    @Test
    fun removeDuringRoutingPreventsDefaultAndClipsMatchHits() {
        val host = TestHost.of()
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val parent = ui.components.surface("clip")
        parent.layout.width = 100.dp
        parent.layout.height = 100.dp
        parent.clipPolicy = ClipPolicy.SHAPE
        parent.clipShape = RoundedRectangle(30f)
        val button = ui.components.button("action")
        button.layout.width = 100.dp
        button.layout.height = 100.dp
        var clicked = 0
        button.onClick { clicked++ }
        parent.add(button)
        ui.root.add(parent)
        ui.frame(Duration.ZERO)
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(1f, 1f)))
        host.queue(PointerEvent(PointerAction.UP, Vec2(1f, 1f)))
        ui.frame(Duration.ZERO)
        check(clicked == 0)
        button.onEvent { if (it.event is PointerEvent && (it.event as PointerEvent).action == PointerAction.UP) parent.remove(button) }
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(50f, 50f)))
        host.queue(PointerEvent(PointerAction.UP, Vec2(50f, 50f)))
        ui.frame(Duration.ZERO)
        check(clicked == 0 && button.isDisposed)
        ui.close()
    }

    @Test
    fun graphemeEditingClipboardUndoAndGeometryChangeBetweenEvents() {
        val host = TestHost.of()
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val field = ui.components.textField("entry")
        ui.root.add(field)
        ui.frame(Duration.ZERO)
        ui.focus.request(field)
        host.queue(TextCommit("A👩‍💻е́"))
        ui.frame(Duration.ZERO)
        check(field.value == "A👩‍💻е́")
        host.queue(KeyEvent(Key.BACKSPACE, KeyAction.DOWN))
        ui.frame(Duration.ZERO)
        check(field.value == "A👩‍💻")
        host.queue(KeyEvent(Key.Z, KeyAction.DOWN, Modifiers(control = true)))
        ui.frame(Duration.ZERO)
        check(field.value == "A👩‍💻е́")
        host.queue(KeyEvent(Key.A, KeyAction.DOWN, Modifiers(control = true)))
        host.queue(KeyEvent(Key.C, KeyAction.DOWN, Modifiers(control = true)))
        ui.frame(Duration.ZERO)
        check(host.readClipboard().getOrThrow() == field.value)
        val button = ui.components.button("move")
        button.placement = Placement(offset = Vec2(300f, 0f))
        var clicks = 0
        button.onClick {
            clicks++
            button.placement = Placement(offset = Vec2(500f, 0f))
        }
        ui.root.add(button)
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(310f, 10f)))
        host.queue(PointerEvent(PointerAction.UP, Vec2(310f, 10f)))
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(510f, 10f)))
        host.queue(PointerEvent(PointerAction.UP, Vec2(510f, 10f)))
        ui.frame(Duration.ZERO)
        check(clicks == 2)
        ui.close()
    }
}
