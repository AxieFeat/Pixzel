package pixzel.arc.graphics

import arc.graphics.RenderContext
import pixzel.arc.text.ArcTextService
import pixzel.graphics.BackendCapabilities
import pixzel.graphics.BackendLimits
import pixzel.graphics.Capability
import pixzel.graphics.RenderSession
import pixzel.graphics.TargetFormat
import pixzel.graphics.TextRenderMode
import pixzel.graphics.UiBackend

/**
 * OpenGL-4.1-compatible rendering through Arc's neutral graphics contracts. Arc bootstrap selects the backend.
 * One reusable UI composition target owns stencil; positioned Surfaces do not allocate targets.
 * Two-times supersampling antialiases tessellated path/clip edges. Intermediate textures use premultiplied alpha.
 */
class ArcUiBackend private constructor(
    private val context: RenderContext,
    private val text: ArcTextService,
    private val supersampling: Int,
    private val customRenderers: ArcCustomRenderers,
    private val targetBudgetBytes: Long,
    private val meshBudgetBytes: Long,
) : UiBackend {

    override val resourceGeneration get() = context.resourceGeneration
    override val capabilities
        get() = BackendCapabilities(
            setOf(
                Capability.RECT_CLIP, Capability.SHAPE_CLIP, Capability.PATH,
                Capability.GRADIENT, Capability.IMAGE, Capability.TEXT, Capability.GROUP_OPACITY, Capability.SHADOW
            ) +
                    if (customRenderers.entries.isEmpty()) emptySet() else setOf(Capability.CUSTOM), "Arc scoped graphics",
            BackendLimits(
                context.capabilities.maxTextureDimension, 255, 64, 4096, ShapeTessellator.MAX_VERTICES,
                setOf(TargetFormat.RGBA8, TargetFormat.RGBA8_DEPTH24_STENCIL8), TextRenderMode.GRAYSCALE_GLYPH_MASK
            )
        )

    init {
        require(supersampling in 1..4)
        require(targetBudgetBytes > 0)
        require(meshBudgetBytes > 0)
        require(context.capabilities.let { it.scopedPass && it.typedState && it.renderTargets && it.stencilMasks }) {
            "Arc UI requires scoped passes, typed state, owned render targets and stencil masks"
        }
    }

    override fun openSession(): RenderSession =
        ArcRenderSession(context, text, supersampling, customRenderers, capabilities, targetBudgetBytes, meshBudgetBytes)

    companion object {

        fun of(
            context: RenderContext, text: ArcTextService, supersampling: Int = 2, customRenderers: ArcCustomRenderers = ArcCustomRenderers.EMPTY,
            targetBudgetBytes: Long = 512L * 1024 * 1024, meshBudgetBytes: Long = 32L * 1024 * 1024
        ) = ArcUiBackend(context, text, supersampling, customRenderers, targetBudgetBytes, meshBudgetBytes)
    }
}

data class ArcRenderStatistics(
    val drawCalls: Int = 0, val uploadedBytes: Long = 0, val ownedTargets: Int = 0, val geometryEntries: Int = 0,
    val textBytes: Long = 0, val ownedTargetBytes: Long = 0, val meshBytes: Long = 0, val cpuGeometryBytes: Long = 0
)
