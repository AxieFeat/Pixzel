// Pixel geometry, palette and motion timings are authored as visual design data.
@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.graphics.Canvas
import pixzel.graphics.DrawImage
import pixzel.graphics.ImageHandle
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
    private val image: ImageHandle,
    private val openGame: (GameMode) -> Unit
) : MenuButton("game-${game.id}", model, game.name.replace('\n', ' ')) {

    private val x = MenuSpring(frequency = 13f)
    private val y = MenuSpring(frequency = 13f)
    private val appearance = MenuSpring(frequency = 12f)
    private val favoritePulse = MenuSpring(frequency = 19f)
    private var clock = 0f
    private var revealAt = 0f
    private var wasPositioned = false
    var shown = true
    var hovered = false
    private var lastFavorite = game.id in model.favorites

    fun destination(left: Float, top: Float, index: Int, reveal: Boolean) {
        x.target = left
        y.target = top
        if (!wasPositioned || reveal) {
            x.snap(left)
            y.snap(top + 24f)
            y.target = top
            appearance.snap(0f)
            revealAt = clock + index.coerceAtMost(18) * 0.024f
            wasPositioned = true
        }
    }

    override fun activate(position: Vec2?) {
        if (position != null && position.x > bounds.width - 40f && position.y < 45f) {
            model.toggleFavorite(game.id)
        } else {
            openGame(game)
        }
    }

    override fun update(delta: Duration): Boolean {
        super.update(delta)
        val seconds = delta.inWholeNanoseconds / 1_000_000_000f
        clock += seconds
        hovered = state.hovered || state.focusVisible
        appearance.target = if (shown && clock >= revealAt) 1f else 0f
        val favorite = game.id in model.favorites
        if (favorite != lastFavorite) {
            favoritePulse.snap(1f)
            favoritePulse.target = 0f
            lastFavorite = favorite
        }

        if (model.reducedMotion) {
            x.snap()
            y.snap()
            appearance.snap(if (shown) 1f else 0f)
            favoritePulse.snap(0f)
        }
        x.step(seconds)
        y.step(seconds)
        appearance.step(seconds)
        favoritePulse.step(seconds)
        opacity = appearance.value.coerceIn(0f, 1f)
        pointerPolicy = if (shown && opacity > 0.5f) PointerPolicy.AUTO else PointerPolicy.NONE
        focusable = shown && opacity > 0.5f

        val lift = if (model.reducedMotion) 0f else hover.value * 6f
        val scale = 1f + hover.value * 0.025f - press.value * 0.025f
        paintTransform = Transform.translation(x.value - bounds.width * (scale - 1f) / 2f, y.value - lift) * Transform.scale(scale)
        zIndex = if (hovered) 1 else 0
        invalidate()
        return true
    }

    override fun paint(canvas: Canvas) {
        val base = game.color.rgb
        canvas.pixelPanel(bounds, base.shade(1f + hover.value * 0.12f), base.shade(1.38f + hover.value * 0.28f))
        canvas.rect(5f, 5f, bounds.width - 10f, 1f, 0xFFFFFF10.rgba)
        val floating = if (model.reducedMotion) 0f else sin(clock * 1.8f + game.online) * (1f + hover.value * 2f)
        val iconSize = (bounds.width + 26f).coerceAtMost(192f)
        val iconRect = Rect((bounds.width - iconSize) / 2f, -7f + floating - hover.value * 6f, iconSize, iconSize)
        canvas.emit(DrawImage(image, Rect(0f, 0f, image.size.width, image.size.height), iconRect, sampling = Sampling.NEAREST))

        with(canvas) {
            val lines = game.name.split('\n')
            lines.forEachIndexed { index, line ->
                val measured = ui!!.text.layout(TextRequest(line, TextStyle(size = 19.sp), textScale = ui!!.settings.textScale)).size.width
                val fontSize = 19f * ((bounds.width - 14f) / measured.coerceAtLeast(1f)).coerceAtMost(1f)
                label(
                    line, Rect(7f, bounds.height - 59f - (lines.size - 1 - index) * 22f, bounds.width - 14f, 22f),
                    fontSize, shadow = true
                )
            }
            label(
                model.label("Онлайн", "Online") + " ${game.online}", Rect(7f, bounds.height - 33f, bounds.width - 14f, 25f),
                18f, 0xFFFFFFA0.rgba
            )

            if (hover.value > 0.01f) {
                pixelPanel(
                    Rect(5f, 5f, bounds.width - 10f, bounds.height - 10f), 0x070A12FF.rgba.alpha(hover.value * 0.88f),
                    base.shade(1.5f).alpha(hover.value)
                )
                label(
                    model.label("ИГРАТЬ", "PLAY"), Rect(5f, bounds.height / 2f - 24f, bounds.width - 10f, 48f),
                    27f, 0xFFFFFFFF.rgba.alpha(hover.value), shadow = true
                )
                label(
                    model.label("Нажми, чтобы войти", "Click to join"), bounds.width / 2f, bounds.height / 2f + 23f,
                    11f, 0xFFFFFFB0.rgba.alpha(hover.value), centered = true
                )
            }

            if (game.badge.isNotEmpty() && hover.value < 0.7f) {
                val badgeWidth = game.badge.length * 8f + 12f
                val badge = Rect(bounds.width - badgeWidth - 9f, 10f, badgeWidth, 25f)
                pixelPanel(badge, 0xDC4057.rgb, 0xFF7C8C.rgb, 3f)
                label(game.badge, badge, 14f)
            }

            paintFavorite()
        }
    }


    private fun Canvas.paintFavorite() {
        if (lastFavorite || hover.value > 0.1f) {
            val size = 20f + favoritePulse.value * 9f
            star(
                bounds.width - 27f - size / 2f, 16f - favoritePulse.value * 4f, size,
                if (lastFavorite) 0xFFDA65.rgb else 0xFFFFFFFF.rgba.alpha(hover.value)
            )
            if (favoritePulse.value > 0.03f) {
                repeat(5) { index ->
                    val distance = (1f - favoritePulse.value) * 35f
                    val angle = index * 1.256f
                    rect(
                        bounds.width - 26f + cos(angle) * distance,
                        24f + sin(angle) * distance, 3f, 3f, 0xFFE5A0FF.rgba.alpha(favoritePulse.value)
                    )
                }
            }
        }
    }
}
