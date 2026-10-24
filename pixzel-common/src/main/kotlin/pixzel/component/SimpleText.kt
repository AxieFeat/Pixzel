package pixzel.component

import pixzel.Ui
import pixzel.graphics.Canvas
import pixzel.graphics.Clip
import pixzel.graphics.Color
import pixzel.graphics.Rectangle
import pixzel.graphics.saved
import pixzel.layout.Constraints
import pixzel.layout.unit.Dp
import pixzel.resource.Subscription
import pixzel.state.Binding
import pixzel.surface.Size
import pixzel.surface.Vec2
import pixzel.text.TextAlignment
import pixzel.text.TextDirection
import pixzel.text.TextLayout
import pixzel.text.TextOverflow
import pixzel.text.TextOverflowContext
import pixzel.text.TextOverflowPolicy
import pixzel.text.TextOverflowResult
import pixzel.text.TextRequest
import pixzel.text.TextReveal
import pixzel.text.TextRevealBehavior
import pixzel.text.TextRevealContext
import pixzel.text.TextRevealFrame
import pixzel.text.TextService
import pixzel.text.TextSpans
import pixzel.text.TextStyle
import pixzel.text.VerticalAlignment
import pixzel.text.hoverReveal
import kotlin.time.Duration

internal open class SimpleText(id: String) : AbstractElement(id), Text {

    private var textRevision = 0L
    private var binding: Binding<String>? = null
    private var observation: Subscription? = null
    private var source = ""
    override var value: String
        get() = source
        set(value) {
            invalidate()
            observation?.let { resources.release(it) }
            observation = null
            binding?.let { resources.release(it) }
            binding = null
            assign(value)
        }

    protected fun assign(value: String) {
        if (source != value) {
            invalidate(Invalidation.MEASURE)
            val previous = source
            source = value
            semantics = semantics.copy(label = value)
            if (buttonLabel) (parent as? Button)?.takeIf { it.content === this }?.let { it.semantics = it.semantics.copy(label = value) }
            onValueChanged(previous, value)
        }
    }

    protected open fun onValueChanged(previous: String, value: String) {}
    private var customStyle: TextStyle? = null
    override var style: TextStyle
        get() = customStyle ?: resolvedTokens.typography
        set(value) {
            if (customStyle != value) {
                invalidate(Invalidation.MEASURE)
                customStyle = value
            }
        }
    override var spans: TextSpans by property(TextSpans.EMPTY, Invalidation.MEASURE)
    override var direction: TextDirection by property(TextDirection.AUTO, Invalidation.MEASURE)
    override var locale: String by property("und", Invalidation.MEASURE)
    private var customColor: Color? = null
    override var color: Color
        get() = customColor ?: resolvedTokens.palette.text
        set(value) {
            if (customColor != value) {
                invalidate()
                customColor = value
            }
        }
    private var buttonLabel = false
    private var customOverflow: TextOverflowPolicy? = null
    override var overflow: TextOverflowPolicy
        get() = customOverflow ?: if (buttonLabel) resolvedTokens.buttonText.overflow ?: TextOverflow.ELLIPSIS else TextOverflow.CLIP
        set(value) {
            if (customOverflow !== value) {
                invalidate()
                customOverflow = value
            }
        }
    private var customReveal: TextRevealBehavior? = null
    override var reveal: TextRevealBehavior
        get() = customReveal ?: if (buttonLabel) resolvedTokens.buttonText.reveal ?: BUTTON_REVEAL else TextReveal.NONE
        set(value) {
            if (customReveal !== value) {
                invalidate()
                customReveal = value
            }
        }
    override var ellipsisMarker: String by property("…", Invalidation.MEASURE)
    override var wrap: Boolean by property(false, Invalidation.MEASURE)
    override var maxLines: Int by property(Int.MAX_VALUE, Invalidation.MEASURE)
    override var horizontalAlignment: TextAlignment by property(TextAlignment.START)
    override var verticalAlignment: VerticalAlignment by property(VerticalAlignment.TOP)
    private var runtimeBehavior: TextRevealBehavior = TextReveal.NONE
    private var runtime = runtimeBehavior.createRuntime()
    private var full: TextLayout? = null
    private var static: TextOverflowResult? = null
    private var frame: TextRevealFrame? = null
    private var preparedRevision = Long.MIN_VALUE
    private var preparedTextScale = 0f
    private var preparedService: TextService? = null
    private var preparedRequest: TextRequest? = null
    private var preparedOverflow: TextOverflowPolicy? = null
    private var lastActivation = Int.MIN_VALUE
    private var cachedViewport = Size.ZERO
    private var measuredKey: TextRequest? = null
    private var measured: TextLayout? = null

    init {
        semantics = Semantics(role = Role.TEXT)
    }

    internal fun useButtonTextDefaults() {
        buttonLabel = true
        invalidate()
    }

    override fun invalidate(reason: Invalidation) {
        super.invalidate(reason)
        if (reason == Invalidation.MEASURE) textRevision++
    }

    override fun bindValue(binding: Binding<String>) {
        invalidate()
        if (this.binding === binding) return
        observation?.let { resources.release(it) }
        this.binding?.let { resources.release(it) }
        this.binding = binding
        assign(binding.value)
        observation = attachments.bind {
            assign(binding.value)
            binding.observe { assign(it) }
        }
        resources.own(binding)
        resources.own(observation!!)
    }

    protected open fun displayValue() = value
    protected fun shaped(request: TextRequest): TextLayout {
        if (request != measuredKey) {
            measured = checkNotNull(ui) { "Text '$id' must be attached before measurement" }.text.layout(request)
            measuredKey = request
        }
        return measured!!
    }

    override fun intrinsic(constraints: Constraints): Size {
        val availableWidth = when (val width = layout.width) {
            is Dp -> (width.value - layout.padding.horizontal).coerceAtLeast(0f).coerceAtMost(constraints.maxWidth)
            else -> constraints.maxWidth
        }
        val request = TextRequest(
            displayValue(),
            style,
            width = if (wrap) availableWidth else Float.POSITIVE_INFINITY,
            wrap = wrap,
            maxLines = maxLines,
            textScale = ui?.settings?.textScale ?: 1f,
            spans = spans,
            direction = direction,
            locale = locale
        )
        return shaped(request).size
    }

    override fun intrinsicBaseline(contentSize: Size): Float? = measured?.let { layout ->
        layout.baseline + when (verticalAlignment) {
            VerticalAlignment.CENTER -> ((contentSize.height - layout.size.height) / 2).coerceAtLeast(0f)
            VerticalAlignment.BOTTOM -> (contentSize.height - layout.size.height).coerceAtLeast(0f)
            else -> 0f
        }
    }

    private fun prepare() {
        val owner = ui ?: return
        val viewport = surface.contentBox.size
        val changedLayout =
            full == null || preparedRevision != textRevision || cachedViewport != viewport ||
            preparedTextScale != owner.settings.textScale || preparedService !== owner.text
        if (changedLayout) {
            val request = TextRequest(
                displayValue(),
                style,
                width = if (wrap) viewport.width else Float.POSITIVE_INFINITY,
                wrap = wrap,
                maxLines = maxLines,
                textScale = owner.settings.textScale,
                spans = spans,
                direction = direction,
                locale = locale
            )
            preparedRequest = request
            full = shaped(request)
            static = null
            runtime.reset()
            cachedViewport = viewport
            preparedRevision = textRevision
            preparedTextScale = owner.settings.textScale
            preparedService = owner.text
        }
        val policy = overflow
        if (static == null || preparedOverflow !== policy) {
            static = policy.resolve(TextOverflowContext(owner.text, preparedRequest!!, full!!, viewport, ellipsisMarker))
            preparedOverflow = policy
            frame = null
            runtime.reset()
            lastActivation = Int.MIN_VALUE
        }
        val behavior = reveal
        if (runtimeBehavior !== behavior) {
            runtimeBehavior = behavior
            runtime = behavior.createRuntime()
            frame = null
            lastActivation = Int.MIN_VALUE
        }
    }

    override fun update(delta: Duration): Boolean {
        if (ui == null || visibility != Visibility.VISIBLE) return false
        prepare()
        var hovered = false
        var focused = false
        var enabled = true
        var current: Element? = this
        while (current != null) {
            hovered = hovered || current.state.hovered
            focused = focused || current.state.focused
            enabled = enabled && current.enabled
            current = current.parent
        }
        val reduced = ui!!.settings.isReducedMotion
        val activation = (if (hovered) 1 else 0) or (if (focused) 2 else 0) or (if (enabled) 4 else 0) or (if (reduced) 8 else 0)
        if (frame?.needsUpdate == false && lastActivation == activation) return false
        lastActivation = activation
        val next = runtime.update(
            TextRevealContext(
                full!!,
                static!!,
                cachedViewport,
                delta,
                hovered = hovered,
                focused = focused,
                reducedMotion = reduced,
                enabled = enabled
            )
        )
        val changed = frame != next
        frame = next
        return changed
    }

    override fun paint(canvas: Canvas) {
        prepare()
        val draw = frame ?: TextRevealFrame(static!!.layout, clip = static!!.clip)
        val box = surface.contentBox
        val layout = draw.layout
        val x = when (horizontalAlignment) {
            TextAlignment.CENTER -> (box.width - layout.size.width) / 2
            TextAlignment.RIGHT -> box.width - layout.size.width
            TextAlignment.END -> if (layout.direction == TextDirection.RTL) 0f else box.width - layout.size.width
            TextAlignment.START -> if (layout.direction == TextDirection.RTL) box.width - layout.size.width else 0f
            else -> 0f
        }
        val y = when (verticalAlignment) {
            VerticalAlignment.CENTER -> (box.height - layout.size.height) / 2
            VerticalAlignment.BOTTOM -> box.height - layout.size.height
            else -> 0f
        }
        canvas.saved {
            if (draw.clip) emit(Clip(Rectangle, box))
            text(layout, Vec2(box.x + x.coerceAtLeast(0f) + draw.offsetX, box.y + y.coerceAtLeast(0f)), color)
        }
    }

    override fun attach(parent: Element?, ui: Ui?) {
        super.attach(parent, ui)
        if (ui == null) runtime.reset()
    }

    companion object {

        private val BUTTON_REVEAL = hoverReveal()
    }
}
