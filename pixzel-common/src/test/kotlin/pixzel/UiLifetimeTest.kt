package pixzel

import org.junit.jupiter.api.Test
import pixzel.component.AbstractElement
import pixzel.component.ClipPolicy
import pixzel.component.Element
import pixzel.component.Layer
import pixzel.component.Modal
import pixzel.component.Painter
import pixzel.component.Visibility
import pixzel.graphics.RecordingBackend
import pixzel.graphics.Rectangle
import pixzel.graphics.SoftwareBackend
import pixzel.graphics.Solid
import pixzel.graphics.group
import pixzel.graphics.rgba
import pixzel.input.InputBehavior
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.WheelEvent
import pixzel.layout.unit.dp
import pixzel.platform.TestHost
import pixzel.state.bind
import pixzel.state.stateOf
import pixzel.surface.Placement
import pixzel.surface.Size
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class UiLifetimeTest {

    @Test
    fun exceptionalCancelStillClosesEveryOwnedResource() {
        val host = TestHost.of()
        val recording = RecordingBackend.of()
        val ui = ui(host, recording, FixedFontTextService.of())
        val button = ui.components.button("captured")
        val original = checkNotNull(button.behavior)
        var resourceClosed = false
        button.resources.own(AutoCloseable { resourceClosed = true })
        button.behavior = InputBehavior { element, event ->
            if ((event.event as? PointerEvent)?.action == PointerAction.CANCEL) error("cancel failure")
            original.handle(element, event)
        }
        ui.root.add(button)
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(10f, 10f)))
        ui.frame(Duration.ZERO)
        check(button.state.pressed)
        check(runCatching { ui.close() }.exceptionOrNull()?.message == "cancel failure")
        check(ui.lifecycle == UiLifecycle.CLOSED && button.isDisposed && resourceClosed)
        check(host.listenerCount == 0 && recording.activeSessions == 0)
        ui.close()
    }

    @Test
    fun closeInActivationExitsTheCurrentFrame() {
        val host = TestHost.of()
        val recording = RecordingBackend.of()
        val ui = ui(host, recording, FixedFontTextService.of())
        val button = ui.components.button()
        button.onClick { ui.close() }
        ui.root.add(button)
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(10f, 10f)))
        host.queue(PointerEvent(PointerAction.UP, Vec2(10f, 10f)))
        ui.frame(Duration.ZERO)
        check(ui.lifecycle == UiLifecycle.CLOSED && recording.frames.isEmpty())
        check(host.listenerCount == 0 && recording.activeSessions == 0)
    }

    @Test
    fun disableAndDetachCancelCaptureAndFocus() {
        val host = TestHost.of()
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val button = ui.components.button()
        var cancelled = 0
        val original = checkNotNull(button.behavior)
        button.behavior = InputBehavior { element, event ->
            if ((event.event as? PointerEvent)?.action == PointerAction.CANCEL) cancelled++
            original.handle(element, event)
        }
        ui.root.add(button)
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(10f, 10f)))
        ui.frame(Duration.ZERO)
        button.enabled = false
        ui.frame(Duration.ZERO)
        check(cancelled == 1 && !button.state.pressed && ui.focus.focused == null)
        button.enabled = true
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(10f, 10f)))
        ui.frame(Duration.ZERO)
        ui.root.remove(button, false)
        check(cancelled == 2 && !button.state.pressed && ui.focus.focused == null)
        ui.root.add(button)
        ui.frame(Duration.ZERO)
        check(!button.state.pressed)
        ui.close()
    }

    @Test
    fun secretPointerAndReplacedValueUseSourceGraphemeBoundaries() {
        val host = TestHost.of()
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val field = ui.components.textField()
        field.value = "👩‍💻A"
        field.password = true
        ui.root.add(field)
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(16f, 10f)))
        host.queue(PointerEvent(PointerAction.UP, Vec2(16f, 10f)))
        ui.frame(Duration.ZERO)
        check(field.selectionEnd == 5) { "First password bullet did not map to the emoji's source end." }
        check(field.semantics.label.isEmpty() && field.semantics.value.isEmpty())
        host.queue(KeyEvent(Key.BACKSPACE, KeyAction.DOWN))
        ui.frame(Duration.ZERO)
        check(field.value == "A")
        field.password = false
        field.value = "ab"
        field.selectionStart = 1
        field.selectionEnd = 1
        field.value = "👩‍💻A"
        check(field.selectionStart == 0 && field.selectionEnd == 0)
        host.queue(KeyEvent(Key.BACKSPACE, KeyAction.DOWN))
        ui.frame(Duration.ZERO)
        check(field.value == "👩‍💻A")
        val source = stateOf("ab")
        field.bindValue(bind { source.value })
        field.selectionStart = 1
        field.selectionEnd = 1
        source.value = "👩‍💻A"
        check(field.selectionEnd == 0)
        ui.close()
    }

    @Test
    fun paintCannotMutateTheTreeAndFailureLeavesTheFrameRecoverable() {
        val host = TestHost.of()
        val recording = RecordingBackend.of()
        val ui = ui(host, recording, FixedFontTextService.of())
        val element = ui.components.surface()
        element.layout.width = 20.dp
        element.layout.height = 20.dp
        ui.root.add(element)
        ui.frame(Duration.ZERO)
        val placement = element.placement
        element.background = Painter { _, _ -> element.placement = Placement.center() }
        check(runCatching { ui.frame(Duration.ZERO) }.exceptionOrNull()?.message?.contains("during paint") == true)
        check(element.placement == placement && recording.frames.size == 1)
        element.background = null
        ui.frame(Duration.ZERO)
        check(recording.frames.size == 2)
        ui.close()
    }

    @Test
    fun nestedScrollPassesOnlyTheUnusedWheelRemainder() {
        val host = TestHost.of()
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val outer = ui.components.scrollView("outer").apply {
            layout.width = 200.dp
            layout.height = 100.dp
        }
        val inner = ui.components.scrollView("inner").apply {
            layout.width = 200.dp
            layout.height = 60.dp
        }
        inner.add(ui.components.surface("long-inner").apply {
            layout.width = 200.dp
            layout.height = 200.dp
        })
        outer.add(inner)
        outer.add(ui.components.surface("long-outer").apply {
            layout.width = 200.dp
            layout.height = 200.dp
        })
        ui.root.add(outer)
        host.queue(WheelEvent(Vec2(10f, 10f), Vec2(0f, -1f)))
        ui.frame(Duration.ZERO)
        check(inner.scrollOffset.y == 32f && outer.scrollOffset.y == 0f)
        inner.scrollOffset = Vec2(0f, 140f)
        host.queue(WheelEvent(Vec2(10f, 10f), Vec2(0f, -1f)))
        ui.frame(Duration.ZERO)
        check(inner.scrollOffset.y == 140f && outer.scrollOffset.y == 32f)
        ui.close()
    }

    @Test
    fun geometryCreatedByUpdateIsArrangedBeforeItPaints() {
        val host = TestHost.of()
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        var advanced = Duration.ZERO
        var added: Element? = null
        val creator = object : AbstractElement("creator") {
            override fun update(delta: Duration): Boolean {
                advanced += delta
                if (added == null) {
                    added = ui.components.surface("created").apply {
                        layout.width = 40.dp
                        layout.height = 30.dp
                    }
                    add(checkNotNull(added))
                }
                return false
            }
        }
        creator.layout.width = 100.dp
        creator.layout.height = 100.dp
        ui.root.add(creator)
        ui.frame(16.milliseconds)
        check(added?.surface?.bounds?.size == Size(40f, 30f))
        check(advanced == 16.milliseconds)
        ui.close()
    }

    @Test
    fun portalTargetsLaterSiblingAndEscapesLogicalClipWithLogicalRouting() {
        val host = TestHost.of(320, 240)
        val software = SoftwareBackend.of()
        val ui = ui(host, software, FixedFontTextService.of())
        val owner = ui.components.surface("logical-owner").apply {
            layout.width = 40.dp
            layout.height = 40.dp
            placement = Placement(offset = Vec2(20f, 0f))
            clipPolicy = ClipPolicy.BOUNDS
        }
        val target = ui.components.surface("later-target").apply {
            layout.width = 100.dp
            layout.height = 100.dp
            placement = Placement(offset = Vec2(150f, 0f))
            layer = Layer.OVERLAY
        }
        val portal = ui.components.button("portal").apply {
            layout.width = 20.dp
            layout.height = 20.dp
            portalTarget = target
            placement = Placement(offset = Vec2(10f, 10f))
            background = Painter { canvas, context -> canvas.fill(Rectangle, context.bounds, Solid(0xFF0000FF.rgba)) }
        }
        owner.add(portal)
        ui.root.add(owner)
        ui.root.add(target)
        var clicks = 0
        var logicalRoutes = 0
        var geometricRoutes = 0
        owner.onEvent {
            if (it.event is PointerEvent) {
                check(it.localPosition == Vec2(145f, 15f))
                logicalRoutes++
            }
        }
        target.onEvent { geometricRoutes++ }
        portal.onClick { clicks++ }
        ui.frame(Duration.ZERO)
        check(portal.surface.toRoot(Vec2.ZERO) == Vec2(160f, 10f))
        check(checkNotNull(software.lastImage).getRGB(165, 15) == 0xFFFF0000.toInt())
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(165f, 15f)))
        host.queue(PointerEvent(PointerAction.UP, Vec2(165f, 15f)))
        ui.frame(Duration.ZERO)
        check(clicks == 1 && logicalRoutes == 2 && geometricRoutes == 0)
        owner.visibility = Visibility.HIDDEN
        ui.frame(Duration.ZERO)
        check(checkNotNull(software.lastImage).getRGB(165, 15) == 0)
        ui.close()
    }

    @Test
    fun portalGeometryCyclesAreDiagnosedBeforePainting() {
        val host = TestHost.of()
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val a = ui.components.surface("a")
        val b = ui.components.surface("b")
        ui.root.add(a)
        ui.root.add(b)
        a.portalTarget = b
        b.portalTarget = a
        check(runCatching { ui.frame(Duration.ZERO) }.exceptionOrNull()?.message?.contains("geometry cycle") == true)
        ui.close()
    }

    @Test
    fun opacityWrapsAComposedSubtree() {
        val host = TestHost.of(32, 24)
        val software = SoftwareBackend.of()
        val ui = ui(host, software, FixedFontTextService.of())
        ui.root.background = Painter { canvas, context -> canvas.fill(Rectangle, context.bounds, Solid(0x0000FFFF.rgba)) }
        val group = ui.components.surface("group").apply {
            layout.width = 32.dp
            layout.height = 24.dp
            opacity = .5f
        }
        for (x in listOf(2f, 6f)) group.add(ui.components.surface().apply {
            layout.width = 12.dp
            layout.height = 12.dp
            placement = Placement(offset = Vec2(x, 2f))
            background = Painter { canvas, context -> canvas.fill(Rectangle, context.bounds, Solid(0xFF0000FF.rgba)) }
        })
        ui.root.add(group)
        ui.frame(Duration.ZERO)
        val image = checkNotNull(software.lastImage)
        check(image.getRGB(3, 3) == image.getRGB(7, 3))
        ui.close()
    }

    @Test
    fun nestedFocusScopesTrapAndRestoreFocus() {
        val host = TestHost.of()
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val outside = ui.components.button("outside")
        ui.root.add(outside)
        ui.frame(Duration.ZERO)
        check(ui.focus.request(outside))
        val modal = Modal.of(ui)
        val first = ui.components.button("first")
        val second = ui.components.button("second")
        modal.body.add(first)
        modal.body.add(second)
        check(ui.focus.request(first) && !ui.focus.request(outside))
        ui.focus.move()
        check(ui.focus.focused === second)
        ui.focus.move()
        check(ui.focus.focused === first)
        val nested = Modal.of(ui, modal.body)
        val third = ui.components.button("third")
        nested.body.add(third)
        check(ui.focus.request(third) && !ui.focus.request(first))
        nested.close()
        check(ui.focus.focused === first)
        modal.close()
        check(ui.focus.focused === outside)
        ui.close()
    }

    @Test
    fun disabledOwnersStillUpdateVisibleControllers() {
        val host = TestHost.of()
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val owner = ui.components.surface("disabled").apply { enabled = false }
        var updates = 0
        owner.add(object : AbstractElement("controller") {
            override fun update(delta: Duration): Boolean {
                updates++
                return false
            }
        })
        ui.root.add(owner)
        ui.frame(Duration.ZERO)
        check(updates == 1)
        ui.close()
    }
}
