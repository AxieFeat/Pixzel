package pixzel.component

import org.junit.jupiter.api.Test
import pixzel.Ui
import pixzel.dsl.ContentScope
import pixzel.dsl.contextMenu
import pixzel.graphics.RecordingBackend
import pixzel.input.EventPhase
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.RoutedEvent
import pixzel.input.RoutingFlags
import pixzel.input.WindowFocus
import pixzel.layout.unit.dp
import pixzel.platform.TestHost
import pixzel.surface.Insets
import pixzel.surface.Placement
import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import pixzel.ui
import kotlin.time.Duration

class ContextMenuTest {
    private class Fixture : AutoCloseable {

        val host = TestHost.of(400, 300)
        val ui: Ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val owner = ui.components.button("owner").apply {
            layout.width = 180.dp
            layout.height = 44.dp
            placement = Placement(offset = Vec2(40f, 30f))
            setLabel("Context owner")
        }

        init {
            ui.root.add(owner)
            frame()
            ui.focus.request(owner)
        }

        fun frame() = ui.frame(Duration.ZERO)
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

    private fun descendants(element: Element): List<Element> = listOf(element) + element.children.flatMap(::descendants)
    private fun assertOpeningReleaseHandlerRemoved(ui: Ui) {
        // Exercise routing independently of the host's intentional held-button masking.
        val flags = RoutingFlags()
        ui.root.dispatch(
            RoutedEvent(
                PointerEvent(PointerAction.UP, Vec2(390f, 290f), button = 1),
                ui.root,
                ui.root,
                EventPhase.CAPTURE,
                Vec2(390f, 290f),
                flags
            )
        )
        check(!flags.consumed) { "Disposed ContextMenu still intercepts root pointer releases" }
    }

    @Test
    fun rightClickDoesNotDismissItsOpeningMenuAndPointerSelectionConsumesGameplay() {
        Fixture().use { f ->
            var selected = 0
            val menu = ContextMenu.of(f.ui, f.owner, listOf(MenuItem("copy", "Copy", 10), MenuItem("paste", "Paste", 20))) { selected = it.value }
            val point = f.owner.surface.toRoot(Vec2(15f, 15f))
            f.host.queue(PointerEvent(PointerAction.DOWN, point, button = 1))
            f.host.queue(PointerEvent(PointerAction.UP, point, button = 1))
            f.frame()
            check(menu.isOpen && menu.element!!.semantics.role == Role.MENU)
            check(f.host.gameplayEvents.isEmpty()) { "Context opening leaked gameplay events: ${f.host.gameplayEvents}" }
            val row = descendants(menu.element!!).first { it.semantics.value == "paste" }
            val itemPoint = row.surface.toRoot(Vec2(10f, 18f))
            f.host.queue(PointerEvent(PointerAction.DOWN, itemPoint))
            f.host.queue(PointerEvent(PointerAction.UP, itemPoint))
            f.frame()
            check(selected == 20 && !menu.isOpen && menu.element == null)
            check(f.host.gameplayEvents.isEmpty() && f.ui.focus.focused === f.owner)
            check(f.owner.children.size == 1 && f.ui.root.frameUpdates.isEmpty)
        }
    }

    @Test
    fun explicitKeyboardInvocationUsesArrowNavigationAndEscapeRestoresFocus() {
        Fixture().use { f ->
            var selected = 0
            val menu = ContentScope(f.ui, f.owner).contextMenu(
                listOf(
                    MenuItem("a", "Alpha", 1),
                    MenuItem("disabled", "Unavailable", 2, enabled = false),
                    MenuItem("c", "Gamma", 3)
                )
            ) { selected = it.value }
            check(menu.openFor())
            f.frame()
            check(f.ui.focus.focused?.semantics?.value == "a")
            f.key(Key.DOWN)
            check(f.ui.focus.focused?.semantics?.value == "c")
            f.key(Key.ENTER)
            check(selected == 3 && !menu.isOpen && f.ui.focus.focused === f.owner)
            check(menu.openFor())
            f.frame()
            f.key(Key.ESCAPE)
            check(!menu.isOpen && f.ui.focus.focused === f.owner && f.ui.root.frameUpdates.isEmpty)
            check(f.host.gameplayEvents.isEmpty()) { "Keyboard context menu leaked gameplay events: ${f.host.gameplayEvents}" }
        }
    }

    @Test
    fun pointInvocationFlipsAndClampsToSafeViewportAndKeyboardAnchorFollowsGeometry() {
        Fixture().use { f ->
            f.host.resize(Size(400f, 300f), safeInsets = Insets(12f, 18f, 20f, 22f))
            f.frame()
            val menu = ContextMenu.of(f.ui, f.owner, listOf(MenuItem("a", "Alpha", 1), MenuItem("b", "Beta", 2))) {}
            check(menu.openAt(Vec2(399f, 299f)))
            f.frame()
            var element = menu.element!!
            var bounds = element.surface.mapRect(Rect(0f, 0f, element.surface.bounds.width, element.surface.bounds.height))
            check(bounds.x >= 12f && bounds.y >= 18f && bounds.right <= 380f && bounds.bottom <= 278f)
            check(bounds.x < 399f && bounds.y < 299f)
            menu.closeMenu()
            check(menu.openFor())
            f.frame()
            f.owner.placement = Placement(offset = Vec2(80f, 50f))
            f.frame()
            element = menu.element!!
            bounds = element.surface.mapRect(Rect(0f, 0f, element.surface.bounds.width, element.surface.bounds.height))
            val ownerBounds = f.owner.surface.mapRect(Rect(0f, 0f, f.owner.surface.bounds.width, f.owner.surface.bounds.height))
            check(bounds.x == ownerBounds.x && bounds.y == ownerBounds.bottom)
            f.host.resize(120, 100)
            f.frame()
            bounds = element.surface.mapRect(Rect(0f, 0f, element.surface.bounds.width, element.surface.bounds.height))
            check(bounds.x >= 12f && bounds.y >= 18f && bounds.right <= 100f && bounds.bottom <= 78f)
        }
    }

    @Test
    fun detachmentAndDisposalRemovePopupAnchorsCallbacksAndOpeningHandlers() {
        Fixture().use { f ->
            val menu = ContextMenu.of(f.ui, f.owner, listOf(MenuItem("a", "Alpha", 1))) {}
            val openingPoint = f.owner.surface.toRoot(Vec2(5f, 5f))
            f.host.queue(PointerEvent(PointerAction.DOWN, openingPoint, button = 1))
            f.frame()
            val popup = menu.element!!
            check(!f.ui.root.frameUpdates.isEmpty)
            f.ui.root.remove(f.owner, dispose = false)
            check(popup.isDisposed && !menu.isOpen && f.owner.children.size == 1 && f.ui.root.frameUpdates.isEmpty)
            check(f.host.listenerCount == 1)
            assertOpeningReleaseHandlerRemoved(f.ui)
            f.host.clearEventRecords()
            f.host.queue(PointerEvent(PointerAction.UP, Vec2(390f, 290f), button = 1))
            f.frame()
            check(f.host.consumedEvents.size == 1 && f.host.gameplayEvents.isEmpty())
            f.host.clearEventRecords()
            f.host.queue(PointerEvent(PointerAction.UP, Vec2(390f, 290f), button = 1))
            f.frame()
            check(f.host.consumedEvents.isEmpty())
            check(!menu.openAt(Vec2.ZERO))
            f.ui.root.add(f.owner)
            f.frame()
            check(menu.openFor())
            f.frame()
            menu.close()
            menu.close()
            check(menu.isClosed && !menu.isOpen && f.owner.children.size == 1 && f.ui.root.frameUpdates.isEmpty)
            val point = f.owner.surface.toRoot(Vec2(5f, 5f))
            f.host.queue(PointerEvent(PointerAction.DOWN, point, button = 1))
            f.host.queue(PointerEvent(PointerAction.UP, point, button = 1))
            f.frame()
            check(!menu.isOpen)
            check(runCatching { menu.openFor() }.isFailure)
        }
    }

    @Test
    fun focusLossAndEscapeBeforeOpeningReleaseRemoveTemporaryListeners() {
        Fixture().use { f ->
            val menu = ContextMenu.of(f.ui, f.owner, listOf(MenuItem("a", "Alpha", 1))) {}
            val point = f.owner.surface.toRoot(Vec2(5f, 5f))
            fun press() {
                f.host.queue(PointerEvent(PointerAction.DOWN, point, button = 1))
                f.frame()
                check(menu.isOpen && f.host.listenerCount == 2)
            }

            fun release(blocked: Boolean) {
                assertOpeningReleaseHandlerRemoved(f.ui)
                f.host.clearEventRecords()
                f.host.queue(PointerEvent(PointerAction.UP, Vec2(390f, 290f), button = 1))
                f.frame()
                check(f.host.consumedEvents.size == (if (blocked) 1 else 0) && f.host.listenerCount == 1)
                f.host.clearEventRecords()
                f.host.queue(PointerEvent(PointerAction.UP, Vec2(390f, 290f), button = 1))
                f.frame()
                check(f.host.consumedEvents.isEmpty())
            }
            press()
            f.host.queue(WindowFocus(false))
            f.frame()
            check(!menu.isOpen && f.ui.focus.focused == null && f.ui.root.frameUpdates.isEmpty)
            release(blocked = false)
            f.host.queue(WindowFocus(true))
            f.frame()
            f.ui.focus.request(f.owner)
            press()
            f.key(Key.ESCAPE)
            check(!menu.isOpen && f.ui.focus.focused === f.owner)
            release(blocked = true)
        }
    }

    @Test
    fun failedMenuContentAndSelectionCallbacksReleaseAnchorsAndRestoreFocus() {
        Fixture().use { f ->
            val invalid = f.ui.components.surface().also { it.close() }
            val menu = ContextMenu.of(f.ui, f.owner, listOf(MenuItem("a", "Alpha", 1, content = { invalid }))) {}
            check(runCatching { menu.openFor() }.isFailure)
            check(!menu.isOpen && f.owner.children.size == 1 && f.ui.root.frameUpdates.isEmpty && f.ui.focus.focused === f.owner)
            menu.items = listOf(MenuItem("a", "Alpha", 1))
            val throwing = ContextMenu.of(f.ui, f.owner, menu.items) { error("selection") }
            throwing.openFor()
            f.frame()
            f.host.queue(KeyEvent(Key.ENTER, KeyAction.DOWN))
            check(runCatching { f.frame() }.isFailure)
            check(!throwing.isOpen && f.owner.children.size == 1 && f.ui.root.frameUpdates.isEmpty && f.ui.focus.focused === f.owner)
        }
    }
}
