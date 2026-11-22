@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.Ui
import pixzel.component.Button
import pixzel.component.ClipPolicy
import pixzel.component.Element
import pixzel.component.ImageFit
import pixzel.component.Painter
import pixzel.component.TextField
import pixzel.dsl.image
import pixzel.graphics.Sampling
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.input.WheelEvent
import pixzel.layout.unit.dp
import pixzel.layout.unit.sp
import pixzel.surface.Insets
import pixzel.surface.Rect
import pixzel.surface.Transform
import pixzel.surface.Vec2
import pixzel.text.TextStyle
import pixzel.text.VerticalAlignment

internal data class ToolbarView(
    val root: Element, val search: TextField, val favorites: Button, val categories: Button,
    val languages: Button, val reviews: Button
)

internal fun navigatorToolbar(ui: Ui, model: MenuModel, openFilter: (Boolean, Button) -> Unit, openReviews: () -> Unit): ToolbarView {
    val root = ui.compose("toolbar")
    val search = ui.components.textField("game-search").apply {
        placeholder = "Поиск игры"
        style = TextStyle(size = 22.sp)
        verticalAlignment = VerticalAlignment.CENTER
        layout.padding = Insets.horizontal(12.dp)
        draw(ui) { pixelPanel(bounds, 0x2362BE.rgb, if (state.focused) 0x92C7FF.rgb else 0x5899ED.rgb, 4f, 2f) }
        foreground = null
    }
    val favorites = ui.pixelButton("favorites", "Избранное", reducedMotion = { model.reducedMotion }) {
        model.category = if (model.category == MenuCategory.FAVORITES) MenuCategory.ALL else MenuCategory.FAVORITES
    }
    val categories = ui.pixelButton("categories", "Все игры   ", reducedMotion = { model.reducedMotion })
    val languages = ui.pixelButton("languages", "RU   ", reducedMotion = { model.reducedMotion })
    categories.onClick { openFilter(false, categories) }
    languages.onClick { openFilter(true, languages) }
    val reviews = ui.pixelButton("reviews", "Отзывы", PixelButtonStyle.PURPLE, { model.reducedMotion }, openReviews)
    val online = ui.components.text("online").apply {
        value = "Онлайн 2.979"
        style = TextStyle(size = 22.sp)
        verticalAlignment = VerticalAlignment.CENTER
    }
    listOf(search, favorites, categories, languages, reviews, online).forEach(root::add)
    root.draw(ui) { pixelPanel(bounds, 0x0B1C32B3.rgba, 0x214A83CC.rgba, 6f, 2f) }
    root.arrange {
        place(search) { Rect(15f, 14f, 200f, 38f) }
        place(online) { Rect(229f, 14f, 170f, 38f) }
        listOf(favorites, categories, languages, reviews).forEachIndexed { index, button ->
            val widths = listOf(136f, 166f, 82f, 102f)
            val offsets = listOf(536f, 388f, 210f, 116f)
            place(button) { size ->
                Rect(
                    if (size.width < 800f) 15f + widths.take(index).sum() + index * 12f else size.width - offsets[index],
                    if (size.width < 800f) 62f else 14f, widths[index], 38f
                )
            }
        }
    }
    root.onFrame {
        model.query = search.value
        search.color = if (search.value.isEmpty()) 0xA0A0A0.rgb else 0xFFFFFF.rgb
        favorites.selected = model.category == MenuCategory.FAVORITES
        categories.title = (if (favorites.selected) MenuCategory.ALL else model.category) + "   "
        languages.title = model.language + "   "
        false
    }
    listOf(categories, languages).forEach { button ->
        button.foreground = Painter { canvas, context ->
            repeat(4) { row ->
                canvas.rect(context.bounds.width - 25f + row * 2, 16f + row * 2, 2f, 2f, 0xE2EDFF.rgb)
                canvas.rect(context.bounds.width - 13f - row * 2, 16f + row * 2, 2f, 2f, 0xE2EDFF.rgb)
            }
        }
    }
    return ToolbarView(root, search, favorites, categories, languages, reviews)
}

/**
 * The same carousel can display any injected promotion list.
 */
internal class PromotionPlayer(private val items: List<Promotion>) {

    var index = 0
        private set
    var elapsed = 0f
        private set
    private var rotated = false
    val progress get() = elapsed / items[index].seconds
    val transition get() = if (rotated) (elapsed / .4f).coerceAtMost(1f) else 1f
    fun advance(seconds: Float) {
        elapsed += seconds
        while (elapsed >= items[index].seconds) {
            elapsed -= items[index].seconds
            index = (index + 1) % items.size
            rotated = true
        }
    }
}

internal fun promotionCarousel(ui: Ui, model: MenuModel, art: MenuArt, activate: () -> Unit): Button {
    val root = ui.components.button("banner").apply { layout.padding = Insets.ZERO; foreground = null; onClick(activate) }
    root.clipPolicy = ClipPolicy.BOUNDS
    root.draw(ui) { pixelPanel(bounds, 0x343146.rgb, 0x79718A.rgb, 6f, 2f) }
    val slides = model.data.banners.mapIndexed { index, promotion ->
        ui.compose("promotion-$index") {
            val image = image(art.banners[index]) { fit = ImageFit.STRETCH; sampling = Sampling.LINEAR }
            val heading = text(promotion.title).apply { style = TextStyle(size = 41.sp, lineHeight = 41f) }
            val button = action("promotion-action-$index", promotion.action, clicked = activate)
            parent.arrange {
                place(image) { Rect(2f, 2f, it.width - 4f, it.height - 4f) }
                place(heading) { Rect(25f, 24f, it.width - 50f, 100f) }
                place(button) { Rect(29f, it.height - 65f, promotion.buttonWidth, 38f) }
            }
        }.also(root::add)
    }
    root.arrange { }
    val player = PromotionPlayer(model.data.banners)
    root.foreground = Painter { canvas, context ->
        val totalWidth = model.data.banners.size * 46f - 7f
        model.data.banners.indices.forEach { index ->
            val x = (context.bounds.width - totalWidth) / 2 + index * 46f
            canvas.rect(x, context.bounds.height - 17f, 39f, 4f, 0xFFFFFF40.rgba)
            if (index == model.data.banners.lastIndex - player.index) canvas.rect(
                x,
                context.bounds.height - 17f,
                39f * player.progress,
                4f,
                0xFFFFFF.rgb
            )
        }
    }
    root.onFrame { seconds ->
        if (!model.reducedMotion) player.advance(seconds)
        slides.forEachIndexed { index, slide ->
            val previous = (player.index - 1 + slides.size) % slides.size
            slide.showIf(index == player.index || index == previous && player.transition < 1f)
            slide.paintTransform = Transform.translation(
                root.surface.bounds.width *
                        if (index == player.index) 1f - player.transition else -player.transition, 0f
            )
        }
        !model.reducedMotion
    }
    return root
}

/**
 * Generic choice popup, used for both categories and languages.
 */
internal class ChoicePopup(private val ui: Ui) {

    val root = ui.compose("dropdown")
    private val overlay = Overlay(root)
    val open get() = overlay.open
    var anchor = Vec2.ZERO
    var menuWidth = 166f
    val menuHeight get() = options.size.coerceAtMost(5) * 48f
    private var options = emptyList<String>()
    private var selected = 0
    private var firstRow = 0
    private var chooseValue: (String) -> Unit = {}

    init {
        root.zIndex = 30
        root.clipPolicy = ClipPolicy.BOUNDS
        root.draw(ui) { pixelPanel(bounds, 0x2362BE.rgb, 0x5899ED.rgb, 4f, 2f) }
        root.onEvent { routed ->
            (routed.event as? WheelEvent)?.let {
                firstRow = (firstRow - it.delta.y.toInt()).coerceIn(0, (options.size - 5).coerceAtLeast(0))
                root.invalidate(pixzel.component.Invalidation.MEASURE)
                routed.consume()
            }
        }
    }

    fun show(values: List<String>, current: String, position: Vec2, width: Float, choose: (String) -> Unit) {
        options = values
        selected = options.indexOf(current).coerceAtLeast(0)
        firstRow = 0
        anchor = position
        menuWidth = width
        chooseValue = choose
        root.children.toList().forEach { root.remove(it) }
        val buttons = values.mapIndexed { index, value ->
            ui.pixelButton("option-$index", value, PixelButtonStyle.BLUE.copy(flat = true, fontSize = 22f)) { this.choose(index) }.also(root::add)
        }
        root.arrange { buttons.forEachIndexed { index, button -> place(button) { Rect(3f, (index - firstRow) * 48f, it.width - 6f, 48f) } } }
        overlay.show()
        ui.invalidate(pixzel.component.Invalidation.MEASURE)
    }

    fun move(direction: Int) {
        selected = (selected + direction + options.size) % options.size
        firstRow = firstRow.coerceIn((selected - 4).coerceAtLeast(0), selected)
        root.invalidate(pixzel.component.Invalidation.MEASURE)
        root.children.getOrNull(selected)?.let { ui.focus.request(it) }
    }

    fun choose(index: Int = selected) {
        if (open) {
            chooseValue(options[index]); dismiss()
        }
    }

    fun dismiss() = overlay.dismiss()
}
