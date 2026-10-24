package sample

import pixzel.Ui
import pixzel.animation.Interpolators
import pixzel.animation.TweenSpec
import pixzel.animation.propertyTransition
import pixzel.component.AbstractElement
import pixzel.component.Button
import pixzel.component.CircleHitShape
import pixzel.component.ComponentSkin
import pixzel.component.PaintContext
import pixzel.component.Painter
import pixzel.component.Role
import pixzel.component.Semantics
import pixzel.dsl.ContentScope
import pixzel.dsl.box
import pixzel.dsl.image
import pixzel.graphics.Canvas
import pixzel.graphics.Clip
import pixzel.graphics.Color
import pixzel.graphics.ColorStop
import pixzel.graphics.Ellipse
import pixzel.graphics.ImageHandle
import pixzel.graphics.LinearGradient
import pixzel.graphics.RadialGradient
import pixzel.graphics.Rectangle
import pixzel.graphics.RoundedRectangle
import pixzel.graphics.Solid
import pixzel.graphics.nineSlice
import pixzel.graphics.rgba
import pixzel.graphics.saved
import pixzel.layout.unit.dp
import pixzel.resource.Subscription
import pixzel.state.State
import pixzel.state.bind
import pixzel.surface.Insets
import pixzel.surface.Rect
import pixzel.surface.Vec2
import pixzel.text.TextOverflow
import pixzel.text.TextOverflowContext
import pixzel.text.TextOverflowPolicy
import pixzel.text.TextRevealBehavior
import pixzel.text.TextRevealContext
import pixzel.text.TextRevealFrame
import pixzel.text.TextRevealRuntime
import pixzel.ui
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * External component: this module has no access to pixzel-common internals.
 */
class HealthBar private constructor(value: State<Float>) : AbstractElement() {

    init {
        require(value.value.isFinite()) { "Health value must be finite" }
    }

    private val animated = propertyTransition(value.value.coerceIn(0f, 1f), Interpolators.FLOAT) { invalidate() }

    init {
        layout.width = 240.dp
        layout.height = 28.dp
        semantics = Semantics(Role.PROGRESS, label = "Health", value = value.value.toString())
        attachments.bind {
            updateValue(value.value)
            val observation = value.observe(::updateValue)
            Subscription {
                observation.close()
                animated.cancel()
            }
        }
        background = Painter { c, ctx -> c.fill(RoundedRectangle(7f), ctx.bounds, Solid(0x172529FF.rgba)) }
    }

    private fun updateValue(next: Float) {
        require(next.isFinite()) { "Health value must be finite" }
        val target = next.coerceIn(0f, 1f)
        if (target != animated.value || animated.isRunning) animated.transitionTo(target, TweenSpec(200.milliseconds))
        semantics = semantics.copy(value = next.toString())
    }

    override fun paint(canvas: Canvas) {
        val box = surface.contentBox
        canvas.saved {
            emit(Clip(RoundedRectangle(7f), box))
            fill(
                Rectangle,
                Rect(box.x, box.y, box.width * animated.value, box.height),
                LinearGradient(Vec2(box.x, box.y), Vec2(box.right, box.y), listOf(ColorStop(0f, 0xD33945FF.rgba), ColorStop(1f, 0xFF9A46FF.rgba)))
            )
        }
    }

    companion object {

        fun of(value: State<Float>) = HealthBar(value)
    }
}

fun ContentScope.healthBar(value: State<Float>, configure: HealthBar.() -> Unit = {}): HealthBar = insert(HealthBar.of(value), configure)

class RadialButtonSkin : ComponentSkin<Button> {

    override fun paint(canvas: Canvas, component: Button, context: PaintContext) {
        val color = if (context.state.pressed) 0xCA4E57FF.rgba else 0x7C4DFFFF.rgba
        canvas.fill(
            Ellipse,
            context.bounds,
            RadialGradient(
                Vec2(context.bounds.width / 2, context.bounds.height / 2),
                context.bounds.width / 2,
                listOf(ColorStop(0f, 0xD6BBFFFF.rgba), ColorStop(1f, color))
            )
        )
        if (context.state.focusVisible) canvas.stroke(Ellipse, context.bounds, Solid(0xFFFFFFFF.rgba), 2f)
    }
}

class GameButtonSkin(private val image: ImageHandle, private val borders: Insets) : ComponentSkin<Button> {

    override fun paint(canvas: Canvas, component: Button, context: PaintContext) {
        canvas.nineSlice(image, context.bounds, borders, tint = if (context.state.pressed) 0xBBBBBBFF.rgba else Color.WHITE)
    }
}

fun Button.useSkin(skin: ComponentSkin<Button>) {
    background = Painter { canvas, context -> skin.paint(canvas, this, context) }
}

fun radialButton(ui: Ui, action: () -> Unit): Button = Button.of(ui.components).apply {
    layout.width = 100.dp
    layout.height = 100.dp
    hitShape = CircleHitShape
    useSkin(RadialButtonSkin())
    setLabel("GO")
    onClick(action)
}

/**
 * An external static algorithm can compose existing public strategies.
 */
class MiddleDotsOverflow : TextOverflowPolicy {

    override fun resolve(context: TextOverflowContext) = TextOverflow.ELLIPSIS_MIDDLE.resolve(context.copy(marker = "..."))
}

/**
 * A deliberately stepped reveal demonstrates replacement without a Button patch.
 */
class SteppedReveal : TextRevealBehavior {

    override fun createRuntime() = object : TextRevealRuntime {
        private var elapsed = Duration.ZERO
        override fun reset() {
            elapsed = Duration.ZERO
        }

        override fun update(context: TextRevealContext): TextRevealFrame {
            if (!context.hovered || context.reducedMotion) {
                reset()
                return TextRevealFrame(context.static.layout)
            }
            elapsed += context.delta
            val distance = (context.full.size.width - context.viewport.width).coerceAtLeast(0f)
            val phase = ((elapsed.inWholeMilliseconds / 300) % 3).toInt()
            return TextRevealFrame(context.full, -distance * phase / 2, needsUpdate = distance > 0)
        }
    }
}
