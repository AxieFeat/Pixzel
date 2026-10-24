package pixzel.component

import pixzel.Diagnostic
import pixzel.DiagnosticSeverity
import pixzel.ErrorDecision
import pixzel.Ui
import pixzel.UiFailure
import pixzel.graphics.Canvas
import pixzel.graphics.Rectangle
import pixzel.graphics.Shape
import pixzel.input.EventPhase
import pixzel.input.InputBehavior
import pixzel.input.PointerPolicy
import pixzel.input.RoutedEvent
import pixzel.layout.Constraints
import pixzel.layout.LayoutConfig
import pixzel.layout.LayoutEnvironment
import pixzel.layout.LayoutPolicy
import pixzel.layout.Placeable
import pixzel.layout.overlayLayout
import pixzel.layout.resolveSize
import pixzel.layout.unit.SizeSpec
import pixzel.layout.unit.WrapContent
import pixzel.resource.AttachmentScope
import pixzel.resource.ResourceScope
import pixzel.resource.Subscription
import pixzel.state.MutableState
import pixzel.state.StateKey
import pixzel.state.getValue
import pixzel.state.setValue
import pixzel.state.stateOf
import pixzel.style.ResolvedTokens
import pixzel.style.StyleScope
import pixzel.style.Theme
import pixzel.style.isEffectivelyEnabled
import pixzel.surface.Placement
import pixzel.surface.Rect
import pixzel.surface.ReferenceBox
import pixzel.surface.Size
import pixzel.surface.Surface
import pixzel.surface.Transform
import pixzel.surface.Vec2
import pixzel.ui
import java.util.Collections.unmodifiableList
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration

/**
 * Convenience base for external components. All collaborators use public contracts.
 */
abstract class AbstractElement(final override val id: String = "") : Element {

    private val ownerThread = Thread.currentThread()

    companion object {

        private val nextGeneration = AtomicLong()
    }

    final override val generation = nextGeneration.incrementAndGet()
    final override var paintVersion = 0L
        private set

    private val mutableChildren = mutableListOf<Element>()
    private val readonlyChildren = unmodifiableList(mutableChildren)
    final override val children: List<Element> get() = readonlyChildren
    final override var parent: Element? = null
        private set
    final override var ui: Ui? = null
        private set

    final override val resources = ResourceScope()
    final override val attachments =
        resources.own(AttachmentScope { check(Thread.currentThread() === ownerThread) { "Attachment scope belongs to another thread" } })
    final override val frameUpdates =
        resources.own(FrameUpdates { check(Thread.currentThread() === ownerThread) { "Element '$id' must be used on its owner thread" } })
    final override var isDisposed = false
        private set

    private val computed = SimpleSurface()
    final override val surface: Surface get() = computed
    final override val layout = LayoutConfig { invalidate(it) }

    protected fun <T> property(initial: T, reason: Invalidation = Invalidation.PAINT) = object : ReadWriteProperty<Any?, T> {
        private var value = initial

        override fun getValue(thisRef: Any?, property: KProperty<*>) = value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (this.value != value) {
                invalidate(reason)
                this.value = value
            }
        }
    }

    override var layoutPolicy: LayoutPolicy by property(overlayLayout(), Invalidation.MEASURE)
    override var placement: Placement by property(Placement(), Invalidation.ARRANGE)
    override var paintTransform: Transform by property(Transform.IDENTITY, Invalidation.TRANSFORM)
    override var portalTarget: Element? = null
        set(value) {
            require(value !== this && (value == null || generateSequence(value.parent) { it.parent }.none { it === this })) { "Portal cannot target its own subtree" }
            invalidate(Invalidation.TREE)
            field = value
        }
    override var opacity: Float = 1f
        set(value) {
            require(value in 0f..1f)
            invalidate()
            field = value
        }
    override var visibility: Visibility by property(Visibility.VISIBLE, Invalidation.MEASURE)
    override var enabled: Boolean = true
        set(value) {
            if (field != value) {
                invalidate(Invalidation.INPUT)
                field = value
                refreshInheritedStyles()
            }
        }

    override var pointerPolicy: PointerPolicy by property(PointerPolicy.PASS_THROUGH, Invalidation.INPUT)
    override var focusable: Boolean by property(false, Invalidation.INPUT)
    override var tabIndex: Int by property(0, Invalidation.INPUT)
    override var zIndex: Int by property(0, Invalidation.TREE)
    override var layer: Layer by property(Layer.CONTENT, Invalidation.TREE)
    override var clipPolicy: ClipPolicy by property(ClipPolicy.NONE, Invalidation.INPUT)
    override var clipShape: Shape by property(Rectangle, Invalidation.INPUT)
    override var hitShape: HitShape by property(RectangleHitShape, Invalidation.INPUT)

    override var semantics: Semantics by property(Semantics(), Invalidation.SEMANTICS)
    override var background: Painter? by property(null)
    override var foreground: Painter? by property(null)
    override var behavior: InputBehavior? by property(null, Invalidation.INPUT)
    final override var state = ElementState()
        private set

    private var tokenParent: ResolvedTokens? = null
    private var tokenScope: StyleScope? = null
    private var tokenState: ElementState? = null
    private var tokenResult: ResolvedTokens? = null

    override var styleScope = StyleScope()
        set(value) {
            if (field != value) {
                invalidate(Invalidation.PAINT)
                val before = resolvedTokens
                field = value
                styleChanged(before)
            }
        }
    final override val resolvedTokens: ResolvedTokens
        get() {
            val inherited = parent?.resolvedTokens ?: ui?.theme?.tokens ?: Theme.DARK.tokens
            val effectiveState = if (state.disabled == !isEffectivelyEnabled) state else state.copy(disabled = !isEffectivelyEnabled)

            if (tokenParent !== inherited || tokenScope !== styleScope || tokenState != effectiveState || tokenResult == null) {
                tokenResult = styleScope.resolve(inherited, effectiveState)
                tokenParent = inherited
                tokenScope = styleScope
                tokenState = effectiveState
            }

            return tokenResult!!
        }

    private fun styleChanged(before: ResolvedTokens) {
        val after = resolvedTokens
        invalidate(if (before.typography != after.typography) Invalidation.MEASURE else Invalidation.PAINT)
        if (before != after) children.forEach { if (it is AbstractElement) it.refreshInheritedStyles() else it.invalidate(Invalidation.MEASURE) }
    }

    internal fun refreshInheritedStyles() {
        val before = tokenResult
        val after = resolvedTokens
        if (before != null && before != after) invalidate(if (before.typography != after.typography) Invalidation.MEASURE else Invalidation.PAINT)
        children.forEach { if (it is AbstractElement) it.refreshInheritedStyles() else it.invalidate(Invalidation.MEASURE) }
    }

    private val handlers = mutableListOf<Pair<EventPhase, (RoutedEvent) -> Unit>>()
    private val localStates = IdentityHashMap<StateKey<*>, MutableState<*>>()

    private var measuring = false
    private var committingPortal = false
    private var measureDirty = true
    private var cachedConstraints: Constraints? = null
    private var cachedTextScale = 0f
    private var cachedPlaceable: Placeable? = null
    private var lastInvalidation = Invalidation.TREE
    final override val inspection get() = ElementInspection(path(), cachedConstraints, cachedPlaceable?.size, lastInvalidation)
    private var detachedMeasureVersion = 0L
    var measureCount = 0
        private set

    override fun invalidate(reason: Invalidation) {
        check(Thread.currentThread() === ownerThread) { "Element '$id' must be mutated on its owner thread" }
        check(!isDisposed) { "Element '$id' has been disposed" }
        ui?.invalidate(reason)
        lastInvalidation = reason
        paintVersion++
        if (reason == Invalidation.MEASURE || reason == Invalidation.TREE) {
            measureDirty = true
            parent?.invalidate(reason)
        } else if (reason == Invalidation.ARRANGE) {
            measureDirty = true
            parent?.invalidate(Invalidation.MEASURE)
        }
    }

    override fun add(child: Element): Element {
        invalidate(Invalidation.TREE)
        require(!child.isDisposed && child.parent == null && child.ui == null) { "Child '${child.id}' must be detached before adding to '$id'" }
        require(child !== this && ancestors().none { it === child }) { "Tree cycle at '$id'" }
        require(child.id.isEmpty() || children.none { it.id == child.id }) { "Duplicate key '${child.id}' at ${path()}" }

        mutableChildren += child
        child.attach(this, ui)
        return child
    }

    override fun remove(child: Element, dispose: Boolean): Boolean {
        invalidate(Invalidation.TREE)
        if (!mutableChildren.remove(child)) return false

        var failure: Throwable? = null
        try {
            child.attach(null, null)
        } catch (error: Throwable) {
            failure = error
        }

        if (dispose) try {
            child.close()
        } catch (error: Throwable) {
            if (failure == null) failure = error else if (failure !== error) failure.addSuppressed(error)
        }
        failure?.let { throw it }
        return true
    }

    override fun reorder(children: List<Element>) {
        invalidate(Invalidation.TREE)
        require(children.size == mutableChildren.size && children.toSet() == mutableChildren.toSet()) { "Reorder must contain each existing child once at ${path()}" }
        mutableChildren.clear()
        mutableChildren.addAll(children)
    }

    override fun attach(parent: Element?, ui: Ui?) {
        check(Thread.currentThread() === ownerThread) { "Element '$id' must attach on its owner thread" }
        check(!isDisposed)
        this.ui?.assertThread()
        ui?.assertThread()
        if (this.ui !== ui) measureDirty = true
        var error: Throwable? = null
        fun attempt(action: () -> Unit) {
            try {
                action()
            } catch (e: Throwable) {
                if (error == null) error = e else error!!.addSuppressed(e)
            }
        }
        if (this.ui != null && this.ui !== ui) attempt { this.ui!!.elementDetached(this) }
        this.parent = parent
        this.ui = ui
        attempt { attachments.setAttached(ui != null) }
        computed.attached = ui != null
        mutableChildren.forEach { attempt { it.attach(this, ui) } }
        if (ui == null) state = state.copy(hovered = false, pressed = false, focused = false, focusVisible = false)
        error?.let { throw it }
    }

    override fun setInteractionState(state: ElementState) {
        if (this.state != state) {
            invalidate(Invalidation.PAINT)
            val before = resolvedTokens
            this.state = state
            styleChanged(before)
        }
    }

    override fun onEvent(phase: EventPhase, handler: (RoutedEvent) -> Unit): Subscription {
        invalidate(Invalidation.INPUT)
        val pair = phase to handler
        handlers += pair
        var closed = false
        return Subscription {
            if (!closed) {
                closed = true
                handlers.remove(pair)
            }
        }
    }

    override fun dispatch(event: RoutedEvent) {
        handlers.toList().filter { it.first == event.phase }.forEach {
            if (!isDisposed) try {
                it.second(event)
            } catch (error: Throwable) {
                val owner = ui
                if (owner == null || owner.settings.errorPolicy.handle(UiFailure(error, "input handler", path())) == ErrorDecision.THROW) throw error
                owner.diagnostics.report(
                    Diagnostic(
                        "handler-error",
                        error.message ?: error::class.simpleName.orEmpty(),
                        path(),
                        DiagnosticSeverity.ERROR
                    )
                )
            }
        }
    }

    protected open fun intrinsic(constraints: Constraints): Size = Size.ZERO
    protected open fun intrinsicBaseline(contentSize: Size): Float? = null

    override fun measure(constraints: Constraints): Placeable {
        check(!measuring) { "Measurement cycle at ${path()}" }

        val constraints = portalTarget?.let { target ->
            val owner = ui
            val box = target.surface.contentBox
            val w = if (box.width > 0) box.width else owner?.let { it.host.metrics.logicalSize.width / it.settings.uiScale } ?: constraints.maxWidth
            val h =
                if (box.height > 0) box.height else owner?.let { it.host.metrics.logicalSize.height / it.settings.uiScale } ?: constraints.maxHeight
            Constraints(maxWidth = w, maxHeight = h)
        } ?: constraints
        val textScale = ui?.settings?.textScale ?: 1f
        if (ui != null && !measureDirty && cachedConstraints == constraints && cachedTextScale == textScale) return cachedPlaceable!!

        measuring = true
        measureCount++
        try {
            val pad = layout.padding
            require(layout.minWidth.value >= 0 && layout.minHeight.value >= 0)
            require(layout.aspectRatio == null || (layout.aspectRatio!!.isFinite() && layout.aspectRatio!! > 0))

            val minW = layout.minWidth.value.coerceIn(constraints.minWidth, constraints.maxWidth)
            val minH = layout.minHeight.value.coerceIn(constraints.minHeight, constraints.maxHeight)
            val maxW = (layout.maxWidth?.value ?: constraints.maxWidth).coerceIn(minW, constraints.maxWidth)
            val maxH = (layout.maxHeight?.value ?: constraints.maxHeight).coerceIn(minH, constraints.maxHeight)
            val bounded = Constraints(minW, maxW, minH, maxH)
            val intrinsic = intrinsic(bounded.inset(pad))

            fun axis(spec: SizeSpec, value: Float, max: Float, min: Float): Float? =
                if (spec == WrapContent && min != max) null else resolveSize(spec, value, max).coerceIn(min, max)

            val fixedW = axis(layout.width, intrinsic.width + pad.horizontal, maxW, minW)
            val fixedH = axis(layout.height, intrinsic.height + pad.vertical, maxH, minH)
            val childConstraints = Constraints(
                fixedW?.let { (it - pad.horizontal).coerceAtLeast(0f) } ?: 0f,
                (fixedW ?: maxW).let { (it - pad.horizontal).coerceAtLeast(0f) },
                fixedH?.let { (it - pad.vertical).coerceAtLeast(0f) } ?: 0f,
                (fixedH ?: maxH).let { (it - pad.vertical).coerceAtLeast(0f) })
            val visible = children.filter { it.visibility != Visibility.COLLAPSED && it.portalTarget == null }
            val portals = children.filter { it.visibility != Visibility.COLLAPSED && it.portalTarget != null }

            val owner = ui
            val rootSize =
                owner?.let { Size(it.host.metrics.logicalSize.width / it.settings.uiScale, it.host.metrics.logicalSize.height / it.settings.uiScale) }
                    ?: Size.ZERO
            var env = LayoutEnvironment(ui?.settings?.textScale ?: 1f, rootSize)
            if (fixedW != null && fixedH != null) {
                val content = pad.inset(Rect(0f, 0f, fixedW, fixedH)).size
                env = env.copy(
                    contentBox = Rect(0f, 0f, content.width, content.height),
                    borderBox = Rect(-pad.left, -pad.top, fixedW, fixedH),
                    safeAreaBox = Rect(0f, 0f, content.width, content.height)
                )
            }
            var plan = layoutPolicy.measure(visible, childConstraints, env)

            var size = bounded.constrain(
                Size(
                    fixedW ?: maxOf(intrinsic.width, plan.size.width) + pad.horizontal,
                    fixedH ?: maxOf(intrinsic.height, plan.size.height) + pad.vertical
                )
            )
            layout.aspectRatio?.let { ratio ->
                if (fixedW != null && fixedH == null) size =
                    bounded.constrain(Size(size.width, size.width / ratio)) else if (fixedH != null && fixedW == null) size =
                    bounded.constrain(Size(size.height * ratio, size.height))
            }
            val finalContent = pad.inset(Rect(0f, 0f, size.width, size.height)).size
            val finalEnv = env.copy(
                contentBox = Rect(0f, 0f, finalContent.width, finalContent.height),
                borderBox = Rect(-pad.left, -pad.top, size.width, size.height),
                safeAreaBox = Rect(0f, 0f, finalContent.width, finalContent.height)
            )
            if (plan.size != finalContent || env != finalEnv) plan = layoutPolicy.measure(visible, Constraints.fixed(finalContent), finalEnv)
            var placedVersion = Long.MIN_VALUE
            var lastPosition: Vec2? = null
            var lastPaintTransform: Transform? = null
            var lastParentTransform: Transform? = null
            val detachedVersion = ++detachedMeasureVersion
            val result = object : Placeable {
                override val size = size
                override val baseline = (plan.baseline ?: intrinsicBaseline(finalContent))?.plus(pad.top)
                override fun place(position: Vec2) {
                    val version = ui?.layoutVersion ?: detachedVersion
                    check(placedVersion != version) { "Child placed twice at ${path()}" }
                    placedVersion = version
                    val target = portalTarget
                    if (target != null && !committingPortal) {
                        checkNotNull(ui) { "A portal must be attached before arrangement" }.deferPlacement(this@AbstractElement, target) {
                            committingPortal = true
                            measureDirty = true
                            try {
                                measure(constraints).place(position)
                            } finally {
                                committingPortal = false
                            }
                        }
                        return
                    }
                    val position = if (target == null) position else {
                        val box = if (placement.referenceBox == ReferenceBox.BORDER) Rect(
                            0f,
                            0f,
                            target.surface.bounds.width,
                            target.surface.bounds.height
                        ) else target.surface.contentBox
                        Vec2(
                            box.x + placement.anchor.x * box.width - placement.pivot.x * size.width + placement.offset.x,
                            box.y + placement.anchor.y * box.height - placement.pivot.y * size.height + placement.offset.y
                        )
                    }
                    val parentTransform = (target ?: parent)?.surface?.localToRoot ?: Transform.IDENTITY
                    val boundsChanged =
                        computed.bounds.x != position.x || computed.bounds.y != position.y ||
                        computed.bounds.width != size.width || computed.bounds.height != size.height
                    if (boundsChanged) computed.bounds = Rect(position.x, position.y, size.width, size.height)
                    val contentWidth = (size.width - pad.horizontal).coerceAtLeast(0f)
                    val contentHeight = (size.height - pad.vertical).coerceAtLeast(0f)
                    if (
                        computed.contentBox.x != pad.left || computed.contentBox.y != pad.top ||
                        computed.contentBox.width != contentWidth || computed.contentBox.height != contentHeight
                    ) computed.contentBox =
                        Rect(pad.left, pad.top, contentWidth, contentHeight)
                    val localChanged = lastPosition != position || lastPaintTransform != paintTransform
                    if (localChanged) computed.localToParent = Transform.translation(position.x, position.y) * paintTransform
                    if (localChanged || lastParentTransform != parentTransform) computed.localToRoot = parentTransform * computed.localToParent
                    lastPosition = position
                    lastPaintTransform = paintTransform
                    lastParentTransform = parentTransform
                    computed.attached = ui != null
                    if (pad.left == 0f && pad.top == 0f) plan.children.forEach { it.child.place(it.position) }
                    else {
                        val origin = Vec2(pad.left, pad.top)
                        plan.children.forEach { it.child.place(it.position + origin) }
                    }
                    portals.forEach { it.measure(Constraints()).place(Vec2.ZERO) }
                }
            }
            cachedPlaceable = result
            cachedConstraints = constraints
            cachedTextScale = textScale
            measureDirty = false
            return result
        } finally {
            measuring = false
        }
    }

    override fun update(delta: Duration) = false

    @Suppress("UNCHECKED_CAST")
    override fun <T> state(key: StateKey<T>, initial: () -> T): MutableState<T> {
        check(Thread.currentThread() === ownerThread && !isDisposed)
        return localStates.getOrPut(key) { stateOf(initial(), name = key.name) } as MutableState<T>
    }

    override fun paint(canvas: Canvas) {}
    override fun close() {
        if (isDisposed) return
        ui?.assertThread()
        var error: Throwable? = null
        fun attempt(action: () -> Unit) {
            try {
                action()
            } catch (e: Throwable) {
                if (error == null) error = e else error!!.addSuppressed(e)
            }
        }
        attempt { parent?.remove(this, false) }
        attempt { attach(null, null) }
        mutableChildren.toList().forEach { attempt { it.close() } }
        mutableChildren.clear()
        handlers.clear()
        localStates.clear()
        cachedPlaceable = null
        attempt { resources.close() }
        isDisposed = true
        error?.let { throw it }
    }

    private fun ancestors() = generateSequence(parent) { it.parent }
    private fun path() = (ancestors().toList().asReversed() + this).joinToString("/") { it.id.ifEmpty { "<anonymous>" } }
}

internal class SimpleSurface : Surface {

    override var bounds = Rect.ZERO
    override var contentBox = Rect.ZERO
    override var localToParent = Transform.IDENTITY
    override var localToRoot = Transform.IDENTITY
    var attached = false
    override val isAttached get() = attached
    private fun checkAttached() {
        check(attached) { "Detached Surface has no root coordinates" }
    }

    override fun toParent(point: Vec2): Vec2 {
        checkAttached()
        return localToParent.map(point)
    }

    override fun toRoot(point: Vec2): Vec2 {
        checkAttached()
        return localToRoot.map(point)
    }

    override fun fromRoot(point: Vec2): Vec2? {
        checkAttached()
        return localToRoot.inverse()?.map(point)
    }

    override fun mapRect(rect: Rect): Rect {
        checkAttached()
        return localToRoot.mapRect(rect)
    }
}
