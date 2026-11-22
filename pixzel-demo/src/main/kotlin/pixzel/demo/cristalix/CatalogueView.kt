@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.Ui
import pixzel.component.Button
import pixzel.component.ClipPolicy
import pixzel.component.Element
import pixzel.graphics.DrawImage
import pixzel.graphics.Sampling
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.input.PointerPolicy
import pixzel.layout.ChildPlacement
import pixzel.layout.Constraints
import pixzel.layout.LayoutPlan
import pixzel.layout.LayoutPolicy
import pixzel.layout.unit.sp
import pixzel.surface.Insets
import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.surface.Transform
import pixzel.surface.Vec2
import pixzel.text.TextRequest
import pixzel.text.TextStyle
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.cos
import kotlin.math.sin

internal data class CatalogueStyle(val gap: Float = 14.5f, val aspect: Float = 1.4f, val minimumColumn: Float = 176f, val maximumColumns: Int = 6)

/**
 * Handles to composed standard controls; this is not a custom Pixzel Element.
 */
internal class GameTile(val game: GameMode, val root: Button, val favorite: Button, val motion: GameCardPosition = GameCardPosition()) {

    var shown = true
    val hovered get() = root.state.hovered || root.state.focusVisible || favorite.state.hovered || favorite.state.focusVisible
}

internal class CatalogueView(val root: Element, val cards: List<GameTile>, val scroll: MenuSpring = MenuSpring(frequency = 17f)) {

    var leadingSpace = 350f
    var bannerTop = 116f
    var contentHeight = 0f
    var visibleCount = 0
    val hoveredCard get() = cards.firstOrNull { it.shown && it.hovered }
    val scrollOffset get() = scroll.value
    val maximumScroll get() = (contentHeight + leadingSpace - root.surface.bounds.height + 20f).coerceAtLeast(0f)
    fun scrollBy(amount: Float) {
        scroll.target = (scroll.target + amount).coerceIn(0f, maximumScroll)
    }

    fun scrollToTop() {
        scroll.target = 0f
    }
}

internal fun Ui.gameCatalogue(
    model: MenuModel, art: MenuArt, banner: Button,
    style: CatalogueStyle = CatalogueStyle(), openGame: (GameMode) -> Unit
): CatalogueView {
    val ui = this
    val root = ui.compose("game-grid")
    val cards = model.data.games.map { gameTile(ui, model, art, it, openGame).also { tile -> root.add(tile.root) } }
    root.add(banner)
    root.clipPolicy = ClipPolicy.BOUNDS
    val view = CatalogueView(root, cards)
    root.layoutPolicy = LayoutPolicy { children, constraints, _ ->
        val columns = columns(constraints.maxWidth, style)
        val width = (constraints.maxWidth - (columns - 1) * style.gap) / columns
        LayoutPlan(Size(constraints.maxWidth, constraints.maxHeight), children.map { child ->
            val size = if (child === banner) Size(constraints.maxWidth, 204f) else Size(width, width * style.aspect)
            ChildPlacement(child.measure(Constraints.fixed(size)), Vec2.ZERO)
        })
    }
    var previous: List<Any?>? = null
    root.onFrame { seconds ->
        val visible = model.visibleModes()
        val signature = listOf(visible, root.surface.bounds.size, view.leadingSpace)
        if (signature != previous) {
            if (previous?.firstOrNull() != visible) view.scrollToTop()
            setTileDestinations(view, visible, style)
            previous = signature
        }
        view.scroll.target = view.scroll.target.coerceIn(0f, view.maximumScroll)
        if (model.reducedMotion) view.scroll.snap()
        val moving = view.scroll.step(seconds)
        banner.paintTransform = Transform.translation(0f, view.bannerTop - view.scrollOffset)
        placeTiles(view, seconds, model.reducedMotion) || moving
    }
    return view
}

private fun columns(width: Float, style: CatalogueStyle) = floor((width + style.gap) / style.minimumColumn).toInt().coerceIn(2, style.maximumColumns)

private fun setTileDestinations(view: CatalogueView, visible: List<GameMode>, style: CatalogueStyle) {
    val columns = columns(view.root.surface.bounds.width, style)
    val width = (view.root.surface.bounds.width - (columns - 1) * style.gap) / columns
    val height = width * style.aspect
    view.contentHeight = (ceil(visible.size.toFloat() / columns) * (height + style.gap) - style.gap).coerceAtLeast(0f)
    view.visibleCount = visible.size
    view.cards.forEach { tile ->
        val index = visible.indexOf(tile.game)
        val wasShown = tile.shown
        tile.shown = index >= 0
        if (index >= 0) tile.motion.destination(
            index % columns * (width + style.gap),
            view.leadingSpace + index / columns * (height + style.gap), index, !wasShown
        )
    }
}

private fun placeTiles(view: CatalogueView, seconds: Float, reducedMotion: Boolean): Boolean {
    var moving = false
    view.cards.forEach { tile ->
        moving = tile.motion.advance(seconds, tile.shown, reducedMotion) || moving
        val point = tile.motion.at(view.scrollOffset)
        tile.root.opacity = tile.motion.opacity
        tile.root.showIf(tile.root.opacity > 0f && point.y + tile.root.surface.bounds.height > 0f && point.y < view.root.surface.bounds.height)
        tile.root.paintTransform = Transform.translation(point.x, point.y)
        tile.root.pointerPolicy = if (tile.shown && tile.root.opacity > .5f) PointerPolicy.AUTO else PointerPolicy.NONE
        tile.root.focusable = tile.shown && tile.root.opacity > .5f
    }
    return moving
}

internal fun gameTile(ui: Ui, model: MenuModel, art: MenuArt, game: GameMode, play: (GameMode) -> Unit): GameTile {
    val root = ui.components.button("game-${game.id}").apply {
        layout.padding = Insets.ZERO
        foreground = null
        semantics = semantics.copy(label = game.name.replace('\n', ' '))
        onClick { play(game) }
    }
    val favorite = ui.components.button("favorite-${game.id}").apply {
        layout.padding = Insets.ZERO
        foreground = null
        semantics = semantics.copy(label = "В избранное: ${game.name.replace('\n', ' ')}")
        onClick { model.toggleFavorite(game.id) }
    }
    root.add(favorite)
    root.arrange { place(favorite) { Rect(it.width / 2 - 25f, 0f, 50f, 45f) } }
    val tile = GameTile(game, root, favorite)
    val pulse = MenuSpring(frequency = 19f)
    var lastFavorite = game.id in model.favorites
    root.draw(ui) { paintGameTile(tile, art) }
    favorite.draw(ui) {
        val size = 20f + pulse.value * 9f
        star((bounds.width - size) / 2, 16f - pulse.value * 4f, size, if (lastFavorite) 0xFFDA65.rgb else 0xFFFFFF.rgb)
        if (pulse.value > .03f) repeat(5) { index ->
            val distance = (1f - pulse.value) * 35f
            val angle = index * 1.256f
            rect(bounds.width / 2 + cos(angle) * distance, 24f + sin(angle) * distance, 3f, 3f, 0xFFE5A0FF.rgba.alpha(pulse.value))
        }
    }
    favorite.onFrame { seconds ->
        val saved = game.id in model.favorites
        if (saved != lastFavorite) {
            pulse.snap(1f); pulse.target = 0f; lastFavorite = saved
        }
        if (model.reducedMotion) pulse.snap(0f)
        pulse.step(seconds)
    }
    root.onFrame {
        favorite.opacity = if (tile.shown && tile.hovered) 1f else 0f
        favorite.focusable = tile.shown && tile.hovered
        root.zIndex = if (tile.hovered) 1 else 0
        false
    }
    root.observe({ game.id in model.favorites }) { favorite.invalidate() }
    return tile
}

private fun PixelCanvas.paintGameTile(tile: GameTile, art: MenuArt) {
    val game = tile.game
    val base = game.color.rgb
    pixelPanel(bounds, base, base.shade(1.52f), 6f, 2f)
    val size = bounds.width * .88f
    val image = art.icons.getValue(game.icon)
    emit(
        DrawImage(
            image,
            Rect(0f, 0f, image.size.width, image.size.height),
            Rect((bounds.width - size) / 2, 14f, size, size),
            sampling = Sampling.NEAREST
        )
    )
    val lines = game.name.split('\n')
    lines.forEachIndexed { index, line ->
        val measured = ui.text.layout(TextRequest(line, TextStyle(size = 22.sp))).size.width
        label(
            line, Rect(5f, bounds.height - 60f - (lines.size - 1 - index) * 19f, bounds.width - 10f, 27f),
            22f * ((bounds.width - 10f) / measured.coerceAtLeast(1f)).coerceAtMost(1f)
        )
    }
    label("Онлайн ${game.online}", Rect(7f, bounds.height - 33f, bounds.width - 14f, 25f), 22f, 0xFFFFFFA0.rgba)
    if (tile.hovered) {
        pixelPanel(bounds, 0x000000E6.rgba, base.shade(.35f), 6f, 2f)
        label("ИГРАТЬ", Rect(5f, bounds.height / 2 - 24f, bounds.width - 10f, 48f), 31f)
    } else if (game.badge.isNotEmpty()) {
        val width = ui.text.layout(TextRequest(game.badge, TextStyle(size = 20.sp))).size.width + 10f
        val badge = Rect(bounds.width - width - 10f, 10f, width, 25f)
        pixelPanel(badge, 0xC32621.rgb, 0xFA685E.rgb, 3f, 2f)
        label(game.badge, badge, 20f)
    }
}
