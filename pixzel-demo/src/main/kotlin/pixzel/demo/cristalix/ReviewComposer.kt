@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.graphics.Canvas
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.input.PointerPolicy
import pixzel.resource.Subscription
import pixzel.surface.Rect

internal class ReviewComposer(model: MenuModel, art: MenuArt, private val publish: (PlayerReview) -> Unit) : MenuElement("review-composer", model) {

    var open = false
        private set
    var positive: Boolean? = null
        private set
    private var game: GameMode? = null
    private var scope: Subscription? = null
    private var emojiOpen = false
    private var error = false
    private var previousPresentation: List<Any?>? = null
    val input = ReviewTextArea(model)
    private val yes = ReviewButton("compose-yes", model, "Хвалю") {
        positive = true
        error = false
    }.apply {
        icon = art.thumbUp
        fontSize = 15.2f
        caption = "советую другим"
        textColor = 0x7EE86B.rgb
    }
    private val no = ReviewButton("compose-no", model, "Ругаю") {
        positive = false
        error = false
    }.apply {
        icon = art.thumbDown
        fontSize = 15.2f
        caption = "не советую"
        textColor = 0xED7563.rgb
    }
    private val cancel = ReviewButton("compose-cancel", model, "Отмена", ::dismiss).apply { fontSize = 15.2f }
    private val close = ReviewButton("compose-close", model, "×", ::dismiss).apply { flat = true; fontSize = 15.2f }
    private val send = ReviewButton("compose-publish", model, "Опубликовать", ::submit).apply {
        blue = true
        fontSize = 15.2f
    }
    private val emoji = ReviewButton("compose-emoji", model, ":)") { emojiOpen = !emojiOpen }.apply { textColor = 0xE1C432.rgb }
    private val emojis = listOf(":)", ":D", "♥", "★", "\u003B)", ":(").mapIndexed { index, value ->
        ReviewButton("compose-emoji-$index", model, value) {
            input.insert(value)
            emojiOpen = false
        }
    }

    init {
        zIndex = 20
        opacity = 0f
        pointerPolicy = PointerPolicy.NONE
        listOf(input, yes, no, cancel, close, send, emoji).forEach(::add)
        emojis.forEach(::add)
        layoutPolicy = positionedLayout { id, size ->
            val box = panel(size.width, size.height)
            when (id) {
                "compose-yes" -> Rect(box.x + 11f, box.y + 81f, (box.width - 34f) / 2, 54f)
                "compose-no" -> Rect(box.x + box.width / 2 + 6f, box.y + 81f, (box.width - 34f) / 2, 54f)
                "review-message" -> Rect(box.x + 11f, box.y + 165f, box.width - 22f, 116f)
                "compose-cancel" -> Rect(box.right - 225f, box.bottom - 39f, 77f, 28f)
                "compose-publish" -> Rect(box.right - 139f, box.bottom - 39f, 128f, 28f)
                "compose-close" -> Rect(box.right - 27f, box.y + 14f, 19f, 19f)
                "compose-emoji" -> Rect(box.right - 35f, box.y + 253f, 21f, 21f)
                else -> Rect(
                    box.right - 200f + id.substringAfterLast('-').toInt() % 3 * 59f,
                    box.y + 254f + id.substringAfterLast('-').toInt() / 3 * 29f, 55f, 26f
                )
            }
        }
        setInteractive(false)
    }

    private fun panel(width: Float, height: Float): Rect {
        val w = minOf(548f, width - 28f)
        return Rect((width - w) / 2, (height - 360f) / 2, w, 360f)
    }

    private fun setInteractive(enabled: Boolean) {
        pointerPolicy = if (enabled) PointerPolicy.AUTO else PointerPolicy.NONE
        children.forEach {
            it.pointerPolicy = pointerPolicy
            it.focusable = enabled
        }
    }

    fun show(game: GameMode) {
        this.game = game
        open = true
        opacity = 1f
        positive = null
        error = false
        setInteractive(true)
        scope?.close()
        scope = ui?.focus?.pushScope(this)
        ui?.focus?.request(input)
        invalidate()
    }

    fun dismiss() {
        open = false
        opacity = 0f
        emojiOpen = false
        setInteractive(false)
        scope?.close()
        scope = null
    }

    private fun submit() {
        val verdict = positive
        if (verdict == null) {
            error = true
            return
        }
        publish(PlayerReview("Ты", "0.0", "25.10.2026", input.value, verdict))
        input.value = ""
        dismiss()
    }

    override fun update(delta: kotlin.time.Duration): Boolean {
        if (!open) return false
        yes.selected = positive == true
        no.selected = positive == false
        emojis.forEach {
            it.opacity = if (emojiOpen) 1f else 0f
            it.pointerPolicy = if (emojiOpen) PointerPolicy.AUTO else PointerPolicy.NONE
            it.focusable = emojiOpen
        }
        val presentation = listOf(game, positive, error, emojiOpen, input.value)
        val changed = presentation != previousPresentation
        previousPresentation = presentation
        return changed
    }

    override fun paint(canvas: Canvas) {
        canvas.rect(0f, 0f, bounds.width, bounds.height, 0x000000AD.rgba)
        val box = panel(bounds.width, bounds.height)
        canvas.pixelPanel(box, 0x343434EF.rgba, 0x505050.rgb, 4f, 2f)
        with(canvas) {
            label("Отзыв о ${game?.name?.replace('\n', ' ')?.lowercase()}", box.x + 12f, box.y + 9f, 15.2f)
            smallLabel("Отзыв увидят все игроки", box.x + 12f, box.y + 24f, 11f, 0xB5B5B5.rgb)
            rect(box.x + 1f, box.y + 49f, box.width - 2f, 1f, 0x55555580.rgba)
            smallLabel(
                if (error) "ВЫБЕРИ ОЦЕНКУ РЕЖИМА" else "КАК ТЕБЕ РЕЖИМ?", box.x + 12f, box.y + 57f, 11f,
                if (error) 0xF28273.rgb else 0xB5B5B5.rgb
            )
            smallLabel("РАССКАЖИ, ПОЧЕМУ (НЕОБЯЗАТЕЛЬНО, НО ТАК ПОЛЕЗНЕЕ)", box.x + 12f, box.y + 144f, 11f, 0xACACAC.rgb)
            smallLabel("${input.value.length} / 500", box.right - 69f, box.y + 286f, 11f, 0x848484.rgb)
            rect(box.x + 1f, box.bottom - 48f, box.width - 2f, 47f, 0x17171777.rgba)
            smallLabel("ОСКОРБЛЕНИЯ И РЕКЛАМА — В БАН", box.x + 12f, box.bottom - 28f, 11f, 0x858585.rgb)
        }
    }

    override fun close() {
        scope?.close()
        super.close()
    }
}
