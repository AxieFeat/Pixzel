package pixzel

import pixzel.component.ClipPolicy
import pixzel.component.Element
import pixzel.component.PaintContext
import pixzel.graphics.BeginGroup
import pixzel.graphics.Canvas
import pixzel.graphics.Clip
import pixzel.graphics.Concat
import pixzel.graphics.DrawCommand
import pixzel.graphics.EndGroup
import pixzel.graphics.Rectangle
import pixzel.graphics.Restore
import pixzel.graphics.Save
import pixzel.style.ResolvedTokens
import pixzel.style.Theme
import pixzel.style.isEffectivelyEnabled
import pixzel.surface.Rect
import pixzel.surface.Transform
import java.util.Collections.unmodifiableList
import java.util.IdentityHashMap

internal data class RecordedPaint(val commands: List<DrawCommand>, val paintedNodes: Int)

/**
 * Keeps each node's own layers separate so children can be replayed in paint order.
 */
internal class UiPainter(private val ui: SimpleUi) {

    private data class PaintKey(
        val version: Long,
        val transform: Transform,
        val bounds: Rect,
        val content: Rect,
        val theme: Theme,
        val settings: UiSettings,
        val tokens: ResolvedTokens,
        val disabled: Boolean
    )

    private data class NodePaint(val key: PaintKey, val before: List<DrawCommand>, val after: List<DrawCommand>)


    private val nodePaint = IdentityHashMap<Element, NodePaint>()

    fun retain(elements: Set<Element>) {
        nodePaint.keys.retainAll(elements)
    }

    fun record(root: Element, capacity: Int): RecordedPaint {
        val buffer = ArrayList<DrawCommand>(capacity)
        var painted = 0

        fun paint(element: Element) {
            if (!ui.visibleAttached(element)) return
            var cached = nodePaint[element]
            val previous = cached?.key
            val tokens = element.resolvedTokens
            val disabled = !element.isEffectivelyEnabled

            // Rebuild only this node's layers. Its children keep independent cache entries.
            if (
                previous == null ||
                previous.version != element.paintVersion ||
                previous.transform != element.surface.localToParent ||
                previous.bounds != element.surface.bounds ||
                previous.content != element.surface.contentBox ||
                previous.theme != ui.theme ||
                previous.settings != ui.settings ||
                previous.tokens !== tokens ||
                previous.disabled != disabled
            ) {
                val key = PaintKey(
                    element.paintVersion,
                    element.surface.localToParent,
                    element.surface.bounds,
                    element.surface.contentBox,
                    ui.theme,
                    ui.settings,
                    tokens,
                    disabled
                )
                painted++
                val before = ArrayList<DrawCommand>()
                val after = ArrayList<DrawCommand>()
                var destination: MutableList<DrawCommand> = before
                val localCanvas = object : Canvas {
                    override fun emit(command: DrawCommand) {
                        destination += command
                    }
                }
                if (element.opacity != 1f) before += BeginGroup(element.opacity)
                before += Save
                before += Concat(element.surface.localToParent)
                val bounds = Rect(0f, 0f, element.surface.bounds.width, element.surface.bounds.height)
                when (element.clipPolicy) {
                    ClipPolicy.BOUNDS -> before += Clip(Rectangle, bounds)
                    ClipPolicy.CONTENT -> before += Clip(Rectangle, element.surface.contentBox)
                    ClipPolicy.SHAPE -> before += Clip(element.clipShape, bounds)
                    else -> {}
                }
                val context = PaintContext(element, bounds, element.state.copy(disabled = disabled), tokens)
                fun layer(phase: String, draw: () -> Unit) {
                    val start = destination.size

                    try {
                        draw()
                    } catch (error: Throwable) {
                        if (ui.settings.errorPolicy.handle(UiFailure(error, phase, element.id)) == ErrorDecision.THROW) throw error
                        // Discard only the failing layer's partial commands before reporting the error.
                        while (destination.size > start) destination.removeAt(destination.lastIndex)
                        ui.diagnostics.report(
                            Diagnostic(
                                "paint-error",
                                error.message ?: error::class.simpleName.orEmpty(),
                                element.id,
                                DiagnosticSeverity.ERROR
                            )
                        )
                    }
                }
                layer("background painter") { element.background?.paint(localCanvas, context) }
                layer("content painter") { element.paint(localCanvas) }
                destination = after
                layer("foreground painter") { element.foreground?.paint(localCanvas, context) }
                after += Restore
                if (element.opacity != 1f) after += EndGroup
                cached = NodePaint(key, before, after)
                nodePaint[element] = cached
            }
            buffer.addAll(cached.before)
            ui.sorted(element).forEach(::paint)
            buffer.addAll(cached.after)
        }

        paint(root)
        return RecordedPaint(unmodifiableList(buffer), painted)
    }

    fun clear() {
        nodePaint.clear()
    }
}
