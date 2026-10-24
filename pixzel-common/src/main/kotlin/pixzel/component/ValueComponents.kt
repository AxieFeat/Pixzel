package pixzel.component

import pixzel.Ui
import pixzel.graphics.Color
import pixzel.graphics.Ellipse
import pixzel.graphics.RoundedRectangle
import pixzel.graphics.Solid
import pixzel.graphics.rgba
import pixzel.input.InputBehavior
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.PointerPolicy
import pixzel.layout.unit.dp
import pixzel.resource.Subscription
import pixzel.surface.Rect
import pixzel.surface.Vec2
import kotlin.math.round
import kotlin.time.Duration
import kotlin.time.DurationUnit

internal abstract class SimpleRangedElement(id: String) : AbstractElement(id), RangedValue {

    private var lower = 0f
    private var upper = 1f
    private var interval = 0f
    private var current = 0f
    private val listeners = linkedMapOf<Any, (Float) -> Unit>()
    override var min: Float
        get() = lower
        set(value) = setRange(value, upper)
    override var max: Float
        get() = upper
        set(value) = setRange(lower, value)
    override var step: Float
        get() = interval
        set(value) {
            require(value.isFinite() && value >= 0f) { "Step must be finite and nonnegative" }
            if (interval == value) return
            invalidate(Invalidation.SEMANTICS)
            interval = value
            this.value = current
        }
    override var value: Float
        get() = current
        set(value) {
            require(value.isFinite()) { "Value must be finite" }
            val resolved = resolve(value.toDouble())
            if (current == resolved) return
            invalidate(Invalidation.SEMANTICS)
            current = resolved
            refreshSemantics()
            listeners.toList().forEach { (key, listener) -> if (!isDisposed && listeners[key] === listener) listener(resolved) }
        }
    protected val fraction: Float
        get() = if (upper == lower) 0f else ((current.toDouble() - lower) / (upper.toDouble() - lower)).toFloat().coerceIn(0f, 1f)
    protected val increment: Double get() = if (interval > 0f) interval.toDouble() else (upper.toDouble() - lower) / 100
    protected fun moveValue(delta: Double) {
        value = (current.toDouble() + delta).coerceIn(lower.toDouble(), upper.toDouble()).toFloat()
    }

    protected fun setFraction(fraction: Float) {
        value = (lower.toDouble() + (upper.toDouble() - lower) * fraction.coerceIn(0f, 1f)).toFloat()
    }

    private fun resolve(value: Double): Float {
        if (value <= lower) return lower
        if (value >= upper) return upper
        val snapped = if (interval == 0f) value else lower + round((value - lower) / interval) * interval
        return snapped.coerceIn(lower.toDouble(), upper.toDouble()).toFloat()
    }

    override fun setRange(min: Float, max: Float) {
        require(min.isFinite() && max.isFinite() && min <= max) { "Range must have finite bounds and min <= max" }
        if (lower == min && upper == max) return
        invalidate(Invalidation.SEMANTICS)
        lower = min
        upper = max
        val previous = current
        value = previous
        refreshSemantics()
    }

    protected open fun refreshSemantics() {
        semantics = semantics.copy(value = current.toString())
    }

    override fun onValueChange(handler: (Float) -> Unit): Subscription {
        invalidate(Invalidation.INPUT)
        val token = Any()
        listeners[token] = handler
        return Subscription { listeners.remove(token) }
    }

    override fun close() {
        if (isDisposed) return
        listeners.clear()
        super.close()
    }
}

internal class SimpleSlider(id: String) : SimpleRangedElement(id), Slider {

    private var draggingPointer: Int? = null

    init {
        layout.width = 200.dp
        layout.height = 32.dp
        pointerPolicy = PointerPolicy.AUTO
        focusable = true
        semantics = Semantics(
            role = Role.SLIDER, value = "0.0", actions = mapOf(
                "increment" to { if (enabled && !isDisposed) moveValue(increment) },
                "decrement" to { if (enabled && !isDisposed) moveValue(-increment) })
        )
        behavior = InputBehavior { _, event ->
            when (val input = event.event) {
                is PointerEvent -> when (input.action) {
                    PointerAction.DOWN -> if (input.button == 0 && draggingPointer == null) {
                        draggingPointer = input.pointerId
                        setInteractionState(state.copy(pressed = true, dragging = true))
                        ui?.focus?.request(this, false)
                        event.capturePointer()
                        pointerValue(event.localPosition)
                        event.consume()
                    }

                    PointerAction.MOVE -> if (draggingPointer == input.pointerId) {
                        pointerValue(event.localPosition)
                        event.consume()
                    }

                    PointerAction.UP -> if (draggingPointer == input.pointerId && input.button == 0) {
                        pointerValue(event.localPosition)
                        stopDrag()
                        event.consume()
                    }

                    PointerAction.CANCEL -> if (draggingPointer == input.pointerId) {
                        stopDrag()
                        event.consume()
                    }

                    else -> Unit
                }

                is KeyEvent -> if (input.key in setOf(Key.LEFT, Key.RIGHT, Key.UP, Key.DOWN, Key.HOME, Key.END)) {
                    if (input.action != KeyAction.UP) when (input.key) {
                        Key.LEFT, Key.DOWN -> moveValue(-increment)
                        Key.RIGHT, Key.UP -> moveValue(increment)
                        Key.HOME -> value = min
                        Key.END -> value = max
                        else -> Unit
                    }
                    event.consume()
                }
            }
        }
        background = Painter { canvas, context ->
            val track = Rect(8f, (context.bounds.height - 4f) / 2, (context.bounds.width - 16f).coerceAtLeast(0f), 4f)
            canvas.fill(RoundedRectangle(2f), track, Solid(0x3E4859FF.rgba))
            canvas.fill(RoundedRectangle(2f), Rect(track.x, track.y, track.width * fraction, track.height), Solid(0x658EF4FF.rgba))
            val center = track.x + track.width * fraction
            canvas.fill(
                Ellipse, Rect(center - 7f, context.bounds.height / 2 - 7f, 14f, 14f),
                Solid(if (!context.state.disabled) Color.WHITE else 0x777777FF.rgba)
            )
            if (state.focusVisible) canvas.stroke(RoundedRectangle(4f), context.bounds, Solid(0xFFDD66FF.rgba), 2f)
        }
    }

    private fun pointerValue(position: Vec2?) {
        if (position == null) return
        val width = (surface.bounds.width - 16f).coerceAtLeast(0f)
        setFraction(if (width == 0f) 0f else (position.x - 8f) / width)
    }

    private fun stopDrag() {
        draggingPointer = null
        setInteractionState(state.copy(pressed = false, dragging = false))
    }

    override fun attach(parent: Element?, ui: Ui?) {
        if (ui == null && draggingPointer != null) stopDrag()
        super.attach(parent, ui)
    }
}

internal class SimpleProgress(id: String) : SimpleRangedElement(id), Progress {

    override var indeterminate: Boolean = false
        set(value) {
            if (field != value) {
                invalidate()
                field = value
                phase = 0.0
                setInteractionState(state.copy(indeterminate = value))
                refreshSemantics()
            }
        }
    private var phase = 0.0

    init {
        layout.width = 200.dp
        layout.height = 12.dp
        semantics = Semantics(role = Role.PROGRESS, value = "0.0")
        background = Painter { canvas, context ->
            canvas.fill(RoundedRectangle(6f), context.bounds, Solid(0x303947FF.rgba))
            val reduced = ui?.settings?.isReducedMotion == true
            val width = if (indeterminate) context.bounds.width * .3f else context.bounds.width * fraction
            val left = if (indeterminate) {
                if (reduced) (context.bounds.width - width) / 2 else ((phase / 1.6).toFloat()) * (context.bounds.width - width)
            } else 0f
            canvas.fill(RoundedRectangle(6f), Rect(left, 0f, width, context.bounds.height), Solid(0x658EF4FF.rgba))
        }
    }

    override fun update(delta: Duration): Boolean {
        if (!indeterminate || !enabled || visibility != Visibility.VISIBLE || ui?.settings?.isReducedMotion == true) return false
        require(delta.isFinite() && !delta.isNegative())
        if (delta == Duration.ZERO) return false
        phase = (phase + delta.toDouble(DurationUnit.SECONDS)) % 1.6
        return true
    }

    override fun refreshSemantics() {
        semantics = semantics.copy(value = if (indeterminate) "indeterminate" else value.toString())
    }

    override fun attach(parent: Element?, ui: Ui?) {
        if (ui == null) phase = 0.0
        super.attach(parent, ui)
    }
}
