package pixzel.arc.graphics

import arc.asset.RuntimeAsset
import arc.graphics.BlendState
import arc.graphics.ColorWriteMask
import arc.graphics.CompareFunction
import arc.graphics.CullMode
import arc.graphics.DepthState
import arc.graphics.DrawBuffer
import arc.graphics.DrawerMode
import arc.graphics.RasterState
import arc.graphics.RenderContext
import arc.graphics.RenderTarget
import arc.graphics.StencilOperation
import arc.graphics.StencilState
import arc.graphics.TextureSampling
import arc.graphics.vertex.VertexBuffer
import arc.graphics.vertex.VertexFormat
import arc.graphics.vertex.VertexFormatElement
import arc.shader.ShaderInstance
import arc.shader.ShaderSettings
import arc.texture.Texture
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector4f
import pixzel.arc.text.ArcTextService
import pixzel.graphics.BackendCapabilities
import pixzel.graphics.BackendLimits
import pixzel.graphics.BeginGroup
import pixzel.graphics.Brush
import pixzel.graphics.Capability
import pixzel.graphics.Clip
import pixzel.graphics.Color
import pixzel.graphics.ColorStop
import pixzel.graphics.Concat
import pixzel.graphics.CustomCommand
import pixzel.graphics.DrawCommand
import pixzel.graphics.DrawImage
import pixzel.graphics.DrawText
import pixzel.graphics.EndGroup
import pixzel.graphics.Fill
import pixzel.graphics.FrameInfo
import pixzel.graphics.ImageBrush
import pixzel.graphics.LinearGradient
import pixzel.graphics.RadialGradient
import pixzel.graphics.Rectangle
import pixzel.graphics.RenderMetrics
import pixzel.graphics.RenderMetricsProvider
import pixzel.graphics.RenderSession
import pixzel.graphics.Restore
import pixzel.graphics.Sampling
import pixzel.graphics.Save
import pixzel.graphics.Shadow
import pixzel.graphics.Shape
import pixzel.graphics.Solid
import pixzel.graphics.Stroke
import pixzel.graphics.StrokeCap
import pixzel.graphics.StrokeJoin
import pixzel.graphics.TargetFormat
import pixzel.graphics.TextRenderMode
import pixzel.graphics.UiBackend
import pixzel.graphics.group
import pixzel.graphics.rgba
import pixzel.graphics.saved
import pixzel.resource.ResourceScope
import pixzel.surface.Rect
import pixzel.surface.Transform
import pixzel.surface.Vec2
import pixzel.text.TextLayout
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

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

private data class GeometryKey(
    val shape: Shape, val bounds: Rect, val stroke: Float?, val tolerance: Float,
    val join: StrokeJoin, val cap: StrokeCap, val miterLimit: Float
)

private data class SavedState(val transform: Transform, val clipDepth: Int)
private data class GroupState(val target: RenderTarget, val state: SavedState, val opacity: Float, val stackDepth: Int)
private data class TextKey(val layout: TextLayout, val scale: Float)
private data class TextTile(val bounds: Rect, val texture: Texture)
private data class PreparedText(val tiles: List<TextTile>, val bytes: Long)
private data class ImageMeshKey(val bounds: Rect, val uv: Rect, val flip: Boolean)
private data class SolidPartKey(val geometry: GeometryKey, val transform: Transform, val color: Color)
private data class SolidBatchKey(val parts: List<SolidPartKey>)
private data class SolidPart(val key: SolidPartKey, val points: FloatArray)

/** Session allocations belong to the Ui; injected context/font service remain borrowed. */
class ArcRenderSession internal constructor(
    private val context: RenderContext,
    private val text: ArcTextService,
    private val supersampling: Int,
    private val customRenderers: ArcCustomRenderers,
    private val capabilities: BackendCapabilities,
    private val targetBudgetBytes: Long,
    private val meshBudgetBytes: Long,
) : RenderSession, RenderMetricsProvider {

    private val owner = Thread.currentThread()
    private val generation = context.resourceGeneration
    private var closed = false
    private val shader: ShaderInstance
    private val format: VertexFormat
    private val coloredFormat: VertexFormat
    private val nearest = lazy { context.createSampler(TextureSampling.NEAREST) }
    private val linear = lazy { context.createSampler(TextureSampling.LINEAR) }
    private val matrix = Matrix4f()
    private val vector = Vector2f()
    private val colorVector = Vector4f()
    private val meshes = object : LinkedHashMap<Any, VertexBuffer>(64, .75f, true) {}
    private var meshBytes = 0L
    private val cpuGeometry = object : LinkedHashMap<GeometryKey, FloatArray>(64, .75f, true) {}
    private var cpuGeometryBytes = 0L
    private val solidParts = mutableListOf<SolidPart>()
    private var solidVertices = 0
    private val texts = object : LinkedHashMap<TextKey, PreparedText>(64, .75f, true) {}
    private var textBytes = 0L
    private var target: RenderTarget? = null
    private val groupTargets = mutableListOf<RenderTarget>()
    private val effectTargets = mutableListOf<RenderTarget>()
    private var frame: FrameInfo? = null
    private var transform = Transform.IDENTITY
    private var clipDepth = 0
    private var currentTarget: RenderTarget? = null
    private val stack = ArrayDeque<SavedState>()
    private val groups = ArrayDeque<GroupState>()
    private val customResources = ResourceScope()
    private var customDepth = 0
    private var draws = 0
    private var uploads = 0L
    private var vertices = 0
    private var textureSwitches = 0
    private var lastTexture = -1
    private var customMetricsUnknown = false
    override var renderMetrics = RenderMetrics()
        private set
    var statistics = ArcRenderStatistics()
        private set

    init {
        context.beginScopedPass(setOf(0)).use {
            shader = ShaderInstance.of(RuntimeAsset.from(VERTEX), RuntimeAsset.from(FRAGMENT), ShaderSettings.of())
            try {
                shader.compileShaders()
                format = VertexFormat.builder().add(VertexFormatElement.POSITION).add(VertexFormatElement.UV).build()
                coloredFormat =
                    VertexFormat.builder().add(VertexFormatElement.POSITION).add(VertexFormatElement.UV).add(VertexFormatElement.COLOR).build()
            } catch (failure: Throwable) {
                shader.cleanup()
                throw failure
            }
        }
    }

    override fun submit(frame: FrameInfo, commands: List<DrawCommand>) {
        checkOwner()
        check(!closed) { "Arc render session is closed" }
        check(context.resourceGeneration == generation && generation != 0L) { "Arc session belongs to a stale or missing context; open a new session for the current generation" }
        require(frame.pixelsPerDp.x > 0 && frame.pixelsPerDp.y > 0) { "Framebuffer pixels per dp must be positive" }
        if (frame.framebufferSize.width <= 0 || frame.framebufferSize.height <= 0 || frame.logicalSize.width <= 0 || frame.logicalSize.height <= 0) {
            statistics = statistics.copy(drawCalls = 0, uploadedBytes = 0)
            renderMetrics = renderMetrics.copy(drawCalls = 0, batches = 0, vertices = 0, uploadedBytes = 0, textureSwitches = 0)
            return
        }
        this.frame = frame
        draws = 0
        uploads = 0
        vertices = 0
        textureSwitches = 0
        lastTexture = -1
        customMetricsUnknown = false
        stack.clear()
        groups.clear()
        solidParts.clear()
        solidVertices = 0
        transform = Transform.IDENTITY
        clipDepth = 0
        val width = ceil(frame.framebufferSize.width * supersampling).toInt()
        val height = ceil(frame.framebufferSize.height * supersampling).toInt()
        require(targetBytes(width, height, true) <= 256L * 1024 * 1024) { "UI composition target exceeds its 256 MiB color/stencil budget" }
        try {
            context.beginScopedPass(setOf(0)).use {
                if (target != null && (target!!.width != width || target!!.height != height)) {
                    groupTargets.forEach(RenderTarget::cleanup)
                    groupTargets.clear()
                    effectTargets.forEach(RenderTarget::cleanup)
                    effectTargets.clear()
                }
                reserveTarget(width, height, true, target)
                if (target == null) target = context.createRenderTarget(width, height, true)
                else if (target!!.width != width || target!!.height != height) target!!.resize(width, height)
                context.beginScopedPass(setOf(0)).use {
                    currentTarget = target
                    bind(currentTarget!!)
                    clearOwned()
                    commands.forEachIndexed { index, command ->
                        try {
                            execute(command)
                        } catch (failure: Throwable) {
                            throw IllegalStateException("Arc draw command $index (${command::class.simpleName}) failed", failure)
                        }
                    }
                    flushSolidBatch()
                    check(stack.isEmpty() && groups.isEmpty()) { "Unbalanced save/group commands" }
                }
                // The inner scope restores the application's actual target, including a non-default framebuffer.
                context.setViewport(0, 0, frame.framebufferSize.width.toInt(), frame.framebufferSize.height.toInt())
                context.setRaster(RasterState())
                context.setDepth(DepthState())
                context.setCull(CullMode.NONE)
                context.setBlend(BlendState.PREMULTIPLIED_ALPHA)
                context.setColorWrite(ColorWriteMask.ALL)
                context.setStencil(StencilState())
                context.setScissor(null)
                transform = Transform.IDENTITY
                clipDepth = 0
                image(target!!.colorTexture, fullBounds(), Rect(0f, 0f, 1f, 1f), Color.WHITE, true, true, Sampling.LINEAR)
            }
            statistics = ArcRenderStatistics(
                draws,
                uploads,
                1 + groupTargets.size + effectTargets.size,
                meshes.keys.count { it !is ImageMeshKey },
                textBytes,
                ownedTargetBytes(),
                meshBytes,
                cpuGeometryBytes
            )
            renderMetrics = RenderMetrics(
                if (customMetricsUnknown) null else draws, if (customMetricsUnknown) null else draws,
                if (customMetricsUnknown) null else vertices, if (customMetricsUnknown) null else uploads,
                if (customMetricsUnknown) null else textureSwitches, if (customMetricsUnknown) null else statistics.ownedTargets,
                if (customRenderers.entries.isEmpty()) 1 + (if (nearest.isInitialized()) 1 else 0) + (if (linear.isInitialized()) 1 else 0) +
                        meshes.size + texts.values.sumOf { it.tiles.size } + statistics.ownedTargets else null
            )
        } finally {
            this.frame = null
            currentTarget = null
            customDepth = 0
            solidParts.clear()
            solidVertices = 0
        }
    }

    private fun execute(command: DrawCommand) {
        when (command) {
            Save -> stack.addLast(SavedState(transform, clipDepth))
            Restore -> {
                check(stack.isNotEmpty()) { "Restore has no matching Save" }
                val saved = stack.removeLast()
                if (saved.clipDepth != clipDepth) flushSolidBatch()
                restoreClip(saved.clipDepth)
                transform = saved.transform
            }

            is Concat -> transform = transform * command.transform
            is Clip -> {
                flushSolidBatch()
                clip(command.shape, command.bounds)
            }

            is Fill -> solidOrFill(command.shape, command.bounds, command.brush)
            is Stroke -> solidOrFill(command.shape, command.bounds, command.brush, command.width, command.join, command.cap, command.miterLimit)
            is DrawText -> {
                flushSolidBatch()
                drawText(command)
            }

            is DrawImage -> {
                flushSolidBatch()
                val image = command.image as? ArcImageHandle ?: throw IllegalArgumentException("Image '${command.image.id}' is not an Arc image")
                check(!image.isClosed) { "Image '${image.id}' is closed" }
                require(image.size.width > 0 && image.size.height > 0)
                val uv = Rect(
                    command.source.x / image.size.width, command.source.y / image.size.height,
                    command.source.width / image.size.width, command.source.height / image.size.height
                )
                image(image.resolve(context), command.destination, uv, command.tint, image.premultiplied, image.flipVertically, command.sampling)
            }

            is BeginGroup -> {
                flushSolidBatch()
                beginGroup(command.opacity)
            }

            EndGroup -> {
                flushSolidBatch()
                endGroup()
            }

            is Shadow -> {
                flushSolidBatch()
                shadow(command)
            }

            is CustomCommand -> {
                flushSolidBatch()
                custom(command)
            }

            else -> throw UnsupportedOperationException("Custom draw command ${command::class.qualifiedName} has no Arc executor")
        }
    }

    private fun bind(target: RenderTarget) {
        context.bindTarget(target)
        context.setViewport(0, 0, target.width, target.height)
        context.setRaster(RasterState())
        context.setScissor(null)
        context.setDepth(DepthState())
        context.setCull(CullMode.NONE)
        context.setBlend(BlendState.PREMULTIPLIED_ALPHA)
        context.setColorWrite(ColorWriteMask.ALL)
        context.setStencil(StencilState())
    }

    private fun clearOwned() {
        context.setStencil(StencilState(writeMask = -1))
        context.setColorWrite(ColorWriteMask.ALL)
        context.setScissor(null)
        context.clearTarget(0f, 0f, 0f, 0f, stencil = true)
        clipState()
    }

    private fun fullBounds() = frame!!.logicalSize.let { Rect(0f, 0f, it.width, it.height) }
    private fun tolerance() = .2f / (max(frame!!.pixelsPerDp.x, frame!!.pixelsPerDp.y) * supersampling)

    private fun mesh(
        shape: Shape,
        bounds: Rect,
        stroke: Float? = null,
        join: StrokeJoin = StrokeJoin.ROUND,
        cap: StrokeCap = StrokeCap.ROUND,
        miterLimit: Float = 4f
    ): VertexBuffer? {
        val key = GeometryKey(shape, bounds, stroke, tolerance(), join, cap, miterLimit)
        meshes[key]?.let { return it }
        val points = geometryPoints(key)
        if (points.isEmpty()) return null
        val vertices = (points.indices step 2).map { Vec2(points[it], points[it + 1]) }
        return cacheMesh(key, buildMesh(vertices.map { it to it }))
    }

    private fun geometryPoints(key: GeometryKey): FloatArray {
        cpuGeometry[key]?.let { return it }
        val vertices = ShapeTessellator.triangles(key.shape, key.bounds, key.stroke, key.tolerance, key.join, key.cap, key.miterLimit)
        require(vertices.size.toLong() * 8 <= meshBudgetBytes) { "CPU geometry exceeds its $meshBudgetBytes-byte budget" }
        val points = FloatArray(vertices.size * 2)
        vertices.forEachIndexed { index, point ->
            points[index * 2] = point.x
            points[index * 2 + 1] = point.y
        }
        cpuGeometry[key] = points
        cpuGeometryBytes += points.size.toLong() * 4
        while (cpuGeometry.size > 512 || cpuGeometryBytes > meshBudgetBytes) {
            val entry = cpuGeometry.entries.iterator()
            val first = entry.next()
            cpuGeometryBytes -= first.value.size.toLong() * 4
            entry.remove()
        }
        return points
    }

    private fun cacheMesh(key: Any, buffer: VertexBuffer): VertexBuffer {
        meshes.put(key, buffer)?.let {
            meshBytes -= it.activeBytes
            it.cleanup()
        }
        meshBytes += buffer.activeBytes
        while (meshes.size > 512 || meshBytes > meshBudgetBytes) {
            val entry = meshes.entries.iterator()
            val first = entry.next()
            meshBytes -= first.value.activeBytes
            first.value.cleanup()
            entry.remove()
        }
        return buffer
    }

    private fun solidOrFill(
        shape: Shape,
        bounds: Rect,
        brush: Brush,
        stroke: Float? = null,
        join: StrokeJoin = StrokeJoin.ROUND,
        cap: StrokeCap = StrokeCap.ROUND,
        miterLimit: Float = 4f
    ) {
        if (brush !is Solid) {
            flushSolidBatch()
            fill(shape, bounds, brush, stroke, join, cap, miterLimit)
            return
        }
        val key = GeometryKey(shape, bounds, stroke, tolerance(), join, cap, miterLimit)
        val points = geometryPoints(key)
        val count = points.size / 2
        if (count == 0) return
        if (count > 65532) {
            flushSolidBatch()
            fill(shape, bounds, brush, stroke, join, cap, miterLimit)
            return
        }
        if (solidVertices + count > 65532) flushSolidBatch()
        solidParts += SolidPart(SolidPartKey(key, transform, brush.color), points)
        solidVertices += count
    }

    private fun flushSolidBatch() {
        if (solidParts.isEmpty()) return
        val saved = transform
        try {
            val key = SolidBatchKey(solidParts.map { it.key })
            val buffer = meshes[key] ?: run {
                require(solidVertices.toLong() * coloredFormat.nextOffset <= meshBudgetBytes) { "Solid batch exceeds its $meshBudgetBytes-byte mesh budget" }
                val gpu = DrawBuffer.of(DrawerMode.TRIANGLES, coloredFormat, Math.multiplyExact(solidVertices, 6)).use { cpu ->
                    solidParts.forEach { part ->
                        val matrix = part.key.transform
                        val color = part.key.color
                        val points = part.points
                        for (index in points.indices step 2) {
                            val x = points[index]
                            val y = points[index + 1]
                            cpu.addVertex(matrix.a * x + matrix.c * y + matrix.tx, matrix.b * x + matrix.d * y + matrix.ty, 0f)
                                .setTexture(x, y).setColor(color.red, color.green, color.blue, color.alpha)
                        }
                    }
                    cpu.build()
                }
                uploads += gpu.activeBytes
                cacheMesh(key, gpu)
            }
            transform = Transform.IDENTITY
            prepare(Solid(Color.WHITE))
            shader.setUniform("vertexColors", true)
            draw(buffer)
        } finally {
            transform = saved
            solidParts.clear()
            solidVertices = 0
        }
    }

    private fun buildMesh(vertices: List<Pair<Vec2, Vec2>>): VertexBuffer {
        require(vertices.size.toLong() * format.nextOffset <= meshBudgetBytes) { "Mesh exceeds its $meshBudgetBytes-byte budget" }
        val result = DrawBuffer.of(DrawerMode.TRIANGLES, format, Math.multiplyExact(vertices.size, 5)).use { buffer ->
            vertices.forEach { (p, uv) -> buffer.addVertex(p.x, p.y, 0f).setTexture(uv.x, uv.y) }
            buffer.build()
        }
        uploads += result.activeBytes
        return result
    }

    private fun prepare(brush: Brush) {
        context.bindShader(shader)
        shader.setUniform("viewportSize", vector.set(frame!!.logicalSize.width, frame!!.logicalSize.height))
        matrix.identity().m00(transform.a).m01(transform.b).m10(transform.c).m11(transform.d).m30(transform.tx).m31(transform.ty)
        shader.setUniform("uiTransform", matrix)
        shader.setUniform("blurStep", vector.set(0f, 0f))
        shader.setUniform("vertexColors", false)
        when (brush) {
            is Solid -> {
                shader.setUniform("paintMode", 0)
                setColor("baseColor", brush.color)
            }

            is LinearGradient -> {
                shader.setUniform("paintMode", 1)
                shader.setUniform("gradientFrom", vector.set(brush.from.x, brush.from.y))
                shader.setUniform("gradientTo", vector.set(brush.to.x, brush.to.y))
                stops(brush.stops)
            }

            is RadialGradient -> {
                shader.setUniform("paintMode", 2)
                shader.setUniform("gradientFrom", vector.set(brush.center.x, brush.center.y))
                shader.setUniform("gradientRadius", brush.radius)
                stops(brush.stops)
            }

            is ImageBrush -> {
                val image = brush.image as? ArcImageHandle ?: throw IllegalArgumentException("Image brush requires an Arc image")
                check(!image.isClosed) { "Image '${image.id}' is closed" }
                val inverse = brush.transform.inverse() ?: error("Image brush transform is singular")
                shader.setUniform("paintMode", 4)
                setColor("baseColor", Color.WHITE)
                shader.setUniform(
                    "brushMapping",
                    matrix.identity().m00(inverse.a).m01(inverse.b).m10(inverse.c).m11(inverse.d).m30(inverse.tx).m31(inverse.ty)
                )
                shader.setUniform("brushImageSize", vector.set(image.size.width, image.size.height))
                shader.setUniform(
                    "brushSource", colorVector.set(
                        brush.source.x / image.size.width, brush.source.y / image.size.height,
                        brush.source.width / image.size.width, brush.source.height / image.size.height
                    )
                )
                shader.setUniform("brushFlip", image.flipVertically)
                shader.setUniform("imagePremultiplied", image.premultiplied)
                shader.setUniform("imageTexture", 0)
                bindTexture(image.resolve(context))
                context.bindSampler(if (brush.sampling == Sampling.LINEAR) linear.value else nearest.value)
            }

            else -> throw UnsupportedOperationException("Brush ${brush::class.qualifiedName} has no Arc material")
        }
    }

    private fun stops(stops: List<ColorStop>) {
        require(stops.size <= 64) { "Arc gradient supports up to 64 color stops" }
        require(stops.zipWithNext().all { it.first.offset <= it.second.offset }) { "Gradient stops must be ordered" }
        shader.setUniform("stopCount", stops.size)
        stops.forEachIndexed { index, stop ->
            shader.setUniform("stopPosition[$index]", stop.offset)
            setColor("stopColor[$index]", stop.color)
        }
    }

    private fun setColor(name: String, color: Color, opacity: Float = 1f) {
        shader.setUniform(name, colorVector.set(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f * opacity))
    }

    private fun draw(buffer: VertexBuffer) {
        context.drawer.draw(buffer)
        draws++
        vertices = Math.addExact(vertices, buffer.vertices)
    }

    private fun bindTexture(texture: Texture) {
        if (texture.id != lastTexture) {
            textureSwitches++
            lastTexture = texture.id
        }
        context.bindTexture(texture)
    }

    private fun fill(
        shape: Shape,
        bounds: Rect,
        brush: Brush,
        stroke: Float? = null,
        join: StrokeJoin = StrokeJoin.ROUND,
        cap: StrokeCap = StrokeCap.ROUND,
        miterLimit: Float = 4f
    ) {
        val buffer = mesh(shape, bounds, stroke, join, cap, miterLimit) ?: return
        prepare(brush)
        draw(buffer)
    }

    private fun clipState() {
        context.setStencil(if (clipDepth == 0) StencilState() else StencilState(true, CompareFunction.EQUAL, clipDepth, writeMask = 0))
    }

    private fun clip(shape: Shape, bounds: Rect) {
        check(clipDepth < 255) { "Clip stack exceeds the owned 8-bit stencil attachment" }
        context.setColorWrite(ColorWriteMask.NONE)
        context.setStencil(StencilState(true, CompareFunction.EQUAL, clipDepth, pass = StencilOperation.INCREMENT_CLAMP))
        fill(shape, bounds, Solid(Color.WHITE))
        clipDepth++
        context.setColorWrite(ColorWriteMask.ALL)
        clipState()
    }

    private fun restoreClip(depth: Int) {
        require(depth <= clipDepth) { "Cannot restore a clip from a different composition group" }
        val saved = transform
        transform = Transform.IDENTITY
        try {
            while (clipDepth > depth) {
                context.setColorWrite(ColorWriteMask.NONE)
                context.setStencil(StencilState(true, CompareFunction.EQUAL, clipDepth, pass = StencilOperation.DECREMENT_CLAMP))
                fill(Rectangle, fullBounds(), Solid(Color.WHITE))
                clipDepth--
            }
        } finally {
            transform = saved
            context.setColorWrite(ColorWriteMask.ALL)
            clipState()
        }
    }

    private fun image(
        texture: Texture, bounds: Rect, uv: Rect, tint: Color, premultiplied: Boolean, flip: Boolean, sampling: Sampling,
        opacity: Float = 1f, blur: Vec2 = Vec2.ZERO
    ) {
        if (bounds.width <= 0 || bounds.height <= 0) return
        val adjusted = if (flip) Rect(uv.x, 1 - uv.bottom, uv.width, uv.height) else uv
        val key = ImageMeshKey(bounds, adjusted, flip)
        val buffer = meshes[key] ?: run {
            val u0 = adjusted.x
            val u1 = adjusted.right
            val v0 = if (flip) adjusted.bottom else adjusted.y
            val v1 = if (flip) adjusted.y else adjusted.bottom
            cacheMesh(
                key, buildMesh(
                    listOf(
                        Vec2(bounds.x, bounds.y) to Vec2(u0, v0), Vec2(bounds.right, bounds.y) to Vec2(u1, v0),
                        Vec2(bounds.right, bounds.bottom) to Vec2(u1, v1), Vec2(bounds.x, bounds.y) to Vec2(u0, v0),
                        Vec2(bounds.right, bounds.bottom) to Vec2(u1, v1), Vec2(bounds.x, bounds.bottom) to Vec2(u0, v1)
                    )
                )
            )
        }
        prepare(Solid(tint))
        shader.setUniform("paintMode", 3)
        setColor("baseColor", tint, opacity)
        shader.setUniform("imagePremultiplied", premultiplied)
        shader.setUniform("imageTexture", 0)
        shader.setUniform("blurStep", vector.set(blur.x, blur.y))
        bindTexture(texture)
        context.bindSampler(if (sampling == Sampling.LINEAR) linear.value else nearest.value)
        draw(buffer)
    }

    private fun beginGroup(opacity: Float) {
        require(groups.size < 8) { "Composition group depth exceeds eight pooled targets" }
        val parent = currentTarget ?: error("No active UI target")
        groups.addLast(GroupState(parent, SavedState(transform, clipDepth), opacity, stack.size))
        val index = groups.size - 1
        if (groupTargets.size <= index) {
            reserveTarget(parent.width, parent.height, true)
            groupTargets += context.createRenderTarget(parent.width, parent.height, true)
        }
        val next = groupTargets[index]
        if (next.width != parent.width || next.height != parent.height) next.resize(parent.width, parent.height)
        currentTarget = next
        clipDepth = 0
        bind(next)
        clearOwned()
    }

    private fun endGroup() {
        check(groups.isNotEmpty()) { "EndGroup has no matching BeginGroup" }
        val group = groups.removeLast()
        check(stack.size == group.stackDepth) { "Save/Restore cannot cross a group boundary" }
        val content = currentTarget!!
        currentTarget = group.target
        bind(group.target)
        clipDepth = group.state.clipDepth
        clipState()
        transform = Transform.IDENTITY
        image(content.colorTexture, fullBounds(), Rect(0f, 0f, 1f, 1f), Color.WHITE, true, true, Sampling.LINEAR, group.opacity)
        transform = group.state.transform
    }

    private fun shadow(shadow: Shadow) {
        require(shadow.blur.isFinite() && shadow.blur >= 0 && shadow.spread.isFinite() && shadow.spread >= 0)
        val parent = currentTarget!!
        val saved = SavedState(transform, clipDepth)
        while (effectTargets.size < 2) {
            reserveTarget(parent.width, parent.height, false)
            effectTargets += context.createRenderTarget(parent.width, parent.height, false)
        }
        effectTargets.forEach { if (it.width != parent.width || it.height != parent.height) it.resize(parent.width, parent.height) }
        val source = effectTargets[0]
        val temporary = effectTargets[1]
        currentTarget = source
        clipDepth = 0
        bind(source)
        context.clearTarget(0f, 0f, 0f, 0f)
        transform = saved.transform * Transform.translation(shadow.offset.x, shadow.offset.y)
        fill(shadow.shape, shadow.bounds, Solid(Color.WHITE))
        if (shadow.spread > 0) fill(shadow.shape, shadow.bounds, Solid(Color.WHITE), shadow.spread * 2)
        transform = Transform.IDENTITY
        currentTarget = temporary
        bind(temporary)
        context.clearTarget(0f, 0f, 0f, 0f)
        image(
            source.colorTexture, fullBounds(), Rect(0f, 0f, 1f, 1f), Color.WHITE, true, true, Sampling.LINEAR,
            blur = Vec2(shadow.blur / frame!!.logicalSize.width / 4, 0f)
        )
        currentTarget = parent
        bind(parent)
        clipDepth = saved.clipDepth
        clipState()
        image(
            temporary.colorTexture, fullBounds(), Rect(0f, 0f, 1f, 1f), shadow.color, true, true, Sampling.LINEAR,
            blur = Vec2(0f, shadow.blur / frame!!.logicalSize.height / 4)
        )
        transform = saved.transform
    }

    private fun drawText(command: DrawText) {
        require(command.layout.providerId == text.capabilities.providerId) { "Text layout and Arc raster provider do not match" }
        val scale = max(frame!!.pixelsPerDp.x, frame!!.pixelsPerDp.y) * supersampling
        val key = TextKey(command.layout, scale)
        val prepared = texts[key] ?: prepareText(command.layout, scale).also {
            texts[key] = it
            textBytes += it.bytes
            while ((texts.size > 256 || textBytes > 32L * 1024 * 1024) && texts.size > 1) {
                val first = texts.entries.iterator()
                val removed = first.next().value
                removed.tiles.forEach { tile -> tile.texture.cleanup() }
                textBytes -= removed.bytes
                first.remove()
            }
        }
        val old = transform
        transform = old * Transform.translation(command.origin.x, command.origin.y)
        try {
            prepared.tiles.forEach { tile -> image(tile.texture, tile.bounds, Rect(0f, 0f, 1f, 1f), command.color, false, false, Sampling.LINEAR) }
        } finally {
            transform = old
        }
    }

    private fun prepareText(layout: TextLayout, scale: Float): PreparedText {
        data class Raster(val x: Int, val y: Int, val width: Int, val height: Int, val bytes: ByteArray)

        val rasters = layout.runs.flatMap { run ->
            run.glyphs.mapNotNull { glyph ->
                val raster = text.rasterize(run.face, glyph.glyphId, run.fontSize, scale)
                if (raster.width == 0 || raster.height == 0) null else Raster(
                    floor(glyph.x * scale).toInt() + raster.left,
                    floor(glyph.y * scale).toInt() - raster.top, raster.width, raster.height, raster.alpha
                )
            }
        }
        if (rasters.isEmpty()) return PreparedText(emptyList(), 0)
        val left = rasters.minOf { it.x } - 1
        val top = rasters.minOf { it.y } - 1
        val right = rasters.maxOf { it.x + it.width } + 1
        val bottom = rasters.maxOf { it.y + it.height } + 1
        require((right - left).toLong() * (bottom - top) * 4 <= 32L * 1024 * 1024) { "One text layout exceeds its 32 MiB raster budget" }
        val tiles = mutableListOf<TextTile>()
        var byteCount = 0L
        try {
            var y = top
            while (y < bottom) {
                val height = min(1024, bottom - y)
                var x = left
                while (x < right) {
                    val width = min(1024, right - x)
                    val rgba = ByteArray(width * height * 4)
                    rasters.forEach { raster ->
                        val x0 = max(x, raster.x)
                        val y0 = max(y, raster.y)
                        val x1 = min(x + width, raster.x + raster.width)
                        val y1 = min(y + height, raster.y + raster.height)
                        for (py in y0 until y1) for (px in x0 until x1) {
                            val source = raster.bytes[(py - raster.y) * raster.width + px - raster.x].toInt() and 255
                            val index = ((py - y) * width + px - x) * 4
                            val old = rgba[index + 3].toInt() and 255
                            rgba[index] = (-1).toByte()
                            rgba[index + 1] = (-1).toByte()
                            rgba[index + 2] = (-1).toByte()
                            rgba[index + 3] = (source + old * (255 - source) / 255).toByte()
                        }
                    }
                    tiles += TextTile(Rect(x / scale, y / scale, width / scale, height / scale), context.createRgbaTexture(width, height, rgba))
                    uploads += rgba.size
                    byteCount += rgba.size
                    x += width
                }
                y += height
            }
        } catch (failure: Throwable) {
            tiles.forEach { it.texture.cleanup() }
            throw failure
        }
        return PreparedText(tiles, byteCount)
    }

    private fun custom(command: CustomCommand) {
        require(command.operationId.isNotBlank()) { "Custom operation ID must not be blank" }
        command.requiredCapabilities.forEach(capabilities::require)
        val renderer = customRenderers.entries[command.operationId]
            ?: throw UnsupportedOperationException("No Arc renderer registered for '${command.operationId}'")
        customMetricsUnknown = true
        check(customDepth < 16) { "Recursive custom operation depth exceeds sixteen" }
        customDepth++
        val saved = SavedState(transform, clipDepth)
        val savedTarget = currentTarget
        val savedStack = stack.size
        val savedGroups = groups.size
        try {
            context.beginScopedPass(renderer.textureUnits.toSet()).use {
                try {
                    renderer.render(ArcCustomDrawScope(context, frame!!, transform, customResources, {
                        execute(it)
                        flushSolidBatch()
                    }), command)
                    check(stack.size == savedStack && groups.size == savedGroups) { "Custom operation has unbalanced save/group commands" }
                } finally {
                    if (currentTarget === savedTarget && groups.size == savedGroups) restoreClip(saved.clipDepth)
                    transform = saved.transform
                }
            }
        } finally {
            customDepth--
        }
    }

    private fun targetBytes(width: Int, height: Int, stencil: Boolean) =
        Math.multiplyExact(Math.multiplyExact(width.toLong(), height.toLong()), if (stencil) 8L else 4L)

    private fun ownedTargetBytes() =
        (listOfNotNull(target) + groupTargets + effectTargets).sumOf { targetBytes(it.width, it.height, it.depthStencil) }

    private fun reserveTarget(width: Int, height: Int, stencil: Boolean, replacing: RenderTarget? = null) {
        val old = replacing?.let { targetBytes(it.width, it.height, it.depthStencil) } ?: 0
        require(
            ownedTargetBytes() - old + targetBytes(
                width,
                height,
                stencil
            ) <= targetBudgetBytes
        ) { "UI target pool exceeds its $targetBudgetBytes-byte budget; reduce supersampling, effects or group depth" }
    }

    override fun close() {
        checkOwner()
        if (closed) return
        closed = true
        var failure: Throwable? = null
        fun release(action: () -> Unit) {
            try {
                action()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
        }
        release(customResources::close)
        meshes.values.forEach { release(it::cleanup) }
        meshes.clear()
        meshBytes = 0
        cpuGeometry.clear()
        cpuGeometryBytes = 0
        solidParts.clear()
        solidVertices = 0
        texts.values.forEach { it.tiles.forEach { tile -> release(tile.texture::cleanup) } }
        texts.clear()
        target?.let { release(it::cleanup) }
        target = null
        groupTargets.forEach { release(it::cleanup) }
        effectTargets.forEach { release(it::cleanup) }
        groupTargets.clear()
        effectTargets.clear()
        if (nearest.isInitialized()) release(nearest.value::cleanup)
        if (linear.isInitialized()) release(linear.value::cleanup)
        release(shader::cleanup)
        textBytes = 0
        statistics = ArcRenderStatistics()
        renderMetrics = RenderMetrics(0, 0, 0, 0, 0, 0, 0)
        failure?.let { throw it }
    }

    private fun checkOwner() {
        check(Thread.currentThread() === owner) { "Arc rendering requires the session's owning thread" }
    }

    companion object {

        private val VERTEX = """
            #version 410 core
            layout(location = 0) in vec3 position;
            layout(location = 1) in vec2 coordinates;
            layout(location = 2) in vec4 color;
            uniform vec2 viewportSize;
            uniform mat4 uiTransform;
            out vec2 localPoint;
            out vec4 paintColor;

            void main() {
                vec4 p = uiTransform * vec4(position, 1);
                gl_Position = vec4(
                    p.x * 2 / viewportSize.x - 1,
                    1 - p.y * 2 / viewportSize.y,
                    0,
                    1
                );
                localPoint = coordinates;
                paintColor = color;
            }
        """.trimIndent()
        private val FRAGMENT = """
            #version 410 core
            in vec2 localPoint;
            in vec4 paintColor;
            out vec4 result;
            uniform int paintMode;
            uniform vec4 baseColor;
            uniform bool vertexColors;
            uniform vec2 gradientFrom;
            uniform vec2 gradientTo;
            uniform float gradientRadius;
            uniform int stopCount;
            uniform float stopPosition[64];
            uniform vec4 stopColor[64];
            uniform sampler2D imageTexture;
            uniform bool imagePremultiplied;
            uniform vec2 blurStep;
            uniform mat4 brushMapping;
            uniform vec2 brushImageSize;
            uniform vec4 brushSource;
            uniform bool brushFlip;

            vec4 gradient(float t) {
                vec4 c = stopColor[0];

                for (int i = 1; i < stopCount; i++) {
                    if (t <= stopPosition[i]) {
                        float span = stopPosition[i] - stopPosition[i - 1];
                        return mix(
                            stopColor[i - 1],
                            stopColor[i],
                            span > 0 ? clamp((t - stopPosition[i - 1]) / span, 0, 1) : 1
                        );
                    }

                    c = stopColor[i];
                }

                return c;
            }

            void main() {
                if (paintMode == 3 || paintMode == 4) {
                    vec2 uv = localPoint;

                    if (paintMode == 4) {
                        vec2 q = (brushMapping * vec4(localPoint, 0, 1)).xy;
                        vec2 halfPixel = min(vec2(.5) / brushImageSize, brushSource.zw * .5);
                        uv = clamp(
                            brushSource.xy + q / brushImageSize,
                            brushSource.xy + halfPixel,
                            brushSource.xy + brushSource.zw - halfPixel
                        );

                        if (brushFlip)
                            uv.y = 1 - uv.y;
                    }

                    vec4 c = texture(imageTexture, uv);

                    if (dot(blurStep, blurStep) > 0) {
                        c *= 0.2270270270;
                        c += (texture(imageTexture, uv + blurStep) + texture(imageTexture, uv - blurStep)) * 0.1945945946;
                        c += (texture(imageTexture, uv + 2 * blurStep) + texture(imageTexture, uv - 2 * blurStep)) * 0.1216216216;
                        c += (texture(imageTexture, uv + 3 * blurStep) + texture(imageTexture, uv - 3 * blurStep)) * 0.0540540541;
                        c += (texture(imageTexture, uv + 4 * blurStep) + texture(imageTexture, uv - 4 * blurStep)) * 0.0162162162;
                    }

                    result = vec4(
                        (imagePremultiplied ? c.rgb : c.rgb * c.a) * baseColor.rgb * baseColor.a,
                        c.a * baseColor.a
                    );
                    return;
                }

                vec4 c = vertexColors ? paintColor : baseColor;

                if (paintMode == 1) {
                    vec2 d = gradientTo - gradientFrom;
                    float q = dot(d, d);
                    c = gradient(q > 0 ? clamp(dot(localPoint - gradientFrom, d) / q, 0, 1) : 0);
                }

                if (paintMode == 2)
                    c = gradient(clamp(length(localPoint - gradientFrom) / gradientRadius, 0, 1));

                result = vec4(c.rgb * c.a, c.a);
            }
        """.trimIndent()
    }
}
