package pixzel.component

import pixzel.graphics.Color
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
import pixzel.layout.ChildPlacement
import pixzel.layout.Constraints
import pixzel.layout.LayoutPlan
import pixzel.layout.LayoutPolicy
import pixzel.layout.Measurable
import pixzel.layout.unit.dp
import pixzel.layout.unit.fillAvailable
import pixzel.resource.Subscription
import pixzel.style.Theme
import pixzel.surface.Insets
import pixzel.surface.Size
import pixzel.surface.Vec2
import pixzel.text.TextAlignment
import pixzel.text.TextOverflow
import pixzel.text.VerticalAlignment
import pixzel.text.hoverReveal

internal open class SimpleButton(id: String, private val factory: ComponentFactory) : AbstractElement(id), Button {

    private val clicks = mutableListOf<() -> Unit>()
    private var before: Element? = null
    private var after: Element? = null
    override val leading get() = before?.takeIf { it.parent === this }
    override val trailing get() = after?.takeIf { it.parent === this }
    override val content get() = children.firstOrNull { it !== leading && it !== trailing }

    init {
        layout.width = 160.dp
        layout.height = 44.dp
        layout.padding = Insets.horizontal(12.dp)
        layoutPolicy = LayoutPolicy { children, c, _ ->
            val left = children.firstOrNull { it === leading }
            val right = children.firstOrNull { it === trailing }
            val middle = children.filter { it !== left && it !== right }
            val gap = layout.spacing.value.also { require(it >= 0f) { "Button slot spacing must be nonnegative" } }
            fun measure(child: Measurable, maxWidth: Float) = child.measure(
                Constraints(
                    maxWidth = (maxWidth - child.layout.margin.horizontal).coerceAtLeast(0f),
                    maxHeight = (c.maxHeight - child.layout.margin.vertical).coerceAtLeast(0f)
                )
            )

            val lp = left?.let { measure(it, c.maxWidth) }
            val lw = lp?.let { it.size.width + left!!.layout.margin.horizontal } ?: 0f
            val rp = right?.let { measure(it, (c.maxWidth - lw).coerceAtLeast(0f)) }
            val rw = rp?.let { it.size.width + right!!.layout.margin.horizontal } ?: 0f
            val gaps =
                gap * ((if (left != null && middle.isNotEmpty()) 1 else 0) +
                        (if (right != null && middle.isNotEmpty()) 1 else 0) +
                        (if (left != null && right != null && middle.isEmpty()) 1 else 0))
            val remaining = (c.maxWidth - lw - rw - gaps).coerceAtLeast(0f)
            val main = middle.map { it to measure(it, remaining) }
            val width = lw + rw + gaps + (main.maxOfOrNull { it.second.size.width + it.first.layout.margin.horizontal } ?: 0f)
            val height = maxOf(
                lp?.let { it.size.height + left!!.layout.margin.vertical } ?: 0f,
                rp?.let { it.size.height + right!!.layout.margin.vertical } ?: 0f,
                main.maxOfOrNull { it.second.size.height + it.first.layout.margin.vertical } ?: 0f
            )
            val size = c.constrain(Size(width, height))
            val mainX = lw + if (left != null && middle.isNotEmpty()) gap else 0f
            val placed = mutableListOf<ChildPlacement>()
            if (lp != null) {
                val m = left!!.layout.margin
                placed += ChildPlacement(lp, Vec2(m.left, m.top + ((size.height - m.vertical - lp.size.height) / 2).coerceAtLeast(0f)))
            }
            main.forEach { (child, p) ->
                val m = child.layout.margin
                val a = child.placement
                val w = (size.width - lw - rw - gaps - m.horizontal).coerceAtLeast(0f)
                val h = (size.height - m.vertical).coerceAtLeast(0f)
                placed += ChildPlacement(
                    p,
                    Vec2(
                        mainX + m.left + a.anchor.x * w - a.pivot.x * p.size.width + a.offset.x,
                        m.top + a.anchor.y * h - a.pivot.y * p.size.height + a.offset.y
                    )
                )
            }
            if (rp != null) {
                val m = right!!.layout.margin
                placed += ChildPlacement(
                    rp,
                    Vec2(size.width - rw + m.left, m.top + ((size.height - m.vertical - rp.size.height) / 2).coerceAtLeast(0f))
                )
            }
            LayoutPlan(size, placed)
        }
        pointerPolicy = PointerPolicy.AUTO
        focusable = true
        semantics = Semantics(Role.BUTTON, actions = mapOf("activate" to { activate() }))
        background = Painter { canvas, c ->
            val theme = ui?.theme ?: Theme.DARK
            val skin = theme.buttonSkin
            if (skin != null) skin.paint(canvas, this, c) else {
                val base = if (c.state.disabled) c.tokens.palette.disabled else c.tokens.palette.primary
                val factor = when {
                    c.state.pressed -> .75f
                    c.state.hovered -> 1.15f
                    else -> 1f
                }
                val color = Color(
                    ((base.red * factor).toInt().coerceIn(0, 255).toLong() shl 24) or ((base.green * factor).toInt().coerceIn(0, 255)
                        .toLong() shl 16) or ((base.blue * factor).toInt().coerceIn(0, 255).toLong() shl 8) or base.alpha.toLong()
                )
                canvas.fill(RoundedRectangle(c.tokens.cornerRadius.value), c.bounds, Solid(color))
            }
        }
        foreground = Painter { canvas, c -> if (c.state.focusVisible) canvas.stroke(RoundedRectangle(6f), c.bounds, Solid(0xFFDD66FF.rgba), 2f) }
        behavior = InputBehavior { _, event ->
            when (val input = event.event) {
                is PointerEvent -> when (input.action) {
                    PointerAction.DOWN -> if (input.button == 0) {
                        setInteractionState(state.copy(pressed = true))
                        ui?.focus?.request(this, false)
                        event.capturePointer()
                        event.consume()
                    }

                    PointerAction.UP -> if (input.button == 0 && state.pressed) {
                        setInteractionState(state.copy(pressed = false))
                        event.consume()
                        val p = surface.fromRoot(input.position)
                        val hit = ui?.hitTest(input.position)
                        if (p != null && hitShape.contains(
                                p,
                                surface.bounds.size
                            ) && generateSequence(hit) { it.parent }.any { it === this }
                        ) activate()
                    }

                    PointerAction.CANCEL -> {
                        setInteractionState(state.copy(pressed = false))
                        event.consume()
                    }

                    else -> {}
                }

                is KeyEvent -> when (input.key) {
                    Key.ENTER -> if (input.action == KeyAction.DOWN) {
                        activate()
                        event.consume()
                    }

                    Key.SPACE -> {
                        if (input.action == KeyAction.DOWN) setInteractionState(state.copy(pressed = true))
                        if (input.action == KeyAction.UP && state.pressed) {
                            setInteractionState(state.copy(pressed = false))
                            activate()
                        }
                        event.consume()
                    }

                    else -> {}
                }
            }
        }
    }

    private fun replaceSide(previous: Element?, next: Element?, assign: (Element?) -> Unit) {
        if (previous === next) return
        require(next == null || next.parent == null && next.ui == null && !next.isDisposed) { "Button slot content must be live and detached" }
        if (previous?.parent === this) remove(previous)
        assign(null)
        if (next != null) {
            add(next)
            assign(next)
        }
    }

    override fun setLeading(content: Element?) = replaceSide(leading, content) { before = it }
    override fun setTrailing(content: Element?) = replaceSide(trailing, content) { after = it }
    private fun clearContent() {
        var failure: Throwable? = null
        children.filter { it !== leading && it !== trailing }.forEach {
            try {
                remove(it)
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    override fun setLabel(value: String): Text {
        clearContent()
        return factory.text("label").also {
            it.value = value
            it.layout.width = fillAvailable
            it.layout.height = fillAvailable
            it.maxLines = 1
            it.horizontalAlignment = TextAlignment.CENTER
            it.verticalAlignment = VerticalAlignment.CENTER
            if (it is SimpleText) it.useButtonTextDefaults() else {
                it.overflow = TextOverflow.ELLIPSIS
                it.reveal = hoverReveal()
            }
            add(it)
            semantics = semantics.copy(label = value)
        }
    }

    override fun setContent(content: Element) {
        if (this.content === content && children.count { it !== leading && it !== trailing } == 1) return
        require(content.parent == null && content.ui == null && !content.isDisposed) { "Button content must be live and detached" }
        clearContent()
        add(content)
    }

    override fun onClick(handler: () -> Unit): Subscription {
        invalidate(Invalidation.INPUT)
        clicks += handler
        var closed = false
        return Subscription {
            if (!closed) {
                closed = true
                clicks.remove(handler)
            }
        }
    }

    override fun replaceOnClick(handler: () -> Unit): Subscription {
        invalidate(Invalidation.INPUT)
        clicks.clear()
        return onClick(handler)
    }

    override fun activate() {
        val owner = ui ?: return
        if (
            generateSequence(this as Element?) { it.parent }.all {
                it.enabled && it.visibility == Visibility.VISIBLE && !it.isDisposed && it.ui === owner
            }
        ) clicks.toList()
            .forEach {
                if (
                    generateSequence(this as Element?) { it.parent }.all {
                        it.enabled && it.visibility == Visibility.VISIBLE && !it.isDisposed && it.ui === owner
                    }
                ) it()
            }
    }
}
