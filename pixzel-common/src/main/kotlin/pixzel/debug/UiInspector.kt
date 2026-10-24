package pixzel.debug

import pixzel.Ui
import pixzel.component.AbstractElement
import pixzel.component.Element
import pixzel.component.Layer
import pixzel.component.Visibility
import pixzel.graphics.Canvas
import pixzel.graphics.Color
import pixzel.graphics.Rectangle
import pixzel.graphics.Solid
import pixzel.graphics.rgba
import pixzel.input.PointerPolicy
import pixzel.surface.Placement
import pixzel.surface.Rect
import pixzel.surface.ReferenceBox
import pixzel.surface.Vec2
import pixzel.text.CachingTextService
import pixzel.text.TextLayout
import pixzel.text.TextRequest
import kotlin.time.Duration

/**
 * Optional per-Ui overlay. Disabled inspection creates no frame callbacks or text work.
 */
class UiInspector private constructor(private val owner: Ui) : AutoCloseable {

    private var selectedElement: Element? = null
    var selected: Element?
        get() = selectedElement
        set(value) {
            owner.assertThread()
            require(value == null || value.ui === owner)
            selectedElement = value
            overlay.invalidate()
        }
    var enabled: Boolean = false
        set(value) {
            owner.assertThread()
            field = value
            overlay.visibility = if (value) Visibility.VISIBLE else Visibility.HIDDEN
        }
    private var details = ""
    private var textLayout: TextLayout? = null
    private val overlay = object : AbstractElement() {
        override fun update(delta: Duration): Boolean {
            val target = selectedElement?.takeUnless { it.isDisposed || it.ui !== owner } ?: return false
            val box = target.surface
            val next =
                "${target.id} #${target.generation}\n${target.inspection}\nBorder ${box.bounds}\nContent ${box.contentBox}\nAnchor ${target.placement.anchor} pivot ${target.placement.pivot}\nState ${target.state}\nClip ${target.clipPolicy} focus ${owner.focus.focused?.id} capture ${owner.capturedPointers.mapValues { it.value.id }}\nMeasured ${owner.statistics.measuredNodes} materialized ${owner.statistics.totalNodes} visible ${owner.statistics.visibleNodes} commands ${owner.statistics.commands}\nText ${(owner.text as? CachingTextService)?.statistics}\nGPU ${owner.renderMetrics}\nDiagnostics ${owner.diagnostics.messages.joinToString { it.code }}"
            if (next == details) return false
            details = next
            textLayout = owner.text.layout(TextRequest(details, width = 600f, wrap = true))
            return true
        }

        override fun paint(canvas: Canvas) {
            val target = selectedElement?.takeUnless { it.isDisposed || !it.surface.isAttached } ?: return
            val border = target.surface.mapRect(Rect(0f, 0f, target.surface.bounds.width, target.surface.bounds.height))
            val content = target.surface.mapRect(target.surface.contentBox)
            canvas.stroke(Rectangle, border, Solid(0xFF4FC7FF.rgba), 1f)
            canvas.stroke(Rectangle, content, Solid(0x4FFFD7FF.rgba), 1f)
            textLayout?.let {
                canvas.fill(Rectangle, Rect(8f, 8f, it.size.width + 16, it.size.height + 16), Solid(0x0B0F18EE.rgba))
                canvas.text(it, Vec2(16f, 16f), Color.WHITE)
            }
        }
    }

    init {
        overlay.portalTarget = owner.root
        overlay.layer = Layer.DEBUG
        overlay.pointerPolicy = PointerPolicy.NONE
        overlay.placement = Placement(referenceBox = ReferenceBox.BORDER)
        overlay.visibility = Visibility.HIDDEN
        owner.root.add(overlay)
    }

    fun selectAt(point: Vec2) {
        selected = owner.hitTest(point)
    }

    override fun close() {
        if (!overlay.isDisposed) overlay.close()
    }

    companion object {

        fun of(ui: Ui) = UiInspector(ui)
    }
}
