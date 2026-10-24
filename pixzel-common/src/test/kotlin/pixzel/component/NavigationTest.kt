package pixzel.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pixzel.Ui
import pixzel.graphics.DrawText
import pixzel.graphics.RecordingBackend
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.platform.TestHost
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import pixzel.ui
import kotlin.time.Duration

class NavigationTest {
    private class Fixture(width: Int = 640) : AutoCloseable {

        val host = TestHost.of(width, 360)
        val backend = RecordingBackend.of()
        val ui: Ui = ui(host, backend, FixedFontTextService.of())
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

    @Test
    fun `tabs preserve text selection state use roving keys and reveal long header strips`() {
        Fixture(320).use { f ->
            val tabs = Tabs.of(f.ui.components)
            val pages = (0..7).map { index -> f.ui.components.textField("editor").apply { value = "page $index" } }
            pages.forEachIndexed { index, page -> tabs.addTab("page-$index", "Page $index", page) }
            f.ui.root.add(tabs)
            f.frame()
            val headers = descendants(tabs).filter { it.semantics.role == Role.TAB }
            assertEquals(8, headers.size)
            assertEquals(1, headers.count { it.focusable })
            pages[0].value = "retained edit"
            pages[0].selectionStart = 2
            pages[0].selectionEnd = 7
            f.ui.focus.request(pages[0])
            tabs.selectedKey = "page-1"
            f.frame()
            assertSame(headers[1], f.ui.focus.focused)
            tabs.selectedKey = "page-0"
            f.frame()
            assertEquals("retained edit", pages[0].value)
            assertEquals(2, pages[0].selectionStart)
            assertEquals(7, pages[0].selectionEnd)
            f.ui.focus.request(headers[0])
            f.key(Key.END)
            assertEquals("page-7", tabs.selectedKey)
            assertSame(headers[7], f.ui.focus.focused)
            val headerPoint = headers[7].surface.toRoot(Vec2(10f, 10f))
            assertTrue(headerPoint.x in 0f..320f)
            f.key(Key.RIGHT)
            assertEquals("page-0", tabs.selectedKey)
            f.key(Key.LEFT)
            assertEquals("page-7", tabs.selectedKey)
            tabs.removeTab("page-7")
            f.frame()
            assertEquals("page-6", tabs.selectedKey)
            assertTrue(pages[7].isDisposed)
            assertTrue(f.ui.focus.focused?.isDisposed == false)
        }
    }

    @Test
    fun `menu keyboard skips disabled items traps focus selects typed value and restores anchor`() {
        Fixture().use { f ->
            val anchor = f.ui.components.button("anchor")
            f.ui.root.add(anchor)
            f.frame()
            f.ui.focus.request(anchor)
            var selected: Int? = null
            val menu = Menu.of(
                f.ui,
                anchor,
                listOf(MenuItem("a", "Alpha", 10), MenuItem("b", "Disabled", 20, enabled = false), MenuItem("c", "Gamma", 30))
            ) { selected = it.value }
            f.frame()
            assertFalse(f.ui.focus.request(anchor))
            assertEquals("a", f.ui.focus.focused?.semantics?.value)
            f.key(Key.DOWN)
            assertEquals("c", f.ui.focus.focused?.semantics?.value)
            f.key(Key.ENTER)
            assertEquals(30, selected)
            assertTrue(menu.isClosed)
            assertSame(anchor, f.ui.focus.focused)
            assertEquals(0, anchor.children.count { it.layer == Layer.OVERLAY })
        }
    }

    @Test
    fun `menu custom item pointer selection empty slot outside and escape clean resources`() {
        Fixture().use { f ->
            val anchor = f.ui.components.button("anchor")
            f.ui.root.add(anchor)
            f.frame()
            f.ui.focus.request(anchor)
            var selected = ""
            val menu = Menu.of(f.ui, anchor, listOf(MenuItem("custom", "semantic label", "typed", content = {
                f.ui.components.text().apply { value = "custom slot" }
            }))) { selected = it.value }
            f.frame()
            assertTrue(f.backend.lastFrame!!.commands.filterIsInstance<DrawText>().any { it.layout.text == "custom slot" })
            val row = descendants(menu.element).first { it.semantics.value == "custom" }
            val point = row.surface.toRoot(Vec2(10f, 18f))
            f.host.queue(PointerEvent(PointerAction.DOWN, point))
            f.host.queue(PointerEvent(PointerAction.UP, point))
            f.frame()
            assertEquals("typed", selected)
            assertTrue(menu.isClosed)
            val empty = Menu.of<String>(f.ui, anchor, emptyList(), emptyContent = {
                f.ui.components.text().apply { value = "Nothing available" }
            }) {}
            f.frame()
            assertTrue(f.backend.lastFrame!!.commands.filterIsInstance<DrawText>().any { it.layout.text == "Nothing available" })
            f.key(Key.ESCAPE)
            assertTrue(empty.isClosed)
            assertSame(anchor, f.ui.focus.focused)
            val outside = Menu.of(f.ui, anchor, listOf(MenuItem("x", "X", 1))) {}
            f.frame()
            f.host.queue(PointerEvent(PointerAction.DOWN, Vec2(600f, 300f)))
            f.frame()
            assertTrue(outside.isClosed)
            assertEquals(1, f.host.listenerCount)
        }
    }

    @Test
    fun `dropdown preserves button identity manages selections model replacement and detach`() {
        Fixture().use { f ->
            val dropdown = dropdown(f.ui, listOf(MenuItem("a", "Alpha", 10), MenuItem("b", "Beta", 20)), "field")
            f.ui.root.add(dropdown)
            f.frame()
            f.ui.focus.request(dropdown)
            val changes = mutableListOf<Int?>()
            val subscription = dropdown.onSelectionChange { changes += it?.value }
            f.key(Key.ENTER)
            assertNotSame(dropdown, f.ui.focus.focused)
            f.key(Key.DOWN)
            f.key(Key.ENTER)
            assertEquals("b", dropdown.selectedKey)
            assertEquals(listOf(20), changes)
            assertSame(dropdown, f.ui.focus.focused)
            dropdown.items = listOf(MenuItem("b", "Updated beta", 200))
            assertEquals(listOf(20, 200), changes)
            assertTrue(descendants(dropdown).filterIsInstance<Text>().any { it.value == "Updated beta" })
            dropdown.fieldContent = { selected -> f.ui.components.text().apply { value = "custom ${selected?.value}" } }
            assertTrue(descendants(dropdown).filterIsInstance<Text>().any { it.value == "custom 200" })
            dropdown.openMenu()
            f.frame()
            f.ui.root.remove(dropdown, dispose = false)
            assertEquals(0, dropdown.children.count { it.layer == Layer.OVERLAY })
            f.ui.root.add(dropdown)
            f.frame()
            dropdown.enabled = false
            dropdown.openMenu()
            assertEquals(0, dropdown.children.count { it.layer == Layer.OVERLAY })
            subscription.close()
            dropdown.close()
        }
    }

    @Test
    fun `menu closes and restores focus even when selection callback throws`() {
        Fixture().use { f ->
            val anchor = f.ui.components.button()
            f.ui.root.add(anchor)
            f.frame()
            f.ui.focus.request(anchor)
            val menu = Menu.of(f.ui, anchor, listOf(MenuItem("x", "X", 1))) { error("selection failed") }
            f.frame()
            f.host.queue(KeyEvent(Key.ENTER, KeyAction.DOWN))
            assertThrows(IllegalStateException::class.java) { f.frame() }
            assertTrue(menu.isClosed)
            assertSame(anchor, f.ui.focus.focused)
        }
    }
}
