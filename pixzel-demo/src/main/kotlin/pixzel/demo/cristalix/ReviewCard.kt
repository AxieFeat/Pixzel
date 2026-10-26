// Reference typography, colors and review content are visual fixture data.
@file:Suppress("MagicNumber", "StringLiteralDuplication")

package pixzel.demo.cristalix

import pixzel.graphics.Canvas
import pixzel.graphics.DrawImage
import pixzel.graphics.Sampling
import pixzel.graphics.ImageHandle
import pixzel.graphics.Color
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.surface.Rect
import pixzel.surface.Vec2
import pixzel.layout.unit.sp
import pixzel.text.FontFamily
import pixzel.text.FontFace
import pixzel.text.TextStyle
import pixzel.text.TextRequest
import pixzel.text.alignedOrigin

internal data class PlayerReview(
    val author: String, val hours: String, val date: String, val comment: String, val positive: Boolean = false
)

internal val referenceReviews = listOf(
    PlayerReview(
        "I | zxc_taka_vpotoke", "8.5", "20.10.2026",
        "режим отличный но слишком мало классов и из за этого катки всегда однотипные люди " +
                "берут 1 класс который в 0 уничтожает все классы из за этого играть уже неособо хочется"
    ),
    PlayerReview(
        "MVP | Gambolan", "21.9", "22.10.2026",
        "Плохой режим. Много юнитов = много лагов = не играбельно. Убрал лимиты и теперь не может оптимизацию закинуть"
    ),
    PlayerReview(
        "P+ | _MintTea__", "6.8", "15.10.2026",
        "Добавлять имбаланс расу за донат — пэй ту вин просто. Зато пофиксить карты чтоб норм здания ставить не могут!"
    ),
    PlayerReview("ALBERTO_3", "0.2", "14.10.2026", "Онлайна нету, Токсичные игроки часто встречаются"),
    PlayerReview(
        "V | tvoi_APOSTOL", "143", "16.10.2026",
        "слишком много багов с расстановкой зданий, например на киберсити очень много где не " +
                "дают ставить и на других картах также только 1 сторона нормальная а на другой вообще не ставятся"
    ),
    PlayerReview("MVP | Zhenia_kopibara", "22.2", "09.10.2026", ""),
    PlayerReview(
        "Bronenos", "9.4", "30.09.2026",
        "Новые расы вытесняют другие, без платных лобби играть невозможно, баланса между " +
                "расами нет. У меня списалось 1320 без открытия кейса, так как был телепортирован во время открытия, " +
                "админы пожалуйста верните монеты или дайте одну из рас: тени, снежные люди. Как можно связаться?"
    ),
    PlayerReview(
        "V | gyanyy", "1.0", "30.09.2026",
        "Был крутой режим, когда убрали ограничение количества башен, режим стал максимально интересным. Теперь играть с друзьями ещё веселее!"
    ),
    PlayerReview("V | 22_DOLDEF_22", "1.9", "30.09.2026", "Режим не очень просто потому что днище внатуре"),
    PlayerReview("P+ | Hosmer", "103", "30.09.2026", "Лимит верни, тогда почешу, а так норм, лимит верни"),
    PlayerReview("I | RUSSR", "4.1", "30.09.2026", "Люблю этот режим SpamFight aka BugFight."),
    PlayerReview("G | Bogdan5375", "46.3", "29.09.2026", ""),
    PlayerReview("P | Prili_V", "4.8", "29.09.2026", "баги? что это? не, не слышали"),
    PlayerReview("P | imya_polzovatela", "578", "19.09.2026", "крутой режим, хвалю!", true),
    PlayerReview("SkyTeaWay", "219", "27.09.2026", "крутой режим", true),
    PlayerReview(
        "P+ | valeraDab",
        "506",
        "15.09.2026",
        "крутой режим, но есть куда расти! не помешало бы всякие критичные баги исправлять маленькими патчами",
        true
    ),
    PlayerReview("Sasha89", "5.9", "15.09.2026", "хороший режим", true),
    PlayerReview("Kuzek", "9.1", "15.09.2026", "неплохо", true),
    PlayerReview("V | Manako5", "148", "15.09.2026", "С друзьями играть ещё интереснее!", true),
    PlayerReview("I | delveis", "54.6", "15.09.2026", "Любимый режим, возвращаюсь снова и снова.", true)
)

internal class ReviewButton(id: String, model: MenuModel, title: String, action: () -> Unit) : MenuButton(id, model, title, action) {

    var selected = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    var blue = false
    var icon: ImageHandle? = null
    var fontSize = 11f
    var textColor: Color? = null
    var flat = false
    var caption = ""

    override fun paint(canvas: Canvas) {
        val base = when {
            selected -> 0xEEEEEEFF.rgba
            blue -> 0x2464BFFF.rgba
            else -> 0x17171770.rgba
        }
        val edge = if (blue) 0x629FEBFF.rgba else if (bounds.height > 40f) 0x50505080.rgba else 0x99999980.rgba
        if (!flat) canvas.pixelPanel(bounds, base.shade(1f + hover.value * 0.2f), edge, 2f, 1f)
        val iconWidth = paintIcon(canvas)
        if (caption.isNotEmpty()) {
            with(canvas) {
                label(title, iconWidth, 11f, fontSize, textColor ?: 0xEEEEEE.rgb)
                smallLabel(caption, iconWidth, 26f, 11f, 0xBBBBBB.rgb)
            }
            return
        }
        val family = if (fontSize <= 15f) FontFamily(listOf(FontFace("minecraft-five"))) else FontFamily.DEFAULT
        val measured = ui!!.text.layout(TextRequest(title, TextStyle(size = fontSize.sp, family = family)))
        val fittedSize = fontSize * ((bounds.width - iconWidth - 10f) / measured.size.width.coerceAtLeast(1f)).coerceIn(0.1f, 1f)
        val shaped = ui!!.text.layout(TextRequest(title, TextStyle(size = fittedSize.sp, family = family)))
        val x = if (icon != null) iconWidth else (bounds.width - shaped.size.width) / 2f
        canvas.text(
            shaped, shaped.alignedOrigin(
                Rect(x, 0f, bounds.width - x, bounds.height),
                pixzel.text.TextAlignment.START, pixzel.text.VerticalAlignment.CENTER
            ),
            textColor ?: if (selected) 0x181818.rgb else 0xEEEEEE.rgb
        )
    }

    private fun paintIcon(canvas: Canvas): Float {
        val image = icon ?: return 0f
        val large = bounds.height > 40f
        val size = if (large) 24f else 14f
        canvas.emit(
            DrawImage(
                image, Rect(0f, 0f, image.size.width, image.size.height),
                Rect(if (large) 10f else 6f, (bounds.height - size) / 2, size, size), sampling = Sampling.NEAREST
            )
        )
        return if (large) 45f else 19f
    }
}

internal class ReviewCard(
    id: String, model: MenuModel, val review: PlayerReview, private val index: Int, private val art: MenuArt,
    private val changed: () -> Unit
) : MenuElement(id, model) {

    var expanded = false
        private set
    var vote = 0
        private set
    private var reacted = false
    private var reported = false
    private var previousPresentation: List<Any>? = null
    private val yes = ReviewButton("yes", model, "ДА") { vote = if (vote == 1) 0 else 1 }.apply { icon = art.thumbUp }
    private val no = ReviewButton("no", model, "НЕТ") { vote = if (vote == -1) 0 else -1 }.apply { icon = art.thumbDown }
    private val reaction = ReviewButton("reaction", model, ":)") { reacted = !reacted }.apply { textColor = 0xFFCE25.rgb }
    private val report = ReviewButton("report", model, "!") { reported = !reported }.apply { textColor = 0xDD6655.rgb }
    private val more = ReviewButton("more", model, "ЧИТАТЬ ВСЕ") {
        expanded = !expanded
        changed()
    }.apply { flat = true; textColor = 0x488FEE.rgb; fontSize = 13f }

    init {
        listOf(yes, no, reaction, report, more).forEach(::add)
        layoutPolicy = positionedLayout { child, size ->
            when (child) {
                "yes" -> Rect(size.width - 188f, size.height - 28f, 51f, 20f)
                "no" -> Rect(size.width - 130f, size.height - 28f, 57f, 20f)
                "reaction" -> Rect(size.width - 66f, size.height - 28f, 24f, 20f)
                "report" -> Rect(size.width - 36f, size.height - 28f, 24f, 20f)
                else -> Rect(10f, size.height - 58f, 116f, 18f)
            }
        }
    }

    override fun update(delta: kotlin.time.Duration): Boolean {
        yes.selected = vote == 1
        no.selected = vote == -1
        reaction.selected = reacted
        report.selected = reported
        more.opacity = if (review.comment.length > 120 && bounds.width < 450f) 1f else 0f
        more.title = if (expanded) "СВЕРНУТЬ" else "ЧИТАТЬ ВСЕ"
        more.focusable = more.opacity > 0f
        more.pointerPolicy = if (more.focusable) pixzel.input.PointerPolicy.AUTO else pixzel.input.PointerPolicy.NONE
        val presentation = listOf(expanded, vote, reacted, reported, bounds.width, bounds.height)
        val changed = presentation != previousPresentation
        previousPresentation = presentation
        return changed
    }

    private fun authorColor() = when {
        review.author.startsWith("P") -> 0x6ADB63.rgb
        review.author.startsWith("MVP") -> 0x58D8ED.rgb
        review.author.startsWith("V") -> 0xF1E651.rgb
        else -> 0xC2C0C0.rgb
    }

    override fun paint(canvas: Canvas) {
        canvas.pixelPanel(bounds, 0x363435A8.rgba, 0x66636380.rgba, 3f, 1f)
        val avatar = art.avatars[index % art.avatars.size]
        canvas.emit(DrawImage(avatar, Rect(0f, 0f, avatar.size.width, avatar.size.height), Rect(10f, 11f, 34f, 34f), sampling = Sampling.NEAREST))
        with(canvas) {
            label(review.author, 52f, 12f, 15.2f, authorColor())
            label("${review.hours} ч в режиме · ${review.date}", 52f, 31f, 15.2f, 0xB8B5B6.rgb)
            smallLabel(
                if (review.positive) "ХВАЛЮ" else "РУГАЮ", bounds.width - 57f, 8f, 11f,
                if (review.positive) 0x6BCD66.rgb else 0xE47362.rgb
            )
            val content = review.comment.ifEmpty { "оценка без комментария" }
            val paragraph = ui!!.text.layout(
                TextRequest(
                    content, TextStyle(size = 15.2f.sp, lineHeight = 17f),
                    width = bounds.width - 20f, wrap = true, maxLines = if (more.opacity > 0f && !expanded) 2 else Int.MAX_VALUE
                )
            )
            val bodyBottom = bounds.height - if (more.opacity > 0f) 60f else 35f
            val bodyTop = 52f + ((bodyBottom - 52f - paragraph.size.height) / 2f).coerceAtLeast(0f)
            text(paragraph, Vec2(10f, bodyTop), if (review.comment.isEmpty()) 0x777575.rgb else 0xBDBABB.rgb)
            rect(1f, bounds.height - 35f, bounds.width - 2f, 34f, 0x13131358.rgba)
            smallLabel(if (reported) "ЖАЛОБА ОТПРАВЛЕНА" else "ОТЗЫВ БЫЛ ПОЛЕЗЕН?", 10f, bounds.height - 25f, 11f, 0xBCBABB.rgb)
        }
    }
}
