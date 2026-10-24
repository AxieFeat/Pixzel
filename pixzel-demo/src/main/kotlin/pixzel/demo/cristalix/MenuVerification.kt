// Pixel geometry, palette and motion timings are authored as visual design data.
@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import arc.Application
import arc.window.KeyAction
import arc.window.KeyModifiers
import arc.window.KeySymbol
import arc.window.KeyboardEvent
import arc.window.PointerButton
import arc.window.PointerMotion
import arc.window.PointerWheel
import arc.window.UnicodeCommit
import pixzel.Ui
import pixzel.component.Element
import pixzel.demo.captureShowcase
import pixzel.surface.Vec2
import java.nio.file.Path

/**
 * Drives the real Arc window event transport and captures the reference menu states.
 */
internal class MenuVerification(
    private val application: Application,
    private val ui: Ui,
    private val menu: CristalixMenu,
    private val model: MenuModel
) {

    private val events get() = application.window.events
    private val window get() = application.window.handle
    private val wool get() = menu.grid.cards.first { it.game.id == "wool" }
    private val primary get() = menu.modal.children.first { it.id == "modal-primary" } as MenuButton
    private var captures = 0

    // Each branch is one scheduled interaction in the native verification fixture.
    @Suppress("CyclomaticComplexMethod")
    fun beforeFrame(frame: Int) {
        if (frame >= 690) {
            beforeExtendedFrame(frame)
            return
        }
        when (frame) {
            100 -> {
                click(menu.toolbar.categories)
            }

            150 -> {
                key(KeySymbol.ESCAPE)
            }

            155 -> {
                move(wool)
            }

            210 -> {
                click(menu.toolbar.favorites)
            }

            260 -> {
                click(menu.toolbar.favorites)
            }

            300 -> {
                click(wool, Vec2(wool.surface.bounds.width - 25f, 25f))
            }

            350 -> {
                click(menu.toolbar.favorites)
            }

            410 -> {
                click(menu.toolbar.favorites)
            }

            445 -> {
                click(menu.toolbar.search)
            }

            450 -> {
                events.enqueue(UnicodeCommit("Prison", events.nextSequence(), System.nanoTime(), window))
            }

            480 -> {
                key(KeySymbol.A, KeyModifiers(control = true))
                key(KeySymbol.BACKSPACE)
            }

            500 -> {
                click(menu.toolbar.reviews)
            }

            550 -> {
                key(KeySymbol.ESCAPE)
            }

            580 -> {
                scrollGrid()
            }

            630 -> {
                application.window.resize(640, 800)
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    fun afterFrame(frame: Int) {
        if (frame >= 690) {
            afterExtendedFrame(frame)
            return
        }
        when (frame) {
            90 -> {
                capture("menu")
            }

            140 -> {
                check(menu.dropdown.open) { "Category button must open the dropdown" }
                capture("dropdown")
            }

            208 -> {
                check(menu.tooltip.show && menu.tooltip.game?.id == "wool") { "Hovered card must show its delayed tooltip" }
                capture("hover")
            }

            250 -> {
                check(model.category == MenuCategory.FAVORITES && model.visibleModes().isEmpty())
                capture("favorites-empty")
            }

            340 -> {
                check("wool" in model.favorites) { "The card's star must toggle favorites" }
            }

            400 -> {
                check(model.visibleModes().map { it.id } == listOf("wool"))
                capture("favorites")
            }

            470 -> {
                check(model.visibleModes().size == 3 && model.query == "Prison") { "Text input must filter modes" }
                capture("search")
            }

            540 -> {
                check(menu.modal.open)
                capture("reviews")
            }

            620 -> {
                check(menu.grid.scrollOffset > 50f) { "Wheel transport must smoothly scroll the grid" }
                capture("scroll")
            }

            675 -> {
                check("wool" in model.favorites && menu.grid.surface.bounds.width < 800f)
                capture("responsive")
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun beforeExtendedFrame(frame: Int) {
        when (frame) {
            690 -> {
                application.window.resize(1600, 900)
            }

            700 -> {
                click(menu.toolbar.categories)
            }

            705 -> {
                key(KeySymbol.DOWN)
                key(KeySymbol.ENTER)
            }

            740 -> {
                click(menu.toolbar.languages)
            }

            745 -> {
                key(KeySymbol.DOWN)
                key(KeySymbol.ENTER)
            }

            780 -> {
                click(menu.toolbar.categories)
            }

            785 -> {
                key(KeySymbol.UP)
                key(KeySymbol.ENTER)
            }

            825 -> {
                click(wool)
            }

            875 -> {
                click(primary)
            }

            975 -> {
                key(KeySymbol.ESCAPE)
            }

            990 -> {
                click(menu.children.first { it.id == "motion" })
            }

            1005 -> {
                click(menu.toolbar.reviews)
            }

            1010 -> {
                click(menu.modal.reviewInput)
            }

            1015 -> {
                events.enqueue(UnicodeCommit("Great pixel menu!", events.nextSequence(), System.nanoTime(), window))
            }

            1020 -> {
                click(primary)
            }

            1040 -> {
                key(KeySymbol.ESCAPE)
            }
        }
    }

    private fun afterExtendedFrame(frame: Int) {
        when (frame) {
            735 -> {
                check(model.category == MenuCategory.HUBS && model.visibleModes().single().id == "hub")
            }

            770 -> {
                check(model.english && menu.toolbar.categories.title.trim() == "Hubs")
                capture("english")
            }

            820 -> {
                check(model.category == MenuCategory.ALL && model.visibleModes().size == gameModes.size)
            }

            870 -> {
                check(menu.modal.open)
                capture("game")
            }

            970 -> {
                check(primary.title == "Play again")
            }

            1000 -> {
                check(model.reducedMotion)
            }

            1030 -> {
                check(menu.modal.open && menu.modal.reviewInput.value.isEmpty())
                capture("review-submitted")
            }
        }
    }

    private fun scrollGrid() {
        move(menu.grid, Vec2(500f, 200f))
        val point = menu.grid.surface.toRoot(Vec2(500f, 200f))
        events.enqueue(
            PointerWheel(
                (point.x * ui.settings.uiScale).toDouble(), (point.y * ui.settings.uiScale).toDouble(),
                0.0, -4.0, events.nextSequence(), System.nanoTime(), window
            )
        )
    }

    private fun move(element: Element, local: Vec2? = null) {
        val point = element.surface.toRoot(local ?: Vec2(element.surface.bounds.width / 2f, element.surface.bounds.height / 2f))
        events.enqueue(
            PointerMotion(
                (point.x * ui.settings.uiScale).toDouble(), (point.y * ui.settings.uiScale).toDouble(),
                events.nextSequence(), System.nanoTime(), window
            )
        )
    }

    private fun click(element: Element, local: Vec2? = null) {
        val point = element.surface.toRoot(local ?: Vec2(element.surface.bounds.width / 2f, element.surface.bounds.height / 2f))
        move(element, local)
        for (action in listOf(KeyAction.PRESS, KeyAction.RELEASE)) {
            events.enqueue(
                PointerButton(
                    0, action, (point.x * ui.settings.uiScale).toDouble(), (point.y * ui.settings.uiScale).toDouble(),
                    KeyModifiers(), events.nextSequence(), System.nanoTime(), window
                )
            )
        }
    }

    private fun key(symbol: KeySymbol, modifiers: KeyModifiers = KeyModifiers()) {
        for (action in listOf(KeyAction.PRESS, KeyAction.RELEASE)) {
            events.enqueue(
                KeyboardEvent(
                    symbol, symbol.ordinal + 1000, 0, action, modifiers,
                    events.nextSequence(), System.nanoTime(), window
                )
            )
        }
    }

    private fun capture(name: String) {
        captureShowcase(application.window.width, application.window.height, Path.of("build", "reports", "cristalix", "$name.png"))
        captures++
    }

    fun finish() {
        check(captures == 12)
        check(ui.diagnostics.messages.isEmpty()) { ui.diagnostics.messages.joinToString { it.message } }
        println(
            "PASS: Cristalix menu input, filters, favorites, reviews, scrolling, resize, " +
                    "keyboard, language and reduced motion; $captures captures"
        )
    }
}
