// Reference geometry is expressed in Pixzel logical coordinates.
@file:Suppress("MagicNumber", "StringLiteralDuplication")

package pixzel.demo.cristalix

import pixzel.Ui
import pixzel.component.ClipPolicy
import pixzel.component.Invalidation
import pixzel.component.Element
import pixzel.graphics.Canvas
import pixzel.graphics.DrawImage
import pixzel.graphics.Sampling
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.input.EventPhase
import pixzel.input.PointerPolicy
import pixzel.input.WheelEvent
import pixzel.input.WheelUnit
import pixzel.layout.unit.sp
import pixzel.layout.unit.dp
import pixzel.surface.Insets
import pixzel.layout.ChildPlacement
import pixzel.layout.Constraints
import pixzel.layout.LayoutPlan
import pixzel.layout.LayoutPolicy
import pixzel.surface.Size
import pixzel.resource.Subscription
import pixzel.surface.Rect
import pixzel.text.TextAlignment
import pixzel.text.TextStyle
import pixzel.text.FontFamily
import pixzel.text.FontFace
import pixzel.text.TextRequest
import pixzel.surface.Vec2
import kotlin.time.Duration

internal class MenuReviews(owner: Ui, model: MenuModel, private val art: MenuArt) : MenuElement("reviews-page", model) {

    private var scope: Subscription? = null
    var open = false
        private set
    var game: GameMode? = null
        private set
    var filter = 0
        private set
    private var sorting = 0
    private var sortOpen = false
    private var mine = false
    private val ownReviews = mutableMapOf<String, PlayerReview>()
    val composer = ReviewComposer(model, art) { review ->
        game?.let { ownReviews[it.id] = review }
        rebuild()
    }
    private val board = ReviewBoard(model)
    private val back = ReviewButton("reviews-back", model, "< В НАВИГАТОР", ::dismiss)
    private val close = ReviewButton("reviews-close", model, "×", ::dismiss)
    private val rate = ReviewButton("reviews-rate", model, "ОЦЕНИТЬ РЕЖИМ") {
        if (game == null) {
            mine = !mine
            rebuild()
        } else composer.show(game!!)
    }
    private val all = ReviewButton("reviews-all", model, "ВСЕ 134") { chooseFilter(0) }
    private val praise = ReviewButton("reviews-praise", model, "ХВАЛЯТ 87") { chooseFilter(1) }
    private val blame = ReviewButton("reviews-blame", model, "РУГАЮТ 47") { chooseFilter(2) }
    private val sort = ReviewButton("reviews-sort", model, "ПОЛЕЗНЫЕ v") {
        sortOpen = !sortOpen
        invalidate(Invalidation.MEASURE)
    }
    private val sortOptions = listOf("ПОЛЕЗНЫЕ", "НОВЫЕ", "СТАРЫЕ").mapIndexed { index, title ->
        ReviewButton("reviews-sort-$index", model, title) {
            sorting = index
            sortOpen = false
            sort.title = "$title v"
            rebuild()
        }
    }
    val search = owner.components.textField("review-search").apply {
        placeholder = "Поиск режима..."
        style = TextStyle(size = 15.2f.sp)
        layout.padding = Insets.horizontal(20.dp)
        color = 0xD2C9C6.rgb
        foreground = pixzel.component.Painter { canvas, _ ->
            canvas.rect(6f, 6f, 6f, 1f, 0xB0A8A8.rgb)
            canvas.rect(5f, 7f, 1f, 5f, 0xB0A8A8.rgb)
            canvas.rect(12f, 7f, 1f, 5f, 0xB0A8A8.rgb)
            canvas.rect(6f, 12f, 6f, 1f, 0xB0A8A8.rgb)
            canvas.rect(12f, 13f, 2f, 2f, 0xB0A8A8.rgb)
        }
        background = pixzel.component.Painter { canvas, context ->
            canvas.pixelPanel(context.bounds, 0x282222A0.rgba, 0x92909080.rgba, 2f, 1f)
        }
    }
    private var previousQuery = ""
    private var previousPresentation: List<Any?>? = null

    init {
        zIndex = 40
        opacity = 0f
        pointerPolicy = PointerPolicy.NONE
        listOf(board, back, close, rate, all, praise, blame, sort, search).forEach(::add)
        sortOptions.forEach {
            it.zIndex = 4
            add(it)
        }
        add(composer)
        layoutPolicy = positionedLayout { id, size ->
            val width = minOf(1170f, size.width - 32f)
            val left = (size.width - width) / 2f
            when (id) {
                "review-board" -> Rect(left, 94f, width, (size.height - 94f).coerceAtLeast(1f))
                "reviews-back" -> Rect(left, 54f, 136f, 20f)
                "reviews-close" -> Rect(size.width - 31f, 10f, 21f, 21f)
                "reviews-rate" -> Rect(size.width - 196f, 10f, 157f, 21f)
                "reviews-all" -> Rect(size.width / 2 - 153f, 54f, 102f, 20f)
                "reviews-praise" -> Rect(size.width / 2 - 51f, 54f, 102f, 20f)
                "reviews-blame" -> Rect(size.width / 2 + 51f, 54f, 102f, 20f)
                "reviews-sort" -> Rect(left + width - 148f, 54f, 148f, 20f)
                "review-search" -> Rect(left + 146f, 54f, 166f, 20f)
                "review-composer" -> Rect(0f, 0f, size.width, size.height)
                else -> Rect(left + width - 148f, 76f + id.substringAfterLast('-').toInt() * 22f, 148f, 22f)
            }
        }
        onEvent(EventPhase.CAPTURE) { routed ->
            val wheel = routed.event as? WheelEvent ?: return@onEvent
            if (open && !composer.open) {
                board.scrollBy(-wheel.delta.y * if (wheel.unit == WheelUnit.LINE) 65f else 1f)
                routed.consume()
            }
        }
        setInteractive(false)
    }

    private fun setInteractive(enabled: Boolean) {
        pointerPolicy = if (enabled) PointerPolicy.AUTO else PointerPolicy.NONE
        fun toggle(element: Element) {
            element.pointerPolicy = if (enabled) PointerPolicy.AUTO else PointerPolicy.NONE
            element.focusable = enabled && element is MenuButton
            element.children.forEach(::toggle)
        }
        children.filter { it !== composer }.forEach { child ->
            toggle(child)
        }
    }

    fun show(game: GameMode?) {
        this.game = game
        filter = 0
        mine = false
        open = true
        opacity = 1f
        setInteractive(true)
        scope?.close()
        scope = ui?.focus?.pushScope(this)
        ui?.focus?.request(back)
        sort.title = if (game == null) "ПО РЕЙТИНГУ v" else "ПОЛЕЗНЫЕ v"
        rebuild()
    }

    fun dismiss() {
        if (composer.open) {
            composer.dismiss()
            return
        }
        open = false
        opacity = 0f
        setInteractive(false)
        scope?.close()
        scope = null
    }

    private fun chooseFilter(value: Int) {
        filter = value
        rebuild()
    }

    private fun rebuild() {
        board.clearItems()
        if (game == null) {
            val modes = gameModes.filter { it.category != MenuCategory.TESTING && it.category != MenuCategory.HUBS }
                .filter { it.name.replace('\n', ' ').contains(search.value, true) && (!mine || it.id in ownReviews) }
            val ordered = when (sorting) {
                1 -> modes.sortedByDescending { it.online }
                2 -> modes.sortedBy { it.name }
                else -> modes.sortedBy { referenceRatingOrder.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
            }
            ordered.forEachIndexed { index, mode ->
                board.add(SummaryCard(model, mode, index, art, { show(mode) }) {
                    show(mode)
                    composer.show(mode)
                })
            }
        } else {
            val reviews = listOfNotNull(ownReviews[game!!.id]) + referenceReviews
            val filtered = reviews.filter { filter == 0 || it.positive == (filter == 1) }
            val ordered = when (sorting) {
                1 -> filtered.reversed()
                2 -> filtered.sortedBy { it.date }
                else -> filtered
            }
            ordered.forEachIndexed { index, review ->
                board.add(ReviewCard("review-$index", model, review, index, art) {
                    board.invalidate(Invalidation.MEASURE)
                })
            }
        }
        board.summary = game == null
        board.scrollToTop()
        invalidate(Invalidation.MEASURE)
    }

    override fun update(delta: Duration): Boolean {
        if (!open) return false
        if (previousQuery != search.value) {
            previousQuery = search.value
            rebuild()
        }
        val overview = game == null
        sortOptions.forEachIndexed { index, option ->
            option.title = if (overview) listOf("ПО РЕЙТИНГУ", "ПО ОНЛАЙНУ", "ПО НАЗВАНИЮ")[index] else
                listOf("ПОЛЕЗНЫЕ", "НОВЫЕ", "СТАРЫЕ")[index]
        }
        listOf(all, praise, blame).forEach {
            it.opacity = if (overview) 0f else 1f
            it.focusable = !overview
            it.pointerPolicy = if (overview) PointerPolicy.NONE else PointerPolicy.AUTO
        }
        all.selected = filter == 0
        praise.selected = filter == 1
        blame.selected = filter == 2
        search.opacity = if (overview) 1f else 0f
        search.focusable = overview
        search.pointerPolicy = if (overview) PointerPolicy.AUTO else PointerPolicy.NONE
        rate.title = if (overview) "МОИ ОТЗЫВЫ" else "ОЦЕНИТЬ РЕЖИМ"
        sortOptions.forEach {
            it.opacity = if (sortOpen) 1f else 0f
            it.focusable = sortOpen
            it.pointerPolicy = if (sortOpen) PointerPolicy.AUTO else PointerPolicy.NONE
        }
        val presentation = listOf(game, filter, sorting, mine, sortOpen, ownReviews.size, search.value)
        val changed = presentation != previousPresentation
        previousPresentation = presentation
        return changed
    }

    override fun paint(canvas: Canvas) {
        val image = art.reviewsBackground
        canvas.emit(DrawImage(image, Rect(0f, 0f, image.size.width, image.size.height), bounds, sampling = Sampling.LINEAR))
        canvas.rect(0f, 0f, bounds.width, 42f, 0x1008079C.rgba)
        with(canvas) {
            val heading = ui!!.text.layout(
                TextRequest(
                    game?.name?.replace('\n', ' ')?.uppercase() ?: "ОТЗЫВЫ",
                    TextStyle(family = FontFamily(listOf(FontFace("minecraft-ten"))), size = 35.sp)
                )
            )
            text(heading, Vec2(10f, 0f), 0xFFFFFF.rgb)
            val offset = if (game == null) 195f else 255f
            rect(offset - 14f, 8f, 1f, 28f, 0x77777777.rgba)
            label(if (game == null) "73     2.403     ${ownReviews.size}" else "65%", offset, 7f, 21f, 0xE4BB5A.rgb)
            label(
                if (game == null) "РЕЖИМА    ОТЗЫВА    ТВОИХ" else "СМЕШАННЫЕ\n134 ОТЗЫВА", offset + if (game == null) 0f else 53f,
                if (game == null) 28f else 8f, 12f, 0xC5C0C0.rgb
            )
            if (board.children.isEmpty()) label("Пока нет отзывов", bounds.width / 2, 210f, 23f, centered = true)
        }
    }

    override fun close() {
        scope?.close()
        super.close()
    }
}

private class ReviewBoard(model: MenuModel) : MenuElement("review-board", model) {

    var summary = false
    private val scroll = MenuSpring(frequency = 20f)
    private var contentHeight = 0f

    init {
        clipPolicy = ClipPolicy.BOUNDS
        layoutPolicy = LayoutPolicy { children, constraints, _ ->
            val size = Size(constraints.maxWidth, constraints.maxHeight)
            // Build the masonry map once per layout, rather than once per card.
            val positions = positions(size.width)
            LayoutPlan(size, children.map { child ->
                val rect = positions.getValue(child.id)
                ChildPlacement(child.measure(Constraints.fixed(rect.size)), Vec2(rect.x, rect.y - scroll.value))
            })
        }
    }

    private fun positions(width: Float): Map<String, Rect> {
        val result = mutableMapOf<String, Rect>()
        val gap = 20f
        val columns = if (width < 700f) 1 else 3
        val third = (width - gap * (columns - 1)) / columns
        var y = 0f
        var index = 0
        if (featuredSummary(columns)) {
            result[children.first().id] = Rect(0f, 0f, width, 124f)
            y = 124f + gap
            index = 1
        }
        if (!summary && columns == 3) {
            while (index < minOf(6, children.size)) {
                val row = children.drop(index).take(2)
                val height = row.maxOf { if ((it as? ReviewCard)?.expanded == true) 274f else 174f }
                row.forEachIndexed { column, child ->
                    result[child.id] = Rect(column * (width + gap) / 2, y, (width - gap) / 2, height)
                }
                y += height + gap
                index += row.size
            }
            if (children.size >= 9) {
                val upperHeight = if ((children[7] as ReviewCard).expanded) 250f else 142f
                val lowerHeight = if ((children[8] as ReviewCard).expanded) 250f else 138f
                val blockHeight = maxOf(300f, upperHeight + lowerHeight + gap)
                result[children[6].id] = Rect(0f, y, third * 2 + gap, blockHeight)
                result[children[7].id] = Rect(third * 2 + gap * 2, y, third, upperHeight)
                result[children[8].id] = Rect(third * 2 + gap * 2, y + upperHeight + gap, third, lowerHeight)
                y += blockHeight + gap
                index = 9
            }
        }
        while (index < children.size) {
            val row = children.drop(index).take(columns)
            val height = if (summary) 108f else row.maxOf { if ((it as? ReviewCard)?.expanded == true) 260f else 142f }
            row.forEachIndexed { column, child -> result[child.id] = Rect(column * (third + gap), y, third, height) }
            y += height + gap
            index += row.size
        }
        contentHeight = y
        return result
    }

    private fun featuredSummary(columns: Int) = summary && columns == 3 && children.isNotEmpty()

    fun clearItems() {
        children.toList().forEach {
            remove(it)
            it.close()
        }
        contentHeight = 0f
    }

    fun scrollToTop() {
        scroll.snap(0f)
        invalidate(Invalidation.MEASURE)
    }

    fun scrollBy(amount: Float) {
        scroll.target = (scroll.target + amount).coerceIn(0f, (contentHeight - bounds.height).coerceAtLeast(0f))
    }

    override fun update(delta: Duration): Boolean {
        if (model.reducedMotion) scroll.snap()
        val changed = scroll.step(delta.inWholeNanoseconds / 1_000_000_000f)
        if (changed) invalidate(Invalidation.MEASURE)
        children.forEach { child ->
            val rect = child.surface.bounds
            child.opacity = if (rect.bottom > 0f && rect.y < bounds.height) 1f else 0f
        }
        return changed
    }
}

private class SummaryCard(
    model: MenuModel, private val game: GameMode, private val index: Int, private val art: MenuArt,
    open: () -> Unit, rate: () -> Unit
) : MenuButton("summary-${game.id}", model, action = open) {

    private val rateButton = ReviewButton("summary-rate", model, "Оценить режим", rate).apply { blue = true; fontSize = 15.2f }

    init {
        add(rateButton)
        layoutPolicy = positionedLayout { _, size -> Rect(size.width - 170f, 30f, 158f, 29f) }
    }

    override fun update(delta: Duration): Boolean {
        val changed = super.update(delta)
        val parentActive = state.hovered || state.focused
        val buttonActive = rateButton.state.focused || rateButton.state.hovered
        rateButton.opacity = if (index == 0 || parentActive || buttonActive) 1f else 0f
        rateButton.pointerPolicy = if (rateButton.opacity > 0f) PointerPolicy.AUTO else PointerPolicy.NONE
        rateButton.focusable = rateButton.opacity > 0f
        return changed
    }

    override fun paint(canvas: Canvas) {
        canvas.pixelPanel(bounds, 0x3E3837AF.rgba, 0x77706A80.rgba, 3f, 1f)
        val iconSize = if (index == 0) 72f else 54f
        val titleLeft = iconSize + 20f
        canvas.pixelPanel(Rect(10f, 10f, iconSize, iconSize), game.color.rgb, game.color.rgb.shade(1.5f), 2f, 1f)
        val image = art.icons.getValue(game.icon)
        canvas.emit(DrawImage(image, Rect(0f, 0f, image.size.width, image.size.height), Rect(11f, 11f, iconSize - 2f, iconSize - 2f)))
        with(canvas) {
            label(game.name.replace('\n', ' '), titleLeft, 14f, 16f)
            label(if (index % 5 == 0) "в основном хвалят" else "крайне хвалят", titleLeft, 35f, 15.2f, 0x6BC65F.rgb)
            label(if (index == 0) "ЛОББИ · АРКАДЫ" else "PVP · КОМАНДЫ", titleLeft, 54f, 12f, 0xAFA6A4.rgb)
            rect(1f, bounds.height - 29f, bounds.width - 2f, 28f, 0x14121280.rgba)
            emit(
                DrawImage(
                    art.thumbUp, Rect(0f, 0f, art.thumbUp.size.width, art.thumbUp.size.height),
                    Rect(10f, bounds.height - 21f, 13f, 12f), sampling = Sampling.NEAREST
                )
            )
            label(if (index == 0) "72" else "${39 + index}", 29f, bounds.height - 21f, 13f, 0x78C76A.rgb)
            emit(
                DrawImage(
                    art.thumbDown, Rect(0f, 0f, art.thumbDown.size.width, art.thumbDown.size.height),
                    Rect(56f, bounds.height - 21f, 13f, 12f), sampling = Sampling.NEAREST
                )
            )
            label(if (index == 0) "16" else "${index % 7 + 1}", 75f, bounds.height - 21f, 13f, 0xED7563.rgb)
            if (index == 0) label("· ТЫ ИГРАЕШЬ · 3 МИН", 105f, bounds.height - 21f, 13f, 0x58D8ED.rgb)
            label(
                "${40 + index} ОТЗЫВОВ ›", Rect(bounds.width - 139f, bounds.height - 27f, 127f, 23f), 12f, 0xCCC4C1.rgb,
                horizontal = TextAlignment.END
            )
        }
    }
}

private val referenceRatingOrder = listOf(
    "arcade", "castles", "woolbattle", "mind", "bridge", "build", "zombie",
    "hide", "blockparty", "bedrock", "columns", "last", "bee", "testing", "random", "ladder", "io", "lucky", "bingo",
    "anarchy", "survival", "creative"
)
