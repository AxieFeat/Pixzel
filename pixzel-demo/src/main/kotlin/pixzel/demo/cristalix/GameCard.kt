// Pixel geometry, palette and motion timings are authored as visual design data.
@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.graphics.Canvas
import pixzel.graphics.DrawImage
import pixzel.graphics.Sampling
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.input.PointerPolicy
import pixzel.layout.unit.sp
import pixzel.surface.Rect
import pixzel.surface.Transform
import pixzel.surface.Vec2
import pixzel.text.TextRequest
import pixzel.text.TextStyle
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration

internal class GameCard(
    val game: GameMode,
    model: MenuModel,
    private val art: MenuArt,
    private val openGame: (GameMode) -> Unit
) : MenuButton("game-${game.id}", model, game.name.replace('\n', ' ')) {

    private val favoritePulse = MenuSpring(frequency = 19f)
    var shown = true
    var hovered = false
    private var lastFavorite = game.id in model.favorites

    fun destination(left: Float, top: Float) {
        // The grid combines animated search positions with its shared, immediate scroll offset.
        paintTransform = Transform.translation(left, top)
    }

    override fun activate(position: Vec2?) {
        if (position != null && position.x in (bounds.width / 2 - 25f)..(bounds.width / 2 + 25f) && position.y < 45f) {
            model.toggleFavorite(game.id)
        } else {
            openGame(game)
        }
    }

    override fun update(delta: Duration): Boolean {
        val buttonChanged = super.update(delta)
        val seconds = delta.inWholeNanoseconds / 1_000_000_000f
        hovered = state.hovered || state.focusVisible
        hover.snap(if (hovered) 1f else 0f)
        val favorite = game.id in model.favorites
        if (favorite != lastFavorite) {
            favoritePulse.snap(1f)
            favoritePulse.target = 0f
            lastFavorite = favorite
        }

        if (model.reducedMotion) {
            favoritePulse.snap(0f)
        }
        val pulsing = favoritePulse.step(seconds)
        pointerPolicy = if (shown && opacity > 0.5f) PointerPolicy.AUTO else PointerPolicy.NONE
        focusable = shown && opacity > 0.5f
        zIndex = if (hovered) 1 else 0
        return buttonChanged || pulsing
    }

    override fun paint(canvas: Canvas) {
        val base = game.color.rgb
        canvas.pixelPanel(bounds, base, base.shade(1.52f), 6f, 2f)
        val iconSize = bounds.width * 0.88f
        val iconRect = Rect((bounds.width - iconSize) / 2, 14f, iconSize, iconSize)
        val image = art.icons.getValue(game.icon)
        canvas.emit(DrawImage(image, Rect(0f, 0f, image.size.width, image.size.height), iconRect, sampling = Sampling.NEAREST))

        with(canvas) {
            val lines = game.name.split('\n')
            lines.forEachIndexed { index, line ->
                val measured = ui!!.text.layout(TextRequest(line, TextStyle(size = 22.sp), textScale = ui!!.settings.textScale)).size.width
                val fontSize = 22f * ((bounds.width - 10f) / measured.coerceAtLeast(1f)).coerceAtMost(1f)
                label(
                    line, Rect(5f, bounds.height - 60f - (lines.size - 1 - index) * 19f, bounds.width - 10f, 27f),
                    fontSize
                )
            }
            label(
                model.label("Онлайн", "Online") + " ${game.online}", Rect(7f, bounds.height - 33f, bounds.width - 14f, 25f),
                22f, 0xFFFFFFA0.rgba
            )

            if (hover.value > 0.01f) {
                pixelPanel(
                    bounds, 0x000000E6.rgba,
                    base.shade(0.35f), 6f, 2f
                )
                label(
                    model.label("ИГРАТЬ", "PLAY"), Rect(5f, bounds.height / 2f - 24f, bounds.width - 10f, 48f),
                    31f, 0xFFFFFFFF.rgba.alpha(hover.value)
                )
            }

            if (game.badge.isNotEmpty() && !hovered) {
                val badgeWidth = ui!!.text.layout(TextRequest(game.badge, TextStyle(size = 20.sp))).size.width + 10f
                val badge = Rect(bounds.width - badgeWidth - 10f, 10f, badgeWidth, 25f)
                pixelPanel(badge, 0xC32621.rgb, 0xFA685E.rgb, 3f, 2f)
                label(game.badge, badge, 20f)
            }

            paintFavorite()
        }
    }


    private fun Canvas.paintFavorite() {
        if (hovered) {
            val size = 20f + favoritePulse.value * 9f
            star(
                bounds.width / 2f - size / 2f, 16f - favoritePulse.value * 4f, size,
                if (lastFavorite) 0xFFDA65.rgb else 0xFFFFFF.rgb
            )
            if (favoritePulse.value > 0.03f) {
                repeat(5) { index ->
                    val distance = (1f - favoritePulse.value) * 35f
                    val angle = index * 1.256f
                    rect(
                        bounds.width / 2f + cos(angle) * distance,
                        24f + sin(angle) * distance, 3f, 3f, 0xFFE5A0FF.rgba.alpha(favoritePulse.value)
                    )
                }
            }
        }
    }
}
