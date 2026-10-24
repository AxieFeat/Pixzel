package pixzel

import pixzel.component.AbstractElement
import pixzel.component.Element
import pixzel.component.Invalidation
import pixzel.component.SimpleOverlayManager
import pixzel.component.Visibility
import pixzel.graphics.DrawCommand
import pixzel.graphics.FrameInfo
import pixzel.graphics.RenderMetrics
import pixzel.graphics.RenderMetricsProvider
import pixzel.graphics.UiBackend
import pixzel.input.NavigationCommand
import pixzel.layout.Constraints
import pixzel.layout.unit.fillAvailable
import pixzel.platform.HostMetrics
import pixzel.platform.UiHost
import pixzel.style.Theme
import pixzel.surface.Insets
import pixzel.surface.Size
import pixzel.surface.Vec2
import pixzel.text.TextService
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration

fun ui(host: UiHost, backend: UiBackend, text: TextService, settings: UiSettings = UiSettings()): Ui = SimpleUi(host, backend, text, settings)

internal class SimpleUi(override val host: UiHost, override val backend: UiBackend, override val text: TextService, settings: UiSettings) : Ui {

    private data class DeferredPlacement(val element: Element, val target: Element, val action: () -> Unit)

    private val thread = Thread.currentThread()
    private val posted = ConcurrentLinkedQueue<() -> Unit>()
    private var session = backend.openSession()
    private var sessionGeneration = backend.resourceGeneration

    override val renderMetrics: RenderMetrics? get() = (session as? RenderMetricsProvider)?.renderMetrics
    override val components = DefaultComponents()
    override val root = components.surface("root")
    override val overlays = SimpleOverlayManager(this)
    override val diagnostics = DiagnosticLog()

    override var settings = settings
        set(value) {
            assertThread()
            check(lifecycle != UiLifecycle.CLOSED)
            if (field != value) {
                invalidate(Invalidation.MEASURE)
                field = value
            }
        }

    override var theme = Theme.DARK
        set(value) {
            assertMutable()
            check(lifecycle != UiLifecycle.CLOSED && !closing) { "Ui is closed" }
            if (field != value) {
                field = value
                (root as AbstractElement).refreshInheritedStyles()
                paintDirty = true
            }
        }

    override var layoutVersion = 0L
        private set

    @Volatile
    override var lifecycle = UiLifecycle.CREATED
        private set
    override var statistics = FrameStatistics()
        private set
    private var layoutDirty = true
    private var treeDirty = true
    private var paintDirty = true

    private var metrics: HostMetrics? = null
    private var appliedUiScale = 0f
    private var commands: List<DrawCommand> = emptyList()
    override val inputMode get() = input.inputMode
    override val capturedPointers get() = input.capturedPointers

    private var inFrame = false
    private var painting = false

    @Volatile
    internal var closing = false
        private set
    private var layingOut = false
    private val deferredPlacements = mutableListOf<DeferredPlacement>()

    private var layoutPasses = 0
    private var measuredNodes = 0
    private var portalChildren: Map<Element, List<Element>> = emptyMap()
    private var treeOrder: Map<Element, Int> = emptyMap()
    private var flatNodes: List<Element> = emptyList()
    private var paintChildren: Map<Element, List<Element>> = emptyMap()
    private val input = UiInput(this)
    private val painter = UiPainter(this)
    override val focus = UiFocus(this)
    private val subscription = host.subscribe(input::dispatch)

    init {
        root.resources.own(overlays)
        root.layout.width = fillAvailable
        root.layout.height = fillAvailable
        root.attach(null, this)
        lifecycle = UiLifecycle.ATTACHED
    }

    override fun assertThread() {
        check(Thread.currentThread() === thread) { "UI mutations must run on the owning thread; use Ui.post" }
    }

    override fun post(action: () -> Unit) {
        synchronized(posted) {
            check(lifecycle != UiLifecycle.CLOSED && !closing) { "Ui is closed" }
            posted += action
        }
    }

    internal fun assertMutable() {
        assertThread()
        check(!painting) { "UI mutation during paint is not allowed; use Ui.post for the next frame." }
    }

    override fun invalidate(reason: Invalidation) {
        assertMutable()
        check(lifecycle != UiLifecycle.CLOSED)
        if (reason == Invalidation.TREE) treeDirty = true
        if (reason == Invalidation.TREE || reason == Invalidation.MEASURE || reason == Invalidation.ARRANGE || reason == Invalidation.TRANSFORM) layoutDirty =
            true
        paintDirty = true
    }

    internal fun nodes(): List<Element> {
        if (treeDirty || flatNodes.isEmpty()) {
            val result = ArrayList<Element>()
            fun visit(element: Element) {
                result += element
                element.children.forEach(::visit)
            }

            visit(root)
            flatNodes = result
        }
        return flatNodes
    }

    internal fun visibleAttached(element: Element): Boolean {
        var current: Element? = element
        while (current != null) {
            if (current.visibility != Visibility.VISIBLE || current.isDisposed || current.ui !== this) return false
            current = current.parent
        }
        return true
    }

    internal fun effective(element: Element): Boolean {
        var current: Element? = element
        while (current != null) {
            if (!current.enabled || current.visibility != Visibility.VISIBLE || current.isDisposed || current.ui !== this) return false
            current = current.parent
        }
        return true
    }

    private fun rebuildGeometryTree() {
        if (!treeDirty) return

        val all = nodes()
        treeOrder = all.withIndex().associate { it.value to it.index }
        all.forEach { element ->
            val seen = linkedSetOf<Element>()
            var current: Element? = element
            while (current != null) {
                require(current.ui === this && !current.isDisposed) { "Portal target '${current.id}' must be attached to the same Ui." }
                check(seen.add(current)) { "Portal geometry cycle: ${seen.joinToString(" -> ") { it.id }} -> ${current.id}" }
                current = current.portalTarget ?: current.parent
            }
        }

        portalChildren = all.filter { it.portalTarget != null }.groupBy { checkNotNull(it.portalTarget) }
        paintChildren = all.associateWith { element ->
            (element.children.filter { it.portalTarget == null } + portalChildren[element].orEmpty()).sortedWith(compareBy<Element> { it.layer.ordinal }.thenBy { it.zIndex }
                .thenBy { treeOrder[it] ?: Int.MAX_VALUE })
        }
        painter.retain(all.toSet())
        treeDirty = false
    }

    internal fun layoutIfNeeded() {
        if (!layoutDirty || lifecycle == UiLifecycle.CLOSED) return

        check(++layoutPasses <= 16) { "Layout/input/style dependency did not settle within 16 passes" }
        val m = host.metrics
        val size = Size(m.logicalSize.width / settings.uiScale, m.logicalSize.height / settings.uiScale)
        layoutDirty = false
        val before = nodes().filterIsInstance<AbstractElement>().sumOf { it.measureCount }
        layoutVersion++
        rebuildGeometryTree()
        layingOut = true
        try {
            root.measure(Constraints.fixed(size)).place(Vec2.ZERO)

            // A portal waits until its target's geometry is committed, even if tree order differs.
            var committed = 0
            while (deferredPlacements.isNotEmpty()) {
                check(++committed <= maxOf(64, treeOrder.size * 4)) { "Deferred portal placement did not settle." }
                val queued = deferredPlacements.map { it.element }.toSet()
                val ready = deferredPlacements.indexOfFirst { task ->
                    generateSequence(task.target as Element?) {
                        it.portalTarget ?: it.parent
                    }.none { it in queued }
                }
                check(ready >= 0) { "Deferred portal placement cycle: ${deferredPlacements.joinToString { it.element.id + " -> " + it.target.id }}" }
                deferredPlacements.removeAt(ready).action()
            }
        } catch (error: Throwable) {
            layoutDirty = true
            throw error
        } finally {
            layingOut = false
            deferredPlacements.clear()
        }
        measuredNodes += nodes().filterIsInstance<AbstractElement>().sumOf { it.measureCount } - before
        paintDirty = true
    }

    override fun deferPlacement(element: Element, target: Element, placement: () -> Unit) {
        assertThread()
        check(layingOut) { "Portal placement can only be deferred during layout." }
        require(element.ui === this && target.ui === this) { "Portal placement belongs to another Ui." }
        check(deferredPlacements.none { it.element === element }) { "Portal '${element.id}' was scheduled for placement twice." }
        deferredPlacements += DeferredPlacement(element, target, placement)
    }

    internal fun sorted(element: Element) = paintChildren[element].orEmpty()
    override fun hitTest(point: Vec2): Element? = input.hitTest(point)

    internal fun hasPointerCapture(element: Element) = input.hasPointerCapture(element)

    override fun releasePointerCapture(element: Element) = input.releasePointerCapture(element)

    override fun navigate(command: NavigationCommand): Boolean = input.navigate(command)

    override fun elementDetached(element: Element) = input.detached(element)

    override fun frame(delta: Duration) {
        assertThread()
        check(lifecycle != UiLifecycle.CLOSED) { "Cannot frame a closed Ui" }
        check(!inFrame) { "Recursive Ui.frame" }
        require(delta.isFinite() && !delta.isNegative())

        inFrame = true
        layoutPasses = 0
        measuredNodes = 0
        try {
            while (true) {
                val action = posted.poll() ?: break
                action()
                if (lifecycle == UiLifecycle.CLOSED) return
            }

            var next = host.metrics
            fun applyMetrics() {
                if (metrics != next || appliedUiScale != settings.uiScale) {
                    metrics = next
                    appliedUiScale = settings.uiScale
                    root.layout.padding = Insets(
                        next.safeInsets.left / settings.uiScale,
                        next.safeInsets.top / settings.uiScale,
                        next.safeInsets.right / settings.uiScale,
                        next.safeInsets.bottom / settings.uiScale
                    )
                    layoutDirty = true
                }
            }

            applyMetrics()
            if (!next.drawable) {
                if (lifecycle != UiLifecycle.SUSPENDED) input.cancelCaptures()
                if (lifecycle == UiLifecycle.CLOSED) return
                lifecycle = UiLifecycle.SUSPENDED
                host.drainEvents()
                return
            }

            val generation = backend.resourceGeneration
            if (generation != sessionGeneration) {
                check(generation != 0L || sessionGeneration == 0L) { "Rendering context is unavailable; resume after the host restores it" }
                session.close()
                session = backend.openSession()
                sessionGeneration = generation
                diagnostics.report(
                    Diagnostic(
                        "context-restored",
                        "Rendering session rebuilt for resource generation $generation",
                        severity = DiagnosticSeverity.INFO
                    )
                )
            }

            val resumed = lifecycle == UiLifecycle.SUSPENDED
            lifecycle = UiLifecycle.ACTIVE
            layoutIfNeeded()
            host.drainEvents()
            if (lifecycle == UiLifecycle.CLOSED) return

            next = host.metrics
            applyMetrics()
            if (!next.drawable) {
                input.cancelCaptures()
                if (lifecycle != UiLifecycle.CLOSED) lifecycle = UiLifecycle.SUSPENDED
                return
            }

            layoutIfNeeded()
            input.cancelCaptures { !effective(it) }
            if (focus.focused?.let { !effective(it) } == true) focus.clear()
            input.hover()

            var all = nodes()
            var updatePass = 0

            // Newly materialized nodes get a zero-time pass; elapsed time is advanced only once.
            do {
                check(++updatePass <= 16) { "Animation/materialization/layout updates did not settle within 16 passes" }
                for (element in all) {
                    if (!visibleAttached(element)) continue
                    val step = if (resumed || updatePass > 1) Duration.ZERO else delta
                    fun update(phase: String, action: () -> Boolean): Boolean = try {
                        action()
                    } catch (error: Throwable) {
                        if (settings.errorPolicy.handle(UiFailure(error, phase, element.id)) == ErrorDecision.THROW) throw error
                        diagnostics.report(
                            Diagnostic(
                                "update-error",
                                error.message ?: error::class.simpleName.orEmpty(),
                                element.id,
                                DiagnosticSeverity.ERROR
                            )
                        )
                        true
                    }

                    val updated = update("element update") { element.update(step) }
                    if (updated && !element.isDisposed && element.ui === this) element.invalidate(Invalidation.PAINT)
                    if (lifecycle == UiLifecycle.CLOSED) return
                    if (!element.isDisposed) {
                        val changed = update("frame update") { element.frameUpdates.advance(step) }
                        if (changed && !element.isDisposed && element.ui === this) element.invalidate(Invalidation.PAINT)
                    }
                    if (lifecycle == UiLifecycle.CLOSED) return
                }
                if (!layoutDirty) break
                layoutIfNeeded()
                input.hover()
                all = nodes()
            } while (true)

            var painted = 0
            if (paintDirty) {
                painting = true
                try {
                    val recorded = painter.record(root, commands.size)
                    commands = recorded.commands
                    painted = recorded.paintedNodes
                } finally {
                    painting = false
                }
                paintDirty = false
            }

            session.submit(
                FrameInfo(
                    root.surface.bounds.size,
                    next.framebufferSize,
                    Vec2(next.framebufferSize.width / root.surface.bounds.width, next.framebufferSize.height / root.surface.bounds.height),
                    next.generation
                ), commands
            )
            statistics = FrameStatistics(all.size, measuredNodes, painted, commands.size, layoutPasses, all.count(::visibleAttached))
        } finally {
            inFrame = false
        }
    }

    override fun close() {
        assertMutable()
        if (lifecycle == UiLifecycle.CLOSED || closing) return
        closing = true
        var failure: Throwable? = null
        fun cleanup(action: () -> Unit) {
            try {
                action()
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
        }
        try {
            cleanup { input.cancelCaptures() }
            cleanup { focus.clear() }
            cleanup { subscription.close() }
            synchronized(posted) { posted.clear() }
            cleanup { root.close() }
            cleanup { session.close() }
        } finally {
            lifecycle = UiLifecycle.CLOSED
            closing = false
            input.clearState()
            commands = emptyList()
            portalChildren = emptyMap()
            treeOrder = emptyMap()
            paintChildren = emptyMap()
            flatNodes = emptyList()
            painter.clear()
        }
        failure?.let { throw it }
    }


}
