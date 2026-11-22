@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.Ui
import pixzel.component.Text
import pixzel.dsl.image
import pixzel.layout.unit.dp
import pixzel.surface.Insets
import pixzel.dsl.image
import pixzel.graphics.Sampling
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.layout.unit.sp
import pixzel.surface.Rect
import pixzel.text.TextStyle
import pixzel.text.VerticalAlignment

/**
 * Reusable review template built from Text, Image and Button, without a custom Element implementation.
 */
@Suppress("LongMethod") // The template keeps its typed controls and placements together.
internal fun reviewTile(ui: Ui, review: PlayerReview, index: Int, art: MenuArt, changed: () -> Unit): TileView {
    val root = ui.compose("review-$index")
    val feedback = ReviewFeedback()
    val avatar = ui.components.image("avatar").apply { image = art.avatars[index % art.avatars.size]; sampling = Sampling.NEAREST }
    val author = ui.label(review.author, 15.2f).apply { color = reviewAuthorColor(review.author) }
    val metadata = ui.label("${review.hours} ч в режиме · ${review.date}", 15.2f).apply { color = 0xB8B5B6.rgb }
    val verdict = ui.label(if (review.positive) "ХВАЛЮ" else "РУГАЮ", 11f, small = true).apply {
        color = if (review.positive) 0x6BCD66.rgb else 0xE47362.rgb
    }
    val body = reviewBody(ui, review)
    val footer = ui.label("ОТЗЫВ БЫЛ ПОЛЕЗЕН?", 11f, small = true).apply { color = 0xBCBABB.rgb }
    val yes = ui.pixelButton("yes", "ДА", PixelButtonStyle.REVIEW) { feedback.vote(1) }
    val no = ui.pixelButton("no", "НЕТ", PixelButtonStyle.REVIEW) { feedback.vote(-1) }
    yes.setLeading(
        ui.components.image("thumb-up")
            .apply { image = art.thumbUp; layout.width = pixzel.layout.unit.Dp(14f); layout.height = pixzel.layout.unit.Dp(14f) })
    no.setLeading(
        ui.components.image("thumb-down")
            .apply { image = art.thumbDown; layout.width = pixzel.layout.unit.Dp(14f); layout.height = pixzel.layout.unit.Dp(14f) })
    val reaction = ui.pixelButton("reaction", ":)", PixelButtonStyle.REVIEW.copy(text = 0xFFCE25.rgb)) { feedback.reacted = !feedback.reacted }
    val report = ui.pixelButton("report", "!", PixelButtonStyle.REVIEW.copy(text = 0xDD6655.rgb)) { feedback.reported = !feedback.reported }
    val more = ui.pixelButton("more", "ЧИТАТЬ ВСЕ", PixelButtonStyle.REVIEW.copy(flat = true, text = 0x488FEE.rgb, fontSize = 13f)) {
        feedback.expanded = !feedback.expanded
        changed()
    }
    listOf(avatar, author, metadata, verdict, body, footer, yes, no, reaction, report, more).forEach(root::add)
    root.draw(ui) {
        pixelPanel(bounds, 0x363435A8.rgba, 0x66636380.rgba, 3f, 1f)
        rect(1f, bounds.height - 35f, bounds.width - 2f, 34f, 0x13131358.rgba)
    }
    root.arrange {
        place(avatar) { Rect(10f, 11f, 34f, 34f) }
        place(author) { Rect(52f, 12f, (it.width - 130f).coerceAtLeast(1f), 20f) }
        place(metadata) { Rect(52f, 31f, (it.width - 62f).coerceAtLeast(1f), 20f) }
        place(verdict) { Rect(it.width - 57f, 8f, 50f, 20f) }
        place(body) {
            Rect(
                10f,
                52f,
                it.width - 20f,
                (it.height - 52f - if (more.visibility == pixzel.component.Visibility.VISIBLE) 60f else 35f).coerceAtLeast(1f)
            )
        }
        place(footer) { Rect(10f, it.height - 25f, (it.width - 205f).coerceAtLeast(1f), 20f) }
        listOf(yes, no, reaction, report).forEachIndexed { i, control ->
            val widths = listOf(51f, 57f, 24f, 24f)
            val offsets = listOf(188f, 130f, 66f, 36f)
            place(control) { Rect(it.width - offsets[i], it.height - 28f, widths[i], 20f) }
        }
        place(more) { Rect(10f, it.height - 58f, 116f, 18f) }
    }
    root.observe({ listOf(feedback.expanded, feedback.vote, feedback.reacted, feedback.reported, root.surface.bounds.width) }) {
        yes.selected = feedback.vote == 1
        no.selected = feedback.vote == -1
        reaction.selected = feedback.reacted
        report.selected = feedback.reported
        more.showIf(review.comment.length > 120 && root.surface.bounds.width < 450f)
        more.title = feedback.moreTitle
        body.maxLines = feedback.lineLimit(more.visibility == pixzel.component.Visibility.VISIBLE)
        footer.value = feedback.footer
    }
    return TileView(root) { TileSize(review.span, if (feedback.expanded) review.expandedHeight else review.height) }
}

internal fun ratingTile(ui: Ui, game: GameMode, art: MenuArt, open: () -> Unit, rate: () -> Unit): TileView {
    val root = ui.components.button("summary-${game.id}").apply { layout.padding = pixzel.surface.Insets.ZERO; foreground = null; onClick(open) }
    val featured = game.rating.featured
    val iconSize = if (featured) 72f else 54f
    val image = ui.components.image("mode-icon").apply {
        image = art.icons.getValue(game.icon); sampling = Sampling.NEAREST
        layout.padding = Insets.all(1.dp)
        draw(ui) { pixelPanel(bounds, game.color.rgb, game.color.rgb.shade(1.5f), 2f, 1f) }
    }
    val name = ui.label(game.name.replace('\n', ' '), 16f)
    val rating = ui.label(game.rating.description, 15.2f).apply { color = 0x6BC65F.rgb }
    val tags = ui.label(game.rating.tags, 12f).apply { color = 0xAFA6A4.rgb }
    val footer = ratingFooter(ui, game.rating, art)
    val button =
        ui.pixelButton("summary-rate", "Оценить режим", PixelButtonStyle.REVIEW_BLUE.copy(fontSize = 15.2f, smallFont = false), clicked = rate)
    listOf(image, name, rating, tags, footer, button).forEach(root::add)
    root.draw(ui) {
        pixelPanel(bounds, 0x3E3837AF.rgba, 0x77706A80.rgba, 3f, 1f)
        rect(1f, bounds.height - 29f, bounds.width - 2f, 28f, 0x14121280.rgba)
    }
    root.arrange {
        place(image) { Rect(10f, 10f, iconSize, iconSize) }
        place(name) { Rect(iconSize + 20f, 14f, it.width - iconSize - 30f, 22f) }
        place(rating) { Rect(iconSize + 20f, 35f, it.width - iconSize - 30f, 20f) }
        place(tags) { Rect(iconSize + 20f, 54f, it.width - iconSize - 30f, 20f) }
        place(footer) { Rect(0f, it.height - 29f, it.width, 29f) }
        place(button) { Rect(it.width - 170f, 30f, 158f, 29f) }
    }
    root.onFrame { button.showIf(featured || root.active || button.active); false }
    return TileView(root) { TileSize(if (featured) 6 else 2, if (featured) 124f else 108f) }
}

internal fun Ui.label(value: String, size: Float, small: Boolean = false): Text = components.text().apply {
    this.value = value
    style = TextStyle(size = size.sp, family = if (small) SMALL_FONT else pixzel.text.FontFamily.DEFAULT)
    overflow = clippedText
}

private class ReviewFeedback {

    var expanded = false
    var vote = 0
    var reacted = false
    var reported = false
    val moreTitle get() = if (expanded) "СВЕРНУТЬ" else "ЧИТАТЬ ВСЕ"
    val footer get() = if (reported) "ЖАЛОБА ОТПРАВЛЕНА" else "ОТЗЫВ БЫЛ ПОЛЕЗЕН?"
    fun lineLimit(hasMore: Boolean) = if (!expanded && hasMore) 2 else Int.MAX_VALUE
    fun vote(value: Int) {
        vote = if (vote == value) 0 else value
    }
}

private fun reviewAuthorColor(author: String) = when {
    author.startsWith("P") -> 0x6ADB63.rgb
    author.startsWith("MVP") -> 0x58D8ED.rgb
    author.startsWith("V") -> 0xF1E651.rgb
    else -> 0xC2C0C0.rgb
}

private val pixzel.component.Element.active get() = state.hovered || state.focused

private fun reviewBody(ui: Ui, review: PlayerReview): Text {
    return ui.label(review.comment.ifEmpty { "оценка без комментария" }, 15.2f).apply {
        style = style.copy(lineHeight = 17f)
        color = if (review.comment.isEmpty()) 0x777575.rgb else 0xBDBABB.rgb
        wrap = true
        verticalAlignment = VerticalAlignment.CENTER
    }
}

private fun ratingFooter(ui: Ui, rating: GameRating, art: MenuArt): pixzel.component.Element = ui.compose("rating-footer") {
    val up = image(art.thumbUp) { sampling = Sampling.NEAREST }
    val down = image(art.thumbDown) { sampling = Sampling.NEAREST }
    val positive = insert(ui.label(rating.positive.toString(), 13f)).apply { color = 0x78C76A.rgb }
    val negative = insert(ui.label(rating.negative.toString(), 13f)).apply { color = 0xED7563.rgb }
    val played = insert(ui.label(rating.playedMinutes?.let { "· ТЫ ИГРАЕШЬ · $it МИН" }.orEmpty(), 13f)).apply { color = 0x58D8ED.rgb }
    val reviews = insert(ui.label("${rating.total} ОТЗЫВОВ ›", 12f)).apply {
        color = 0xCCC4C1.rgb
        horizontalAlignment = pixzel.text.TextAlignment.END
        verticalAlignment = VerticalAlignment.CENTER
    }
    parent.arrange {
        place(up) { Rect(10f, 8f, 13f, 12f) }
        place(down) { Rect(56f, 8f, 13f, 12f) }
        place(positive) { Rect(29f, 8f, 26f, 20f) }
        place(negative) { Rect(75f, 8f, 26f, 20f) }
        place(played) { Rect(105f, 8f, (it.width - 244f).coerceAtLeast(0f), 20f) }
        place(reviews) { Rect(it.width - 139f, 2f, 127f, 23f) }
    }
}
