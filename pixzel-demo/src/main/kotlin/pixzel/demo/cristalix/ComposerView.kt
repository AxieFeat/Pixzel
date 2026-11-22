@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.Ui
import pixzel.component.Element
import pixzel.dsl.image
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.layout.unit.sp
import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.text.TextStyle

internal class ComposerView(val root: Element, val input: MultilineEditor, val overlay: Overlay, private val select: (GameMode) -> Unit) {

    val open get() = overlay.open
    fun show(game: GameMode) {
        select(game); overlay.show()
    }

    fun dismiss() = overlay.dismiss()
}

internal fun reviewComposer(ui: Ui, art: MenuArt, publish: (PlayerReview) -> Unit): ComposerView {
    val root = ui.compose("review-composer").apply { zIndex = 20 }
    val input = MultilineEditor(ui)
    root.add(input.root)
    val overlay = Overlay(root) { input.root }
    var game: GameMode? = null
    var positive: Boolean? = null
    var error = false
    val yes = ratingChoice(ui, "compose-yes", "Хвалю", "советую другим", art.thumbUp, 0x7EE86B.rgb) { positive = true; error = false }
    val no = ratingChoice(ui, "compose-no", "Ругаю", "не советую", art.thumbDown, 0xED7563.rgb) { positive = false; error = false }
    val cancel =
        ui.pixelButton("compose-cancel", "Отмена", PixelButtonStyle.REVIEW.copy(fontSize = 15.2f, smallFont = false), clicked = overlay::dismiss)
    val close = ui.pixelButton("compose-close", "×", PixelButtonStyle.REVIEW.copy(flat = true), clicked = overlay::dismiss)
    val send = ui.pixelButton("compose-publish", "Опубликовать", PixelButtonStyle.REVIEW_BLUE.copy(fontSize = 15.2f, smallFont = false)) {
        val verdict = positive
        if (verdict == null) error = true else {
            publish(localReview(input.value, verdict))
            input.value = ""
            overlay.dismiss()
        }
    }
    val emojiPopup = ui.compose("emoji-popup")
    val emoji = ui.pixelButton(
        "compose-emoji",
        ":)",
        PixelButtonStyle.REVIEW.copy(text = 0xE1C432.rgb)
    ) { emojiPopup.showIf(emojiPopup.visibility != pixzel.component.Visibility.VISIBLE) }
    emojiPopup.showIf(false)
    val emojis = listOf(":)", ":D", "♥", "★", ";)", ":(").mapIndexed { index, value ->
        ui.pixelButton("emoji-$index", value, PixelButtonStyle.REVIEW) { input.insert(value); emojiPopup.showIf(false) }.also(emojiPopup::add)
    }
    emojiPopup.arrange { emojis.forEachIndexed { index, button -> place(button) { Rect(index % 3 * 59f, index / 3 * 29f, 55f, 26f) } } }
    listOf(yes, no, cancel, close, send, emoji, emojiPopup).forEach(root::add)
    root.arrange {
        place(yes) { composerPanel(it).let { box -> Rect(box.x + 11f, box.y + 81f, (box.width - 34f) / 2, 54f) } }
        place(no) { composerPanel(it).let { box -> Rect(box.x + box.width / 2 + 6f, box.y + 81f, (box.width - 34f) / 2, 54f) } }
        place(input.root) { composerPanel(it).let { box -> Rect(box.x + 11f, box.y + 165f, box.width - 22f, 116f) } }
        place(cancel) { composerPanel(it).let { box -> Rect(box.right - 225f, box.bottom - 39f, 77f, 28f) } }
        place(send) { composerPanel(it).let { box -> Rect(box.right - 139f, box.bottom - 39f, 128f, 28f) } }
        place(close) { composerPanel(it).let { box -> Rect(box.right - 27f, box.y + 14f, 19f, 19f) } }
        place(emoji) { composerPanel(it).let { box -> Rect(box.right - 35f, box.y + 253f, 21f, 21f) } }
        place(emojiPopup) { composerPanel(it).let { box -> Rect(box.right - 200f, box.y + 254f, 177f, 58f) } }
    }
    root.draw(ui) {
        composerFrame(game, error, input.value.length)
    }
    root.observe({ listOf(game, positive, error, input.value) }) {
        yes.selected = positive == true
        no.selected = positive == false
        root.invalidate()
    }
    return ComposerView(root, input, overlay) { selected ->
        game = selected; positive = null; error = false; emojiPopup.showIf(false); root.invalidate()
    }
}

private fun composerPanel(size: Size): Rect {
    val width = minOf(548f, size.width - 28f)
    return Rect((size.width - width) / 2, (size.height - 360f) / 2, width, 360f)
}

@Suppress("LongParameterList") // Shared button content accepts independent text, icon and tint.
private fun ratingChoice(
    ui: Ui, id: String, title: String, caption: String, icon: pixzel.graphics.ImageHandle,
    color: pixzel.graphics.Color, selected: () -> Unit
): pixzel.component.Button {
    val buttonStyle = PixelButtonStyle.REVIEW.copy(fill = 0x232323.rgb, edge = 0x50505080.rgba, padding = 9f)
    val button = ui.pixelButton(id, style = buttonStyle, clicked = selected)
    val contents = ui.compose("$id-content") {
        val picture = image(icon) { sampling = pixzel.graphics.Sampling.NEAREST }
        val label = text(title).apply { style = TextStyle(size = 15.2f.sp); this.color = color }
        val detail = text(caption).apply { style = TextStyle(size = 11.sp, family = SMALL_FONT); this.color = 0xBBBBBB.rgb }
        parent.arrange {
            place(picture) { Rect(1f, (it.height - 24f) / 2, 24f, 24f) }
            place(label) { Rect(36f, 11f, it.width - 36f, 20f) }
            place(detail) { Rect(36f, 26f, it.width - 36f, 20f) }
        }
    }
    button.setContent(contents)
    contents.layout.width = pixzel.layout.unit.fillAvailable
    contents.layout.height = pixzel.layout.unit.fillAvailable
    return button
}

private fun PixelCanvas.composerFrame(game: GameMode?, error: Boolean, length: Int) {
    rect(0f, 0f, bounds.width, bounds.height, 0x000000AD.rgba)
    val box = composerPanel(bounds.size)
    pixelPanel(box, 0x343434EF.rgba, 0x505050.rgb, 4f, 2f)
    label("Отзыв о ${game?.name?.replace('\n', ' ')?.lowercase()}", box.x + 12f, box.y + 9f, 15.2f)
    smallLabel("Отзыв увидят все игроки", box.x + 12f, box.y + 24f, 11f, 0xB5B5B5.rgb)
    rect(box.x + 1f, box.y + 49f, box.width - 2f, 1f, 0x55555580.rgba)
    smallLabel(if (error) "ВЫБЕРИ ОЦЕНКУ РЕЖИМА" else "КАК ТЕБЕ РЕЖИМ?", box.x + 12f, box.y + 57f, 11f, if (error) 0xF28273.rgb else 0xB5B5B5.rgb)
    smallLabel("РАССКАЖИ, ПОЧЕМУ (НЕОБЯЗАТЕЛЬНО, НО ТАК ПОЛЕЗНЕЕ)", box.x + 12f, box.y + 144f, 11f, 0xACACAC.rgb)
    smallLabel("${length} / 500", box.right - 69f, box.y + 286f, 11f, 0x848484.rgb)
    rect(box.x + 1f, box.bottom - 48f, box.width - 2f, 47f, 0x17171777.rgba)
    smallLabel("ОСКОРБЛЕНИЯ И РЕКЛАМА — В БАН", box.x + 12f, box.bottom - 28f, 11f, 0x858585.rgb)
}

private fun localReview(value: String, positive: Boolean) = PlayerReview(
    "Ты", "0.0", java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")), value, positive
)
