@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.surface.Vec2

/**
 * Original GameCard search/reveal animation, with scrolling composed after its content position.
 */
internal class GameCardPosition {

    private val x = MenuSpring(frequency = 13f)
    private val y = MenuSpring(frequency = 13f)
    private val appearance = MenuSpring(frequency = 12f)
    private var clock = 0f
    private var revealAt = 0f
    private var wasPositioned = false
    val opacity get() = appearance.value.coerceIn(0f, 1f)

    fun destination(left: Float, top: Float, index: Int, reveal: Boolean) {
        // Keep the original destination/reveal algorithm and timings from commit 85c7efd.
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

    fun advance(seconds: Float, shown: Boolean, reducedMotion: Boolean): Boolean {
        clock += seconds
        appearance.target = if (shown && clock >= revealAt) 1f else 0f
        if (reducedMotion) {
            x.snap()
            y.snap()
            appearance.snap(if (shown) 1f else 0f)
        }
        return x.step(seconds) or y.step(seconds) or appearance.step(seconds)
    }

    fun at(scrollOffset: Float) = Vec2(x.value, y.value - scrollOffset)
}
