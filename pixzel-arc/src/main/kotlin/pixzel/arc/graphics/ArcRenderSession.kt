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
import org.joml.Vector2f
import pixzel.arc.text.ArcTextService
import pixzel.graphics.BackendCapabilities
import pixzel.graphics.BeginGroup
import pixzel.graphics.Brush
import pixzel.graphics.Clip
import pixzel.graphics.Color
import pixzel.graphics.Concat
import pixzel.graphics.CustomCommand
import pixzel.graphics.DrawCommand
import pixzel.graphics.DrawImage
import pixzel.graphics.DrawText
import pixzel.graphics.EndGroup
import pixzel.graphics.Fill
import pixzel.graphics.FrameInfo
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
import pixzel.graphics.group
import pixzel.graphics.saved
import pixzel.resource.ResourceScope
import pixzel.surface.Rect
import pixzel.surface.Transform
import pixzel.surface.Vec2
import kotlin.math.ceil
import kotlin.math.max

private data class SavedState(val transform: Transform, val clipDepth: Int)
private data class GroupState(val target: RenderTarget, val state: SavedState, val opacity: Float, val stackDepth: Int)
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
    private val material: ArcMaterial
    private val format: VertexFormat
    private val coloredFormat: VertexFormat
    private val nearest = lazy { context.createSampler(TextureSampling.NEAREST) }
    private val linear = lazy { context.createSampler(TextureSampling.LINEAR) }
    private val vector = Vector2f()

    private val meshes = ArcGeometryCache(meshBudgetBytes)
    private val solidParts = mutableListOf<SolidPart>()
    private var solidVertices = 0
    private val textCache = ArcTextCache(context, text) { uploads += it }

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
            shader = ShaderInstance.of(RuntimeAsset.from(ArcMaterial.VERTEX), RuntimeAsset.from(ArcMaterial.FRAGMENT), ShaderSettings.of())
            try {
                shader.compileShaders()
                material = ArcMaterial(context, shader) { texture, sampling ->
                    bindTexture(texture)
                    context.bindSampler(if (sampling == Sampling.LINEAR) linear.value else nearest.value)
                }
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
                textCache.textBytes,
                ownedTargetBytes(),
                meshes.meshBytes,
                meshes.cpuGeometryBytes
            )
            renderMetrics = RenderMetrics(
                if (customMetricsUnknown) null else draws, if (customMetricsUnknown) null else draws,
                if (customMetricsUnknown) null else vertices, if (customMetricsUnknown) null else uploads,
                if (customMetricsUnknown) null else textureSwitches, if (customMetricsUnknown) null else statistics.ownedTargets,
                if (customRenderers.entries.isEmpty()) 1 + (if (nearest.isInitialized()) 1 else 0) + (if (linear.isInitialized()) 1 else 0) +
                        meshes.size + textCache.tileCount + statistics.ownedTargets else null
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
        val points = meshes.points(key)
        if (points.isEmpty()) return null
        val vertices = (points.indices step 2).map { Vec2(points[it], points[it + 1]) }
        return meshes.put(key, buildMesh(vertices.map { it to it }))
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
        // Only adjacent compatible solids are batched; every clip/material/target change flushes them.
        if (brush !is Solid) {
            flushSolidBatch()
            fill(shape, bounds, brush, stroke, join, cap, miterLimit)
            return
        }
        val key = GeometryKey(shape, bounds, stroke, tolerance(), join, cap, miterLimit)
        val points = meshes.points(key)
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
                meshes.put(key, gpu)
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

    private fun prepare(brush: Brush) = material.prepare(brush, frame!!.logicalSize, transform)

    private fun setColor(name: String, color: Color, opacity: Float = 1f) = material.setColor(name, color, opacity)

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
            // Remove deeper stencil levels over the whole target, independent of the current transform.
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
            meshes.put(
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
        val scale = max(frame!!.pixelsPerDp.x, frame!!.pixelsPerDp.y) * supersampling
        val prepared = textCache.get(command.layout, scale)
        val old = transform
        transform = old * Transform.translation(command.origin.x, command.origin.y)
        try {
            prepared.tiles.forEach { tile -> image(tile.texture, tile.bounds, Rect(0f, 0f, 1f, 1f), command.color, false, false, Sampling.LINEAR) }
        } finally {
            transform = old
        }
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
        release(meshes::close)
        solidParts.clear()
        solidVertices = 0
        release(textCache::close)
        target?.let { release(it::cleanup) }
        target = null
        groupTargets.forEach { release(it::cleanup) }
        effectTargets.forEach { release(it::cleanup) }
        groupTargets.clear()
        effectTargets.clear()
        if (nearest.isInitialized()) release(nearest.value::cleanup)
        if (linear.isInitialized()) release(linear.value::cleanup)
        release(shader::cleanup)
        statistics = ArcRenderStatistics()
        renderMetrics = RenderMetrics(0, 0, 0, 0, 0, 0, 0)
        failure?.let { throw it }
    }

    private fun checkOwner() {
        check(Thread.currentThread() === owner) { "Arc rendering requires the session's owning thread" }
    }
}
