// Pixel geometry, palette and motion timings are authored as visual design data.
@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.Ui
import pixzel.component.Painter
import pixzel.graphics.Canvas
import pixzel.graphics.rgba
import pixzel.layout.unit.dp
import pixzel.layout.unit.sp
import pixzel.surface.Insets
import pixzel.surface.Placement
import pixzel.surface.Rect
import pixzel.surface.Vec2
import pixzel.text.TextStyle
import pixzel.text.TextAlignment
import pixzel.text.VerticalAlignment
import kotlin.time.Duration

internal class MenuToolbar(
    owner: Ui,
    model: MenuModel,
    openDropdown: (String, Vec2) -> Unit,
    openReviews: () -> Unit
) : MenuElement("toolbar", model) {

    val search = owner.components.textField("game-search").apply {
        placeholder = "Поиск игры"
        style = TextStyle(size = 22.sp)
        color = 0xFFFFFFFF.rgba
        verticalAlignment = VerticalAlignment.CENTER
        layout.padding = Insets.horizontal(12.dp)
        placement = Placement.of(offset = Vec2(150f, 14f))
        background = Painter { canvas, context ->
            canvas.pixelPanel(
                context.bounds, 0x2362BEFF.rgba,
                if (context.state.focused) 0x92C7FFFF.rgba else 0x5899EDFF.rgba, 4f, 2f
            )
        }
        foreground = null
    }
    val favorites = MenuButton("favorites", model, MenuCategory.FAVORITES) {
        model.category = if (model.category == MenuCategory.FAVORITES) MenuCategory.ALL else MenuCategory.FAVORITES
    }
    val categories = MenuButton(MenuDropdown.CATEGORIES, model, "Все игры   ")
    val languages = MenuButton(MenuDropdown.LANGUAGES, model, "RU   ")
    val reviews = MenuButton("reviews", model, "Отзывы", openReviews).apply { purple = true }

    init {
        add(search)
        add(favorites)
        add(categories)
        add(languages)
        add(reviews)

        categories.action = { openDropdown(MenuDropdown.CATEGORIES, categories.surface.toRoot(Vec2(0f, categories.surface.bounds.height - 2f))) }
        languages.action = { openDropdown(MenuDropdown.LANGUAGES, languages.surface.toRoot(Vec2(0f, languages.surface.bounds.height - 2f))) }
        listOf(categories, languages).forEach { button ->
            button.foreground = Painter { canvas, context ->
                val x = context.bounds.width - 25f
                repeat(4) { row ->
                    canvas.rect(x + row * 2, 16f + row * 2, 2f, 2f, 0xE2EDFFFF.rgba)
                    canvas.rect(x + 12f - row * 2, 16f + row * 2, 2f, 2f, 0xE2EDFFFF.rgba)
                }
            }
        }

        layoutPolicy = positionedLayout { id, size ->
            val small = size.width < 800f
            when (id) {
                "game-search" -> Rect(15f, 14f, 200f, 38f)
                "favorites" -> Rect(if (small) 15f else size.width - 536f, if (small) 62f else 14f, 136f, 38f)
                MenuDropdown.CATEGORIES -> Rect(if (small) 163f else size.width - 388f, if (small) 62f else 14f, 166f, 38f)
                MenuDropdown.LANGUAGES -> Rect(if (small) 341f else size.width - 210f, if (small) 62f else 14f, 82f, 38f)
                else -> Rect(if (small) 435f else size.width - 116f, if (small) 62f else 14f, 102f, 38f)
            }
        }
    }

    override fun update(delta: Duration): Boolean {
        model.query = search.value
        search.color = if (search.value.isEmpty()) 0xA0A0A0FF.rgba else 0xFFFFFFFF.rgba
        search.placeholder = model.label("Поиск игры", "Find a game")
        favorites.title = model.label(MenuCategory.FAVORITES, "Favorites")
        favorites.active = model.category == MenuCategory.FAVORITES
        categories.title = categoryLabel(if (model.category == MenuCategory.FAVORITES) MenuCategory.ALL else model.category) + "   "
        languages.title = model.language + "   "
        reviews.title = model.label("Отзывы", "Reviews")
        return false
    }

    private fun categoryLabel(category: String): String = if (!model.english) category else when (category) {
        MenuCategory.FAVORITES -> "Favorites"
        MenuCategory.HUBS -> "Hubs"
        MenuCategory.TESTING -> "Testing"
        MenuCategory.ARCADE -> "Arcade"
        MenuCategory.TABLETOP -> "Tabletop"
        else -> "All games"
    }

    override fun paint(canvas: Canvas) {
        canvas.pixelPanel(bounds, 0x0B1C32B3.rgba, 0x214A83CC.rgba, 6f, 2f)
        with(canvas) {
            label("Онлайн 2.979", Rect(229f, 14f, 170f, 38f), 22f, horizontal = TextAlignment.START)
        }
    }
}
