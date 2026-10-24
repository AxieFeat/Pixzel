package pixzel.demo

import arc.gl.OpenGL
import com.sun.management.ThreadMXBean
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import pixzel.arc.graphics.ArcRenderSession
import pixzel.arc.graphics.ArcUiBackend
import pixzel.arc.text.ArcTextService
import pixzel.graphics.BeginGroup
import pixzel.graphics.Clip
import pixzel.graphics.Color
import pixzel.graphics.Concat
import pixzel.graphics.DrawCommand
import pixzel.graphics.EndGroup
import pixzel.graphics.Fill
import pixzel.graphics.FrameInfo
import pixzel.graphics.Rectangle
import pixzel.graphics.Restore
import pixzel.graphics.RoundedRectangle
import pixzel.graphics.Save
import pixzel.graphics.Shadow
import pixzel.graphics.Solid
import pixzel.graphics.rgba
import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.surface.Transform
import pixzel.surface.Vec2
import java.lang.management.ManagementFactory.getThreadMXBean
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ceil

internal object ArcGraphicsBenchmark {

    private const val WIDTH = 480
    private const val HEIGHT = 320

    fun run(text: ArcTextService) {
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

    private fun colorOf(red: Int, green: Int, blue: Int, alpha: Int = 255) =
        Color((red.toLong() shl 24) or (green.toLong() shl 16) or (blue.toLong() shl 8) or alpha.toLong())

}
