@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.Ui
import pixzel.component.Element
import pixzel.graphics.Concat
import pixzel.graphics.DrawImage
import pixzel.graphics.Sampling
import pixzel.graphics.rgb
import pixzel.graphics.saved
import pixzel.input.PointerPolicy
import pixzel.layout.unit.sp
import pixzel.surface.Rect
import pixzel.surface.Transform
import pixzel.text.TextRequest
import pixzel.text.TextStyle

internal class TooltipView(val root: Element, private val content: (GameMode) -> Float) {

    var game: GameMode? = null
        private set
    var contentHeight = 260f
        private set
    val show get() = root.visibility == pixzel.component.Visibility.VISIBLE
    fun show(value: GameMode?) {
        if (value != null && value != game) {
            contentHeight = content(value); game = value
        }
        root.showIf(value != null)
    }
}

internal fun gameTooltip(ui: Ui, art: MenuArt): TooltipView {
    val root = ui.compose("tooltip").apply { pointerPolicy = PointerPolicy.NONE; zIndex = 20; showIf(false) }
    return TooltipView(root) { game ->
        root.children.toList().forEach { root.remove(it) }
        val description = ui.text.layout(TextRequest(game.description, TextStyle(size = 20.5f.sp, lineHeight = 24.5f), width = 408f, wrap = true))
        var left = 18f
        var row = 0f
        val tags = game.tags.map { title ->
            val width = ui.text.layout(TextRequest(title, TextStyle(size = 15.2f.sp))).size.width + 10f
            if (left + width > 426f) {
                left = 18f; row += 28f
            }
            (title to Rect(left, row, width, 23f)).also { left += width + 6f }
        }
        val height = description.size.height + 147f + row
        val tagTop = height - 41f - row
        root.draw(ui) {
            tooltipFrame(game.color.rgb, art)
            rect(18f, tagTop - 33f, 9f, 14f, 0xEEEEEE.rgb)
            rect(22f, tagTop - 33f, 4f, 10f, 0xEB5D4A.rgb)
        }
        val controls = pixzel.dsl.ContentScope(ui, root)
        with(controls) {
            val title = text(game.name.replace('\n', ' ')).apply { style = TextStyle(size = 20.5f.sp) }
            val count = text(game.online.toString()).apply { style = TextStyle(size = 20.5f.sp); color = 0x488FEE.rgb }
            val body = text(game.description).apply { style = TextStyle(size = 20.5f.sp, lineHeight = 24.5f); wrap = true }
            val reviews = text("Открыть отзывы").apply { style = TextStyle(size = 22.sp); color = 0xF1ECEB.rgb }
            val titleWidth = ui.text.layout(TextRequest(game.name.replace('\n', ' '), TextStyle(size = 20.5f.sp))).size.width
            val chips = tags.map { (label, rectangle) ->
                val color = tagColor(label)
                text(label).apply {
                    style = TextStyle(size = 15.2f.sp); horizontalAlignment = pixzel.text.TextAlignment.CENTER
                    verticalAlignment = pixzel.text.VerticalAlignment.CENTER
                }.apply {
                    draw(ui) { pixelPanel(bounds, color, color.shade(1.35f), 2f, 1f) }
                } to rectangle
            }
            root.arrange {
                place(title) { Rect(18f, 15f, titleWidth, 28f) }
                place(count) { Rect(25f + titleWidth, 15f, 100f, 28f) }
                place(body) { Rect(18f, 51f, 408f, description.size.height) }
                place(reviews) { Rect(35f, tagTop - 34f, 391f, 28f) }
                chips.forEach { (chip, rect) -> place(chip) { Rect(rect.x, tagTop + rect.y, rect.width, rect.height) } }
            }
        }
        height
    }
}

private fun PixelCanvas.tooltipFrame(color: pixzel.graphics.Color, art: MenuArt) {
    val accent = color.shade(1.8f)
    pixelPanel(bounds, 0x343434.rgb, 0x343434.rgb, 4f, 2f)
    repeat(2) { side ->
        repeat(2) { vertical ->
            saved {
                emit(
                    Concat(
                        Transform.translation(if (side == 0) 50f else bounds.width - 50f, if (vertical == 0) 2f else bounds.height - 2f) *
                                Transform.scale(if (side == 0) -1f else 1f, if (vertical == 0) 1f else -1f)
                    )
                )
                emit(
                    DrawImage(
                        art.corner,
                        Rect(0f, 0f, art.corner.size.width, art.corner.size.height),
                        Rect(0f, 0f, 48f, 48f),
                        accent,
                        Sampling.NEAREST
                    )
                )
            }
        }
    }
    rect(18f, 43f, bounds.width - 36f, 2f, accent)
}

private fun tagColor(label: String) = when (label) {
    "PVP" -> 0xF34D59.rgb
    "СЕЗОННЫЙ", "РАЗВИТИЕ", "ЭКШЕН", "СЕССИОННЫЙ" -> 0x737373.rgb
    else -> 0x00ADC2.rgb
}
