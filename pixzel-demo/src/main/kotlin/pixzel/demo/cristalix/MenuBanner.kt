// Pixel geometry, palette and motion timings are authored as visual design data.
@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.graphics.Canvas
import pixzel.graphics.ColorStop
import pixzel.graphics.DrawImage
import pixzel.graphics.LinearGradient
import pixzel.graphics.RadialGradient
import pixzel.graphics.Rectangle
import pixzel.graphics.Sampling
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.surface.Rect
import pixzel.surface.Vec2
import kotlin.math.sin
import kotlin.time.Duration

internal class MenuBanner(model: MenuModel, private val art: MenuArt, action: () -> Unit) : MenuButton("banner", model, action = action) {

    private var clock = 0f

    override fun update(delta: Duration): Boolean {
        super.update(delta)
        if (!model.reducedMotion) clock += delta.inWholeNanoseconds / 1_000_000_000f
        invalidate()
        return true
    }

    override fun paint(canvas: Canvas) {
        canvas.pixelPanel(bounds, 0x321D29.rgb, 0x80706A.rgb, 6f)
        canvas.fill(
            Rectangle, Rect(3f, 3f, bounds.width - 6f, bounds.height - 6f), LinearGradient(
                Vec2(0f, 0f), Vec2(bounds.width, bounds.height * 0.2f),
                listOf(ColorStop(0f, 0x2D1724.rgb), ColorStop(0.44f, 0x4E2D31.rgb), ColorStop(1f, 0xC1A222.rgb))
            )
        )
        canvas.fill(
            Rectangle, bounds, RadialGradient(
                Vec2(bounds.width * 0.85f, 20f), 230f,
                listOf(ColorStop(0f, 0xFFE52ABD.rgba), ColorStop(1f, 0xFFE52A00.rgba))
            )
        )

        val right = bounds.width
        with(canvas) {
            polygon(
                listOf(
                    Vec2(right * 0.61f, 160f), Vec2(right * 0.78f, 100f), Vec2(right - 30f, 147f),
                    Vec2(right * 0.82f, 195f)
                ), 0xFFC53B.rgb
            )
            polygon(
                listOf(
                    Vec2(right * 0.61f, 155f), Vec2(right * 0.78f, 95f), Vec2(right - 30f, 142f),
                    Vec2(right * 0.82f, 190f)
                ), 0x3F3A3A.rgb
            )
            val bob = sin(clock * 1.5f) * 4f
            fun sprite(key: String, x: Float, y: Float, size: Float) {
                val image = art.icons.getValue(key)
                emit(DrawImage(image, Rect(0f, 0f, 192f, 192f), Rect(x, y, size, size), sampling = Sampling.NEAREST))
            }
            sprite("avatar", right * 0.49f, -2f + bob, 192f)
            sprite("avatar", right * 0.41f, 74f - bob, 141f)
            sprite("chest", right * 0.66f, 18f - bob, 181f)
            sprite("computer", right * 0.83f, 6f + bob, 113f)

            repeat(10) { index ->
                val x = right * 0.5f + ((index * 47f + clock * 13f) % (right * 0.47f))
                val y = 17f + (index * 31f) % 150f + sin(clock + index) * 4f
                rect(x, y, 2f, 2f, 0xFFEAA0FF.rgba.alpha(0.3f + sin(clock * 2f + index) * 0.15f))
            }

            val title = model.label("Новый кейс!\nРазработки Арасаки", "New case!\nArasaka technologies")
            label(title, 24f, 20f, if (bounds.width < 800f) 28f else 34f, shadow = true)
            val buttonWidth = if (model.english) 227f else 268f
            pixelPanel(
                Rect(28f, bounds.height - 65f, buttonWidth, 39f), 0x2862AD.rgb.shade(1f + hover.value * 0.25f),
                0x6BA6F0.rgb, 4f
            )
            label(model.label("Приступить к взлому", "Start the heist"), Rect(28f, bounds.height - 65f, buttonWidth, 39f), 18f, shadow = true)
        }
    }
}
