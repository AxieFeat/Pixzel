package pixzel.component

import pixzel.Ui
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
import pixzel.input.WheelEvent
import pixzel.input.WheelUnit
import pixzel.layout.unit.dp
import pixzel.surface.Rect
import pixzel.surface.Vec2
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration

internal class SimpleScrollBar(id: String) : AbstractElement(id), ScrollBar {
    private data class Metrics(
        val track: Rect, val length: Float, val thumbLength: Float, val position: Float,
        val offset: Float, val viewport: Float, val range: Float
    )

    private var pointer: Int? = null
    private var grab = 0f
    private var dragging = false
    private var previous: Metrics? = null
    override var view: ScrollView? = null
        set(value) {
            if (field !== value) {
                invalidate(Invalidation.INPUT)
                field = value
                stopDrag()
            }
        }
    override var orientation: ScrollBarOrientation = ScrollBarOrientation.VERTICAL
        set(value) {
            if (field == value) return
            invalidate(Invalidation.MEASURE)
            val defaultSize = if (field == ScrollBarOrientation.VERTICAL) layout.width == 14.dp && layout.height == 160.dp
            else layout.width == 160.dp && layout.height == 14.dp
            field = value
            stopDrag()
            if (defaultSize) {
                layout.width = (if (value == ScrollBarOrientation.VERTICAL) 14 else 160).dp
                layout.height = (if (value == ScrollBarOrientation.VERTICAL) 160 else 14).dp
            }
        }
    override var minThumbSize: Float = 18f
        set(value) {
            require(value.isFinite() && value >= 0)
            if (field != value) {
                invalidate()
                field = value
            }
        }
    override var lineStep: Float = 32f
        set(value) {
            require(value.isFinite() && value >= 0)
            if (field != value) {
                invalidate(Invalidation.INPUT)
                field = value
            }
        }

    private fun validView(): ScrollView? = view?.takeIf { candidate ->
        !candidate.isDisposed && candidate.ui != null && candidate.ui === ui && candidate.visibility == Visibility.VISIBLE &&
                (candidate.axes == ScrollAxes.BOTH || candidate.axes == ScrollAxes.VERTICAL && orientation == ScrollBarOrientation.VERTICAL ||
                        candidate.axes == ScrollAxes.HORIZONTAL && orientation == ScrollBarOrientation.HORIZONTAL)
    }

    private fun metrics(): Metrics {
        val track = surface.contentBox
        val target = validView()
        val horizontal = orientation == ScrollBarOrientation.HORIZONTAL
        val length = if (horizontal) track.width else track.height
        val viewport = target?.surface?.contentBox?.let { if (horizontal) it.width else it.height } ?: 0f
        val extent = target?.extent?.let { if (horizontal) it.width else it.height } ?: 0f
        val range = if (viewport > 0f) max(0f, extent - viewport) else 0f
        val thumb = if (range == 0f || extent == 0f) length else min(length, max(minThumbSize, length * (viewport / extent)))
        val offset = target?.scrollOffset?.let { if (horizontal) it.x else it.y }?.coerceIn(0f, range) ?: 0f
        val position = if (range == 0f) 0f else offset / range * (length - thumb)
        return Metrics(track, length, thumb, position, offset, viewport, range)
    }

    override val canScroll: Boolean get() = enabled && metrics().range > 0f
    override val thumbBounds: Rect
        get() = metrics().let { m ->
            if (orientation == ScrollBarOrientation.HORIZONTAL) Rect(
                m.track.x + m.position, m.track.y + 2f,
                m.thumbLength, (m.track.height - 4f).coerceAtLeast(0f)
            )
            else Rect(m.track.x + 2f, m.track.y + m.position, (m.track.width - 4f).coerceAtLeast(0f), m.thumbLength)
        }

    init {
        layout.width = 14.dp
        layout.height = 160.dp
        pointerPolicy = PointerPolicy.AUTO
        focusable = true
        semantics = Semantics(
            role = Role.SLIDER, label = "Scroll", value = "0.0", actions = mapOf(
                "increment" to { if (canScroll) setOffset(metrics().offset + lineStep) },
                "decrement" to { if (canScroll) setOffset(metrics().offset - lineStep) })
        )
        behavior = InputBehavior { _, event ->
            val m = metrics()
            if (!enabled || m.range <= 0f) return@InputBehavior
            when (val input = event.event) {
                is PointerEvent -> when (input.action) {
                    PointerAction.DOWN -> if (input.button == 0 && pointer == null) {
                        val coordinate = coordinate(event.localPosition ?: return@InputBehavior, m)
                        pointer = input.pointerId
                        dragging = coordinate >= m.position && coordinate < m.position + m.thumbLength
                        grab = coordinate - m.position
                        if (!dragging) setOffset(m.offset + if (coordinate < m.position) -m.viewport else m.viewport)
                        setInteractionState(state.copy(pressed = true, dragging = dragging))
                        ui?.focus?.request(this, false)
                        event.capturePointer()
                        event.consume()
                    }

                    PointerAction.MOVE -> if (pointer == input.pointerId && dragging) {
                        val travel = m.length - m.thumbLength
                        val position = coordinate(event.localPosition ?: return@InputBehavior, m) - grab
                        setOffset(if (travel <= 0f) 0f else position.coerceIn(0f, travel) / travel * m.range)
                        event.consume()
                    }

                    PointerAction.UP -> if (pointer == input.pointerId && input.button == 0) {
                        stopDrag()
                        event.consume()
                    }

                    PointerAction.CANCEL -> if (pointer == input.pointerId) {
                        stopDrag()
                        event.consume()
                    }

                    else -> Unit
                }

                is KeyEvent -> if (input.key in setOf(Key.LEFT, Key.RIGHT, Key.UP, Key.DOWN, Key.HOME, Key.END)) {
                    if (input.action != KeyAction.UP) when (input.key) {
                        Key.LEFT, Key.UP -> setOffset(m.offset - lineStep)
                        Key.RIGHT, Key.DOWN -> setOffset(m.offset + lineStep)
                        Key.HOME -> setOffset(0f)
                        Key.END -> setOffset(m.range)
                        else -> Unit
                    }
                    event.consume()
                }

                is WheelEvent -> {
                    val remaining = event.scrollRemaining ?: input.delta
                    val delta = if (orientation == ScrollBarOrientation.VERTICAL) remaining.y else remaining.x
                    val before = m.offset
                    val scale = when (input.unit) {
                        WheelUnit.LINE -> lineStep
                        WheelUnit.DP -> 1f
                        WheelUnit.PAGE -> m.viewport
                    }
                    setOffset(before - delta * scale)
                    val used = if (scale == 0f) 0f else -(metrics().offset - before) / scale
                    event.consumeScroll(if (orientation == ScrollBarOrientation.VERTICAL) Vec2(0f, used) else Vec2(used, 0f))
                }
            }
        }
        background = Painter { canvas, context ->
            canvas.fill(RoundedRectangle(4f), context.bounds, Solid(0x222A35FF.rgba))
            canvas.fill(
                RoundedRectangle(4f), thumbBounds, Solid(
                    if (!canScroll) 0x485160FF.rgba else
                        if (state.pressed) 0x98ADD8FF.rgba else 0x7187ADFF.rgba
                )
            )
            if (state.focusVisible) canvas.stroke(RoundedRectangle(4f), context.bounds, Solid(0xFFDD66FF.rgba), 1f)
        }
    }

    private fun coordinate(point: Vec2, m: Metrics) = if (orientation == ScrollBarOrientation.HORIZONTAL) point.x - m.track.x else point.y - m.track.y
    private fun setOffset(value: Float) {
        val target = validView() ?: return
        val clamped = value.coerceIn(0f, metrics().range)
        target.scrollOffset = if (orientation == ScrollBarOrientation.HORIZONTAL) Vec2(clamped, target.scrollOffset.y)
        else Vec2(target.scrollOffset.x, clamped)
        invalidate(Invalidation.SEMANTICS)
    }

    private fun stopDrag() {
        val captured = pointer != null
        pointer = null
        dragging = false
        try {
            if (captured) ui?.releasePointerCapture(this)
        } finally {
            setInteractionState(state.copy(pressed = false, dragging = false))
        }
    }

    override fun update(delta: Duration): Boolean {
        val next = metrics()
        if (next.range <= 0f && pointer != null) stopDrag()
        if (next == previous) return false
        previous = next
        semantics = semantics.copy(value = next.offset.toString())
        return true
    }

    override fun attach(parent: Element?, ui: Ui?) {
        if (ui == null) stopDrag()
        super.attach(parent, ui)
    }
}
