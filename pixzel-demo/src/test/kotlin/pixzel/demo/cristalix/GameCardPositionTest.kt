package pixzel.demo.cristalix

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pixzel.surface.Vec2

class GameCardPositionTest {

    @Test
    fun searchRetargetsContinuously() {
        val position = GameCardPosition()
        position.destination(400f, 350f, 0, reveal = false)
        repeat(120) { position.advance(.016f, shown = true, reducedMotion = false) }
        position.destination(0f, 102f, 0, reveal = false)
        assertEquals(Vec2(400f, 350f), position.at(0f))
        position.advance(.016f, shown = true, reducedMotion = false)
        val intermediate = position.at(0f)
        assertTrue(intermediate.x in 0f..399.99f && intermediate.y in 102f..349.99f)
        position.destination(200f, 102f, 0, reveal = false)
        assertEquals(intermediate, position.at(0f))
        repeat(120) { position.advance(.016f, shown = true, reducedMotion = false) }
        assertEquals(Vec2(200f, 102f), position.at(0f))
    }

    @Test
    fun scrollDoesNotLagDuringSearch() {
        val position = GameCardPosition()
        position.destination(400f, 350f, 0, reveal = false)
        repeat(120) { position.advance(.016f, shown = true, reducedMotion = false) }
        position.destination(0f, 102f, 0, reveal = false)
        position.advance(.016f, shown = true, reducedMotion = false)
        val beforeScroll = position.at(0f)
        // Sending the same content destination on a scroll frame must preserve the search animation.
        position.destination(0f, 102f, 0, reveal = false)
        assertEquals(beforeScroll, position.at(0f))
        assertEquals(beforeScroll - Vec2(0f, 80f), position.at(80f))
        position.advance(0f, shown = true, reducedMotion = true)
        assertEquals(Vec2(0f, 22f), position.at(80f))
    }

    @Test
    fun originalRevealAndFadeReturn() {
        val position = GameCardPosition()
        position.destination(200f, 102f, 3, reveal = true)
        assertEquals(Vec2(200f, 126f), position.at(0f))
        repeat(4) { position.advance(.016f, shown = true, reducedMotion = false) }
        assertEquals(0f, position.opacity)
        position.advance(.016f, shown = true, reducedMotion = false)
        assertEquals(.016234f, position.opacity, .00001f)
        repeat(120) { position.advance(.016f, shown = true, reducedMotion = false) }
        assertEquals(1f, position.opacity)
        position.advance(.016f, shown = false, reducedMotion = false)
        assertTrue(position.opacity > 0f && position.opacity < 1f)
        repeat(120) { position.advance(.016f, shown = false, reducedMotion = false) }
        assertEquals(0f, position.opacity)
        position.destination(400f, 350f, 18, reveal = true)
        assertEquals(Vec2(400f, 374f), position.at(0f))
        repeat(26) { position.advance(.016f, shown = true, reducedMotion = false) }
        assertEquals(0f, position.opacity)
        position.advance(0f, shown = true, reducedMotion = true)
        assertEquals(Vec2(400f, 350f), position.at(0f))
        assertEquals(1f, position.opacity)
    }
}
