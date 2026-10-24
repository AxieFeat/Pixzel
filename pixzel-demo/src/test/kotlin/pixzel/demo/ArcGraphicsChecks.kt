package pixzel.demo

import arc.ArcObjectProvider
import arc.asset.RuntimeAsset
import arc.font.NativeFontProvider
import arc.gl.OpenGL
import arc.graphics.BlendState
import arc.graphics.ColorWriteMask
import arc.graphics.CompareFunction
import arc.graphics.DepthState
import arc.graphics.DrawerMode
import arc.graphics.PixelRect
import arc.graphics.PolygonMode
import arc.graphics.RasterState
import arc.graphics.TextureSampling
import arc.graphics.vertex.VertexArrayBuffer
import arc.graphics.vertex.VertexFormat
import arc.graphics.vertex.VertexFormatElement
import arc.shader.ShaderInstance
import arc.shader.UniformBuffer
import com.sun.management.ThreadMXBean
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL33
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
import java.lang.management.ManagementFactory.getThreadMXBean
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.IdentityHashMap
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.ceil

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
                contextGeneration(window, text)
                batching(text)
                fractionalScale(text)
                if ("--benchmark" in args) performance(text)
            }
        } finally {
            GLFW.glfwDestroyWindow(window)
            GLFW.glfwTerminate()
        }
    }

    /** Two fresh, unshared contexts force the same native object names to refer to different resources. */
    private fun contextGeneration(originalWindow: Long, text: ArcTextService) {
        val originalCapabilities = GL.getCapabilities()
        val first = GLFW.glfwCreateWindow(16, 16, "Pixzel old context", 0, 0)
        val second = GLFW.glfwCreateWindow(16, 16, "Pixzel replacement context", 0, 0)
        check(first != 0L && second != 0L)
        try {
            GLFW.glfwMakeContextCurrent(first)
            GL.createCapabilities()
            val context = OpenGL.currentRenderContext()
            val generation = context.resourceGeneration
            val red = ArcImageHandle.rgba(context, 1, 1, byteArrayOf(-1, 0, 0, -1), "restorable-red", intrinsicSize = Size(4f, 3f))
            check(red.size == Size(1f, 1f) && red.intrinsicSize == Size(4f, 3f))
            val oldRedId = red.texture.id
            val oldBorrowedTexture = context.createRgbaTexture(1, 1, byteArrayOf(0, 0, -1, -1))
            val borrowed = ArcImageHandle.borrowed(oldBorrowedTexture, Size(1f, 1f), "rebound-blue")
            val oldTarget = context.createRenderTarget(16, 16, true)
            val oldTargetTexture = oldTarget.colorTexture.id
            context.bindTarget(oldTarget)
            val oldFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
            val oldRenderbuffer = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
            )
            val format = VertexFormat.builder().add(VertexFormatElement.POSITION).build()
            val oldArray = VertexArrayBuffer.of(DrawerMode.TRIANGLES, format, ByteBuffer.allocateDirect(36), 3)
            val oldArrayId = oldArray.bindingId
            val oldBufferId = oldArray.id
            val oldUniform = UniformBuffer.of(16)
            val oldUniformId = oldUniform.id
            val oldShader = ShaderInstance.of(
                RuntimeAsset.from("#version 410 core\nlayout(location=0) in vec3 p;void main(){gl_Position=vec4(p,1);}"),
                RuntimeAsset.from("#version 410 core\nout vec4 c;void main(){c=vec4(1);}")
            )
            oldShader.compileShaders()
            val oldProgramId = oldShader.id
            val oldSampler = context.createSampler(TextureSampling.LINEAR)
            context.bindSampler(oldSampler)
            val oldSamplerId = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING)
            context.bindSampler(null)
            val backend = ArcUiBackend.of(context, text, supersampling = 1)
            val staleSession = backend.openSession()
            val frame = FrameInfo(Size(16f, 16f), Size(16f, 16f), Vec2(1f, 1f))
            val redCommands = listOf(
                DrawImage(red, Rect(0f, 0f, 1f, 1f), Rect(0f, 0f, 8f, 16f), sampling = Sampling.NEAREST),
                DrawText(text.layout(TextRequest("A")), Vec2.ZERO, Color.WHITE)
            )
            staleSession.submit(frame, redCommands)
            val stalePass = context.beginScopedPass(setOf(0))
            GLFW.glfwMakeContextCurrent(second)
            val replacementCapabilities = GL.createCapabilities()
            check(context.resourceGeneration != generation && backend.resourceGeneration == context.resourceGeneration)
            context.setViewport(0, 0, 16, 16)
            check(runCatching { stalePass.close() }.exceptionOrNull()?.message?.contains("context changed") == true)
            stalePass.close()
            check(IntArray(4).also { GL11.glGetIntegerv(GL11.GL_VIEWPORT, it) }.contentEquals(intArrayOf(0, 0, 16, 16)))
            check(runCatching { staleSession.submit(frame, redCommands) }.exceptionOrNull()?.message?.contains("stale or missing") == true)

            // Realize every object type with names covering all explicitly captured old handles. These are
            // intentionally different objects despite the numeric ID collisions across the two contexts.
            val count = 64
            val textures = IntArray(count) { GL11.glGenTextures().also { GL11.glBindTexture(GL11.GL_TEXTURE_2D, it) } }
            val buffers = IntArray(count) { GL15.glGenBuffers().also { GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, it) } }
            val arrays = IntArray(count) { GL30.glGenVertexArrays().also { GL30.glBindVertexArray(it) } }
            val programs = IntArray(count) { GL20.glCreateProgram() }
            val samplers = IntArray(count) { GL33.glGenSamplers().also { GL33.glBindSampler(0, it) } }
            val framebuffers = IntArray(count) { GL30.glGenFramebuffers().also { GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, it) } }
            val renderbuffers = IntArray(count) { GL30.glGenRenderbuffers().also { GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, it) } }
            check(oldRedId in textures && oldBorrowedTexture.id in textures && oldTargetTexture in textures && oldSamplerId in samplers)
            check(oldBufferId in buffers && oldUniformId in buffers && oldArrayId in arrays && oldProgramId in programs)
            check(oldFramebuffer in framebuffers && oldRenderbuffer in renderbuffers)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0)
            GL30.glBindVertexArray(0)
            GL33.glBindSampler(0, 0)
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0)
            fun replacementObjectsSurvive() {
                check(textures.all(GL11::glIsTexture) && buffers.all(GL15::glIsBuffer) && arrays.all(GL30::glIsVertexArray))
                check(programs.all(GL20::glIsProgram) && samplers.all(GL33::glIsSampler))
                check(framebuffers.all(GL30::glIsFramebuffer) && renderbuffers.all(GL30::glIsRenderbuffer))
            }
            staleSession.close()
            oldArray.cleanup()
            oldUniform.cleanup()
            oldShader.cleanup()
            oldSampler.cleanup()
            oldTarget.cleanup()
            oldBorrowedTexture.cleanup()
            replacementObjectsSurvive()

            val freshTarget = context.createRenderTarget(16, 16, true)
            context.bindTarget(freshTarget)
            context.setViewport(0, 0, 16, 16)
            val freshSession = backend.openSession() as ArcRenderSession
            val oldImageGeneration = red.generation
            freshSession.submit(frame, listOf(redCommands.first()))
            check(red.generation != oldImageGeneration && red.texture.resourceGeneration == context.resourceGeneration)
            check(pixel16(4, 8).take(3) == listOf(255, 0, 0)) { "Owned image did not restore its retained source bytes" }
            replacementObjectsSurvive()
            val staleBorrowedFailure = runCatching {
                freshSession.submit(
                    frame, listOf(
                        DrawImage(borrowed, Rect(0f, 0f, 1f, 1f), Rect(8f, 0f, 8f, 16f))
                    )
                )
            }.exceptionOrNull()
            check(staleBorrowedFailure?.cause?.message?.contains("rebind its replacement") == true)
            check(runCatching {
                borrowed.rebind(
                    oldBorrowedTexture,
                    context = context
                )
            }.exceptionOrNull()?.message?.contains("stale context generation") == true)
            val replacementBlue = context.createRgbaTexture(1, 1, byteArrayOf(0, 0, -1, -1))
            borrowed.rebind(replacementBlue, context = context)
            freshSession.submit(frame, listOf(redCommands.first(), DrawImage(borrowed, Rect(0f, 0f, 1f, 1f), Rect(8f, 0f, 8f, 16f))))
            check(pixel16(4, 8).take(3) == listOf(255, 0, 0) && pixel16(12, 8).take(3) == listOf(0, 0, 255))
            freshSession.submit(frame.copy(framebufferSize = Size.ZERO), emptyList())
            check(freshSession.statistics.drawCalls == 0 && freshSession.statistics.uploadedBytes == 0L && freshSession.renderMetrics.vertices == 0)
            check(GL11.glGetError() == GL11.GL_NO_ERROR) { "Context replacement rendering left a graphics error" }

            val restoredRedId = red.texture.id
            val blueId = replacementBlue.id
            GLFW.glfwMakeContextCurrent(0)
            GL.setCapabilities(null)
            check(context.resourceGeneration == 0L && backend.resourceGeneration == 0L)
            // Any accidental native call now fails in LWJGL because no capabilities are installed.
            freshSession.close()
            freshTarget.cleanup()
            red.close()
            borrowed.close()
            replacementBlue.cleanup()
            GLFW.glfwMakeContextCurrent(second)
            GL.setCapabilities(replacementCapabilities)
            replacementObjectsSurvive()
            check(GL11.glIsTexture(restoredRedId) && GL11.glIsTexture(blueId)) { "No-current cleanup issued native deletes" }
            textures.forEach(GL11::glDeleteTextures)
            buffers.forEach(GL15::glDeleteBuffers)
            arrays.forEach(GL30::glDeleteVertexArrays)
            programs.forEach(GL20::glDeleteProgram)
            samplers.forEach(GL33::glDeleteSamplers)
            framebuffers.forEach(GL30::glDeleteFramebuffers)
            renderbuffers.forEach(GL30::glDeleteRenderbuffers)
            check(GL11.glGetError() == GL11.GL_NO_ERROR)
            println("PASS: two unshared context generations, colliding texture/buffer/VAO/program/sampler/FBO/renderbuffer IDs survive stale cleanup; owned image restores, borrowed image requires rebind, no-current cleanup makes no native calls")
        } finally {
            GLFW.glfwMakeContextCurrent(originalWindow)
            GL.setCapabilities(originalCapabilities)
            GLFW.glfwDestroyWindow(first)
            GLFW.glfwDestroyWindow(second)
        }
    }

    private fun pixel16(x: Int, y: Int): List<Int> {
        val bytes = ByteBuffer.allocateDirect(4)
        GL11.glReadPixels(x, 15 - y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes)
        return (0..3).map { bytes[it].toInt() and 255 }
    }

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

    private fun performance(text: ArcTextService) {
        val context = OpenGL.currentRenderContext()
        val width = 1920
        val height = 1080
        GLFW.glfwSwapInterval(0)
        val backend = ArcUiBackend.of(context, text)
        val scene = context.createRenderTarget(width, height, true)
        val session = backend.openSession() as ArcRenderSession
        val frame = FrameInfo(Size(width.toFloat(), height.toFloat()), Size(width.toFloat(), height.toFloat()), Vec2(1f, 1f))
        val commands = buildList<DrawCommand> {
            add(Save)
            add(Concat(Transform.scale(width / 480f, height / 320f)))
            repeat(5) { index ->
                add(Save)
                add(Clip(RoundedRectangle(8f + index), Rect(4f + index * 3, 4f + index * 3, WIDTH - 8f - index * 6, HEIGHT - 8f - index * 6)))
            }
            add(BeginGroup(.72f))
            add(Shadow(RoundedRectangle(12f), Rect(25f, 25f, 420f, 265f), 0x000000B0.rgba, 12f, 2f, Vec2(3f, 4f)))
            repeat(1000) { index ->
                add(Save)
                add(Concat(Transform.translation(35f + (index % 40) * 10f, 35f + (index / 40) * 10f)))
                add(Fill(Rectangle, Rect(0f, 0f, 9f, 9f), Solid(colorOf((index * 17) % 256, (index * 31) % 256, (index * 47) % 256))))
                add(Restore)
            }
            add(EndGroup)
            repeat(5) { add(Restore) }
            add(Restore)
        }
        val warmup = 100
        val samples = 300
        val bean = (getThreadMXBean() as? ThreadMXBean)
            ?.takeIf { it.isThreadAllocatedMemorySupported }
        bean?.let { if (!it.isThreadAllocatedMemoryEnabled) it.isThreadAllocatedMemoryEnabled = true }
        val thread = Thread.currentThread().threadId()
        val elapsed = LongArray(samples)
        val allocated = LongArray(samples)
        var peakTargets = 0L
        var peakMeshes = 0L
        var peakText = 0L
        var peakCpuGeometry = 0L
        var peakResources = 0
        fun submit() {
            context.bindTarget(scene)
            context.clearTarget(.05f, .06f, .08f, 1f)
            session.submit(frame, commands)
        }
        try {
            repeat(warmup) { submit() }
            GL11.glFinish()
            repeat(samples) { index ->
                context.bindTarget(scene)
                context.clearTarget(.05f, .06f, .08f, 1f)
                val before = bean?.getThreadAllocatedBytes(thread) ?: 0
                val start = System.nanoTime()
                session.submit(frame, commands)
                elapsed[index] = System.nanoTime() - start
                allocated[index] = (bean?.getThreadAllocatedBytes(thread) ?: 0) - before
                val stats = session.statistics
                check(stats.uploadedBytes == 0L) { "Warmed retained clip/effect scene uploaded geometry" }
                peakTargets = maxOf(peakTargets, stats.ownedTargetBytes)
                peakMeshes = maxOf(peakMeshes, stats.meshBytes)
                peakText = maxOf(peakText, stats.textBytes)
                peakCpuGeometry = maxOf(peakCpuGeometry, stats.cpuGeometryBytes)
                peakResources = maxOf(peakResources, session.renderMetrics.ownedResources ?: 0)
            }
            GL11.glFinish()
            elapsed.sort()
            allocated.sort()
            fun percentile(values: LongArray, p: Double) = values[(ceil(values.size * p).toInt() - 1).coerceIn(values.indices)]
            val p50 = percentile(elapsed, .50) / 1_000_000.0
            val p95 = percentile(elapsed, .95) / 1_000_000.0
            val p99 = percentile(elapsed, .99) / 1_000_000.0
            val report = """
                # Arc native clip/effect submission benchmark

                GPU: ${GL11.glGetString(GL11.GL_RENDERER)}; OpenGL: ${GL11.glGetString(GL11.GL_VERSION)}; vendor: ${GL11.glGetString(GL11.GL_VENDOR)}.
                JVM: ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}; OS: ${System.getProperty("os.name")} ${
                System.getProperty(
                    "os.arch"
                )
            }; processors: ${Runtime.getRuntime().availableProcessors()}.
                Hidden GLFW OpenGL 4.1 core context; vsync 0; offscreen application target/logical/framebuffer $width x $height; scale 1; supersampling 2; RGBA8/depth24stencil8; configured pool 512 MiB/mesh 32 MiB.

                Scene: five nested rounded stencil clips, isolated opacity group 0.72, two-pass blurred/spread shadow, 1000 transformed multicolor rectangles. Warmup $warmup frames; measured $samples retained frames. CPU timing surrounds RenderSession.submit only, excluding application clear, event polling, swap/present, report construction and final glFinish. Driver backpressure during submit is included. No timer-query GPU duration is claimed.

                | CPU submit | p50 | p95 | p99 |
                |---|---:|---:|---:|
                | milliseconds | $p50 | $p95 | $p99 |
                | JVM bytes allocated | ${
                if (bean == null) "unavailable" else percentile(
                    allocated,
                    .50
                )
            } | ${if (bean == null) "unavailable" else percentile(allocated, .95)} | ${
                if (bean == null) "unavailable" else percentile(
                    allocated,
                    .99
                )
            } |

                Last frame: ${session.statistics}. Independent metrics: ${session.renderMetrics}.
                Tracked high-water target bytes $peakTargets; mesh bytes $peakMeshes; text bytes $peakText; total GPU storage estimate ${peakTargets + peakMeshes + peakText}; CPU geometry-array bytes $peakCpuGeometry; owned logical resources $peakResources.
                Storage estimates count requested attachment/VBO/texture bytes and exclude driver padding, shaders, borrowed application scene/resources and native font memory. Native allocation counts and driver/device total GPU memory are unavailable. Static upload bytes were 0 throughout all $samples measured frames. This is a renderer submission benchmark; layout/recomposition/input performance belongs to the independent UI benchmarks.
            """.trimIndent() + "\n"
            val path = Path.of("build", "reports", "arc-graphics-performance.md")
            Files.createDirectories(path.parent)
            Files.writeString(path, report)
            println("GPU scene CPU submit warmup$warmup/samples$samples p50=$p50 ms p95=$p95 ms p99=$p99 ms; draws=${session.statistics.drawCalls}; static uploads0; tracked GPU bytes=${peakTargets + peakMeshes + peakText}; report=${path.toAbsolutePath()}")
        } finally {
            session.close()
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
