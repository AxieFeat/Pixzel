@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import org.lwjgl.opengl.GL11
import pixzel.Ui
import pixzel.arc.graphics.ArcRenderSession
import pixzel.graphics.RenderSession
import pixzel.graphics.UiBackend
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * Demo-only measurement of completed GPU frames, excluding startup and framebuffer readback.
 */
internal class MenuProfile(private val ui: Ui, private val backend: MenuBackend) {

    private val samples = mutableListOf<Double>()

    fun frame(start: Long, index: Int) {
        GL11.glFinish()
        if (index >= 60) samples += (System.nanoTime() - start) / 1_000_000.0
    }

    fun finish(scenario: String) {
        if (samples.isEmpty()) return
        val sorted = samples.sorted()
        val render = backend.session.statistics
        val metrics = ui.host.metrics
        val report = """
            |Resolution: ${metrics.framebufferSize}; scenario: $scenario
            |Completed UI frame, warmup 60 frames, samples ${samples.size}:
            |median=${format(sorted[sorted.size / 2])} ms; p95=${format(sorted[(sorted.size * .95).toInt().coerceAtMost(sorted.lastIndex)])} ms
            |Draw calls=${render.drawCalls}; targets=${render.ownedTargets}; target bytes=${render.ownedTargetBytes}
            |Text bytes=${render.textBytes}; GPU mesh bytes=${render.meshBytes}; uploaded bytes/frame=${render.uploadedBytes}
            |Measured nodes=${ui.statistics.measuredNodes}; painted nodes=${ui.statistics.paintedNodes}; commands=${ui.statistics.commands}
            |${GL11.glGetString(GL11.GL_RENDERER)}; ${GL11.glGetString(GL11.GL_VERSION)}
            |Target/text/mesh counters exclude artwork textures and driver allocations.
        """.trimMargin()
        val path = Path.of("build", "reports", "cristalix", "profile-$scenario.txt")
        Files.createDirectories(path.parent)
        Files.writeString(path, report)
        println(report)
    }

    private fun format(value: Double) = String.format(Locale.ROOT, "%.3f", value)
}

/**
 * Exposes the existing renderer counters without changing production rendering or ownership.
 */
internal class MenuBackend(private val delegate: UiBackend) : UiBackend by delegate {

    private var current: ArcRenderSession? = null
    val session get() = checkNotNull(current)

    override fun openSession(): RenderSession = delegate.openSession().also { current = it as ArcRenderSession }
}
