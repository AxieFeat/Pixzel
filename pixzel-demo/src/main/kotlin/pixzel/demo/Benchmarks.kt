package pixzel.demo

import com.sun.management.ThreadMXBean
import pixzel.Ui
import pixzel.UiSettings
import pixzel.component.Painter
import pixzel.component.Visibility
import pixzel.component.virtualList
import pixzel.graphics.BackendCapabilities
import pixzel.graphics.Capability
import pixzel.graphics.Clip
import pixzel.graphics.Concat
import pixzel.graphics.DrawCommand
import pixzel.graphics.DrawText
import pixzel.graphics.Fill
import pixzel.graphics.FrameInfo
import pixzel.graphics.Rectangle
import pixzel.graphics.RenderSession
import pixzel.graphics.Restore
import pixzel.graphics.Save
import pixzel.graphics.Solid
import pixzel.graphics.Stroke
import pixzel.graphics.UiBackend
import pixzel.graphics.rgba
import pixzel.layout.unit.dp
import pixzel.layout.unit.fillAvailable
import pixzel.platform.TestHost
import pixzel.state.MutableState
import pixzel.state.StateRuntime
import pixzel.state.bind
import pixzel.state.stateOf
import pixzel.state.stateRuntime
import pixzel.state.transaction
import pixzel.surface.Placement
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import pixzel.text.RevealTrigger
import pixzel.text.TextOverflow
import pixzel.text.hoverReveal
import pixzel.ui
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * CPU-only deterministic benchmark. No Arc, window, GPU, rasterization or presentation occurs.
 * Setup and scene destruction are excluded except for the explicitly named lifecycle scenario.
 * Run a fresh JVM and retain the generated environment/flags alongside every measurement.
 */
fun main(args: Array<String>) {
    fun integer(name: String, default: Int): Int = args.firstOrNull { it.startsWith("--$name=") }
        ?.substringAfter('=')?.toInt()?.also { require(it > 0) } ?: default

    val warmup = integer("warmup", 60)
    val samples = integer("frames", 300)
    val cycles = integer("lifecycle", 1_000)
    val output = Path.of(
        args.firstOrNull { it.startsWith("--output=") }?.substringAfter('=')
            ?: "build/benchmarks/pixzel-cpu.md"
    ).toAbsolutePath()
    val selected = args.firstOrNull { it.startsWith("--scene=") }?.substringAfter('=')?.split(',')?.toSet() ?: setOf("all")
    require(selected.all { it in setOf("all", "static", "live", "text", "virtual", "lifecycle") }) { "Unknown benchmark scene filter." }
    val allocation = AllocationCounter()
    val conditions = BenchmarkConditions.capture()
    val results = mutableListOf<BenchmarkResult>()
    val scenes = listOf("static" to ::staticScene, "live" to ::liveScene, "text" to ::textScene, "virtual" to ::virtualScene)
    scenes.filter { "all" in selected || it.first in selected }.forEach { (_, create) ->
        create().use { scene -> results += measure(scene, warmup, samples, allocation) }
    }
    if ("all" in selected || "lifecycle" in selected) results += measureLifecycle(minOf(warmup, 50), cycles, allocation)
    Files.createDirectories(output.parent)
    val report = report(results, warmup, allocation.available, conditions)
    Files.writeString(output, report)
    println(report)
    println("Benchmark report: $output")
}

private data class Percentiles(val p50: Double, val p95: Double, val p99: Double)
private data class BenchmarkConditions(val cpu: String, val powerPlan: String, val garbageCollectors: String) {
    companion object {

        fun capture(): BenchmarkConditions {
            fun probe(vararg command: String): String? = runCatching {
                val process = ProcessBuilder(*command).redirectErrorStream(true).start()
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    null
                } else process.inputStream.bufferedReader().use { it.readText() }.trim().takeIf { process.exitValue() == 0 }
            }.getOrNull()

            val windows = System.getProperty("os.name").startsWith("Windows")
            val cpu = if (windows) probe("reg", "query", "HKLM\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "/v", "ProcessorNameString")
                ?.lineSequence()?.firstOrNull { it.contains("ProcessorNameString") }?.substringAfter("REG_SZ")?.trim() else null
            val plan = if (windows) probe("powercfg", "/GETACTIVESCHEME")?.replace(Regex("\\s+"), " ") else null
            return BenchmarkConditions(
                cpu ?: System.getenv("PROCESSOR_IDENTIFIER") ?: "identifier unavailable",
                plan ?: "not available; no power-mode assumption", ManagementFactory.getGarbageCollectorMXBeans().joinToString { it.name })
        }
    }
}

private data class BenchmarkResult(
    val name: String,
    val samples: Int,
    val cpu: Percentiles,
    val allocationMean: Double?,
    val allocationP95: Double?,
    val measuredNodesMean: Double,
    val shapeCalls: Long,
    val totalNodes: Int,
    val materializedRows: Int?,
    val commandCount: Int,
    val drawOperations: Int,
    val checks: String
)

private class AllocationCounter {

    private val bean = (ManagementFactory.getThreadMXBean() as? ThreadMXBean)?.takeIf {
        it.isThreadAllocatedMemorySupported && runCatching {
            if (!it.isThreadAllocatedMemoryEnabled) it.isThreadAllocatedMemoryEnabled = true
            it.isThreadAllocatedMemoryEnabled
        }.getOrDefault(false)
    }
    private val threadId = Thread.currentThread().threadId()
    val available: Boolean get() = bean != null && read() >= 0
    fun read(): Long = bean?.let { runCatching { it.getThreadAllocatedBytes(threadId) }.getOrDefault(-1L) } ?: -1L
}

/**
 * Submission counts commands without retaining frames or pretending to render pixels.
 */
private class CpuDiscardBackend : UiBackend {

    override val capabilities = BackendCapabilities(setOf(Capability.RECT_CLIP, Capability.TEXT), "CPU benchmark command counter; no rendering")
    var activeSessions = 0
        private set
    var commands = 0
        private set
    var drawOperations = 0
        private set

    override fun openSession(): RenderSession {
        activeSessions++
        return object : RenderSession {
            private var closed = false
            override fun submit(frame: FrameInfo, commands: List<DrawCommand>) {
                check(!closed)
                var depth = 0
                var draws = 0
                commands.forEach { command ->
                    when (command) {
                        Save -> depth++
                        Restore -> check(--depth >= 0)
                        is Concat -> Unit
                        is Clip -> require(command.shape === Rectangle) { "Benchmark backend counts rectangular clips only." }
                        is Fill, is Stroke, is DrawText -> draws++
                        else -> error("Unsupported CPU benchmark operation ${command::class.simpleName}.")
                    }
                }
                check(depth == 0)
                this@CpuDiscardBackend.commands = commands.size
                drawOperations = draws
            }

            override fun close() {
                if (!closed) {
                    closed = true
                    activeSessions--
                }
            }
        }
    }
}

private class BenchmarkScene(
    val name: String,
    val host: TestHost,
    val backend: CpuDiscardBackend,
    val text: FixedFontTextService,
    val ui: Ui,
    val tick: (Int) -> Unit = {},
    val rows: () -> Int? = { null },
    val validate: () -> String = { "" }
) : AutoCloseable {

    override fun close() {
        try {
            ui.close()
        } finally {
            host.close()
        }
        check(host.listenerCount == 0 && backend.activeSessions == 0)
    }
}

private fun scene(name: String, build: (Ui, TestHost, CpuDiscardBackend, FixedFontTextService) -> BenchmarkScene): BenchmarkScene {
    val host = TestHost.of(1920, 1080)
    val backend = CpuDiscardBackend()
    val text = FixedFontTextService.of()
    val ui = ui(host, backend, text, UiSettings())
    try {
        return build(ui, host, backend, text)
    } catch (failure: Throwable) {
        ui.close()
        host.close()
        throw failure
    }
}

private val HUD_PAINT = Painter { canvas, context -> canvas.fill(Rectangle, context.bounds, Solid(0x385FDCFF.rgba)) }

private fun populateHud(ui: Ui, counters: List<MutableState<Int>> = emptyList(), runtime: StateRuntime? = null) {
    // Root + 9,999 children = 10,000 retained nodes; 1,000 visible simple children.
    repeat(9_999) { index ->
        val element = if (index < counters.size) ui.components.text("counter-$index").apply {
            bindValue(bind(checkNotNull(runtime)) { counters[index].value.toString() })
        } else ui.components.surface("hud-$index").apply { background = HUD_PAINT }
        element.layout.width = if (index < counters.size) 64.dp else 32.dp
        element.layout.height = 18.dp
        element.placement = Placement(offset = Vec2((index % 50) * 38f, (index / 50) * 24f))
        if (index >= 1_000) element.visibility = Visibility.HIDDEN
        ui.root.add(element)
    }
}

private fun staticScene() = scene("Static HUD: 1,000 visible / 10,000 retained") { ui, host, backend, text ->
    populateHud(ui)
    BenchmarkScene("Static HUD: 1,000 visible / 10,000 retained", host, backend, text, ui, validate = {
        check(ui.statistics.totalNodes == 10_000 && ui.statistics.measuredNodes == 0)
        "10,000 nodes; steady-state measuredNodes=0; no model writes"
    })
}

private fun liveScene() = scene("Live data: 100 writes/frame / 10,000 retained") { ui, host, backend, text ->
    val runtime = stateRuntime()
    val counters = List(100) { stateOf(0, runtime) }
    populateHud(ui, counters, runtime)
    BenchmarkScene(
        "Live data: 100 writes/frame / 10,000 retained", host, backend, text, ui,
        tick = { transaction(runtime) { counters.forEach { it.value++ } } }, validate = {
            check(ui.statistics.totalNodes == 10_000)
            "100 structurally different counter writes in one transaction each frame"
        })
}

private fun textScene() = scene("Text menu: 300 labels / 50 active reveals") { ui, host, backend, text ->
    repeat(300) { index ->
        val label = ui.components.text("label-$index").apply {
            value = if (index < 50) "A long animated status label number $index" else "Label $index"
            layout.width = 180.dp
            layout.height = 24.dp
            placement = Placement(offset = Vec2((index % 10) * 190f, (index / 10) * 32f))
            overflow = TextOverflow.ELLIPSIS
            if (index < 50) reveal = hoverReveal {
                trigger = RevealTrigger.ALWAYS
                delay = Duration.ZERO
            }
        }
        ui.root.add(label)
    }
    BenchmarkScene(
        "Text menu: 300 labels / 50 active reveals", host, backend, text, ui,
        validate = { "300 retained labels; 50 ALWAYS reveal policies; shape count reported for measured interval" })
}

private fun virtualScene() = scene("VirtualList: 100,000 items / about 30 visible rows") { ui, host, backend, text ->
    val list = virtualList(ui, (0 until 100_000).toList(), { it }, estimatedHeight = 24f, overscan = 2) { item ->
        ui.components.text("label").apply {
            value = "Item ${item.value}"
            layout.width = fillAvailable
            layout.height = 24.dp
        }
    }
    list.layout.width = 400.dp
    list.layout.height = 720.dp
    ui.root.add(list)
    BenchmarkScene(
        "VirtualList: 100,000 items / about 30 visible rows", host, backend, text, ui,
        tick = { frame -> list.scrollToKey((frame.toLong() * 97 % 99_900).toInt()) }, rows = { list.materializedCount }, validate = {
            check(list.materializedCount <= 36)
            "30-row viewport, 2 overscan rows per side, 97-item deterministic jumps; materialized<=36"
        })
}

private fun measure(scene: BenchmarkScene, warmup: Int, samples: Int, allocation: AllocationCounter): BenchmarkResult {
    scene.ui.frame(Duration.ZERO)
    repeat(warmup) {
        scene.tick(it)
        scene.ui.frame(16.milliseconds)
    }
    val times = LongArray(samples)
    val bytes = LongArray(samples) { -1 }
    val shapeStart = scene.text.layoutCount
    var measuredNodes = 0L
    repeat(samples) { index ->
        val beforeBytes = allocation.read()
        val started = System.nanoTime()
        scene.tick(index + warmup)
        scene.ui.frame(16.milliseconds)
        times[index] = System.nanoTime() - started
        val afterBytes = allocation.read()
        if (beforeBytes >= 0 && afterBytes >= beforeBytes) bytes[index] = afterBytes - beforeBytes
        measuredNodes += scene.ui.statistics.measuredNodes
    }
    val shapeCalls = scene.text.layoutCount - shapeStart
    val checks = scene.validate()
    if (scene.name.startsWith("Text menu")) check(shapeCalls == 0L) { "Reveal-only frames reshaped text $shapeCalls times." }
    return BenchmarkResult(
        scene.name, samples, percentiles(times, 1_000_000.0),
        bytes.takeIf { it.all { count -> count >= 0 } }?.average(),
        bytes.takeIf { it.all { count -> count >= 0 } }?.let { percentiles(it, 1.0).p95 },
        measuredNodes.toDouble() / samples, shapeCalls, scene.ui.statistics.totalNodes, scene.rows(), scene.backend.commands,
        scene.backend.drawOperations, checks
    )
}

private fun measureLifecycle(warmup: Int, cycles: Int, allocation: AllocationCounter): BenchmarkResult {
    val host = TestHost.of(1920, 1080)
    val backend = CpuDiscardBackend()
    val text = FixedFontTextService.of()
    fun cycle(index: Int) {
        host.resize(if (index % 2 == 0) 1920 else 1280, if (index % 2 == 0) 1080 else 720)
        ui(host, backend, text).use { owner ->
            buildMenu(owner)
            owner.frame(Duration.ZERO)
        }
        check(host.listenerCount == 0 && backend.activeSessions == 0) { "Lifecycle cycle retained a listener or render session." }
    }
    repeat(warmup, ::cycle)
    val times = LongArray(cycles)
    val bytes = LongArray(cycles) { -1 }
    val shapeStart = text.layoutCount
    repeat(cycles) { index ->
        val beforeBytes = allocation.read()
        val started = System.nanoTime()
        cycle(index)
        times[index] = System.nanoTime() - started
        val afterBytes = allocation.read()
        if (beforeBytes >= 0 && afterBytes >= beforeBytes) bytes[index] = afterBytes - beforeBytes
    }
    host.close()
    return BenchmarkResult(
        "Lifecycle: mount/frame/resize/close", cycles, percentiles(times, 1_000_000.0),
        bytes.takeIf { it.all { count -> count >= 0 } }?.average(),
        bytes.takeIf { it.all { count -> count >= 0 } }?.let { percentiles(it, 1.0).p95 },
        0.0, text.layoutCount - shapeStart, 0, null, backend.commands, backend.drawOperations,
        "$cycles complete create/build/frame/close cycles; alternating1080p/720p; listeners=0 and sessions=0 after every cycle"
    )
}

private fun percentiles(values: LongArray, divisor: Double): Percentiles {
    val sorted = values.sortedArray()
    fun at(fraction: Double) = sorted[(ceil(fraction * sorted.size).toInt() - 1).coerceIn(sorted.indices)] / divisor
    return Percentiles(at(.50), at(.95), at(.99))
}

private fun report(results: List<BenchmarkResult>, warmup: Int, allocationAvailable: Boolean, conditions: BenchmarkConditions): String {
    fun number(value: Double?) = value?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "unavailable"
    return buildString {
        appendLine("# Pixzel independent CPU benchmark")
        appendLine()
        appendLine("Measured at ${ZonedDateTime.now(ZoneId.of("Asia/Vladivostok"))}.")
        appendLine()
        appendLine("- OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
        appendLine("- JVM: ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}")
        appendLine("- CPU: ${conditions.cpu}; ${Runtime.getRuntime().availableProcessors()} logical processors")
        appendLine("- CPU power plan (read-only OS query): ${conditions.powerPlan}; actual clocks/thermal throttling were not sampled")
        appendLine("- Garbage collectors: ${conditions.garbageCollectors}; collection pauses are included in the observed frame timings")
        appendLine("- JVM flags: ${ManagementFactory.getRuntimeMXBean().inputArguments.joinToString(" ").ifEmpty { "none" }}")
        appendLine("- Heap maximum: ${Runtime.getRuntime().maxMemory()} bytes")
        appendLine("- Framebuffer/logical viewport: 1920x1080, DPI1, uiScale1, textScale1; deterministic delta16ms")
        appendLine(
            "- Warmup: $warmup frames per sampled steady scene; lifecycle ${
                if (results.any { it.name.startsWith("Lifecycle") }) minOf(
                    warmup,
                    50
                ).toString() + " cycles" else "not selected"
            }"
        )
        appendLine("- Measured samples: ${results.joinToString { it.name + "=" + it.samples }}")
        appendLine("- Allocation source: ${if (allocationAvailable) "ThreadMXBean current-thread allocated bytes" else "unavailable; no allocation estimate substituted"}")
        appendLine("- Submission: bounded CPU command counter, no rasterization, GPU, driver, vsync or presentation; operations are draw commands, not GPU draw calls")
        appendLine()
        appendLine("| Scene | Samples | CPU p50 ms | p95 ms | p99 ms | Mean allocated B/sample | Allocation p95 B | Mean measured nodes | Shape calls | Retained nodes | Materialized rows | Commands / draw operations |")
        appendLine("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
        results.forEach { result ->
            appendLine(
                "| ${result.name} | ${result.samples} | ${number(result.cpu.p50)} | ${number(result.cpu.p95)} | ${number(result.cpu.p99)} | ${
                    number(
                        result.allocationMean
                    )
                } | ${number(result.allocationP95)} | ${number(result.measuredNodesMean)} | ${result.shapeCalls} | ${result.totalNodes} | ${result.materializedRows ?: "n/a"} | ${result.commandCount} / ${result.drawOperations} |"
            )
        }
        appendLine()
        results.forEach { appendLine("- ${it.name}: ${it.checks}.") }
        val static = results.firstOrNull { it.name.startsWith("Static HUD") }
        val live = results.firstOrNull { it.name.startsWith("Live data") }
        appendLine()
        appendLine("Reference engineering budgets (not universal guarantees): static p95<=2ms ${if (static == null) "not sampled" else if (static.cpu.p95 <= 2) "met" else "exceeded"}; live p95<=4ms ${if (live == null) "not sampled" else if (live.cpu.p95 <= 4) "met" else "exceeded"} for this CPU-only run.")
        appendLine()
        appendLine("Native/GPU memory high-water marks, GPU submit/present times, actual draw calls and visual acceptance were not measured. Setup/destruction are excluded from steady scenes and included in lifecycle. CPU thread allocation includes the library, deterministic fixture and benchmark callbacks; JVM/GC scheduling affects tail latency.")
    }
}
