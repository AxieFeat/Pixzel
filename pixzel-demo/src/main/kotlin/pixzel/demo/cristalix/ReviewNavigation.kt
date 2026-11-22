@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.Ui
import pixzel.component.Element
import pixzel.component.TextField
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.input.EventPhase
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.layout.unit.sp
import pixzel.layout.unit.dp
import pixzel.surface.Insets
import pixzel.surface.Rect
import pixzel.surface.Vec2
import pixzel.text.TextStyle

internal class ReviewNavigation(val root: Element, val search: TextField, val sorting: ChoicePopup, val update: () -> Unit)

internal fun reviewNavigation(ui: Ui, state: ReviewsState, rateGame: (GameMode) -> Unit, dismiss: () -> Unit): ReviewNavigation {
    val root = ui.compose("review-navigation")
    val back = ui.pixelButton("reviews-back", "< В НАВИГАТОР", PixelButtonStyle.REVIEW, clicked = dismiss)
    val close = ui.pixelButton("reviews-close", "×", PixelButtonStyle.REVIEW, clicked = dismiss)
    val rate = ui.pixelButton("reviews-rate", "МОИ ОТЗЫВЫ", PixelButtonStyle.REVIEW) {
        state.game?.let(rateGame) ?: run { state.mine = !state.mine }
    }
    val metrics = state.data.metrics
    val filters = listOf("ВСЕ ${metrics.total}", "ХВАЛЯТ ${metrics.positive}", "РУГАЮТ ${metrics.negative}").mapIndexed { index, title ->
        ui.pixelButton("reviews-filter-$index", title, PixelButtonStyle.REVIEW) { state.filter = index }
    }
    val search = ui.components.textField("review-search").apply {
        placeholder = "Поиск режима..."
        style = TextStyle(size = 15.2f.sp)
        layout.padding = Insets.horizontal(20.dp)
        color = 0xD2C9C6.rgb
        draw(ui) { pixelPanel(bounds, 0x282222A0.rgba, 0x92909080.rgba, 2f, 1f) }
        foreground = pixzel.component.Painter { canvas, _ ->
            canvas.rect(6f, 6f, 6f, 1f, 0xB0A8A8.rgb)
            canvas.rect(5f, 7f, 1f, 5f, 0xB0A8A8.rgb)
            canvas.rect(12f, 7f, 1f, 5f, 0xB0A8A8.rgb)
            canvas.rect(6f, 12f, 6f, 1f, 0xB0A8A8.rgb)
            canvas.rect(12f, 13f, 2f, 2f, 0xB0A8A8.rgb)
        }
    }
    val sort = ui.pixelButton("reviews-sort", state.sortOptions[state.sorting] + " v", PixelButtonStyle.REVIEW)
    val sorting = ChoicePopup(ui)
    sort.onClick {
        sorting.show(state.sortOptions, state.sortOptions[state.sorting], sort.surface.toRoot(Vec2(0f, 20f)), 148f) {
            state.sorting = state.sortOptions.indexOf(it)
        }
    }
    listOf(back, close, rate, search, sort, sorting.root).plus(filters).forEach(root::add)
    root.arrange {
        place(back) { val width = minOf(1170f, it.width - 32f); Rect((it.width - width) / 2, 54f, 136f, 20f) }
        place(close) { Rect(it.width - 31f, 10f, 21f, 21f) }
        place(rate) { Rect(it.width - 196f, 10f, 157f, 21f) }
        filters.forEachIndexed { index, control -> place(control) { Rect(it.width / 2 - 153f + index * 102f, 54f, 102f, 20f) } }
        place(search) { val width = minOf(1170f, it.width - 32f); Rect((it.width - width) / 2 + 146f, 54f, 166f, 20f) }
        place(sort) { val width = minOf(1170f, it.width - 32f); Rect((it.width - width) / 2 + width - 148f, 54f, 148f, 20f) }
        place(sorting.root) { Rect(sorting.anchor.x, sorting.anchor.y, sorting.menuWidth, sorting.menuHeight) }
    }
    root.onEvent(EventPhase.CAPTURE) { routed ->
        val event = routed.event as? PointerEvent
        if (sorting.open && event?.action == PointerAction.DOWN) {
            val point = event.position
            if (!sorting.root.surface.mapRect(sorting.root.bounds).contains(point) && !sort.surface.mapRect(sort.bounds)
                    .contains(point)
            ) sorting.dismiss()
        }
    }
    return ReviewNavigation(root, search, sorting) {
        filters.forEachIndexed { index, control -> control.showIf(!state.overview); control.selected = state.filter == index }
        search.showIf(state.overview)
        rate.title = if (state.overview) "МОИ ОТЗЫВЫ" else "ОЦЕНИТЬ РЕЖИМ"
        sort.title = state.sortOptions[state.sorting] + " v"
    }
}
