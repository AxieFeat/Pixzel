package pixzel

import org.junit.jupiter.api.Test
import pixzel.component.AbstractElement
import pixzel.component.CheckState
import pixzel.component.ElementState
import pixzel.component.Modal
import pixzel.component.Painter
import pixzel.component.performSemanticsAction
import pixzel.component.semanticsTree
import pixzel.graphics.Color
import pixzel.graphics.DrawText
import pixzel.graphics.Fill
import pixzel.graphics.RecordingBackend
import pixzel.graphics.Rectangle
import pixzel.graphics.Save
import pixzel.graphics.Solid
import pixzel.input.Composition
import pixzel.input.EventPhase
import pixzel.input.InputMode
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.NavigationCommand
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.PointerPolicy
import pixzel.input.WheelEvent
import pixzel.input.WheelUnit
import pixzel.input.WindowFocus
import pixzel.layout.Constraints
import pixzel.layout.CrossAxisAlignment
import pixzel.layout.rowLayout
import pixzel.layout.unit.dp
import pixzel.layout.unit.sp
import pixzel.platform.TestHost
import pixzel.resource.AttachmentScope
import pixzel.style.StateStyle
import pixzel.style.StyleScope
import pixzel.style.StyleTokens
import pixzel.style.Theme
import pixzel.surface.Insets
import pixzel.surface.Placement
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import pixzel.text.TextDirection
import pixzel.text.TextSpan
import pixzel.text.TextSpans
import pixzel.text.TextStyle
import kotlin.math.abs
import kotlin.time.Duration

class RuntimeAcceptanceTest {

    @Test
    fun keyboardPressLosesItsPressedStateWhenFocusMovesAndItsReleaseStaysConsumed() {
        val host = TestHost.of()
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        var clicks = 0
        val first = ui.components.button()
        val next = ui.components.button().apply {
            placement = Placement(offset = Vec2(200f, 0f))
            onClick { clicks++ }
        }
        ui.root.add(first)
        ui.root.add(next)
        ui.frame(Duration.ZERO)
        ui.focus.request(first)
        host.queue(KeyEvent(Key.SPACE, KeyAction.DOWN))
        ui.frame(Duration.ZERO)
        check(first.state.pressed)
        ui.focus.move()
        check(!first.state.pressed)
        host.queue(KeyEvent(Key.SPACE, KeyAction.UP))
        ui.frame(Duration.ZERO)
        check(clicks == 0 && host.gameplayEvents.isEmpty())
        ui.close()
    }

    @Test
    fun styleMutationsRejectWrongThreadAndPaintBeforeChangingValues() {
        val element = object : AbstractElement() {}
        val original = element.styleScope
        val state = element.state
        val worker = Thread {
            check(runCatching { element.styleScope = StyleScope(StyleTokens(spacing = 99.dp)) }.isFailure)
            check(runCatching { element.setInteractionState(state.copy(pressed = true)) }.isFailure)
        }
        worker.start()
        worker.join()
        check(element.styleScope === original && element.state == state)
        val ui = ui(TestHost.of(), RecordingBackend.of(), FixedFontTextService.of())
        element.layout.width = 10.dp
        element.layout.height = 10.dp
        element.background = Painter { _, _ -> check(runCatching { element.setInteractionState(state.copy(selected = true)) }.isFailure) }
        ui.root.add(element)
        ui.frame(Duration.ZERO)
        check(element.state.selected == false)
        ui.close()
    }

    @Test
    fun cancellationCanCloseMinimizedUiWithoutResurrectingIt() {
        val host = TestHost.of()
        val backend = RecordingBackend.of()
        val ui = ui(host, backend, FixedFontTextService.of())
        val button = ui.components.button()
        ui.root.add(button)
        button.onEvent(EventPhase.TARGET) { if ((it.event as? PointerEvent)?.action == PointerAction.CANCEL) ui.close() }
        ui.frame(Duration.ZERO)
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(2f, 2f)))
        ui.frame(Duration.ZERO)
        host.resize(0, 0)
        ui.frame(Duration.ZERO)
        check(ui.lifecycle == UiLifecycle.CLOSED && host.listenerCount == 0 && backend.activeSessions == 0)
    }

    @Test
    fun throwingHoverExitStillCancelsCaptureAndClearsFocusOnWindowLoss() {
        val host = TestHost.of()
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        var cancelled = false
        val element = object : AbstractElement() {
            override fun setInteractionState(state: ElementState) {
                if (this.state.hovered && !state.hovered) error("hover exit")
                super.setInteractionState(state)
            }
        }
        element.layout.width = 20.dp
        element.layout.height = 20.dp
        element.focusable = true
        element.pointerPolicy = PointerPolicy.AUTO
        element.onEvent(EventPhase.TARGET) {
            when ((it.event as? PointerEvent)?.action) {
                PointerAction.DOWN -> it.capturePointer()
                PointerAction.CANCEL -> cancelled = true
                else -> Unit
            }
        }
        ui.root.add(element)
        ui.frame(Duration.ZERO)
        ui.focus.request(element)
        host.queue(PointerEvent(PointerAction.DOWN, Vec2(2f, 2f)))
        ui.frame(Duration.ZERO)
        host.queue(WindowFocus(false))
        check(runCatching { ui.frame(Duration.ZERO) }.isFailure)
        check(cancelled && ui.capturedPointers.isEmpty() && ui.focus.focused == null)
        ui.close()
    }

    @Test
    fun attachmentFactoriesCanDetachOrCloseTheirScopeWithoutLeakingTheReturnedLease() {
        val scope = AttachmentScope()
        var closed = 0
        scope.setAttached(true)
        scope.bind {
            scope.setAttached(false)
            AutoCloseable { closed++ }
        }
        check(closed == 1)
        scope.setAttached(true)
        check(closed == 2)
        scope.close()
        check(closed == 2)
        val reentrant = AttachmentScope()
        reentrant.setAttached(true)
        reentrant.bind {
            reentrant.close()
            AutoCloseable { closed++ }
        }
        check(closed == 3)
        val failures = AttachmentScope()
        var otherClosed = false
        failures.bind { AutoCloseable { error("close failure") } }
        failures.bind { AutoCloseable { otherClosed = true } }
        failures.setAttached(true)
        check(runCatching { failures.close() }.isFailure && otherClosed)
        val reopened = AttachmentScope()
        var opened = 0
        var released = 0
        var once = true
        reopened.bind {
            opened++
            AutoCloseable {
                released++
                if (once) {
                    once = false
                    reopened.setAttached(true)
                }
            }
        }
        reopened.bind {
            opened++
            AutoCloseable { released++ }
        }
        reopened.setAttached(true)
        reopened.setAttached(false)
        reopened.close()
        check(opened == released)
    }

    @Test
    fun inheritedTokensRulesInstanceOverridesAndDisabledSkinsStayConsistent() {
        val host = TestHost.of()
        val backend = RecordingBackend.of()
        val ui = ui(host, backend, FixedFontTextService.of())
        val scope = ui.components.surface().apply {
            layout.width = 300.dp
            layout.height = 100.dp
        }
        val text = ui.components.text().apply { value = "Label" }
        val button = ui.components.button().apply { placement = Placement(offset = Vec2(0f, 40f)) }
        scope.add(text)
        scope.add(button)
        ui.root.add(scope)
        val large = TextStyle(size = 24.sp)
        scope.styleScope = StyleScope(StyleTokens(typography = large), listOf(StateStyle({ it.hovered }, StyleTokens(palette = Theme.GAME.palette))))
        ui.frame(Duration.ZERO)
        check(text.style == large)
        scope.setInteractionState(scope.state.copy(hovered = true))
        ui.frame(Duration.ZERO)
        check(text.color == Theme.GAME.palette.text)
        text.color = Color.WHITE
        text.style = TextStyle(size = 12.sp)
        ui.frame(Duration.ZERO)
        check(text.color == Color.WHITE && text.style.size == 12.sp)
        scope.enabled = false
        ui.frame(Duration.ZERO)
        val fills = backend.lastFrame!!.commands.filterIsInstance<Fill>()
        check(fills.any { (it.brush as? Solid)?.color == Theme.GAME.palette.disabled })
        check(!ui.focus.request(button))
        scope.enabled = true
        ui.theme = Theme.LIGHT
        ui.frame(Duration.ZERO)
        check(ui.statistics.measuredNodes == 0)
        ui.close()
    }

    @Test
    fun reducedMotionChangesCachedIndeterminatePaintAndWheelDpRemainsFractional() {
        val host = TestHost.of()
        val backend = RecordingBackend.of()
        val ui = ui(host, backend, FixedFontTextService.of())
        val progress = ui.components.progress().apply { indeterminate = true }
        ui.root.add(progress)
        ui.frame(Duration.ZERO)
        val moving = backend.lastFrame!!.commands.filterIsInstance<Fill>().last().bounds
        ui.settings = ui.settings.copy(isReducedMotion = true)
        ui.frame(Duration.ZERO)
        val still = backend.lastFrame!!.commands.filterIsInstance<Fill>().last().bounds
        check(still.x > moving.x)
        val scroll = ui.components.scrollView().apply {
            layout.width = 100.dp
            layout.height = 100.dp
            placement = Placement(offset = Vec2(0f, 40f))
        }
        scroll.add(ui.components.surface().apply {
            layout.width = 100.dp
            layout.height = 500.dp
        })
        ui.root.add(scroll)
        ui.frame(Duration.ZERO)
        host.queue(WheelEvent(Vec2(5f, 50f), Vec2(0f, -.25f), unit = WheelUnit.DP))
        ui.frame(Duration.ZERO)
        check(scroll.scrollOffset.y == .25f)
        host.queue(WheelEvent(Vec2(5f, 50f), Vec2(0f, -1f), unit = WheelUnit.PAGE))
        ui.frame(Duration.ZERO)
        check(scroll.scrollOffset.y == 100.25f)
        ui.close()
    }

    @Test
    fun fieldDirectionAndShorterPreeditUseCurrentCaretWithinScaledField() {
        val host = TestHost.of()
        val backend = RecordingBackend.of()
        val ui = ui(host, backend, FixedFontTextService.of(), UiSettings(uiScale = 1.25f))
        val field = ui.components.textField().apply {
            value = "123"
            direction = TextDirection.RTL
            locale = "ar"
            layout.width = 80.dp
        }
        ui.root.add(field)
        ui.frame(Duration.ZERO)
        ui.focus.request(field)
        check(backend.lastFrame!!.commands.filterIsInstance<DrawText>().single().layout.direction == TextDirection.RTL)
        field.value = "A very long selected string"
        field.selectionStart = 0
        field.selectionEnd = field.value.length
        host.queue(Composition("x", 1, 1))
        ui.frame(Duration.ZERO)
        check(field.value == "A very long selected string" && field.composition?.preedit == "x")
        check(host.imeCaret!!.right <= 100f && host.imeCaret!!.x >= 0f)
        check(runCatching { field.spans = TextSpans.of(TextSpan(0, 1, TextStyle())) }.isFailure)
        ui.close()
    }

    @Test
    fun geometricPortalParentDisabledDoesNotDisableLogicalOwnerAndSelectionCannotBypassIt() {
        val ui = ui(TestHost.of(), RecordingBackend.of(), FixedFontTextService.of())
        val target = ui.components.surface().apply {
            layout.width = 300.dp
            layout.height = 100.dp
            enabled = false
        }
        val owner = ui.components.surface().apply {
            layout.width = 300.dp
            layout.height = 100.dp
        }
        val portal = ui.components.button().apply { portalTarget = target }
        owner.add(portal)
        ui.root.add(owner)
        ui.root.add(target)
        ui.frame(Duration.ZERO)
        check(ui.hitTest(Vec2(10f, 10f)) === portal)
        val check = ui.components.checkbox()
        owner.add(check)
        owner.enabled = false
        check.activate()
        check(check.checkState == CheckState.UNCHECKED)
        ui.close()
    }

    @Test
    fun directionalCommandsAndModalMaskHeldGameplayInputUntilRelease() {
        val host = TestHost.of()
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        var activations = 0
        val left = ui.components.button("left")
        val right = ui.components.button("right").apply {
            placement = Placement(offset = Vec2(200f, 0f))
            onClick { activations++ }
        }
        ui.root.add(left)
        ui.root.add(right)
        ui.frame(Duration.ZERO)
        ui.focus.request(left)
        check(ui.navigate(NavigationCommand.RIGHT) && ui.focus.focused === right)
        check(ui.navigate(NavigationCommand.ACTIVATE) && activations == 1 && ui.inputMode == InputMode.NAVIGATION)
        ui.focus.clear()
        host.queue(KeyEvent(Key.A, KeyAction.DOWN))
        ui.frame(Duration.ZERO)
        check(host.isPhysicalKeyDown(Key.A) && host.isGameplayKeyDown(Key.A))
        val modal = Modal.of(ui)
        modal.content { button("Inside") }
        check(host.isPhysicalKeyDown(Key.A) && !host.isGameplayKeyDown(Key.A))
        check(ui.navigate(NavigationCommand.LEFT).not())
        check(ui.navigate(NavigationCommand.CANCEL) && modal.element.isDisposed)
        check(!host.isGameplayKeyDown(Key.A))
        host.queue(KeyEvent(Key.A, KeyAction.UP))
        host.queue(KeyEvent(Key.A, KeyAction.DOWN))
        ui.frame(Duration.ZERO)
        check(host.isGameplayKeyDown(Key.A))
        ui.close()
    }

    @Test
    fun semanticActionsUseFullLabelsBoundsAndLogicalEnabledState() {
        val ui = ui(TestHost.of(), RecordingBackend.of(), FixedFontTextService.of())
        var clicks = 0
        val owner = ui.components.surface().apply {
            layout.width = 300.dp
            layout.height = 100.dp
        }
        val button = ui.components.button("action").apply {
            setLabel("A complete overflowing accessible label")
            onClick { clicks++ }
        }
        owner.add(button)
        ui.root.add(owner)
        ui.frame(Duration.ZERO)
        val node = ui.semanticsTree().children.single().children.single()
        check(node.label == "A complete overflowing accessible label" && node.bounds.width == 160f && "activate" in node.actions)
        check(ui.performSemanticsAction(button, "activate").isSuccess && clicks == 1)
        owner.enabled = false
        check(!ui.semanticsTree().children.single().children.single().enabled)
        check(ui.performSemanticsAction(button, "activate").isFailure && clicks == 1)
        ui.close()
    }

    @Test
    fun handledPainterAndUpdateFailuresKeepScopesBalancedAndOtherCallbacksRunning() {
        val backend = RecordingBackend.of()
        val ui = ui(TestHost.of(), backend, FixedFontTextService.of(), UiSettings(errorPolicy = UiErrorPolicy { ErrorDecision.CONTINUE }))
        var ran = false
        val element = ui.components.surface().apply {
            layout.width = 20.dp
            layout.height = 20.dp
            background = Painter { canvas, _ ->
                canvas.emit(Save)
                error("painter failed")
            }
            foreground = Painter { canvas, context -> canvas.fill(Rectangle, context.bounds, Solid(Color.WHITE)) }
        }
        element.frameUpdates.add { error("update failed") }
        element.frameUpdates.add {
            ran = true
            false
        }
        ui.root.add(element)
        ui.frame(Duration.ZERO)
        check(ran && backend.lastFrame!!.commands.filterIsInstance<Fill>().size == 1)
        check(ui.diagnostics.messages.any { it.code == "paint-error" } && ui.diagnostics.messages.any { it.code == "update-error" })
        ui.close()
    }

    @Test
    fun rowsAlignRealTextBaselinesAcrossFontSizesAndPadding() {
        val ui = ui(TestHost.of(), RecordingBackend.of(), FixedFontTextService.of())
        val row = ui.components.surface().apply {
            layoutPolicy = rowLayout(4.dp, CrossAxisAlignment.BASELINE)
            layout.width = 300.dp
            layout.height = 60.dp
        }
        val small = ui.components.text().apply {
            value = "Small"
            style = TextStyle(size = 12.sp)
            layout.padding = Insets.all(2.dp)
        }
        val large = ui.components.text().apply {
            value = "Large"
            style = TextStyle(size = 28.sp)
        }
        row.add(small)
        row.add(large)
        ui.root.add(row)
        ui.frame(Duration.ZERO)
        val smallBase = small.measure(Constraints(maxWidth = 300f, maxHeight = 60f)).baseline!!
        val largeBase = large.measure(Constraints(maxWidth = 300f, maxHeight = 60f)).baseline!!
        check(abs(small.surface.bounds.y + smallBase - large.surface.bounds.y - largeBase) < .01f)
        ui.close()
    }
}
