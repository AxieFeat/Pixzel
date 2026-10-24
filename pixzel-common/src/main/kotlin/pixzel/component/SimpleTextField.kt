package pixzel.component

import pixzel.graphics.Canvas
import pixzel.graphics.Clip
import pixzel.graphics.Concat
import pixzel.graphics.Rectangle
import pixzel.graphics.RoundedRectangle
import pixzel.graphics.Solid
import pixzel.graphics.rgba
import pixzel.graphics.saved
import pixzel.input.Composition
import pixzel.input.InputBehavior
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.PointerPolicy
import pixzel.input.TextCommit
import pixzel.layout.unit.dp
import pixzel.surface.Insets
import pixzel.surface.Rect
import pixzel.surface.Transform
import pixzel.surface.Vec2
import pixzel.text.TextRange
import pixzel.text.TextRequest
import pixzel.text.TextSpans
import pixzel.text.UnicodeGraphemeSegmenter
import pixzel.text.nextBoundary
import pixzel.text.previousBoundary
import kotlin.math.abs
import kotlin.time.Duration

internal class SimpleTextField(id: String) : SimpleText(id), TextField {
    private data class Edit(val text: String, val start: Int, val end: Int)

    private val undoStack = ArrayDeque<Edit>()
    private val redoStack = ArrayDeque<Edit>()
    private var start = 0
    private var end = 0
    override var selectionStart: Int
        get() = boundary(start.coerceIn(0, value.length))
        set(value) {
            start = boundary(value)
            invalidate()
        }
    override var selectionEnd: Int
        get() = boundary(end.coerceIn(0, value.length))
        set(value) {
            end = boundary(value)
            invalidate()
        }
    override var readOnly: Boolean by property(false, Invalidation.INPUT)

    /** Rich editing is separate from the single-style 1.0 field; never silently discard spans. */
    override var spans: TextSpans
        get() = TextSpans.EMPTY
        set(value) {
            require(value.isEmpty()) { "Single-line TextField does not support styled editing spans; use Text for styled display" }
        }
    private var secret = false
    override var password: Boolean
        get() = secret
        set(value) {
            if (secret != value) {
                invalidate(Invalidation.MEASURE)
                secret = value
                updateSemantics()
            }
        }
    override var placeholder: String by property("", Invalidation.MEASURE)
    override var validator: (String) -> Boolean by property({ _: String -> true }, Invalidation.INPUT)
    private var caretOffset = 0f
    override var composition: TextComposition? = null
        private set
    private var lastImeCaret: Rect? = null

    init {
        layout.width = 220.dp
        layout.height = 36.dp
        layout.padding = Insets.all(8.dp)
        focusable = true
        pointerPolicy = PointerPolicy.AUTO
        semantics = Semantics(Role.TEXT_FIELD)
        background = Painter { canvas, c -> canvas.fill(RoundedRectangle(4f), c.bounds, Solid(0x18212BFF.rgba)) }
        foreground = Painter { canvas, c -> if (c.state.focused) canvas.stroke(RoundedRectangle(4f), c.bounds, Solid(0x88AAFFFF.rgba), 1f) }
        behavior = InputBehavior { _, event ->
            when (val input = event.event) {
                is PointerEvent -> when (input.action) {
                    PointerAction.DOWN -> if (input.button == 0) {
                        composition = null
                        ui!!.focus.request(this)
                        val point = event.localPosition ?: Vec2.ZERO
                        val shaped = shaped(fieldRequest(displayValue()))
                        selectionEnd = sourceIndex(shaped.hitTest(point - Vec2(surface.contentBox.x - caretOffset, surface.contentBox.y)).index)
                        selectionStart = selectionEnd
                        event.capturePointer()
                        event.consume()
                    }

                    PointerAction.MOVE -> if (state.pressed) {
                        val point = event.localPosition ?: Vec2.ZERO
                        selectionEnd = sourceIndex(
                            shaped(fieldRequest(displayValue())).hitTest(
                                point - Vec2(
                                    surface.contentBox.x - caretOffset,
                                    surface.contentBox.y
                                )
                            ).index
                        )
                        event.consume()
                    }

                    else -> {}
                }

                is TextCommit -> {
                    if (!readOnly) {
                        composition?.let {
                            selectionStart = it.replacement.start
                            selectionEnd = it.replacement.end
                        }
                        composition = null
                        replaceSelection(input.text)
                    }
                    event.consume()
                }

                is KeyEvent -> {
                    if (input.action != KeyAction.UP) {
                        if (composition != null) {
                            if (input.key == Key.ESCAPE) {
                                composition = null
                                invalidate()
                            }
                        } else handleKey(input)
                        event.consume()
                    }
                }

                is Composition -> {
                    if (!readOnly) handleComposition(input)
                    event.consume()
                }
            }
        }
    }

    private fun boundary(index: Int): Int {
        require(index in 0..value.length)
        return UnicodeGraphemeSegmenter.segment(value).lastOrNull { it.start <= index }?.let { if (index >= it.end) it.end else it.start } ?: 0
    }

    private fun sourceIndex(displayIndex: Int): Int = if (!password) boundary(displayIndex.coerceIn(0, value.length)) else {
        if (displayIndex <= 0) 0 else UnicodeGraphemeSegmenter.segment(value).getOrNull(displayIndex - 1)?.end ?: value.length
    }

    private fun updateSemantics() {
        semantics = semantics.copy(label = if (password) "" else value, value = if (password) "" else value)
    }

    override fun onValueChanged(previous: String, value: String) {
        composition = null
        start = boundary(start.coerceIn(0, value.length))
        end = boundary(end.coerceIn(0, value.length))
        caretOffset = 0f
        updateSemantics()
    }

    private fun handleComposition(event: Composition) {
        if (event.cancelled) {
            composition = null
            invalidate()
            return
        }
        val range = if (event.replacementStart != null || event.replacementEnd != null) {
            val a = boundary(event.replacementStart ?: selectionStart)
            val b = boundary(event.replacementEnd ?: selectionEnd)
            TextRange(minOf(a, b), maxOf(a, b))
        } else composition?.replacement ?: TextRange(minOf(selectionStart, selectionEnd), maxOf(selectionStart, selectionEnd))
        if (event.committed) {
            composition = null
            selectionStart = range.start
            selectionEnd = range.end
            replaceSelection(event.preedit)
        } else {
            val ranges = UnicodeGraphemeSegmenter.segment(event.preedit)
            fun edge(i: Int) = ranges.lastOrNull { it.end <= i.coerceIn(0, event.preedit.length) }?.end ?: 0
            val a = edge(event.selectionStart)
            val b = edge(event.selectionEnd)
            composition = TextComposition(event.preedit, minOf(a, b), maxOf(a, b), range)
            invalidate()
        }
    }

    override fun setInteractionState(state: ElementState) {
        if (!state.focused && this.state.focused) {
            composition = null
            lastImeCaret = null
        }
        super.setInteractionState(state)
    }

    private fun snapshot() = Edit(value, selectionStart, selectionEnd)
    private fun restore(edit: Edit) {
        value = edit.text
        selectionStart = edit.start
        selectionEnd = edit.end
    }

    private fun replaceSelection(insert: String) {
        val a = minOf(selectionStart, selectionEnd)
        val b = maxOf(selectionStart, selectionEnd)
        val sanitized = insert.replace("\r", "").replace("\n", "")
        val next = value.substring(0, a) + sanitized + value.substring(b)
        if (!validator(next)) return
        undoStack += snapshot()
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        value = next
        selectionEnd = (a + sanitized.length).coerceAtMost(value.length)
        selectionStart = selectionEnd
        updateSemantics()
    }

    private fun move(position: Int, extend: Boolean) {
        selectionEnd = position
        if (!extend) selectionStart = selectionEnd
    }

    private fun wordBoundary(index: Int, forward: Boolean): Int {
        var p = index
        if (forward) {
            while (p < value.length && !value[p].isWhitespace()) p = UnicodeGraphemeSegmenter.nextBoundary(value, p)
            while (p < value.length && value[p].isWhitespace()) p = UnicodeGraphemeSegmenter.nextBoundary(value, p)
        } else {
            while (p > 0 && value[p - 1].isWhitespace()) p = UnicodeGraphemeSegmenter.previousBoundary(value, p)
            while (p > 0 && !value[p - 1].isWhitespace()) p = UnicodeGraphemeSegmenter.previousBoundary(value, p)
        }
        return p
    }

    private fun handleKey(event: KeyEvent) {
        val ctrl = event.modifiers.control || event.modifiers.meta
        when (event.key) {
            Key.A -> if (ctrl) {
                selectionStart = 0
                selectionEnd = value.length
            }

            Key.C, Key.X -> if (ctrl && !password) {
                val selected = value.substring(minOf(selectionStart, selectionEnd), maxOf(selectionStart, selectionEnd))
                val copied = ui!!.host.writeClipboard(selected)
                if (event.key == Key.X && copied.isSuccess && !readOnly) replaceSelection("")
            }

            Key.V -> if (ctrl && !readOnly) ui!!.host.readClipboard().onSuccess { replaceSelection(it) }
            Key.Z -> if (ctrl && !readOnly) {
                if (event.modifiers.shift) redo() else undo()
            }

            Key.Y -> if (ctrl && !readOnly) redo()
            Key.LEFT -> move(
                if (ctrl) wordBoundary(selectionEnd, false) else UnicodeGraphemeSegmenter.previousBoundary(value, selectionEnd),
                event.modifiers.shift
            )

            Key.RIGHT -> move(
                if (ctrl) wordBoundary(selectionEnd, true) else UnicodeGraphemeSegmenter.nextBoundary(value, selectionEnd),
                event.modifiers.shift
            )

            Key.HOME -> move(0, event.modifiers.shift)
            Key.END -> move(value.length, event.modifiers.shift)
            Key.BACKSPACE -> if (!readOnly) {
                if (selectionStart == selectionEnd) selectionStart =
                    if (ctrl) wordBoundary(selectionEnd, false) else UnicodeGraphemeSegmenter.previousBoundary(value, selectionEnd)
                replaceSelection("")
            }

            Key.DELETE -> if (!readOnly) {
                if (selectionStart == selectionEnd) selectionEnd =
                    if (ctrl) wordBoundary(selectionEnd, true) else UnicodeGraphemeSegmenter.nextBoundary(value, selectionEnd)
                replaceSelection("")
            }

            else -> {}
        }
    }

    override fun displayValue(): String {
        val composed = composition?.let { value.substring(0, it.replacement.start) + it.preedit + value.substring(it.replacement.end) } ?: value
        return if (composed.isEmpty()) placeholder else if (password) "•".repeat(UnicodeGraphemeSegmenter.segment(composed).size) else composed
    }

    private fun displayCaret(): Int {
        val index = composition?.let { it.replacement.start + it.selectionEnd } ?: selectionEnd
        return if (password) UnicodeGraphemeSegmenter.segment((composition?.let {
            value.substring(
                0,
                it.replacement.start
            ) + it.preedit + value.substring(it.replacement.end)
        } ?: value)).count { it.end <= index } else index
    }

    private fun fieldRequest(display: String) =
        TextRequest(display, style, direction = direction, locale = locale, textScale = ui?.settings?.textScale ?: 1f)

    private fun updateCaretOffset(x: Float) {
        val width = surface.contentBox.width
        caretOffset = caretOffset.coerceIn((x - width + 1).coerceAtLeast(0f), x.coerceAtLeast(0f))
    }

    override fun update(delta: Duration): Boolean {
        val changed = super.update(delta)
        val owner = ui ?: return changed
        if (state.focused && owner.host.capabilities.imeCaret) {
            val display = displayValue()
            val shaped = shaped(fieldRequest(display))
            val caret = shaped.caret(displayCaret().coerceAtMost(display.length))
            val box = surface.contentBox
            updateCaretOffset(caret.position.x)
            val root = surface.mapRect(Rect(box.x + caret.position.x - caretOffset, box.y, 1f, caret.height))
            val scale = owner.settings.uiScale
            val rect = Rect(root.x * scale, root.y * scale, root.width * scale, root.height * scale)
            if (lastImeCaret != rect) {
                owner.host.setImeCaret(rect)
                lastImeCaret = rect
            }
        }
        return changed
    }

    override fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack += snapshot()
            restore(undoStack.removeLast())
        }
    }

    override fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack += snapshot()
            restore(redoStack.removeLast())
        }
    }

    override fun paint(canvas: Canvas) {
        val owner = ui ?: return
        val display = displayValue()
        val layout = shaped(fieldRequest(display))
        val caretIndex = displayCaret()
        val caret = layout.caret(caretIndex.coerceAtMost(display.length))
        val box = surface.contentBox
        updateCaretOffset(caret.position.x)
        canvas.saved {
            emit(Clip(Rectangle, box))
            emit(Concat(Transform.translation(box.x - caretOffset, box.y)))
            if (state.focused && selectionStart != selectionEnd && !password && composition == null) {
                val a = layout.caret(selectionStart).position.x
                val b = layout.caret(selectionEnd).position.x
                fill(Rectangle, Rect(minOf(a, b), 0f, abs(b - a), box.height), Solid(0x4466AAFF.rgba))
            }
            text(layout, Vec2.ZERO, color)
            composition?.takeIf { !password }?.let { preedit ->
                val a = layout.caret(preedit.replacement.start).position.x
                val b = layout.caret(preedit.replacement.start + preedit.preedit.length).position.x
                fill(Rectangle, Rect(minOf(a, b), minOf(box.height, caret.height) - 1f, abs(b - a), 1f), Solid(color))
            }
            if (state.focused) fill(Rectangle, Rect(caret.position.x, 0f, 1f, minOf(box.height, caret.height)), Solid(color))
        }
    }
}
