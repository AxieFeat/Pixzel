package pixzel.component

import org.junit.jupiter.api.Test
import pixzel.dsl.ButtonScope
import pixzel.dsl.content
import pixzel.dsl.icon
import pixzel.graphics.DrawText
import pixzel.graphics.RecordingBackend
import pixzel.layout.unit.dp
import pixzel.layout.unit.fillAvailable
import pixzel.platform.TestHost
import pixzel.surface.Insets
import pixzel.surface.Rect
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import pixzel.ui
import kotlin.time.Duration

class ComponentSlotTest {

    @Test
    fun buttonSlotsReserveLabelWidthAndPreserveBehaviorDuringReplacement() {
        val host = TestHost.of(320, 240)
        val backend = RecordingBackend.of()
        val owner = ui(host, backend, FixedFontTextService.of())
        val button = owner.components.button("action")
        button.layout.width = 200.dp
        button.layout.height = 40.dp
        button.layout.padding = Insets.all(10.dp)
        button.layout.spacing = 5.dp
        val leading = owner.components.surface("leading").apply {
            layout.width = 20.dp
            layout.height = 16.dp
        }
        val trailing = owner.components.surface("trailing").apply {
            layout.width = 30.dp
            layout.height = 16.dp
        }
        button.setLeading(leading)
        button.setTrailing(trailing)
        val label = button.setLabel("A label too long for the remaining space")
        var clicks = 0
        button.onClick { clicks++ }
        owner.root.add(button)
        owner.frame(Duration.ZERO)
        owner.focus.request(button)
        check(label.surface.bounds.width == 120f)
        check(label.surface.toRoot(Vec2.ZERO) == Vec2(35f, 10f))
        val drawn = backend.lastFrame!!.commands.filterIsInstance<DrawText>().single()
        check(drawn.layout.size.width <= 120f && drawn.layout.text.endsWith("…"))
        val arbitrary = owner.components.surface("custom").apply {
            layout.width = 40.dp
            layout.height = 20.dp
        }
        button.setContent(arbitrary)
        check(label.isDisposed && button.content === arbitrary && button.leading === leading && button.trailing === trailing)
        check(owner.focus.focused === button)
        button.activate()
        check(clicks == 1)
        val next = owner.components.surface("next").apply {
            layout.width = 10.dp
            layout.height = 10.dp
        }
        button.setLeading(next)
        check(leading.isDisposed && !arbitrary.isDisposed)
        owner.close()
        check(next.isDisposed && arbitrary.isDisposed && trailing.isDisposed)
    }

    @Test
    fun buttonDslContentReplacesOnlyItsCenterAndRollsBackFailedSlotBuilders() {
        val host = TestHost.of(320, 240)
        val owner = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val button = owner.components.button()
        owner.root.add(button)
        val scope = ButtonScope(owner, button)
        scope.leading {
            surface("icon") {
                layout {
                    width = 20.dp
                    height = 20.dp
                }
            }
        }
        val icon = button.leading!!
        scope.label("Old")
        scope.content {
            surface("arbitrary") {
                layout {
                    width = 30.dp
                    height = 20.dp
                }
            }
        }
        check(button.leading === icon && !icon.isDisposed && button.content!!.children.single().id == "arbitrary")
        var released = false
        check(runCatching {
            scope.trailing {
                surface().resources.own(AutoCloseable { released = true })
                error("slot builder")
            }
        }.isFailure)
        check(released && button.trailing == null)
        owner.frame(Duration.ZERO)
        owner.close()
    }

    @Test
    fun modalNamedSlotsUseIndependentContentAndRetainFocusSemantics() {
        val host = TestHost.of(640, 480)
        val owner = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val outside = owner.components.button("outside")
        owner.root.add(outside)
        owner.frame(Duration.ZERO)
        owner.focus.request(outside)
        val modal = Modal.of(owner)
        val header = owner.components.text("heading").apply {
            value = "Settings"
            layout.width = fillAvailable
            layout.height = 24.dp
        }
        val body = owner.components.textField("editor").apply {
            value = "draft"
            layout.width = fillAvailable
            layout.height = 36.dp
        }
        val action = owner.components.button("accept").apply {
            layout.width = 90.dp
            layout.height = 32.dp
            setLabel("Accept")
        }
        modal.header.add(header)
        modal.body.add(body)
        modal.actions.add(action)
        owner.frame(Duration.ZERO)
        check(
            header.surface.mapRect(
                Rect(
                    0f,
                    0f,
                    header.surface.bounds.width,
                    header.surface.bounds.height
                )
            ).bottom <= body.surface.toRoot(Vec2.ZERO).y
        )
        check(body.surface.toRoot(Vec2.ZERO).y < action.surface.toRoot(Vec2.ZERO).y)
        check(owner.focus.request(body) && !owner.focus.request(outside))
        modal.close()
        check(header.isDisposed && body.isDisposed && action.isDisposed && owner.focus.focused === outside)
        owner.close()
    }

    @Test
    fun structuredNotificationSlotsPackWithinViewportAndClosePromotesTheQueue() {
        val host = TestHost.of(400, 260)
        val owner = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val notifications = NotificationHost.of(owner, limit = 3)
        lateinit var firstSlots: NotificationSlots
        val first = notifications.showSlots(timeout = null, height = 112.dp) {
            firstSlots = this
            icon {
                surface("picture") {
                    layout {
                        width = 20.dp
                        height = 20.dp
                    }
                }
            }
            title { text("Saved") { layout { height = 20.dp } } }
            body {
                surface("custom-body") {
                    layout {
                        width = fillAvailable
                        height = 20.dp
                    }
                }
            }
            actions {
                button {
                    label("Undo")
                    layout {
                        width = 60.dp
                        height = 24.dp
                    }
                }
            }
        }
        val second = notifications.showSlots(timeout = null, height = 112.dp) { body { text("Queued") } }
        owner.frame(Duration.ZERO)
        check(first.element.parent === notifications && second.element.parent == null)
        check(firstSlots.title.children.single().semantics.label == "Saved")
        check(firstSlots.body.children.single().id == "custom-body")
        check(firstSlots.icon.children.single().surface.bounds.width == 20f)
        firstSlots.close.activate()
        owner.frame(Duration.ZERO)
        check(first.element.isDisposed && second.element.parent === notifications)
        host.resize(80, 70)
        owner.frame(Duration.ZERO)
        val bounds = second.element.surface.mapRect(Rect(0f, 0f, second.element.surface.bounds.width, second.element.surface.bounds.height))
        check(bounds.x >= 0f && bounds.y >= 0f && bounds.right <= 80f && bounds.bottom <= 70f)
        owner.close()
        check(second.element.isDisposed)
    }

    @Test
    fun removeDisposesResourcesAfterDetachCleanupFailsAndPreservesSuppressedErrors() {
        val host = TestHost.of(100, 100)
        val owner = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val child = owner.components.surface("child")
        var released = false
        child.attachments.bind { AutoCloseable { error("detach") } }
        child.resources.own(AutoCloseable {
            released = true
            error("dispose")
        })
        owner.root.add(child)
        val failure = runCatching { owner.root.remove(child) }.exceptionOrNull()!!
        check(failure.message == "detach" && failure.suppressed.any { it.message == "dispose" })
        check(child.isDisposed && child.parent == null && child.ui == null && released && child !in owner.root.children)
        owner.close()
    }
}
