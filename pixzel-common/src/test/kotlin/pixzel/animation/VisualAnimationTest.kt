package pixzel.animation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import pixzel.UiSettings
import pixzel.component.AbstractElement
import pixzel.component.Painter
import pixzel.graphics.RecordingBackend
import pixzel.graphics.Rectangle
import pixzel.graphics.Solid
import pixzel.graphics.rgba
import pixzel.layout.unit.dp
import pixzel.platform.TestHost
import pixzel.surface.Transform
import pixzel.text.FixedFontTextService
import pixzel.ui
import kotlin.math.PI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class VisualAnimationTest {

    private fun near(expected: Float, actual: Float) = assertEquals(expected, actual, .0001f)

    @Test
    fun `color interpolates alpha without hidden endpoint tint and retarget starts at current color`() {
        val red = 0xFF0000FF.rgba
        val blue = 0x0000FFFF.rgba
        val animation = animateColor(red, blue, 1.seconds)
        assertEquals(0x800080FF.rgba, animation.advance(500.milliseconds))
        assertEquals(blue, animation.advance(500.milliseconds))
        assertEquals(0x0000FF80.rgba, Interpolators.COLOR.interpolate(0xFF000000.rgba, blue, .5f))
        val controller = animationController(red, Interpolators.COLOR)
        controller.transitionTo(blue, tween(1.seconds))
        controller.advance(500.milliseconds)
        controller.transitionTo(0x00FF00FF.rgba, tween(1.seconds))
        assertEquals(0x408040FF.rgba, controller.advance(500.milliseconds))
        assertEquals(blue, animateColor(red, blue, Duration.ZERO).value)
        controller.close()
    }

    @Test
    fun `transform interpolation takes shortest rotation and preserves scale shear and translation`() {
        fun radians(degrees: Double) = (degrees * PI / 180).toFloat()
        val from = Transform.rotation(radians(170.0)) * Transform.scale(1f, 2f)
        val to = Transform.translation(20f, 10f) * Transform.rotation(radians(-170.0)) * Transform.scale(3f, 4f)
        val middle = animateTransform(from, to, 1.seconds).advance(500.milliseconds)
        near(-2f, middle.a)
        near(0f, middle.b)
        near(0f, middle.c)
        near(-3f, middle.d)
        near(10f, middle.tx)
        near(5f, middle.ty)
        assertNotNull(middle.inverse())
        val halfTurn = Transform.rotation(PI.toFloat())
        val rotation = Interpolators.TRANSFORM.interpolate(Transform.IDENTITY, halfTurn, .5f)
        near(1f, rotation.a * rotation.d - rotation.b * rotation.c)
        assertNull(Interpolators.AFFINE_TRANSFORM.interpolate(Transform.IDENTITY, halfTurn, .5f).inverse())
        val shear = Interpolators.TRANSFORM.interpolate(Transform.IDENTITY, Transform(2f, 0f, 1f, 3f, 20f, 10f), .5f)
        assertEquals(Transform(1.5f, 0f, .5f, 2f, 10f, 5f), shear)
        assertEquals(to, Interpolators.TRANSFORM.interpolate(from, to, 1f))
        val singular = Transform(0f, 0f, -2f, 3f, 2f, 4f)
        val restored = Interpolators.TRANSFORM.interpolate(singular, singular, .5f)
        near(singular.c, restored.c)
        near(singular.d, restored.d)
    }

    @Test
    fun `owned visual controllers follow Ui clock pause while detached and finish for reduced motion`() {
        val host = TestHost.of(400, 240)
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        try {
            val element = ui.components.surface().also {
                it.layout.width = 80.dp
                it.layout.height = 40.dp
            }
            ui.root.add(element)
            ui.frame(Duration.ZERO)
            val opacity = element.opacityTransition()
            val transform = element.transformTransition()
            val color =
                element.colorTransition(0xFF0000FF.rgba) { next -> background = Painter { c, ctx -> c.fill(Rectangle, ctx.bounds, Solid(next)) } }
            val measures = (element as AbstractElement).measureCount
            opacity.transitionTo(0f, tween(1.seconds))
            transform.transitionTo(Transform.translation(80f, 0f), tween(1.seconds))
            color.transitionTo(0x0000FFFF.rgba, tween(1.seconds))
            ui.frame(500.milliseconds)
            near(.5f, element.opacity)
            near(40f, element.paintTransform.tx)
            assertEquals(0x800080FF.rgba, color.value)
            assertEquals(measures, element.measureCount)
            ui.root.remove(element, dispose = false)
            ui.frame(500.milliseconds)
            near(.5f, opacity.value)
            ui.root.add(element)
            ui.frame(250.milliseconds)
            near(.25f, element.opacity)
            color.transitionTo(0x00FF00FF.rgba, tween(1.seconds), RetargetPolicy.QUEUE)
            ui.settings = UiSettings(isReducedMotion = true)
            ui.frame(Duration.ZERO)
            assertEquals(0x00FF00FF.rgba, color.value)
            assertEquals(0f, element.opacity)
            assertEquals(80f, element.paintTransform.tx)
            assertFalse(opacity.isRunning)
            assertFalse(color.isRunning)
            opacity.transitionTo(.7f, tween(1.seconds))
            near(.7f, element.opacity)
            assertThrows(IllegalArgumentException::class.java) { opacity.transitionTo(2f, tween(Duration.ZERO)) }
            element.close()
            assertThrows(IllegalStateException::class.java) { transform.transitionTo(Transform.IDENTITY, tween(1.seconds)) }
        } finally {
            ui.close()
            host.close()
        }
    }

    @Test
    fun `opacity bounds custom easing while keyframed colors remain typed`() {
        val opacity = animateOpacity(0f, 1f, 1.seconds, Easing { 3f })
        assertEquals(1f, opacity.advance(500.milliseconds))
        assertThrows(IllegalArgumentException::class.java) { animateOpacity(-1f, 1f, Duration.ZERO) }
        val frames = keyframes(1.seconds, listOf(Keyframe(500.milliseconds, 0x00FF00FF.rgba)))
        val color = startAnimation(0xFF0000FF.rgba, 0x0000FFFF.rgba, frames, Interpolators.COLOR)
        assertEquals(0x808000FF.rgba, color.advance(250.milliseconds))
        assertEquals(0x00FF00FF.rgba, color.advance(250.milliseconds))
    }
}
