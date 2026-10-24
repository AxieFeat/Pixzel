package pixzel

import pixzel.component.AbstractElement
import pixzel.component.Button
import pixzel.component.Checkbox
import pixzel.component.ClipPolicy
import pixzel.component.ComponentFactory
import pixzel.component.Element
import pixzel.component.Image
import pixzel.component.Invalidation
import pixzel.component.PaintContext
import pixzel.component.Progress
import pixzel.component.Radio
import pixzel.component.ScrollBar
import pixzel.component.ScrollView
import pixzel.component.SimpleButton
import pixzel.component.SimpleCheckbox
import pixzel.component.SimpleImage
import pixzel.component.SimpleOverlayManager
import pixzel.component.SimpleProgress
import pixzel.component.SimpleRadio
import pixzel.component.SimpleScrollBar
import pixzel.component.SimpleScrollView
import pixzel.component.SimpleSlider
import pixzel.component.SimpleTabs
import pixzel.component.SimpleText
import pixzel.component.SimpleTextField
import pixzel.component.SimpleToggle
import pixzel.component.Slider
import pixzel.component.Tabs
import pixzel.component.Text
import pixzel.component.TextField
import pixzel.component.Toggle
import pixzel.component.Visibility
import pixzel.graphics.BeginGroup
import pixzel.graphics.Canvas
import pixzel.graphics.Clip
import pixzel.graphics.Concat
import pixzel.graphics.DrawCommand
import pixzel.graphics.EndGroup
import pixzel.graphics.FrameInfo
import pixzel.graphics.Rectangle
import pixzel.graphics.RenderMetrics
import pixzel.graphics.RenderMetricsProvider
import pixzel.graphics.Restore
import pixzel.graphics.Save
import pixzel.graphics.UiBackend
import pixzel.input.Composition
import pixzel.input.EventPhase
import pixzel.input.FocusManager
import pixzel.input.InputEvent
import pixzel.input.InputMode
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.NavigationCommand
import pixzel.input.NavigationDirection
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.PointerPolicy
import pixzel.input.RoutedEvent
import pixzel.input.RoutingFlags
import pixzel.input.TextCommit
import pixzel.input.WheelEvent
import pixzel.input.WindowFocus
import pixzel.layout.Constraints
import pixzel.layout.unit.fillAvailable
import pixzel.platform.HostMetrics
import pixzel.platform.UiHost
import pixzel.resource.Subscription
import pixzel.style.ResolvedTokens
import pixzel.style.Theme
import pixzel.style.isEffectivelyEnabled
import pixzel.surface.Insets
import pixzel.surface.Rect
import pixzel.surface.SimpleSurfaceElement
import pixzel.surface.Size
import pixzel.surface.SurfaceElement
import pixzel.surface.Transform
import pixzel.surface.Vec2
import pixzel.text.TextService
import java.util.Collections.unmodifiableList
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.time.Duration

fun ui(host: UiHost, backend: UiBackend, text: TextService, settings: UiSettings = UiSettings()): Ui = SimpleUi(host, backend, text, settings)

internal class SimpleUi(override val host: UiHost, override val backend: UiBackend, override val text: TextService, settings: UiSettings) : Ui {
    private class FocusScope(val root: Element, val previous: Element?, val restore: Boolean)
    private data class DeferredPlacement(val element: Element, val target: Element, val action: () -> Unit)
    private data class PaintKey(
        val version: Long,
        val transform: Transform,
        val bounds: Rect,
        val content: Rect,
        val theme: Theme,
        val settings: UiSettings,
        val tokens: ResolvedTokens,
        val disabled: Boolean
    )

    private data class NodePaint(val key: PaintKey, val before: List<DrawCommand>, val after: List<DrawCommand>)

    private val thread = Thread.currentThread()
    private val posted = ConcurrentLinkedQueue<() -> Unit>()
    private var session = backend.openSession()
    private var sessionGeneration = backend.resourceGeneration
    override val renderMetrics: RenderMetrics? get() = (session as? RenderMetricsProvider)?.renderMetrics
    override val components = object : ComponentFactory {
        override fun surface(id: String): SurfaceElement = SimpleSurfaceElement(id)
        override fun text(id: String): Text = SimpleText(id)
        override fun button(id: String): Button = SimpleButton(id, this)
        override fun textField(id: String): TextField = SimpleTextField(id)
        override fun scrollView(id: String): ScrollView = SimpleScrollView(id)
        override fun image(id: String): Image = SimpleImage(id)
        override fun checkbox(id: String): Checkbox = SimpleCheckbox(id, this)
        override fun toggle(id: String): Toggle = SimpleToggle(id, this)
        override fun radio(id: String): Radio = SimpleRadio(id, this)
        override fun slider(id: String): Slider = SimpleSlider(id)
        override fun progress(id: String): Progress = SimpleProgress(id)
        override fun tabs(id: String): Tabs = SimpleTabs(id, this)
        override fun scrollBar(id: String): ScrollBar = SimpleScrollBar(id)
    }
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
    private var pointer: Vec2? = null
    override var inputMode = InputMode.POINTER
        private set
    private val captures = mutableMapOf<Int, Element>()
    override val capturedPointers: Map<Int, Element> get() = captures.toMap()
    private val captureButtons = mutableMapOf<Int, Int>()
    private val consumedKeys = mutableSetOf<Pair<Key, Int>>()
    private val hovered = mutableSetOf<Element>()
    private var inFrame = false
    private var painting = false
    @Volatile
    private var closing = false
    private var layingOut = false
    private val deferredPlacements = mutableListOf<DeferredPlacement>()
    private var layoutPasses = 0
    private var measuredNodes = 0
    private var portalChildren: Map<Element, List<Element>> = emptyMap()
    private var treeOrder: Map<Element, Int> = emptyMap()
    private var flatNodes: List<Element> = emptyList()
    private var paintChildren: Map<Element, List<Element>> = emptyMap()
    private val nodePaint = IdentityHashMap<Element, NodePaint>()
    override val focus = SimpleFocus()
    private val subscription = host.subscribe(::input)

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

    private fun assertMutable() {
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

    private fun nodes(): List<Element> {
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

    private fun visibleAttached(element: Element): Boolean {
        var current: Element? = element
        while (current != null) {
            if (current.visibility != Visibility.VISIBLE || current.isDisposed || current.ui !== this) return false
            current = current.parent
        }
        return true
    }

    private fun effective(element: Element): Boolean {
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
        nodePaint.keys.retainAll(all.toSet())
        treeDirty = false
    }

    private fun layoutIfNeeded() {
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

    private fun sorted(element: Element) = paintChildren[element].orEmpty()
    private fun hit(element: Element, position: Vec2, includeDisabled: Boolean = false): Element? {
        if (!visibleAttached(element) || element.pointerPolicy == PointerPolicy.NONE) return null
        val enabled = includeDisabled || effective(element)
        val local = element.surface.fromRoot(position) ?: return null
        val bounds = Rect(0f, 0f, element.surface.bounds.width, element.surface.bounds.height)
        val clip = when (element.clipPolicy) {
            ClipPolicy.BOUNDS -> Rectangle.contains(local, bounds)
            ClipPolicy.CONTENT -> Rectangle.contains(local, element.surface.contentBox)
            ClipPolicy.SHAPE -> element.clipShape.contains(local, bounds)
            else -> true
        }
        if (!clip) return null
        sorted(element).asReversed().forEach { hit(it, position, includeDisabled)?.let { return it } }
        return if (enabled && element.pointerPolicy == PointerPolicy.AUTO && element.hitShape.contains(local, bounds.size)) element else null
    }

    override fun hitTest(point: Vec2): Element? {
        assertThread()
        layoutIfNeeded()
        return hit(root, point)
    }

    override fun releasePointerCapture(element: Element) {
        assertMutable()
        cancelCaptures { it === element }
    }

    override fun navigate(command: NavigationCommand): Boolean {
        assertMutable()
        if (lifecycle == UiLifecycle.CLOSED || closing) return false
        inputMode = InputMode.NAVIGATION
        layoutIfNeeded()
        return when (command) {
            NavigationCommand.NEXT -> focus.move(true)
            NavigationCommand.PREVIOUS -> focus.move(false)
            NavigationCommand.LEFT -> focus.move(NavigationDirection.LEFT)
            NavigationCommand.RIGHT -> focus.move(NavigationDirection.RIGHT)
            NavigationCommand.UP -> focus.move(NavigationDirection.UP)
            NavigationCommand.DOWN -> focus.move(NavigationDirection.DOWN)
            NavigationCommand.ACTIVATE -> {
                val target = focus.focused?.takeIf(::effective)
                val action = target?.semantics?.actions?.get("activate")
                if (action != null) {
                    action()
                    true
                } else false
            }

            NavigationCommand.CANCEL -> overlays.dismissTop()
        }
    }

    private fun hover() {
        val target = pointer?.let { hit(root, it, includeDisabled = true) }
        val next = generateSequence(target) { it.parent }.toSet()
        val leaving = (hovered - next).filter { !it.isDisposed }
        val entering = (next - hovered).toList()
        hovered.clear()
        hovered.addAll(next)
        var failure: Throwable? = null
        fun update(element: Element, value: Boolean) {
            try {
                element.setInteractionState(element.state.copy(hovered = value))
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
        }
        leaving.forEach { update(it, false) }
        entering.forEach { update(it, true) }
        failure?.let { throw it }
    }

    private fun input(original: InputEvent): Boolean {
        assertThread()
        if (lifecycle == UiLifecycle.CLOSED || closing) return false
        when (original) {
            is PointerEvent, is WheelEvent -> inputMode = InputMode.POINTER
            is KeyEvent, is TextCommit, is Composition -> inputMode = InputMode.KEYBOARD
        }
        layoutIfNeeded()
        cancelCaptures { !effective(it) }
        val event = when (original) {
            is PointerEvent -> original.copy(position = Vec2(original.position.x / settings.uiScale, original.position.y / settings.uiScale))
            is WheelEvent -> original.copy(position = Vec2(original.position.x / settings.uiScale, original.position.y / settings.uiScale))
            else -> original
        }
        if (event is WindowFocus && !event.focused) {
            consumedKeys.clear()
            var failure: Throwable? = null
            try {
                pointer = null
                hover()
            } catch (error: Throwable) {
                failure = error
            }
            try {
                cancelCaptures()
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
            try {
                focus.clear()
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
            failure?.let { throw it }
            return false
        }
        if (event is KeyEvent && event.key == Key.TAB) {
            if (event.action == KeyAction.DOWN) focus.move(!event.modifiers.shift)
            return true
        }
        if (event is PointerEvent) {
            pointer = if (event.action == PointerAction.LEAVE) null else event.position
            hover()
        }
        val position = when (event) {
            is PointerEvent -> event.position
            is WheelEvent -> event.position
            else -> null
        }
        val target = when (event) {
            is PointerEvent -> captures[event.pointerId]?.takeIf(::effective) ?: hit(root, event.position)
            is WheelEvent -> hit(root, event.position)
            else -> focus.focused?.takeIf(::effective)
        } ?: root
        val route = generateSequence(target as Element?) { it.parent }.toList().asReversed()
        val keyIdentity = (event as? KeyEvent)?.let { it.key to it.physicalCode }
        val flags = RoutingFlags().apply {
            if (event is WheelEvent) scrollRemainder = event.delta
            if (event is KeyEvent && event.action == KeyAction.UP && consumedKeys.remove(checkNotNull(keyIdentity))) consumed = true
        }

        fun dispatch(node: Element, phase: EventPhase) {
            if (effective(node)) {
                node.dispatch(RoutedEvent(event, target, node, phase, position?.let { node.surface.fromRoot(it) }, flags))
            }
        }
        route.forEach { if (!flags.stopped) dispatch(it, EventPhase.CAPTURE) }
        if (!flags.stopped) dispatch(target, EventPhase.TARGET)
        route.asReversed().forEach { if (!flags.stopped) dispatch(it, EventPhase.BUBBLE) }
        if (lifecycle == UiLifecycle.CLOSED || closing) return flags.consumed
        if (event is WheelEvent) {
            for (node in route.asReversed()) {
                val remainder = flags.scrollRemainder ?: event.delta
                if (flags.prevented || flags.stopped || (abs(remainder.x) < .00001f && abs(remainder.y) < .00001f)) break
                if (effective(node)) node.behavior?.handle(
                    node,
                    RoutedEvent(event, target, node, EventPhase.TARGET, position?.let { node.surface.fromRoot(it) }, flags)
                )
                if (lifecycle == UiLifecycle.CLOSED || closing) return flags.consumed
            }
        } else if (!flags.prevented && effective(target)) target.behavior?.handle(
            target,
            RoutedEvent(event, target, target, EventPhase.TARGET, position?.let { target.surface.fromRoot(it) }, flags)
        )
        if (lifecycle == UiLifecycle.CLOSED || closing) return flags.consumed
        if (event is KeyEvent && event.action == KeyAction.DOWN && !flags.consumed && !flags.prevented) {
            val direction = when (event.key) {
                Key.LEFT -> NavigationDirection.LEFT
                Key.RIGHT -> NavigationDirection.RIGHT
                Key.UP -> NavigationDirection.UP
                Key.DOWN -> NavigationDirection.DOWN
                else -> null
            }
            if (direction != null && focus.move(direction)) flags.consumed = true
        }
        if (event is PointerEvent) {
            flags.capture?.takeIf(::effective)?.let {
                captures[event.pointerId] = it
                if (event.action == PointerAction.DOWN) captureButtons[event.pointerId] = event.button
                it.setInteractionState(it.state.copy(pressed = true))
            }
                if (
                    event.action == PointerAction.CANCEL ||
                    event.action == PointerAction.UP &&
                    (captureButtons[event.pointerId] == null || captureButtons[event.pointerId] == event.button)
                ) {
                captureButtons.remove(event.pointerId)
                captures.remove(event.pointerId)?.takeIf { !it.isDisposed }?.let { it.setInteractionState(it.state.copy(pressed = false)) }
            }
        }
        if (event is KeyEvent && event.action != KeyAction.UP && flags.consumed) consumedKeys += checkNotNull(keyIdentity)
        return flags.consumed
    }

    private fun cancelCaptures(predicate: (Element) -> Boolean = { true }) {
        var failure: Throwable? = null
        captures.toMap().filterValues(predicate).forEach { (id, node) ->
            captures.remove(id)
            captureButtons.remove(id)
            if (!node.isDisposed) {
                try {
                    val flags = RoutingFlags()
                    val event = PointerEvent(PointerAction.CANCEL, pointer ?: Vec2.ZERO, pointerId = id)
                    val route = generateSequence(node as Element?) { it.parent }.toList().asReversed()
                    fun deliver(current: Element, phase: EventPhase) {
                        if (!current.isDisposed) current.dispatch(
                            RoutedEvent(
                                event,
                                node,
                                current,
                                phase,
                                if (current.surface.isAttached) current.surface.fromRoot(event.position) else null,
                                flags
                            )
                        )
                    }
                    route.forEach { if (!flags.stopped) deliver(it, EventPhase.CAPTURE) }
                    if (!flags.stopped) deliver(node, EventPhase.TARGET)
                    route.asReversed().forEach { if (!flags.stopped) deliver(it, EventPhase.BUBBLE) }
                    if (!node.isDisposed) node.behavior?.handle(node, RoutedEvent(event, node, node, EventPhase.TARGET, null, flags))
                } catch (error: Throwable) {
                    if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
                } finally {
                    try {
                        if (!node.isDisposed) node.setInteractionState(node.state.copy(pressed = false))
                    } catch (error: Throwable) {
                        if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
                    }
                }
            }
        }
        failure?.let { throw it }
    }

    override fun elementDetached(element: Element) {
        assertMutable()
        fun owned(node: Element) = generateSequence(node as Element?) { it.parent }.any { it === element }
        var failure: Throwable? = null
        try {
            cancelCaptures(::owned)
        } catch (error: Throwable) {
            failure = error
        }
        try {
            focus.detached(element)
        } catch (error: Throwable) {
            if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
        }
        val leaving = hovered.filter(::owned)
        hovered.removeAll(leaving.toSet())
        leaving.filter { !it.isDisposed }.forEach { node ->
            try {
                node.setInteractionState(node.state.copy(hovered = false, pressed = false))
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

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
                if (lifecycle != UiLifecycle.SUSPENDED) cancelCaptures()
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
                cancelCaptures()
                if (lifecycle != UiLifecycle.CLOSED) lifecycle = UiLifecycle.SUSPENDED
                return
            }
            layoutIfNeeded()
            cancelCaptures { !effective(it) }
            if (focus.focused?.let { !effective(it) } == true) focus.clear()
            hover()
            var all = nodes()
            var updatePass = 0
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
                hover()
                all = nodes()
            } while (true)
            var painted = 0
            if (paintDirty) {
                val buffer = ArrayList<DrawCommand>(commands.size)
                fun paint(element: Element) {
                    if (!visibleAttached(element)) return
                    var cached = nodePaint[element]
                    val previous = cached?.key
                    val tokens = element.resolvedTokens
                    val disabled = !element.isEffectivelyEnabled
                    if (
                        previous == null ||
                        previous.version != element.paintVersion ||
                        previous.transform != element.surface.localToParent ||
                        previous.bounds != element.surface.bounds ||
                        previous.content != element.surface.contentBox ||
                        previous.theme != theme ||
                        previous.settings != settings ||
                        previous.tokens !== tokens ||
                        previous.disabled != disabled
                    ) {
                        val key = PaintKey(
                            element.paintVersion,
                            element.surface.localToParent,
                            element.surface.bounds,
                            element.surface.contentBox,
                            theme,
                            settings,
                            tokens,
                            disabled
                        )
                        painted++
                        val before = ArrayList<DrawCommand>()
                        val after = ArrayList<DrawCommand>()
                        var destination: MutableList<DrawCommand> = before
                        val localCanvas = object : Canvas {
                            override fun emit(command: DrawCommand) {
                                destination += command
                            }
                        }
                        if (element.opacity != 1f) before += BeginGroup(element.opacity)
                        before += Save
                        before += Concat(element.surface.localToParent)
                        val bounds = Rect(0f, 0f, element.surface.bounds.width, element.surface.bounds.height)
                        when (element.clipPolicy) {
                            ClipPolicy.BOUNDS -> before += Clip(Rectangle, bounds)
                            ClipPolicy.CONTENT -> before += Clip(Rectangle, element.surface.contentBox)
                            ClipPolicy.SHAPE -> before += Clip(element.clipShape, bounds)
                            else -> {}
                        }
                        val context = PaintContext(element, bounds, element.state.copy(disabled = disabled), tokens)
                        fun layer(phase: String, draw: () -> Unit) {
                            val start = destination.size
                            try {
                                draw()
                            } catch (error: Throwable) {
                                if (settings.errorPolicy.handle(UiFailure(error, phase, element.id)) == ErrorDecision.THROW) throw error
                                while (destination.size > start) destination.removeAt(destination.lastIndex)
                                diagnostics.report(
                                    Diagnostic(
                                        "paint-error",
                                        error.message ?: error::class.simpleName.orEmpty(),
                                        element.id,
                                        DiagnosticSeverity.ERROR
                                    )
                                )
                            }
                        }
                        layer("background painter") { element.background?.paint(localCanvas, context) }
                        layer("content painter") { element.paint(localCanvas) }
                        destination = after
                        layer("foreground painter") { element.foreground?.paint(localCanvas, context) }
                        after += Restore
                        if (element.opacity != 1f) after += EndGroup
                        cached = NodePaint(key, before, after)
                        nodePaint[element] = cached
                    }
                    buffer.addAll(cached.before)
                    sorted(element).forEach(::paint)
                    buffer.addAll(cached.after)
                }
                painting = true
                try {
                    paint(root)
                } finally {
                    painting = false
                }
                commands = unmodifiableList(buffer)
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
            cleanup { cancelCaptures() }
            cleanup { focus.clear() }
            cleanup { subscription.close() }
            synchronized(posted) { posted.clear() }
            cleanup { root.close() }
            cleanup { session.close() }
        } finally {
            lifecycle = UiLifecycle.CLOSED
            closing = false
            captures.clear()
            captureButtons.clear()
            consumedKeys.clear()
            hovered.clear()
            commands = emptyList()
            portalChildren = emptyMap()
            treeOrder = emptyMap()
            paintChildren = emptyMap()
            flatNodes = emptyList()
            nodePaint.clear()
        }
        failure?.let { throw it }
    }

    inner class SimpleFocus : FocusManager {

        private val scopes = mutableListOf<FocusScope>()
        private fun inside(element: Element, scope: Element) = generateSequence(element as Element?) { it.parent }.any { it === scope }
        private fun permitted(element: Element) = scopes.lastOrNull()?.let { inside(element, it.root) } ?: true
        override var focused: Element? = null
            private set

        private fun unfocus(element: Element) {
            if (!element.isDisposed) element.setInteractionState(
                element.state.copy(
                    focused = false,
                    focusVisible = false,
                    pressed = element.state.pressed && captures.containsValue(element)
                )
            )
        }

        override fun request(element: Element, visible: Boolean): Boolean {
            assertMutable()
            if (lifecycle == UiLifecycle.CLOSED || closing || !element.focusable || !effective(element) || !permitted(element)) return false
            if (focused !== element) host.maskHeldInput()
            focused?.takeIf { it !== element }?.let(::unfocus)
            focused = element
            element.setInteractionState(element.state.copy(focused = true, focusVisible = visible))
            return true
        }

        override fun clear() {
            assertMutable()
            val previous = focused
            focused = null
            previous?.let(::unfocus)
        }

        override fun move(forward: Boolean): Boolean {
            assertMutable()
            val list = nodes().filter { it.focusable && effective(it) && it.tabIndex >= 0 && permitted(it) }.sortedBy { it.tabIndex }
            if (list.isEmpty()) {
                clear()
                return false
            }
            val index = list.indexOf(focused)
            return request(list[if (forward) (index + 1) % list.size else if (index <= 0) list.lastIndex else index - 1])
        }

        override fun move(direction: NavigationDirection): Boolean {
            assertMutable()
            layoutIfNeeded()
            val origin = focused?.takeIf(::effective) ?: return move(true)
            val from = origin.surface.mapRect(Rect(0f, 0f, origin.surface.bounds.width, origin.surface.bounds.height))
            val x = from.x + from.width / 2
            val y = from.y + from.height / 2
            val horizontal = direction == NavigationDirection.LEFT || direction == NavigationDirection.RIGHT
            val positive = direction == NavigationDirection.RIGHT || direction == NavigationDirection.DOWN
            val candidate = nodes().asSequence().filter { it !== origin && it.focusable && it.tabIndex >= 0 && effective(it) && permitted(it) }
                .mapNotNull { element ->
                    val bounds = element.surface.mapRect(Rect(0f, 0f, element.surface.bounds.width, element.surface.bounds.height))
                    val dx = bounds.x + bounds.width / 2 - x
                    val dy = bounds.y + bounds.height / 2 - y
                    val primary = (if (horizontal) dx else dy) * (if (positive) 1 else -1)
                    if (primary <= .0001f) null else {
                        val perpendicular = abs(if (horizontal) dy else dx)
                        val overlap =
                            if (horizontal) bounds.y < from.bottom && bounds.bottom > from.y else bounds.x < from.right && bounds.right > from.x
                        element to (primary + perpendicular * 2 + (if (overlap) 0f else 100000f))
                    }
                }.minByOrNull { it.second }?.first ?: return false
            return request(candidate)
        }

        override fun pushScope(root: Element, restoreFocus: Boolean): Subscription {
            assertMutable()
            require(effective(root)) { "Focus scope must be attached, visible and enabled." }
            host.maskHeldInput()
            val scope = FocusScope(root, focused, restoreFocus)
            scopes += scope
            if (focused?.let { !permitted(it) } == true) clear()
            if (focused == null) move()
            return Subscription {
                assertMutable()
                val wasTop = scopes.lastOrNull() === scope
                if (scopes.remove(scope) && wasTop && scope.restore) {
                    val previous = scope.previous
                    if (previous == null || !request(previous)) move()
                }
            }
        }

        internal fun detached(element: Element) {
            val removed = scopes.filter { inside(it.root, element) }
            val wasTop = scopes.lastOrNull() in removed
            scopes.removeAll(removed.toSet())
            if (focused?.let { inside(it, element) } == true) clear()
            if (wasTop) {
                val previous = removed.lastOrNull()?.takeIf { it.restore }?.previous
                if (previous != null && !inside(previous, element) && request(previous)) return
                if (!closing) nodes().filter { !inside(it, element) && it.focusable && effective(it) && permitted(it) && it.tabIndex >= 0 }
                    .minByOrNull { it.tabIndex }?.let { request(it) }
            }
        }
    }
}
