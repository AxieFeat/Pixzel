// Pixel geometry, palette and motion timings are authored as visual design data.
@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.component.AbstractElement
import pixzel.component.ClipPolicy
import pixzel.component.Invalidation
import pixzel.layout.ChildPlacement
import pixzel.layout.Constraints
import pixzel.layout.LayoutPlan
import pixzel.layout.LayoutPolicy
import pixzel.surface.Size
import pixzel.surface.Vec2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.ceil
import kotlin.time.Duration

internal class GameGrid(
    private val model: MenuModel,
    art: MenuArt,
    openGame: (GameMode) -> Unit
) : AbstractElement("game-grid") {

    val cards = gameModes.map { GameCard(it, model, art.icons.getValue(it.icon), openGame) }
    private val scroll = MenuSpring(frequency = 17f)
    private var previousSignature = ""
    private var previousWidth = -1f
    private var previousScroll = -1f
    var visibleCount = gameModes.size
        private set
    val hoveredCard get() = cards.firstOrNull { it.shown && (it.state.hovered || it.state.focusVisible) }
    val scrollOffset get() = scroll.value
    private var contentHeight = 0f
    val maximumScroll get() = max(0f, contentHeight - surface.bounds.height + 20f)

    init {
        clipPolicy = ClipPolicy.BOUNDS
        cards.forEach(::add)
        layoutPolicy = LayoutPolicy { children, constraints, _ ->
            val columns = columns(constraints.maxWidth)
            val width = (constraints.maxWidth - (columns - 1) * GAP) / columns
            val size = Size(constraints.maxWidth, constraints.maxHeight)
            LayoutPlan(size, children.map { child ->
                ChildPlacement(child.measure(Constraints.fixed(Size(width, width * CARD_RATIO))), Vec2.ZERO)
            })
        }
    }

    fun scrollBy(amount: Float) {
        scroll.target = (scroll.target + amount).coerceIn(0f, maximumScroll)
    }

    fun scrollToTop() {
        scroll.target = 0f
    }

    override fun update(delta: Duration): Boolean {
        val visible = model.visibleModes()
        val signature = visible.joinToString { it.id }
        val changed = signature != previousSignature
        if (changed) scroll.target = 0f
        scroll.target = scroll.target.coerceIn(0f, maximumScroll)
        if (model.reducedMotion) scroll.snap()
        val moving = scroll.step(delta.inWholeNanoseconds / 1_000_000_000f)
        val width = surface.bounds.width
        if (width <= 0) return false

        if (changed || width != previousWidth || scroll.value != previousScroll) {
            val columns = columns(width)
            val cardWidth = (width - (columns - 1) * GAP) / columns
            val cardHeight = cardWidth * CARD_RATIO
            contentHeight = ceil(visible.size.toFloat() / columns) * (cardHeight + GAP) - GAP
            visibleCount = visible.size

            cards.forEach { card ->
                val index = visible.indexOf(card.game)
                val wasShown = card.shown
                card.shown = index >= 0
                if (index >= 0) card.destination(
                    index % columns * (cardWidth + GAP), index / columns * (cardHeight + GAP) - scroll.value,
                    index, !wasShown
                )
            }
            previousSignature = signature
            previousWidth = width
            previousScroll = scroll.value
            invalidate(Invalidation.PAINT)
        }

        return moving
    }

    private fun columns(width: Float) = floor((width + GAP) / 176f).toInt().coerceIn(2, 6)

    companion object {

        private const val GAP = 16f
        private const val CARD_RATIO = 1.4125f
    }
}
