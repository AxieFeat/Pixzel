// Reference coordinates and colors are visual design data.
@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.graphics.Canvas
import pixzel.graphics.Concat
import pixzel.graphics.DrawImage
import pixzel.graphics.Sampling
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.graphics.saved
import pixzel.component.ClipPolicy
import pixzel.surface.Rect
import pixzel.surface.Transform
import pixzel.surface.Vec2
import pixzel.layout.unit.sp
import pixzel.text.TextRequest
import pixzel.text.TextStyle
import kotlin.time.Duration

internal class MenuBanner(model: MenuModel, private val art: MenuArt, action: () -> Unit) : MenuButton("banner", model, action = action) {

    private var clock = 0f
    val slide get() = (clock / SLIDE_SECONDS).toInt() % 2
    val progress get() = (clock % SLIDE_SECONDS) / SLIDE_SECONDS

    init {
        clipPolicy = ClipPolicy.BOUNDS
    }

    override fun update(delta: Duration): Boolean {
        super.update(delta)
        if (!model.reducedMotion) clock += delta.inWholeNanoseconds / 1_000_000_000f
        invalidate()
        return true
    }

    override fun paint(canvas: Canvas) {
        canvas.pixelPanel(bounds, 0x343146.rgb, 0x79718A.rgb, 6f, 2f)
        val transition = if (clock < SLIDE_SECONDS || model.reducedMotion) 1f else ((clock % SLIDE_SECONDS) / 0.4f).coerceIn(0f, 1f)
        if (transition < 1f) paintSlide(canvas, 1 - slide, -bounds.width * transition)
        paintSlide(canvas, slide, bounds.width * (1f - transition))
        with(canvas) {
            repeat(2) { index ->
                val x = bounds.width / 2f - 42f + index * 46f
                rect(x, bounds.height - 17f, 39f, 4f, 0xFFFFFF40.rgba)
                if (index == 1 - slide) rect(x, bounds.height - 17f, 39f * progress, 4f, 0xFFFFFF.rgb)
            }
        }
    }

    private fun paintSlide(canvas: Canvas, index: Int, offset: Float) = canvas.saved {
        emit(Concat(Transform.translation(offset, 0f)))
        // Artwork contains no controls or lettering: both are real Pixzel drawing below.
        val image = art.banners[index]
        canvas.emit(
            DrawImage(
                image, Rect(0f, 0f, image.size.width, image.size.height),
                Rect(2f, 2f, bounds.width - 4f, bounds.height - 4f), sampling = Sampling.LINEAR
            )
        )
        with(canvas) {
            val title = if (index == 0) "Новый кейс:\nНочь Кошмаров" else "Новый кейс:\nРазработки Арасаки"
            text(ui!!.text.layout(TextRequest(title, TextStyle(size = 41.sp, lineHeight = 41f))), Vec2(25f, 24f), 0xFFFFFF.rgb)
            val buttonWidth = if (index == 0) 265f else 263f
            val button = Rect(29f, bounds.height - 65f, buttonWidth, 38f)
            pixelPanel(button, 0x2362BE.rgb.shade(1f + hover.value * 0.18f), 0x5C9FF7.rgb, 4f, 2f)
            label(if (index == 0) "Сладость или гадость!" else "Приступить к взлому", button, 20f)
        }
    }

    companion object {

        private const val SLIDE_SECONDS = 5f
    }
}
