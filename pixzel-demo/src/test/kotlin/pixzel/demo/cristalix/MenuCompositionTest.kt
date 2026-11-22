package pixzel.demo.cristalix

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pixzel.component.Element
import pixzel.graphics.RecordingBackend
import pixzel.graphics.SoftwareImage
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

class MenuCompositionTest {

    private val data = MenuData(
        listOf(
            GameMode("custom-a", "First", 12, 0x336699, "custom-art", category = "Custom"),
            GameMode("custom-b", "Second", 3, 0x993366, "custom-art")
        ),
        listOf(Promotion("custom-banner", "Promotion", "Open", 2f, 120f)), emptyList()
    )

    @Test
    fun composesInjectedContent() {
        val host = TestHost.of(1600, 1000)
        SoftwareImage.of(1, 1, intArrayOf(-1)).use { image ->
            ui(host, RecordingBackend.of(), FixedFontTextService.of()).use { ui ->
                val art = MenuArt(
                    null, listOf(image), image, image, image, image, listOf(image), image, image,
                    mapOf("custom-art" to image)
                )
                val model = MenuModel(data = data).apply { reducedMotion = true }
                var launched: GameMode? = null
                val menu = CristalixMenu(ui, model, art, actions = MenuActions { launched = it })
                ui.root.add(menu.root)
                ui.frame(Duration.ZERO)
                assertEquals(data.games, menu.grid.cards.map { it.game })
                fun elements(root: Element): List<Element> = listOf(root) + root.children.flatMap(::elements)
                assertTrue(elements(menu.root).none { it.javaClass.packageName == "pixzel.demo.cristalix" })
                val tile = menu.grid.cards.first()
                // Moving and clicking within a single frame must hit the native favorite button.
                val star = tile.favorite.surface.toRoot(Vec2(25f, 22f))
                host.queue(PointerEvent(PointerAction.MOVE, star))
                host.queue(PointerEvent(PointerAction.DOWN, star))
                host.queue(PointerEvent(PointerAction.UP, star))
                ui.frame(Duration.ZERO)
                assertTrue("custom-a" in model.favorites)
                assertFalse(menu.modal.open)
                tile.root.activate()
                ui.frame(Duration.ZERO)
                (menu.modal.root.children.first { it.id == "modal-primary" } as pixzel.component.Button).activate()
                assertEquals(data.games.first(), launched)
                host.queue(KeyEvent(Key.ESCAPE, KeyAction.DOWN))
                ui.frame(Duration.ZERO)
                assertFalse(menu.modal.open)
                menu.toolbar.search.value = "Second"
                ui.frame(Duration.ZERO)
                assertEquals(listOf("custom-b"), model.visibleModes().map { it.id })
                assertEquals(listOf(false, true), menu.grid.cards.map { it.shown })
                menu.reviews.show(data.games.first())
                ui.frame(Duration.ZERO)
                host.queue(KeyEvent(Key.ESCAPE, KeyAction.DOWN))
                ui.frame(Duration.ZERO)
                assertFalse(menu.reviews.open)
                assertTrue(ui.diagnostics.messages.isEmpty())
            }
        }
        host.close()
    }

    @Test
    fun rotatesInjectedPromotions() {
        val player = PromotionPlayer(
            listOf(
                Promotion("a", "A", "Go", 2f, 40f), Promotion("b", "B", "Go", 3f, 40f),
                Promotion("c", "C", "Go", 4f, 40f)
            )
        )
        player.advance(1f)
        assertEquals(.5f, player.progress)
        player.advance(1.5f)
        assertEquals(1, player.index)
        assertEquals(.5f / 3, player.progress)
        player.advance(7f)
        assertEquals(0, player.index)
        assertEquals(.25f, player.progress)
    }

    @Test
    fun loadsFixtureContent() {
        val content = MenuData.load()
        assertEquals(97, content.games.size)
        assertEquals(20, content.reviews.size)
        assertTrue(content.games.first { it.id == "chaos" }.name.contains('\n'))
        assertEquals(2, content.banners.size)
        assertEquals(listOf("RU", "US", "UA", "AM", "TR", "KZ", "BY", "DE", "ES"), content.languages)
        (content.games.map { it.icon } + content.banners.map { it.image }).forEach { asset ->
            assertTrue(javaClass.getResource("/cristalix/reference/$asset.png") != null, asset)
        }
    }
}
