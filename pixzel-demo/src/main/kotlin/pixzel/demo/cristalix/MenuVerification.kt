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
import kotlin.math.abs
import pixzel.graphics.Canvas
import pixzel.graphics.DrawCommand
import pixzel.graphics.DrawImage
import pixzel.graphics.Fill
import pixzel.graphics.Solid
import pixzel.graphics.rgb

/**
 * Drives the real Arc window event transport and captures the reference menu states.
 */
@Suppress("TooManyFunctions") // Each helper describes a separate native interaction assertion.
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
                click(wool, Vec2(wool.surface.bounds.width / 2, 25f))
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

            155 -> {
                check(wool.hovered && wool.hover.value == 1f && menu.tooltip.show && menu.tooltip.opacity == 1f) {
                    "Card hover and tooltip must appear on the first input frame"
                }
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
                verifyHiddenFavorite()
                capture("favorites")
            }

            470 -> {
                check(model.visibleModes().size == 3 && model.query == "Prison") { "Text input must filter modes" }
                capture("search")
            }

            540 -> {
                check(menu.reviews.open && menu.reviews.game == null)
                capture("reviews")
            }

            620 -> {
                check(menu.grid.scrollOffset > 50f) { "Wheel transport must smoothly scroll the grid" }
                verifySharedScroll()
                capture("scroll")
            }

            675 -> {
                check("wool" in model.favorites && menu.grid.surface.bounds.width < 800f)
                capture("responsive")
            }
        }
    }

    private fun verifyHiddenFavorite() {
        val commands = mutableListOf<DrawCommand>()
        wool.paint(object : Canvas {
            override fun emit(command: DrawCommand) {
                commands += command
            }
        })
        check(commands.filterIsInstance<DrawImage>().none { "star" in it.image.id } &&
                commands.filterIsInstance<Fill>().none { (it.brush as? Solid)?.color == 0xFFDA65.rgb }) {
            "A saved favorite must not display a star when it is not hovered"
        }
    }

    private fun verifySharedScroll() {
        val first = menu.grid.cards.first()
        check(abs(first.surface.toRoot(Vec2.ZERO).y + menu.grid.scrollOffset - menu.grid.leadingSpace) < .01f)
        check(abs(menu.banner.surface.toRoot(Vec2.ZERO).y + menu.grid.scrollOffset - menu.grid.bannerTop) < .01f) {
            "Banner and cards must share exactly the same scroll displacement"
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
                model.reducedMotion = true
            }

            1005 -> {
                click(menu.toolbar.reviews)
            }

            1010 -> {
                menu.reviews.show(wool.game)
                menu.reviews.composer.show(wool.game)
                click(menu.reviews.composer.input)
            }

            1015 -> {
                events.enqueue(UnicodeCommit("Great pixel menu!", events.nextSequence(), System.nanoTime(), window))
            }

            1020 -> {
                click(menu.reviews.composer.children.first { it.id == "compose-yes" })
                click(menu.reviews.composer.children.first { it.id == "compose-publish" })
            }

            1040 -> {
                key(KeySymbol.ESCAPE)
            }
        }
    }

    private fun afterExtendedFrame(frame: Int) {
        when (frame) {
            735 -> {
                check(model.category == MenuCategory.HUBS && model.visibleModes().map { it.id } == listOf("hub", "hub3"))
            }

            770 -> {
                check(model.language == "US" && menu.toolbar.languages.title.trim() == "US")
                capture("english")
            }

            820 -> {
                check(model.category == MenuCategory.ALL && model.visibleModes().isNotEmpty())
            }

            870 -> {
                check(menu.modal.open)
                capture("game")
            }

            970 -> {
                check(primary.title == "Ещё раз")
            }

            1000 -> {
                check(model.reducedMotion)
            }

            1030 -> {
                check(menu.reviews.open && !menu.reviews.composer.open && menu.reviews.composer.input.value.isEmpty())
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
