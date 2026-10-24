package pixzel.arc.graphics

import arc.graphics.RenderContext
import pixzel.graphics.CustomCommand
import pixzel.graphics.DrawCommand
import pixzel.graphics.FrameInfo
import pixzel.resource.ResourceScope
import pixzel.surface.Transform

/**
 * Borrowed handler; allocate cached resources through the session-owned scope.
 */
fun interface ArcCustomRenderer {

    fun render(scope: ArcCustomDrawScope, command: CustomCommand)

    /**
     * Every texture unit the handler may modify; native state is restored by a nested Arc pass.
     */
    val textureUnits: Set<Int> get() = setOf(0)
}

/**
 * An immutable, Ui-backend-local registry. No renderer is found through a global provider.
 */
class ArcCustomRenderers private constructor(internal val entries: Map<String, ArcCustomRenderer>) {

    companion object {

        val EMPTY = ArcCustomRenderers(emptyMap())
        fun of(vararg renderers: Pair<String, ArcCustomRenderer>): ArcCustomRenderers {
            require(renderers.all { it.first.isNotBlank() }) { "Custom operation IDs must not be blank" }
            require(renderers.map { it.first }.distinct().size == renderers.size) { "Duplicate custom operation ID" }
            return ArcCustomRenderers(renderers.toMap())
        }
    }
}

/**
 * Native custom materials must respect the active target/clip and apply [transform]. Resource contents are not
 * rolled back. [emit] is the portable way to reuse standard painters while retaining clips and painter order.
 * Resources in [resources] release with this session; context, frame and handler remain borrowed.
 */
class ArcCustomDrawScope internal constructor(
    val context: RenderContext,
    val frame: FrameInfo,
    val transform: Transform,
    val resources: ResourceScope,
    private val emitter: (DrawCommand) -> Unit,
) {

    fun emit(command: DrawCommand) = emitter(command)
}
