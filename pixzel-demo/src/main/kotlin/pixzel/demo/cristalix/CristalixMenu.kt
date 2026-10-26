// Pixel geometry, palette and motion timings are authored as visual design data.
@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.Ui
import pixzel.component.ClipPolicy
import pixzel.component.Invalidation
import pixzel.component.Visibility
import pixzel.graphics.Canvas
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
import kotlin.time.Duration

internal class CristalixMenu(owner: Ui, model: MenuModel, private val art: MenuArt) : MenuElement("cristalix-menu", model) {

    val modal = MenuModal(owner, model)
    val reviews = MenuReviews(owner, model, art)
    val dropdown = MenuDropdown(model) { grid.scrollToTop() }
    val toolbar = MenuToolbar(owner, model, ::openDropdown) { reviews.show(null) }
    val banner = MenuBanner(model, art) { modal.show(null) }
    val grid = GameGrid(model, art, banner) { game -> modal.show(game) }
    val tooltip = GameTooltip(model, art)
    private val bannerPresence = MenuSpring(1f, 14f)
    private val emptyPresence = MenuSpring()
    private var pointer = Vec2.ZERO
    private var previousSize = Size.ZERO
    private var previousBannerPresence = -1f
    private var empty = false
    private var panelWidth = 1040f
    private var panelLeft = 0f
    private var toolbarHeight = 66f
    private var gridTop = 352f

    init {
        layout.width = fillAvailable
        layout.height = fillAvailable
        clipPolicy = ClipPolicy.BOUNDS
        pointerPolicy = PointerPolicy.AUTO
        add(grid)
        add(toolbar)
        add(tooltip)
        add(dropdown)
        add(reviews)
        add(modal)
        toolbar.zIndex = 10

        layoutPolicy = positionedLayout { id, size ->
            when (id) {
                "toolbar" -> Rect(panelLeft, 22f, panelWidth, toolbarHeight)
                "game-grid" -> Rect(
                    panelLeft, 0f, panelWidth, max(1f, size.height)
                )

                "dropdown" -> Rect(
                    dropdown.anchor.x.coerceIn(4f, max(4f, size.width - dropdown.menuWidth - 4f)),
                    dropdown.anchor.y, dropdown.menuWidth, dropdown.menuHeight
                )

                "tooltip" -> tooltipBounds(size).let { Rect(0f, 0f, it.width, it.height) }
                else -> Rect(0f, 0f, size.width, size.height)
            }
        }

        onEvent(EventPhase.CAPTURE) { routed ->
            when (val event = routed.event) {
                is PointerEvent -> {
                    pointer = routed.localPosition ?: pointer
                    if (dropdown.open && event.action == PointerAction.DOWN) {
                        val inside = dropdown.surface.mapRect(dropdownRect()).contains(pointer)
                        val onTrigger = listOf(toolbar.categories, toolbar.languages).any { button ->
                            button.surface.mapRect(Rect(0f, 0f, button.surface.bounds.width, button.surface.bounds.height)).contains(pointer)
                        }
                        if (!inside && !onTrigger) dropdown.dismiss()
                    }
                    if (event.action == PointerAction.UP && event.button == 1 && !reviews.open) {
                        grid.hoveredCard?.let {
                            reviews.show(it.game)
                            routed.consume()
                        }
                    }
                }

                is WheelEvent -> {
                    if (!modal.open && !dropdown.open && !reviews.open) {
                        grid.scrollBy(-event.delta.y * if (event.unit == WheelUnit.LINE) 58f else 1f)
                        tooltip.show = false
                        routed.consume()
                    }
                }

                is KeyEvent -> {
                    if (event.action == KeyAction.DOWN) {
                        when {
                            event.key == Key.ESCAPE && modal.open -> {
                                modal.dismiss()
                                routed.consume()
                                routed.preventDefault()
                            }

                            event.key == Key.ESCAPE && reviews.open -> {
                                reviews.dismiss()
                                routed.consume()
                                routed.preventDefault()
                            }

                            event.key == Key.ESCAPE && dropdown.open -> {
                                dropdown.dismiss()
                                routed.consume()
                                routed.preventDefault()
                            }

                            dropdown.open && event.key == Key.DOWN -> {
                                dropdown.move(1)
                                routed.consume()
                                routed.preventDefault()
                            }

                            dropdown.open && event.key == Key.UP -> {
                                dropdown.move(-1)
                                routed.consume()
                                routed.preventDefault()
                            }

                            dropdown.open && event.key == Key.ENTER -> {
                                dropdown.choose()
                                routed.consume()
                                routed.preventDefault()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun dropdownRect() = Rect(0f, 0f, dropdown.surface.bounds.width, dropdown.surface.bounds.height)

    private fun openDropdown(kind: String, position: Vec2) {
        tooltip.show = false
        dropdown.show(kind, position)
        invalidate(Invalidation.MEASURE)
    }

    private fun tooltipBounds(size: Size): Rect {
        val width = 444f.coerceAtMost(size.width - 24f)
        val x = if (pointer.x + width + 32f > size.width) pointer.x - width - 26f else pointer.x + 26f
        val height = tooltip.contentHeight
        return Rect(
            x.coerceIn(8f, max(8f, size.width - width - 8f)),
            (pointer.y - 26f).coerceIn(8f, max(8f, size.height - height - 10f)), width, height
        )
    }

    override fun update(delta: Duration): Boolean {
        val seconds = delta.inWholeNanoseconds / 1_000_000_000f
        val size = logicalViewport()
        val wasEmpty = empty
        empty = model.visibleModes().isEmpty()
        bannerPresence.target = if (model.category == MenuCategory.ALL && model.query.isBlank()) 1f else 0f
        emptyPresence.target = if (empty) 1f else 0f
        if (model.reducedMotion) {
            bannerPresence.snap()
            emptyPresence.snap()
        }
        bannerPresence.step(seconds)
        val emptyMoving = emptyPresence.step(seconds)

        val viewportChanged = size != previousSize || bannerPresence.value != previousBannerPresence
        if (viewportChanged) {
            updateViewport(size)
        }

        val catalogueVisibility = if (reviews.open) Visibility.HIDDEN else Visibility.VISIBLE
        if (grid.visibility != catalogueVisibility) invalidate()
        grid.visibility = catalogueVisibility
        toolbar.visibility = catalogueVisibility
        banner.opacity = bannerPresence.value.coerceIn(0f, 1f)
        banner.pointerPolicy = if (banner.opacity > 0.8f) PointerPolicy.AUTO else PointerPolicy.NONE
        banner.focusable = banner.opacity > 0.8f
        updateHover()
        if (tooltip.show) {
            val rect = tooltipBounds(size)
            // Pointer motion changes placement only; it must not remeasure the entire menu.
            tooltip.paintTransform = Transform.translation(rect.x, rect.y)
        }
        return emptyMoving || wasEmpty != empty
    }

    private fun logicalViewport() = ui?.let { owner ->
        owner.host.metrics.logicalSize.let { Size(it.width / owner.settings.uiScale, it.height / owner.settings.uiScale) }
    } ?: Size.ZERO

    private fun updateViewport(size: Size) {
        panelWidth = 1044f.coerceAtMost((size.width - 32f).coerceAtLeast(360f))
        panelLeft = (size.width - panelWidth) / 2f
        toolbarHeight = if (panelWidth < 800f) 108f else 66f
        gridTop = 22f + toolbarHeight + 14f
        grid.leadingSpace = gridTop + 248f * bannerPresence.value
        grid.bannerTop = 22f + toolbarHeight + 28f
        previousSize = size
        previousBannerPresence = bannerPresence.value
        invalidate(Invalidation.MEASURE)
    }

    private fun updateHover() {
        val overlayOpen = dropdown.open || modal.open || reviews.open
        val outsideScrollRange = grid.scrollOffset > grid.maximumScroll + 1f
        val hovered = if (overlayOpen || outsideScrollRange) null else grid.hoveredCard
        val changedGame = hovered != null && hovered.game != tooltip.game
        if (hovered != null) tooltip.game = hovered.game
        val show = hovered != null && !overlayOpen
        if (tooltip.show != show || changedGame) invalidate(Invalidation.MEASURE)
        tooltip.show = show
    }

    override fun paint(canvas: Canvas) {
        if (reviews.open) return
        canvas.rect(0f, 0f, bounds.width, bounds.height, 0x0C1220.rgb)
        art.background?.let { image ->
            val scale = max(bounds.width / image.size.width, bounds.height / image.size.height) * 1.045f
            val width = image.size.width * scale
            val height = image.size.height * scale
            val drift = 0f
            val mouseX = 0f
            canvas.emit(
                DrawImage(
                    image, Rect(0f, 0f, image.size.width, image.size.height),
                    Rect((bounds.width - width) / 2 + mouseX + drift, (bounds.height - height) / 2 - drift * 0.3f, width, height),
                    sampling = Sampling.LINEAR
                )
            )
        }
        canvas.rect(0f, 0f, bounds.width, bounds.height, 0x000000FF.rgba.alpha(0.995f))
        canvas.fill(
            Rectangle, bounds, LinearGradient(
                Vec2(0f, 0f), Vec2(0f, bounds.height),
                listOf(ColorStop(0f, 0x03081625.rgba), ColorStop(1f, 0x03030B80.rgba))
            )
        )
        if (emptyPresence.value > 0.01f) {
            with(canvas) {
                val favorite = model.category == MenuCategory.FAVORITES
                val color = 0xFFFFFFFF.rgba.alpha(emptyPresence.value)
                val y = 280f
                label(if (favorite) "Упс!" else "Ой...", bounds.width / 2, y, 22f, color, centered = true)
                label(
                    if (favorite) model.label("У вас нет избранных", "No favorite games yet") else
                        "А где все игры?", bounds.width / 2, y + 35f, 22f, color, centered = true
                )
                label(
                    if (favorite) model.label("режимов, выберите их в играх.", "Choose a star on a game card.") else "",
                    bounds.width / 2, y + 57f, 22f, color, centered = true
                )
            }
        }

    }
}
