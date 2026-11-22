@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.Ui
import pixzel.component.Button
import pixzel.component.ClipPolicy
import pixzel.component.Invalidation
import pixzel.graphics.ColorStop
import pixzel.graphics.DrawImage
import pixzel.graphics.LinearGradient
import pixzel.graphics.Rectangle
import pixzel.graphics.Sampling
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.input.EventPhase
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.PointerPolicy
import pixzel.input.WheelEvent
import pixzel.input.WheelUnit
import pixzel.layout.unit.fillAvailable
import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.surface.Transform
import pixzel.surface.Vec2
import kotlin.math.max

internal data class NavigatorStyle(
    val width: Float = 1044f, val top: Float = 22f,
    val bannerSpace: Float = 248f, val tooltipWidth: Float = 444f, val catalogue: CatalogueStyle = CatalogueStyle()
)

internal data class MenuActions(val play: (GameMode) -> Unit = {})

/**
 * A composition root, not a new element type. Views use ordinary Pixzel components.
 */
internal class CristalixMenu(
    private val ui: Ui, private val model: MenuModel, private val art: MenuArt,
    private val style: NavigatorStyle = NavigatorStyle(), actions: MenuActions = MenuActions()
) {

    val root = ui.compose("cristalix-menu")
    val modal = gameDialog(ui, model, actions.play)
    val reviews = reviewsPage(ui, model, art)
    val dropdown = ChoicePopup(ui)
    val toolbar = navigatorToolbar(ui, model, ::openFilter) { reviews.show(null) }
    val banner = promotionCarousel(ui, model, art) { modal.show(null) }
    val grid = ui.gameCatalogue(model, art, banner, style.catalogue) { modal.show(it) }
    val tooltip = gameTooltip(ui, art)
    private val bannerPresence = MenuSpring(1f, 14f)
    private val emptyPresence = MenuSpring()
    private var pointer = Vec2.ZERO
    private var previousSize = Size.ZERO
    private var previousBannerPresence = -1f
    private var panelWidth = style.width
    private var panelLeft = 0f
    private var toolbarHeight = 66f
    private val overlayOpen get() = dropdown.open || modal.open || reviews.open

    init {
        root.layout.width = fillAvailable
        root.layout.height = fillAvailable
        root.clipPolicy = ClipPolicy.BOUNDS
        listOf(grid.root, toolbar.root, tooltip.root, dropdown.root, reviews.root, modal.root).forEach(root::add)
        toolbar.root.zIndex = 10
        root.arrange {
            place(toolbar.root) { Rect(panelLeft, style.top, panelWidth, toolbarHeight) }
            place(grid.root) { Rect(panelLeft, 0f, panelWidth, max(1f, it.height)) }
            place(dropdown.root) {
                Rect(
                    dropdown.anchor.x.coerceIn(4f, max(4f, it.width - dropdown.menuWidth - 4f)),
                    dropdown.anchor.y, dropdown.menuWidth, dropdown.menuHeight
                )
            }
            place(tooltip.root) { val rect = tooltipBounds(it); Rect(0f, 0f, rect.width, rect.height) }
        }
        root.onFrame(::update)
        root.onEvent(EventPhase.CAPTURE) { routed ->
            when (val event = routed.event) {
                is PointerEvent -> {
                    pointer = routed.localPosition ?: pointer
                    if (dropdown.open && event.action == PointerAction.DOWN) {
                        val inside = dropdown.root.surface.mapRect(dropdown.root.bounds).contains(pointer)
                        val trigger = listOf(toolbar.categories, toolbar.languages).any { it.surface.mapRect(it.bounds).contains(pointer) }
                        if (!inside && !trigger) dropdown.dismiss()
                    }
                    if (event.action == PointerAction.UP && event.button == 1 && !overlayOpen) {
                        grid.hoveredCard?.let { reviews.show(it.game); routed.consume() }
                    }
                }

                is WheelEvent -> {
                    if (!overlayOpen) {
                        grid.scrollBy(-event.delta.y * if (event.unit == WheelUnit.LINE) 58f else 1f)
                        tooltip.show(null)
                        routed.consume()
                        routed.preventDefault()
                    }
                }

                is KeyEvent -> {
                    if (event.action == KeyAction.DOWN && handleKey(event.key)) {
                        routed.consume()
                        routed.preventDefault()
                    }
                }
            }
        }
        root.draw(ui) { paintBackground(art, bounds, emptyPresence.value, model.category == MenuCategory.FAVORITES) }
    }

    private fun handleKey(key: Key): Boolean = when {
        key == Key.ESCAPE && modal.open -> {
            modal.dismiss(); true
        }

        key == Key.ESCAPE && reviews.open -> {
            reviews.dismiss(); true
        }

        key == Key.ESCAPE && dropdown.open -> {
            dropdown.dismiss(); true
        }

        dropdown.open && key == Key.DOWN -> {
            dropdown.move(1); true
        }

        dropdown.open && key == Key.UP -> {
            dropdown.move(-1); true
        }

        dropdown.open && key == Key.ENTER -> {
            dropdown.choose(); true
        }

        else -> {
            false
        }
    }

    private fun openFilter(languages: Boolean, button: Button) {
        tooltip.show(null)
        val values = if (languages) model.data.languages else
            (listOf(MenuCategory.ALL, MenuCategory.HUBS, MenuCategory.TESTING) + model.data.games.map { it.category }).distinct()
        dropdown.show(
            values, if (languages) model.language else model.category,
            button.surface.toRoot(Vec2(0f, 36f)), if (languages) 82f else 166f
        ) {
            if (languages) model.language = it else model.category = it
            grid.scrollToTop()
        }
    }

    private fun update(seconds: Float): Boolean {
        model.query = toolbar.search.value
        val size = root.bounds.size
        bannerPresence.target = if (model.category == MenuCategory.ALL && model.query.isBlank()) 1f else 0f
        emptyPresence.target = if (model.visibleModes().isEmpty()) 1f else 0f
        if (model.reducedMotion) {
            bannerPresence.snap(); emptyPresence.snap()
        }
        val moving = bannerPresence.step(seconds) or emptyPresence.step(seconds)
        updateViewport(size)
        grid.root.showIf(!reviews.open)
        toolbar.root.showIf(!reviews.open)
        banner.opacity = bannerPresence.value.coerceIn(0f, 1f)
        banner.pointerPolicy = if (banner.opacity > .8f) PointerPolicy.AUTO else PointerPolicy.NONE
        banner.focusable = banner.opacity > .8f
        updateHover(size)
        return moving
    }

    private fun updateViewport(size: Size) {
        if (size != previousSize || bannerPresence.value != previousBannerPresence) {
            panelWidth = style.width.coerceAtMost((size.width - 32f).coerceAtLeast(360f))
            panelLeft = (size.width - panelWidth) / 2
            toolbarHeight = if (panelWidth < 800f) 108f else 66f
            grid.leadingSpace = style.top + toolbarHeight + 14f + style.bannerSpace * bannerPresence.value
            grid.bannerTop = style.top + toolbarHeight + 28f
            previousSize = size
            previousBannerPresence = bannerPresence.value
            root.invalidate(Invalidation.MEASURE)
        }
    }

    private fun updateHover(size: Size) {
        val hovered = if (overlayOpen || grid.scrollOffset > grid.maximumScroll + 1f) null else grid.hoveredCard
        val previous = tooltip.game
        tooltip.show(hovered?.game)
        if (previous != tooltip.game) root.invalidate(Invalidation.MEASURE)
        if (tooltip.show) tooltipBounds(size).let { tooltip.root.paintTransform = Transform.translation(it.x, it.y) }
    }

    private fun tooltipBounds(size: Size): Rect {
        val width = minOf(style.tooltipWidth, size.width - 24f)
        val x = if (pointer.x + width + 32f > size.width) pointer.x - width - 26f else pointer.x + 26f
        val height = tooltip.contentHeight
        return Rect(
            x.coerceIn(8f, max(8f, size.width - width - 8f)),
            (pointer.y - 26f).coerceIn(8f, max(8f, size.height - height - 10f)), width, height
        )
    }
}

private fun PixelCanvas.paintBackground(art: MenuArt, bounds: Rect, empty: Float, favorite: Boolean) {
    rect(0f, 0f, bounds.width, bounds.height, 0x0C1220.rgb)
    art.background?.let { image ->
        val scale = max(bounds.width / image.size.width, bounds.height / image.size.height) * 1.045f
        val width = image.size.width * scale
        val height = image.size.height * scale
        emit(
            DrawImage(
                image, Rect(0f, 0f, image.size.width, image.size.height),
                Rect((bounds.width - width) / 2, (bounds.height - height) / 2, width, height), sampling = Sampling.LINEAR
            )
        )
    }
    rect(0f, 0f, bounds.width, bounds.height, 0x000000FF.rgba.alpha(.995f))
    fill(
        Rectangle, bounds, LinearGradient(
            Vec2.ZERO, Vec2(0f, bounds.height),
            listOf(ColorStop(0f, 0x03081625.rgba), ColorStop(1f, 0x03030B80.rgba))
        )
    )
    if (empty > .01f) {
        val color = 0xFFFFFF.rgb.alpha(empty)
        label(if (favorite) "Упс!" else "Ой...", bounds.width / 2, 280f, 22f, color, centered = true)
        label(if (favorite) "У вас нет избранных" else "А где все игры?", bounds.width / 2, 315f, 22f, color, centered = true)
        if (favorite) label("режимов, выберите их в играх.", bounds.width / 2, 337f, 22f, color, centered = true)
    }
}
