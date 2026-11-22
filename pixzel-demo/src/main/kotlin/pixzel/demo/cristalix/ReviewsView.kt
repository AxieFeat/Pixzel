@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.Ui
import pixzel.component.Element
import pixzel.graphics.DrawImage
import pixzel.graphics.Sampling
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.input.EventPhase
import pixzel.input.WheelEvent
import pixzel.input.WheelUnit
import pixzel.layout.unit.sp
import pixzel.surface.Rect
import pixzel.surface.Vec2
import pixzel.text.FontFace
import pixzel.text.FontFamily
import pixzel.text.TextRequest
import pixzel.text.TextStyle
import java.util.Locale

internal class ReviewsState(val data: MenuData) {

    var game: GameMode? = null
    var filter = 0
    var sorting = 0
    var mine = false
    var query = ""
    var revision = 0
    val own = mutableMapOf<String, PlayerReview>()
    val overview get() = game == null
    val sortOptions get() = if (overview) listOf("ПО РЕЙТИНГУ", "ПО ОНЛАЙНУ", "ПО НАЗВАНИЮ") else listOf("ПОЛЕЗНЫЕ", "НОВЫЕ", "СТАРЫЕ")

    fun modes(): List<GameMode> {
        val modes = data.games.filter { it.category != MenuCategory.TESTING && it.category != MenuCategory.HUBS }
            .filter { it.name.replace('\n', ' ').contains(query, true) && (!mine || it.id in own) }
        return when (sorting) {
            1 -> modes.sortedByDescending { it.online }
            2 -> modes.sortedBy { it.name }
            else -> modes.sortedBy { it.reviewOrder }
        }
    }

    fun reviews(): List<PlayerReview> {
        val reviews = (listOfNotNull(own[game?.id]) + data.reviews).filter { filter == 0 || it.positive == (filter == 1) }
        return when (sorting) {
            1 -> reviews.reversed()
            2 -> reviews.sortedBy { it.date }
            else -> reviews
        }
    }
}

internal class ReviewsView(
    val root: Element, val overlay: Overlay, val state: ReviewsState, val composer: ComposerView,
    private val navigation: ReviewNavigation
) {

    val open get() = overlay.open
    val game get() = state.game
    fun show(game: GameMode?) {
        state.game = game
        state.filter = 0
        state.mine = false
        state.revision++
        overlay.show()
    }

    fun dismiss() = when {
        composer.open -> composer.dismiss()
        navigation.sorting.open -> navigation.sorting.dismiss()
        else -> overlay.dismiss()
    }
}

internal fun reviewsPage(ui: Ui, model: MenuModel, art: MenuArt): ReviewsView {
    val root = ui.compose("reviews-page").apply { zIndex = 40 }
    val state = ReviewsState(model.data)
    val board = TileBoard(ui) { model.reducedMotion }
    val overlay = Overlay(root)
    val composer = reviewComposer(ui, art) { review -> state.game?.let { state.own[it.id] = review } }
    val navigation = reviewNavigation(ui, state, composer::show, overlay::dismiss)
    listOf(board.root, navigation.root, composer.root).forEach(root::add)
    composer.root.zIndex = 50
    root.arrange {
        place(board.root) { val width = minOf(1170f, it.width - 32f); Rect((it.width - width) / 2, 94f, width, (it.height - 94f).coerceAtLeast(1f)) }
    }
    fun refresh() {
        val views = if (state.overview) state.modes().map { mode ->
            ratingTile(ui, mode, art, { state.game = mode }, { state.game = mode; composer.show(mode) })
        } else state.reviews().mapIndexed { index, review -> reviewTile(ui, review, index, art, board::reflow) }
        board.replace(views)
        navigation.update()
        root.invalidate()
    }
    root.observe({ listOf(state.revision, state.game, state.filter, state.sorting, state.mine, navigation.search.value, state.own.toMap()) }) {
        state.query = navigation.search.value
        refresh()
    }
    root.onEvent(EventPhase.CAPTURE) { routed ->
        val wheel = routed.event as? WheelEvent
        if (wheel != null && !composer.open && !navigation.sorting.open) {
            board.scrollBy(-wheel.delta.y * if (wheel.unit == WheelUnit.LINE) 65f else 1f)
            routed.consume()
            routed.preventDefault()
        }
    }
    root.draw(ui) { reviewBackground(state, art); if (board.empty) label("Пока нет отзывов", bounds.width / 2, 210f, 23f, centered = true) }
    return ReviewsView(root, overlay, state, composer, navigation)
}

private fun PixelCanvas.reviewBackground(state: ReviewsState, art: MenuArt) {
    val image = art.reviewsBackground
    emit(DrawImage(image, Rect(0f, 0f, image.size.width, image.size.height), bounds, sampling = Sampling.LINEAR))
    rect(0f, 0f, bounds.width, 42f, 0x1008079C.rgba)
    text(
        ui.text.layout(
            TextRequest(
                state.game?.name?.replace('\n', ' ')?.uppercase() ?: "ОТЗЫВЫ",
                TextStyle(family = FontFamily(listOf(FontFace("minecraft-ten"))), size = 35.sp)
            )
        ), Vec2(10f, 0f), 0xFFFFFF.rgb
    )
    val metrics = state.data.metrics
    val x = if (state.overview) 195f else 255f
    rect(x - 14f, 8f, 1f, 28f, 0x77777777.rgba)
    val counters = if (state.overview) "${metrics.modes}     ${
        String.format(
            Locale.GERMANY,
            "%,d",
            metrics.reviews
        )
    }     ${state.own.size}" else "${metrics.approval}%"
    label(counters, x, 7f, 21f, 0xE4BB5A.rgb)
    smallLabel(
        if (state.overview) "РЕЖИМА    ОТЗЫВА    ТВОИХ" else "СМЕШАННЫЕ\n${metrics.total} ОТЗЫВА",
        x + if (state.overview) 0f else 53f, if (state.overview) 28f else 8f, 11f, 0xC5C0C0.rgb
    )
}
