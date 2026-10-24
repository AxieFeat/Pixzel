// Pixel geometry, palette and motion timings are authored as visual design data.
@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.Ui
import pixzel.component.ClipPolicy
import pixzel.component.Invalidation
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
import kotlin.math.sin
import kotlin.time.Duration

internal class CristalixMenu(owner: Ui, model: MenuModel, private val art: MenuArt) : MenuElement("cristalix-menu", model) {

    val modal = MenuModal(owner, model)
    val dropdown = MenuDropdown(model) { grid.scrollToTop() }
    val toolbar = MenuToolbar(owner, model, ::openDropdown) { modal.show(null, true) }
    val banner = MenuBanner(model, art) { modal.show(null) }
    val grid = GameGrid(model, art) { game -> modal.show(game) }
    val tooltip = GameTooltip(model)
    private val entrance = MenuSpring(frequency = 10f).apply { target = 1f }
    private val bannerPresence = MenuSpring(1f, 14f)
    private val emptyPresence = MenuSpring()
    private var pointer = Vec2.ZERO
    private var clock = 0f
    private var hoverTime = 0f
    private var hoveredId: String? = null
    private var previousSize = Size.ZERO
    private var previousBannerPresence = -1f
    private var previousPointer = Vec2.ZERO
    private var empty = false
    private var panelWidth = 1040f
    private var panelLeft = 0f
    private var toolbarHeight = 66f
    private var gridTop = 352f
    private val motionButton = MenuButton("motion", model, "Анимации: вкл") {
        model.reducedMotion = !model.reducedMotion
    }

    init {
        layout.width = fillAvailable
        layout.height = fillAvailable
        clipPolicy = ClipPolicy.BOUNDS
        pointerPolicy = PointerPolicy.AUTO
        add(banner)
        add(grid)
        add(toolbar)
        add(motionButton)
        add(tooltip)
        add(dropdown)
        add(modal)
        toolbar.zIndex = 10
        motionButton.zIndex = 11

        layoutPolicy = positionedLayout { id, size ->
            when (id) {
                "toolbar" -> Rect(panelLeft, 24f, panelWidth, toolbarHeight)
                "banner" -> Rect(panelLeft, 24f + toolbarHeight + 28f, panelWidth, 204f)
                "game-grid" -> Rect(
                    panelLeft, gridTop, panelWidth,
                    max(1f, size.height - gridTop - if (size.width < 1280f) 56f else 0f)
                )

                "motion" -> Rect(18f, size.height - 48f, 172f, 31f)
                "dropdown" -> Rect(
                    dropdown.anchor.x.coerceIn(4f, max(4f, size.width - dropdown.menuWidth - 4f)),
                    dropdown.anchor.y, dropdown.menuWidth, dropdown.menuHeight
                )

                "tooltip" -> tooltipBounds(size)
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
                    if (event.action == PointerAction.UP && event.button == 1) {
                        grid.hoveredCard?.let {
                            modal.show(it.game, true)
                            routed.consume()
                        }
                    }
                }

                is WheelEvent -> {
                    if (!modal.open && !dropdown.open) {
                        grid.scrollBy(-event.delta.y * if (event.unit == WheelUnit.LINE) 58f else 1f)
                        tooltip.show = false
                        hoverTime = 0f
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
        val width = 460f.coerceAtMost(size.width - 24f)
        val x = if (pointer.x + width + 28f > size.width) pointer.x - width - 18f else pointer.x + 20f
        return Rect(
            x.coerceIn(8f, max(8f, size.width - width - 8f)),
            (pointer.y + 20f).coerceIn(8f, max(8f, size.height - 310f)), width, 300f
        )
    }

    override fun update(delta: Duration): Boolean {
        val seconds = delta.inWholeNanoseconds / 1_000_000_000f
        if (!model.reducedMotion) clock += seconds
        val size = ui?.host?.metrics?.logicalSize?.let { Size(it.width / ui!!.settings.uiScale, it.height / ui!!.settings.uiScale) } ?: Size.ZERO
        empty = model.visibleModes().isEmpty()
        bannerPresence.target = if (model.category == MenuCategory.ALL && model.query.isBlank()) 1f else 0f
        emptyPresence.target = if (empty) 1f else 0f
        if (model.reducedMotion) {
            entrance.snap()
            bannerPresence.snap()
            emptyPresence.snap()
        }
        entrance.step(seconds)
        bannerPresence.step(seconds)
        emptyPresence.step(seconds)

        if (size != previousSize || bannerPresence.value != previousBannerPresence || pointer != previousPointer) {
            panelWidth = 1040f.coerceAtMost((size.width - 32f).coerceAtLeast(360f))
            panelLeft = (size.width - panelWidth) / 2f
            toolbarHeight = if (panelWidth < 800f) 108f else 66f
            gridTop = 24f + toolbarHeight + 28f + 232f * bannerPresence.value
            previousSize = size
            previousBannerPresence = bannerPresence.value
            previousPointer = pointer
            invalidate(Invalidation.MEASURE)
        }

        toolbar.opacity = entrance.value.coerceIn(0f, 1f)
        toolbar.paintTransform = Transform.translation(0f, -14f * (1f - toolbar.opacity))
        banner.opacity = (bannerPresence.value * entrance.value).coerceIn(0f, 1f)
        banner.pointerPolicy = if (banner.opacity > 0.8f) PointerPolicy.AUTO else PointerPolicy.NONE
        banner.focusable = banner.opacity > 0.8f
        banner.paintTransform = Transform.translation(0f, (1f - banner.opacity) * 12f)
        motionButton.title = model.label("Анимации: ", "Motion: ") + if (model.reducedMotion) model.label("выкл", "off") else model.label("вкл", "on")
        motionButton.opacity = 0.7f

        updateHover(seconds)
        invalidate()
        return true
    }

    private fun updateHover(seconds: Float) {
        val hovered = if (dropdown.open || modal.open || grid.scrollOffset > grid.maximumScroll + 1f) null else grid.hoveredCard
        if (hovered?.game?.id != hoveredId) {
            hoveredId = hovered?.game?.id
            hoverTime = 0f
        } else {
            hoverTime += seconds
        }
        if (hovered != null) tooltip.game = hovered.game
        tooltip.show = hovered != null && hoverTime > 0.45f && !modal.open && !dropdown.open
    }

    override fun paint(canvas: Canvas) {
        canvas.rect(0f, 0f, bounds.width, bounds.height, 0x0C1220.rgb)
        art.background?.let { image ->
            val scale = max(bounds.width / image.size.width, bounds.height / image.size.height) * 1.045f
            val width = image.size.width * scale
            val height = image.size.height * scale
            val drift = if (model.reducedMotion) 0f else sin(clock * 0.16f) * 7f
            val mouseX = if (model.reducedMotion || bounds.width == 0f) 0f else (pointer.x / bounds.width - 0.5f) * 12f
            canvas.emit(
                DrawImage(
                    image, Rect(0f, 0f, image.size.width, image.size.height),
                    Rect((bounds.width - width) / 2 + mouseX + drift, (bounds.height - height) / 2 - drift * 0.3f, width, height),
                    sampling = Sampling.LINEAR
                )
            )
        }
        canvas.rect(0f, 0f, bounds.width, bounds.height, 0x030509FF.rgba.alpha(0.72f + emptyPresence.value * 0.05f))
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
                val y = if (favorite) 280f else gridTop + 88f
                label(model.label("Упс!", "Oops!"), bounds.width / 2, y, 30f, color, centered = true, shadow = true)
                label(
                    if (favorite) model.label("У вас нет избранных", "No favorite games yet") else
                        model.label("Ничего не нашлось", "No games found"), bounds.width / 2, y + 41f, 29f, color, centered = true
                )
                label(
                    if (favorite) model.label("режимов, выберите их в играх.", "Choose a star on a game card.") else
                        model.label("Попробуйте другой запрос.", "Try a different search."), bounds.width / 2, y + 81f, 28f, color, centered = true
                )
            }
        }

        with(canvas) { label("PIXZEL  ·  DEMO", bounds.width - 178f, bounds.height - 30f, 12f, 0xFFFFFF60.rgba) }
    }
}
