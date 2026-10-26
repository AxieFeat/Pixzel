@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.component.ClipPolicy
import pixzel.component.Role
import pixzel.component.Semantics
import pixzel.graphics.Canvas
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.input.EventPhase
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.PointerPolicy
import pixzel.input.TextCommit
import pixzel.layout.unit.sp
import pixzel.surface.Vec2
import pixzel.text.TextRequest
import pixzel.text.TextStyle
import pixzel.text.UnicodeGraphemeSegmenter
import pixzel.text.nextBoundary
import pixzel.text.previousBoundary

/**
 * Demo-local multiline editor: reuses Pixzel shaping, focus, input, clipping and grapheme boundaries.
 */
internal class ReviewTextArea(model: MenuModel) : MenuElement("review-message", model) {

    var value = ""
        set(value) {
            field = value.take(500)
            caret = caret.coerceAtMost(field.length)
            anchor = anchor.coerceAtMost(field.length)
            invalidate()
        }
    private var caret = 0
    private var anchor = 0
    private var offset = 0f
    private val undo = ArrayDeque<Triple<String, Int, Int>>()
    private val redo = ArrayDeque<Triple<String, Int, Int>>()

    init {
        pointerPolicy = PointerPolicy.AUTO
        focusable = true
        clipPolicy = ClipPolicy.BOUNDS
        semantics = Semantics(Role.TEXT_FIELD, "Текст отзыва")
        onEvent(EventPhase.TARGET) { routed ->
            when (val input = routed.event) {
                is TextCommit -> {
                    replace(input.text)
                    routed.consume()
                }

                is KeyEvent -> {
                    if (input.action != KeyAction.UP && edit(input)) {
                        routed.consume()
                        routed.preventDefault()
                    }
                }

                is PointerEvent -> {
                    when (input.action) {
                        PointerAction.DOWN -> if (input.button == 0) {
                            ui?.focus?.request(this)
                            caret = shaped().hitTest((routed.localPosition ?: Vec2.ZERO) - Vec2(10f, 7f - offset)).index
                            anchor = caret
                            routed.capturePointer()
                            routed.consume()
                            invalidate()
                        }

                        PointerAction.MOVE -> if (state.pressed) {
                            caret = shaped().hitTest((routed.localPosition ?: Vec2.ZERO) - Vec2(10f, 7f - offset)).index
                            invalidate()
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    private fun shaped() = ui!!.text.layout(
        TextRequest(
            value, TextStyle(size = 15.2f.sp, lineHeight = 17f),
            width = (bounds.width - 20f).coerceAtLeast(1f), wrap = true
        )
    )

    private fun snapshot() = Triple(value, caret, anchor)
    private fun restore(snapshot: Triple<String, Int, Int>) {
        value = snapshot.first
        caret = snapshot.second
        anchor = snapshot.third
        invalidate()
    }

    fun insert(text: String) {
        replace(text)
    }

    private fun replace(text: String) {
        val start = minOf(caret, anchor)
        val end = maxOf(caret, anchor)
        val available = (500 - value.length + end - start).coerceAtLeast(0)
        val segmenter = UnicodeGraphemeSegmenter
        val clean = text.replace("\r", "")
        val legalEnd = segmenter.segment(clean).lastOrNull { it.end <= available }?.end ?: 0
        val inserted = clean.take(legalEnd)
        undo.addLast(snapshot())
        if (undo.size > 100) undo.removeFirst()
        redo.clear()
        value = value.substring(0, start) + inserted + value.substring(end)
        caret = start + inserted.length
        anchor = caret
        invalidate()
    }

    @Suppress("CyclomaticComplexMethod")
    private fun edit(event: KeyEvent): Boolean {
        val control = event.modifiers.control || event.modifiers.meta
        val segmenter = UnicodeGraphemeSegmenter
        if (control) {
            return editControl(event)
        } else {
            val next = when (event.key) {
                Key.LEFT -> {
                    segmenter.previousBoundary(value, caret)
                }

                Key.RIGHT -> {
                    segmenter.nextBoundary(value, caret)
                }

                Key.HOME -> {
                    value.lastIndexOf('\n', (caret - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
                }

                Key.END -> {
                    value.indexOf('\n', caret).let { if (it < 0) value.length else it }
                }

                Key.UP, Key.DOWN -> {
                    val layout = shaped()
                    val current = layout.caret(caret)
                    layout.hitTest(current.position + Vec2(0f, current.height * if (event.key == Key.UP) -1 else 1)).index
                }

                Key.BACKSPACE -> {
                    if (caret == anchor) anchor = segmenter.previousBoundary(value, caret)
                    replace("")
                    return true
                }

                Key.DELETE -> {
                    if (caret == anchor) anchor = segmenter.nextBoundary(value, caret)
                    replace("")
                    return true
                }

                Key.ENTER -> {
                    replace("\n")
                    return true
                }

                else -> {
                    return false
                }
            }
            caret = next
            if (!event.modifiers.shift) anchor = caret
        }
        invalidate()
        return true
    }

    private fun editControl(event: KeyEvent): Boolean {
        when (event.key) {
            Key.A -> {
                anchor = 0
                caret = value.length
            }

            Key.C, Key.X -> {
                ui!!.host.writeClipboard(value.substring(minOf(caret, anchor), maxOf(caret, anchor)))
                if (event.key == Key.X) replace("")
            }

            Key.V -> {
                ui!!.host.readClipboard().onSuccess(::replace)
            }

            Key.Z -> {
                if (event.modifiers.shift) redo() else undo()
            }

            Key.Y -> {
                redo()
            }

            else -> {
                return false
            }
        }
        invalidate()
        return true
    }

    private fun undo() {
        if (undo.isNotEmpty()) {
            redo.addLast(snapshot())
            restore(undo.removeLast())
        }
    }

    private fun redo() {
        if (redo.isNotEmpty()) {
            undo.addLast(snapshot())
            restore(redo.removeLast())
        }
    }

    override fun paint(canvas: Canvas) {
        canvas.pixelPanel(bounds, 0x242424E5.rgba, if (state.focused) 0x4679B4.rgb else 0x484848.rgb, 3f, 1f)
        val layout = shaped()
        val position = layout.caret(caret)
        if (position.position.y - offset > bounds.height - 35f) offset = position.position.y - bounds.height + 35f
        if (position.position.y < offset) offset = position.position.y
        val origin = Vec2(10f, 7f - offset)
        if (state.focused && caret != anchor) {
            layout.clusters.filter { it.start >= minOf(caret, anchor) && it.end <= maxOf(caret, anchor) }.forEach {
                canvas.rect(origin.x + it.bounds.x, origin.y + it.bounds.y, it.bounds.width, it.bounds.height, 0x3878BC80.rgba)
            }
        }
        if (value.isEmpty()) with(canvas) { label("Что зашло, что бесит, кому советуешь...", 10f, 7f, 15.2f, 0xB9B9B9.rgb) }
        else canvas.text(layout, origin, 0xD2D2D2.rgb)
        if (state.focused) canvas.rect(origin.x + position.position.x, origin.y + position.position.y, 1f, position.height, 0xDDDDDD.rgb)
    }
}

