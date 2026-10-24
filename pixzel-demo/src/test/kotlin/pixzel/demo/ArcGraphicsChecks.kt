package pixzel.demo

import arc.ArcObjectProvider
import arc.font.NativeFontProvider
import arc.gl.OpenGL
import arc.graphics.BlendState
import arc.graphics.ColorWriteMask
import arc.graphics.CompareFunction
import arc.graphics.DepthState
import arc.graphics.PixelRect
import arc.graphics.PolygonMode
import arc.graphics.RasterState
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import pixzel.arc.graphics.ArcCustomRenderer
import pixzel.arc.graphics.ArcCustomRenderers
import pixzel.arc.graphics.ArcImageHandle
import pixzel.arc.graphics.ArcRenderSession
import pixzel.arc.graphics.ArcUiBackend
import pixzel.arc.text.ArcTextService
import pixzel.graphics.BeginGroup
import pixzel.graphics.Canvas
import pixzel.graphics.Capability
import pixzel.graphics.Clip
import pixzel.graphics.ClosePath
import pixzel.graphics.Color
import pixzel.graphics.ColorStop
import pixzel.graphics.Concat
import pixzel.graphics.CornerRadii
import pixzel.graphics.CustomCommand
import pixzel.graphics.DrawCommand
import pixzel.graphics.DrawImage
import pixzel.graphics.DrawText
import pixzel.graphics.Ellipse
import pixzel.graphics.EndGroup
import pixzel.graphics.Fill
import pixzel.graphics.FillRule
import pixzel.graphics.FrameInfo
import pixzel.graphics.ImageBrush
import pixzel.graphics.LineTo
import pixzel.graphics.LinearGradient
import pixzel.graphics.MoveTo
import pixzel.graphics.RadialGradient
import pixzel.graphics.Rectangle
import pixzel.graphics.Restore
import pixzel.graphics.RoundedCorners
import pixzel.graphics.RoundedRectangle
import pixzel.graphics.Sampling
import pixzel.graphics.Save
import pixzel.graphics.Shadow
import pixzel.graphics.Solid
import pixzel.graphics.Stroke
import pixzel.graphics.StrokeCap
import pixzel.graphics.StrokeJoin
import pixzel.graphics.VectorPath
import pixzel.graphics.nineSlice
import pixzel.graphics.rgba
import pixzel.layout.unit.Sp
import pixzel.resource.ResourceScope
import pixzel.surface.Insets
import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.surface.Transform
import pixzel.surface.Vec2
import pixzel.text.TextRequest
import pixzel.text.TextStyle
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.IdentityHashMap
import javax.imageio.ImageIO
import kotlin.math.abs

object ArcGraphicsChecks {

    private const val WIDTH = 480
    private const val HEIGHT = 320

    private data class AcceptanceCommand(val bounds: Rect, val fails: Boolean = false) : CustomCommand {

        override val operationId = "acceptance.disc"
        override val requiredCapabilities = setOf(Capability.CUSTOM, Capability.SHAPE_CLIP)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        check(GLFW.glfwInit()) { "GLFW desktop initialization failed" }
        GLFW.glfwDefaultWindowHints()
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 1)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
        val window = GLFW.glfwCreateWindow(WIDTH, HEIGHT, "Pixzel GPU checks", 0, 0)
        check(window != 0L) { "OpenGL4.1 context unavailable" }
        try {
            GLFW.glfwMakeContextCurrent(window)
            GL.createCapabilities()
            ArcObjectProvider.install()
            ArcObjectProvider.bootstrap()
            OpenGL.preload()
            val context = OpenGL.currentRenderContext()
            NativeFontProvider.of().use { fonts ->
                val fontPath = Path.of(System.getProperty("pixzel.fonts", "../assets/fonts"), "NotoSans-Regular.ttf")
                val face = fonts.load("default", Files.readAllBytes(fontPath))
                val text = ArcTextService.of(listOf(face))
                var releasedLeases = 0
                var customProgram = 0
                var customBuffer = 0
                var customTarget = 0
                val leaseScopes = Collections.newSetFromMap(IdentityHashMap<ResourceScope, Boolean>())
                val custom = ArcCustomRenderers.of("acceptance.disc" to ArcCustomRenderer { scope, operation ->
                    if (leaseScopes.add(scope.resources)) scope.resources.own(AutoCloseable {
                        leaseScopes.remove(scope.resources)
                        releasedLeases++
                    })
                    val command = operation as AcceptanceCommand
                    if (command.fails) {
                        scope.context.setViewport(9, 10, 1, 2)
                        scope.context.setDepth(DepthState(true, true, CompareFunction.NEVER))
                        scope.context.setRaster(RasterState(PolygonMode.LINE, discard = true))
                        error("Expected custom renderer failure")
                    }
                    scope.emit(Save)
                    scope.emit(Clip(Ellipse, command.bounds))
                    scope.emit(Fill(Rectangle, command.bounds, Solid(0x2EE6CEFF.rgba)))
                    scope.emit(Restore)
                    customProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                    customBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
                    customTarget = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                })
                val backend = ArcUiBackend.of(context, text, customRenderers = custom)
                val scene = context.createRenderTarget(WIDTH, HEIGHT, true)
                val image = ArcImageHandle.rgba(
                    context, 2, 2, byteArrayOf(
                        -1, 0, 0, -1, 0, -1, 0, -1, 0, 0, -1, -1, -1, -1, -1, -1
                    ), "checker"
                )
                val session = backend.openSession() as ArcRenderSession
                try {
                    context.bindTarget(scene)
                    context.setViewport(3, 4, 123, 117)
                    context.setDepth(DepthState(true, false, CompareFunction.GREATER))
                    context.setColorWrite(ColorWriteMask.ALL)
                    context.setBlend(BlendState.DISABLED)
                    context.clearTarget(.08f, .1f, .16f, 1f, stencil = true)
                    context.setRaster(RasterState(PolygonMode.LINE, true, true, true, true, true, setOf(0)))
                    val framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                    val viewport = IntArray(4).also { GL11.glGetIntegerv(GL11.GL_VIEWPORT, it) }
                    val layout = text.layout(TextRequest("Привет, Pixzel", TextStyle(size = Sp(24f))))
                    val hole = VectorPath(
                        listOf(
                            MoveTo(Vec2(0f, 0f)), LineTo(Vec2(100f, 0f)), LineTo(Vec2(100f, 90f)),
                            LineTo(Vec2(0f, 90f)), ClosePath, MoveTo(Vec2(30f, 25f)), LineTo(Vec2(70f, 25f)),
                            LineTo(Vec2(70f, 65f)), LineTo(Vec2(30f, 65f)), ClosePath
                        ), FillRule.EVEN_ODD
                    )
                    val commands = listOf(
                        Save,
                        Concat(Transform.translation(10f, 10f)),
                        Fill(
                            RoundedRectangle(14f), Rect(0f, 0f, 220f, 68f), LinearGradient(
                                Vec2.ZERO, Vec2(220f, 68f),
                                listOf(ColorStop(0f, 0x355CFFFF.rgba), ColorStop(1f, 0x8F34D9FFL.rgba))
                            )
                        ),
                        DrawText(layout, Vec2(12f, 16f), Color.WHITE),
                        Restore,
                        Save,
                        Concat(Transform.translation(245f, 10f)),
                        BeginGroup(.5f),
                        Fill(Rectangle, Rect(0f, 0f, 90f, 60f), Solid(0xFF0000FFL.rgba)),
                        Fill(Rectangle, Rect(45f, 0f, 90f, 60f), Solid(0x00FF00FF.rgba)),
                        EndGroup,
                        Restore,
                        Save,
                        Concat(Transform.translation(10f, 100f)),
                        Fill(hole, Rect(0f, 0f, 100f, 90f), Solid(0xFFB300FFL.rgba)),
                        Restore,
                        Save,
                        Concat(Transform.translation(130f, 100f)),
                        Clip(Ellipse, Rect(0f, 0f, 100f, 90f)),
                        Fill(
                            Rectangle, Rect(0f, 0f, 100f, 90f), RadialGradient(
                                Vec2(50f, 45f), 60f,
                                listOf(ColorStop(0f, 0xFFFFFFFFL.rgba), ColorStop(1f, 0x006AFFFF.rgba))
                            )
                        ),
                        Restore,
                        DrawImage(image, Rect(0f, 0f, 2f, 2f), Rect(250f, 100f, 120f, 90f), sampling = Sampling.NEAREST),
                        Fill(
                            RoundedCorners(CornerRadii(0f, 26f, 6f, 16f)), Rect(390f, 100f, 70f, 70f),
                            ImageBrush(image, sampling = Sampling.NEAREST, transform = Transform.translation(390f, 100f) * Transform.scale(35f))
                        ),
                        AcceptanceCommand(Rect(405f, 15f, 50f, 50f)),
                        Shadow(RoundedRectangle(15f), Rect(20f, 230f, 160f, 45f), 0x000000B0.rgba, 16f, 3f, Vec2(6f, 8f)),
                        Fill(RoundedRectangle(15f), Rect(20f, 230f, 160f, 45f), Solid(0x27A77AFF.rgba)),
                        Stroke(Ellipse, Rect(220f, 220f, 100f, 60f), Solid(0xFFCC00FFL.rgba), 5f),
                        Save,
                        Concat(Transform.translation(390f, 205f) * Transform.rotation(.35f)),
                        Clip(RoundedRectangle(10f), Rect(0f, 0f, 60f, 70f)),
                        Fill(Rectangle, Rect(0f, 0f, 60f, 70f), Solid(0xF35B82FFL.rgba)),
                        Restore,
                        Stroke(
                            VectorPath(listOf(MoveTo(Vec2(10f, 300f)), LineTo(Vec2(50f, 300f)))),
                            Rect(0f, 290f, 60f, 20f),
                            Solid(Color.WHITE),
                            8f,
                            cap = StrokeCap.BUTT
                        ),
                        Stroke(
                            VectorPath(listOf(MoveTo(Vec2(90f, 300f)), LineTo(Vec2(130f, 300f)))),
                            Rect(80f, 290f, 60f, 20f),
                            Solid(0xF35B82FFL.rgba),
                            8f,
                            cap = StrokeCap.ROUND
                        ),
                        Stroke(
                            VectorPath(listOf(MoveTo(Vec2(170f, 300f)), LineTo(Vec2(210f, 300f)))),
                            Rect(160f, 290f, 60f, 20f),
                            Solid(0x2EE6CEFF.rgba),
                            8f,
                            cap = StrokeCap.SQUARE
                        ),
                        Stroke(
                            VectorPath(listOf(MoveTo(Vec2(250f, 305f)), LineTo(Vec2(270f, 285f)), LineTo(Vec2(290f, 305f)))),
                            Rect(240f, 280f, 60f, 30f),
                            Solid(Color.WHITE),
                            6f,
                            StrokeJoin.MITER,
                            StrokeCap.BUTT,
                            4f
                        ),
                        Stroke(
                            VectorPath(listOf(MoveTo(Vec2(320f, 305f)), LineTo(Vec2(340f, 285f)), LineTo(Vec2(360f, 305f)))),
                            Rect(310f, 280f, 60f, 30f),
                            Solid(Color.WHITE),
                            6f,
                            StrokeJoin.BEVEL,
                            StrokeCap.BUTT
                        ),
                    )
                    val frame = FrameInfo(Size(WIDTH.toFloat(), HEIGHT.toFloat()), Size(WIDTH.toFloat(), HEIGHT.toFloat()), Vec2(1f, 1f))
                    session.submit(frame, commands)
                    check(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == framebuffer) { "Scene target was not restored" }
                    check(viewport.contentEquals(IntArray(4).also { GL11.glGetIntegerv(GL11.GL_VIEWPORT, it) })) { "Scene viewport was not restored" }
                    check(GL11.glGetInteger(GL11.GL_DEPTH_FUNC) == GL11.GL_GREATER && GL11.glIsEnabled(GL11.GL_DEPTH_TEST))
                    check(
                        IntArray(2).also {
                            GL11.glGetIntegerv(
                                GL11.GL_POLYGON_MODE,
                                it
                            )
                        }[0] == GL11.GL_LINE && GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD) &&
                                GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP) && GL11.glIsEnabled(GL30.GL_CLIP_DISTANCE0)
                    ) { "Application raster state was not restored" }
                    check(pixel(60, 145).take(3).zip(listOf(20, 26, 41)).all { (a, b) -> abs(a - b) <= 1 }) { "Even-odd path hole was filled" }
                    check(pixel(132, 102).take(3).zip(listOf(20, 26, 41)).all { (a, b) -> abs(a - b) <= 1 }) { "Ellipse clip leaked outside shape" }
                    val left = pixel(265, 35)
                    val overlap = pixel(310, 35)
                    check(abs(left[0] - 137) <= 2 && abs(left[1] - 13) <= 2) { "Group red opacity is wrong: $left" }
                    check(abs(overlap[0] - 10) <= 2 && abs(overlap[1] - 140) <= 2) { "Overlapping group alpha was multiplied per child: $overlap" }
                    check(pixel(270, 120).take(3) == listOf(255, 0, 0)) { "Image top-left sampling/orientation mismatch" }
                    check(pixel(340, 170).take(3) == listOf(255, 255, 255)) { "Image bottom-right sampling mismatch" }
                    check(pixel(392, 102).take(3) == listOf(255, 0, 0)) { "Image brush mapping or square top-left corner failed" }
                    check(
                        pixel(458, 102).take(3).zip(listOf(20, 26, 41))
                            .all { (a, b) -> abs(a - b) <= 1 }) { "Independent top-right radius did not clip image brush" }
                    check(pixel(430, 40).take(3) == listOf(46, 230, 206)) { "Registered custom operation failed" }
                    check(pixel(7, 300).take(3).zip(listOf(20, 26, 41)).all { (a, b) -> abs(a - b) <= 1 }) { "Butt cap extended past the endpoint" }
                    check(pixel(168, 300).take(3) == listOf(46, 230, 206)) { "Square cap did not extend by half the stroke width" }
                    val first = session.statistics
                    context.setRaster(RasterState())
                    context.setDepth(DepthState())
                    context.setViewport(0, 0, WIDTH, HEIGHT)
                    // Repaint the scene, then resubmit the same retained commands. No layout/raster/mesh work is needed.
                    context.clearTarget(.08f, .1f, .16f, 1f)
                    session.submit(frame, commands)
                    check(session.statistics.uploadedBytes == 0L) { "Static frame uploaded ${session.statistics.uploadedBytes} bytes" }
                    val textInk = (20..210).sumOf { x ->
                        (24..60).count { y ->
                            val p = pixel(x, y)
                            p[0] > 225 && p[1] > 225 && p[2] > 225
                        }
                    }
                    check(textInk > 150) { "Real Cyrillic text did not produce expected ink ($textInk pixels)" }
                    val output = Path.of("build", "reports", "arc-graphics.png")
                    Files.createDirectories(output.parent)
                    writePng(output)
                    val beforeFailure = pixel(60, 145)
                    val failure = runCatching {
                        session.submit(
                            frame,
                            listOf(Save, Clip(Ellipse, Rect(0f, 0f, 80f, 80f)), BeginGroup(.5f), AcceptanceCommand(Rect(0f, 0f, 30f, 30f), true))
                        )
                    }.exceptionOrNull()
                    check(failure?.cause?.message == "Expected custom renderer failure") { "Custom renderer did not report its failure: $failure" }
                    check(
                        GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == framebuffer && pixel(
                            60,
                            145
                        ) == beforeFailure
                    ) { "Failing custom renderer changed the application's target/content" }
                    check(GL11.glGetInteger(GL11.GL_DEPTH_FUNC) == GL11.GL_LESS && !GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) { "Failing nested custom/group pass did not restore application depth" }
                    check(IntArray(2).also {
                        GL11.glGetIntegerv(
                            GL11.GL_POLYGON_MODE,
                            it
                        )
                    }[0] == GL11.GL_FILL && !GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD))
                    session.submit(frame, commands)
                    check(session.statistics.uploadedBytes == 0L) { "Recovery rebuilt retained geometry" }
                    val tiny = FrameInfo(Size(8f, 8f), Size(8f, 8f), Vec2(1f, 1f))
                    val smallCommands = listOf(AcceptanceCommand(Rect(1f, 1f, 6f, 6f)))
                    val start = System.nanoTime()
                    repeat(1000) {
                        val cycle = backend.openSession() as ArcRenderSession
                        cycle.use { it.submit(tiny, smallCommands) }
                        check(customProgram != 0 && customBuffer != 0 && customTarget != 0)
                        check(!GL20.glIsProgram(customProgram) && !GL15.glIsBuffer(customBuffer) && !GL30.glIsFramebuffer(customTarget)) {
                            "Session retained native program/buffer/target after close at cycle $it"
                        }
                        check(cycle.renderMetrics.ownedResources == 0 && cycle.statistics.ownedTargets == 0)
                    }
                    check(releasedLeases == 1000 && leaseScopes.size == 1) { "Custom session resources accumulated across 1000 cycles" }
                    check(GL11.glIsTexture(image.texture.id)) { "Session cleanup deleted a borrowed image" }
                    val limited = ArcUiBackend.of(context, text, targetBudgetBytes = 64)
                    val budgetFailure = runCatching { limited.openSession().use { it.submit(tiny, emptyList()) } }.exceptionOrNull()
                    check(budgetFailure?.message?.contains("target pool") == true) { "Target budget was not enforced: $budgetFailure" }
                    println("Lifecycle: 1000 GPU sessions closed, native program/buffer/FBO deleted each cycle, borrowed image alive, custom leases released, elapsed ${(System.nanoTime() - start) / 1_000_000}ms; target budget failure preserved state")
                    // An explicit next scene operation remains possible on the expected target after UI submission.
                    context.setScissor(PixelRect(0, 0, 1, 1))
                    context.clearTarget(1f, 1f, 0f, 1f)
                    context.setScissor(null)
                    check(pixel(0, HEIGHT - 1).take(3) == listOf(255, 255, 0)) { "Next scene draw used the wrong target/state" }
                    check(GL11.glGetError() == GL11.GL_NO_ERROR) { "Arc/Pixzel rendering left an OpenGL error" }
                    println("PASS: real Cyrillic text, path hole, transformed/independent rounded clips, images/brush, stroke caps/joins, custom command/error, gradient, group alpha, shadow, scene restoration, static uploads=0")
                    println("Initial $first; static ${session.statistics}; preview=${output.toAbsolutePath()}")
                } finally {
                    session.close()
                    check(leaseScopes.isEmpty())
                    image.close()
                    context.bindTarget(null)
                    scene.cleanup()
                }
                ArcContextGenerationChecks.run(window, text)
                batching(text)
                fractionalScale(text)
                if ("--benchmark" in args) ArcGraphicsBenchmark.run(text)
            }
        } finally {
            GLFW.glfwDestroyWindow(window)
            GLFW.glfwTerminate()
        }
    }

    /**
     * Two fresh, unshared contexts force the same native object names to refer to different resources.
     */
    private fun batching(text: ArcTextService) {
        val context = OpenGL.currentRenderContext()
        val backend = ArcUiBackend.of(context, text, supersampling = 1)
        val scene = context.createRenderTarget(WIDTH, HEIGHT, true)
        val session = backend.openSession() as ArcRenderSession
        val frame = FrameInfo(Size(WIDTH.toFloat(), HEIGHT.toFloat()), Size(WIDTH.toFloat(), HEIGHT.toFloat()), Vec2(1f, 1f))
        val commands = buildList<DrawCommand> {
            repeat(1000) { index ->
                add(Save)
                add(Concat(Transform.translation((index % 40) * 12f, (index / 40) * 12f)))
                add(Fill(Rectangle, Rect(0f, 0f, 12f, 12f), Solid(colorOf((index * 17) % 256, (index * 31) % 256, (index * 47) % 256))))
                add(Restore)
            }
            add(Fill(Rectangle, Rect(0f, 300f, 16f, 16f), Solid(colorOf(255, 0, 0, 128))))
            add(Fill(Rectangle, Rect(8f, 300f, 16f, 16f), Solid(colorOf(0, 0, 255, 128))))
        }

        fun clear() {
            context.bindTarget(scene)
            context.setRaster(RasterState())
            context.setColorWrite(ColorWriteMask.ALL)
            context.setScissor(null)
            context.clearTarget(0f, 0f, 0f, 1f)
        }

        fun pixels(): ByteArray {
            val bytes = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4)
            GL11.glReadPixels(0, 0, WIDTH, HEIGHT, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes)
            return ByteArray(bytes.capacity()).also { bytes.get(it) }
        }
        try {
            clear()
            session.submit(frame, commands)
            val batchStats = session.statistics
            check(batchStats.drawCalls == 2) { "1002 compatible solid commands were not batched: $batchStats" }
            val batched = pixels()
            check(pixel(18, 6).take(3) == listOf(17, 31, 47))
            check(pixel(10, 306).take(3).zip(listOf(64, 0, 128)).all { (a, b) -> abs(a - b) <= 1 }) { "Batch reordered translucent primitives" }
            clear()
            session.submit(frame, commands)
            check(session.statistics.uploadedBytes == 0L) { "Static solid batch reuploaded vertices" }
            val emptyText = DrawText(text.layout(TextRequest("")), Vec2.ZERO, Color.WHITE)
            val unbatched = commands.flatMap { if (it is Fill) listOf(it, emptyText) else listOf(it) }
            clear()
            session.submit(frame, unbatched)
            check(session.statistics.drawCalls == 1003)
            check(batched.contentEquals(pixels())) { "Batched and separate primitive submissions produced different pixels" }
            val pressure = ArcUiBackend.of(context, text, supersampling = 1, meshBudgetBytes = 512).openSession() as ArcRenderSession
            pressure.use {
                var last = listOf<DrawCommand>()
                repeat(100) { index ->
                    last = listOf(
                        Concat(Transform.translation(index.toFloat(), 0f)),
                        Fill(Rectangle, Rect(0f, 0f, 10f, 10f), Solid(colorOf(index, 0, 0)))
                    )
                    clear()
                    it.submit(frame, last)
                    check(it.statistics.meshBytes <= 512 && it.statistics.cpuGeometryBytes <= 512) { "Mesh byte budget was exceeded" }
                }
                clear()
                it.submit(frame, last)
                check(it.statistics.uploadedBytes == 0L)
            }
            println("PASS: 1002 transformed/multicolor/translucent solid commands -> ${batchStats.drawCalls} draws, pixel-identical to1003 separate draws, static uploads0; mesh/CPU byte-budget eviction verified")
        } finally {
            session.close()
            context.bindTarget(null)
            scene.cleanup()
        }
    }

    private fun colorOf(red: Int, green: Int, blue: Int, alpha: Int = 255) =
        Color((red.toLong() shl 24) or (green.toLong() shl 16) or (blue.toLong() shl 8) or alpha.toLong())

    private fun fractionalScale(text: ArcTextService) {
        val context = OpenGL.currentRenderContext()
        val scene = context.createRenderTarget(80, 80, true)
        val colors = listOf(0xFF0000FFL, 0xFFFFFFFFL, 0x00FF00FFL, 0xFFFFFFFFL, 0xFFFFFFFFL, 0xFFFFFFFFL, 0x0000FFFFL, 0xFFFFFFFFL, 0xFFFF00FFL)
        val bytes = ByteArray(36)
        colors.forEachIndexed { index, rgba -> for (channel in 0..3) bytes[index * 4 + channel] = ((rgba ushr (24 - channel * 8)) and 255).toByte() }
        val image = ArcImageHandle.rgba(context, 3, 3, bytes, "fractional-nine-slice")
        val session = ArcUiBackend.of(context, text).openSession()
        try {
            context.bindTarget(scene)
            context.setViewport(0, 0, 80, 80)
            context.setRaster(RasterState())
            context.setColorWrite(ColorWriteMask.ALL)
            context.setScissor(null)
            context.clearTarget(0f, 0f, 0f, 1f, stencil = true)
            val commands = mutableListOf<DrawCommand>()
            val canvas = object : Canvas {
                override fun emit(command: DrawCommand) {
                    commands += command
                }
            }
            canvas.nineSlice(image, Rect(0f, 0f, 4f, 4f), Insets(1f, 1f, 1f, 1f), Insets(3f, 3f, 3f, 3f), sampling = Sampling.NEAREST)
            commands += DrawImage(image, Rect(2f, 0f, 1f, 1f), Rect(8f, 0f, 8f, 8f), colorOf(128, 64, 255, 128), Sampling.NEAREST)
            commands += Stroke(Rectangle, Rect(8f, 16f, 32f, 24f), Solid(Color.WHITE), .8f, StrokeJoin.MITER)
            commands += Save
            repeat(5) { index -> commands += Clip(RoundedRectangle(2f), Rect(44f + index, 16f + index, 18f - index * 2, 40f - index * 2)) }
            commands += Fill(Rectangle, Rect(44f, 16f, 20f, 40f), Solid(0x00FFFFFF.rgba))
            commands += Restore
            session.submit(FrameInfo(Size(64f, 64f), Size(80f, 80f), Vec2(1.25f, 1.25f)), commands)
            fun sample(x: Int, y: Int): List<Int> {
                val buffer = ByteBuffer.allocateDirect(4)
                GL11.glReadPixels(x, 79 - y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer)
                return (0..2).map { buffer[it].toInt() and 255 }
            }
            check(
                sample(0, 0) == listOf(255, 0, 0) && sample(4, 0) == listOf(0, 255, 0) &&
                        sample(0, 4) == listOf(0, 0, 255) && sample(4, 4) == listOf(255, 255, 0)
            ) { "Undersized nine-slice did not shrink its borders proportionally" }
            check(sample(15, 5).zip(listOf(0, 32, 0)).all { (a, b) -> abs(a - b) <= 1 }) { "Subregion sprite tint/alpha was not applied once" }
            check(maxOf(sample(9, 25)[0], sample(10, 25)[0]) > 64 && sample(7, 25) == listOf(0, 0, 0) && sample(13, 25) == listOf(0, 0, 0)) {
                "A one-physical-pixel border vanished or widened at scale1.25"
            }
            check(
                sample(68, 40) == listOf(0, 255, 255) && sample(56, 30) == listOf(
                    0,
                    0,
                    0
                )
            ) { "Five nested shape clips leaked or removed interior coverage" }
            writePng(Path.of("build", "reports", "arc-graphics-scale125.png"), 80, 80)
            check(GL11.glGetError() == GL11.GL_NO_ERROR)
            println("PASS: scale1.25 one-pixel border, undersized nine-slice corners, cropped sprite tint/alpha, five nested rounded clip pixels")
        } finally {
            session.close()
            image.close()
            context.bindTarget(null)
            scene.cleanup()
        }
    }

    private fun pixel(x: Int, y: Int): List<Int> {
        val bytes = ByteBuffer.allocateDirect(4)
        GL11.glReadPixels(x, HEIGHT - y - 1, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes)
        return (0..3).map { bytes[it].toInt() and 255 }
    }

    private fun writePng(path: Path, width: Int = WIDTH, height: Int = HEIGHT) {
        val bytes = ByteBuffer.allocateDirect(width * height * 4)
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) for (x in 0 until width) {
            val i = ((height - y - 1) * width + x) * 4
            image.setRGB(
                x, y, ((bytes[i + 3].toInt() and 255) shl 24) or ((bytes[i].toInt() and 255) shl 16) or
                        ((bytes[i + 1].toInt() and 255) shl 8) or (bytes[i + 2].toInt() and 255)
            )
        }
        ImageIO.write(image, "png", path.toFile())
    }
}
