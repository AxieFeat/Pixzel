package pixzel.demo

import arc.asset.RuntimeAsset
import arc.gl.OpenGL
import arc.graphics.DrawerMode
import arc.graphics.TextureSampling
import arc.graphics.vertex.VertexArrayBuffer
import arc.graphics.vertex.VertexFormat
import arc.graphics.vertex.VertexFormatElement
import arc.shader.ShaderInstance
import arc.shader.UniformBuffer
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL33
import pixzel.arc.graphics.ArcImageHandle
import pixzel.arc.graphics.ArcRenderSession
import pixzel.arc.graphics.ArcUiBackend
import pixzel.arc.text.ArcTextService
import pixzel.graphics.Color
import pixzel.graphics.DrawImage
import pixzel.graphics.DrawText
import pixzel.graphics.FrameInfo
import pixzel.graphics.Sampling
import pixzel.graphics.rgba
import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.surface.Vec2
import pixzel.text.TextRequest
import java.nio.ByteBuffer

internal object ArcContextGenerationChecks {

    fun run(originalWindow: Long, text: ArcTextService) {
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

}
