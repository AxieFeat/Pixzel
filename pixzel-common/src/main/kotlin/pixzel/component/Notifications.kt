package pixzel.component

import pixzel.Ui
import pixzel.UiLifecycle
import pixzel.dsl.ContentScope
import pixzel.graphics.RoundedRectangle
import pixzel.graphics.Solid
import pixzel.graphics.rgba
import pixzel.input.PointerPolicy
import pixzel.layout.columnLayout
import pixzel.layout.rowLayout
import pixzel.layout.unit.Dp
import pixzel.layout.unit.dp
import pixzel.layout.unit.fillAvailable
import pixzel.layout.unit.wrapContent
import pixzel.surface.Insets
import pixzel.surface.Placement
import pixzel.surface.Rect
import pixzel.surface.ReferenceBox
import pixzel.surface.Vec2
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class NotificationOverflow {
    DROP_OLDEST,
    DROP_NEWEST,
    QUEUE
}
interface Notification : AutoCloseable {

    val element: Element
    override fun close()
}

/** Named content slots accept ordinary elements and own their complete subtrees. */
class NotificationSlots internal constructor(private val ui: Ui, element: Element, dismiss: () -> Unit) {

    private val row = ui.components.surface("notification-slots").apply {
        layout.width = fillAvailable
        layout.height = fillAvailable
        layoutPolicy = rowLayout(8.dp)
    }
    val icon = ui.components.surface("icon").apply {
        layout.width = wrapContent
        layout.height = fillAvailable
    }
    private val text = ui.components.surface("text").apply {
        layout.width = fillAvailable
        layout.height = fillAvailable
        layoutPolicy = columnLayout()
    }
    val title = ui.components.surface("title").apply {
        layout.width = fillAvailable
        layout.height = wrapContent
    }
    val body = ui.components.surface("body").apply {
        layout.width = fillAvailable
        layout.height = fillAvailable
    }
    val actions = ui.components.surface("actions").apply {
        layout.width = fillAvailable
        layout.height = wrapContent
        layoutPolicy = rowLayout(8.dp)
    }
    val close = ui.components.button("close").apply {
        layout.width = 24.dp
        layout.height = 24.dp
        layout.padding = Insets.ZERO
        setLabel("×")
        resources.own(onClick(dismiss))
    }

    init {
        element.add(row)
        row.add(icon)
        row.add(text)
        row.add(close)
        text.add(title)
        text.add(body)
        text.add(actions)
    }

    fun icon(build: ContentScope.() -> Unit) {
        ContentScope(ui, icon).build()
    }

    fun title(build: ContentScope.() -> Unit) {
        ContentScope(ui, title).build()
    }

    fun body(build: ContentScope.() -> Unit) {
        ContentScope(ui, body).build()
    }

    fun actions(build: ContentScope.() -> Unit) {
        ContentScope(ui, actions).build()
    }

    fun close(content: Element) {
        close.setContent(content)
    }
}

/**
 * Toasts are ordinary Surfaces. A FIFO queue fills the visible limit and the current safe viewport;
 * entries that do not fit remain detached and their timers pause. Very short viewports show one
 * height-constrained entry under an exact content clip. Capacity overflow follows [overflow].
 */
class NotificationHost private constructor(
    private val owner: Ui,
    private val limit: Int,
    private val capacity: Int,
    private val overflow: NotificationOverflow
) : AbstractElement() {

    private inner class Entry(override val element: Element, val timeout: Duration?, val pause: Boolean, val height: Float) : Notification {

        var remaining = timeout
        var closed = false
        override fun close() {
            owner.assertThread()
            if (closed) return
            closed = true
            entries.remove(this)
            var failure: Throwable? = null
            try {
                element.close()
            } catch (error: Throwable) {
                failure = error
            }
            try {
                promote()
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure.addSuppressed(error)
            }
            failure?.let { throw it }
        }
    }

    private val entries = mutableListOf<Entry>()
    private var closing = false
    private var viewportHeight = 0f
    private var viewportWidth = 0f

    init {
        require(limit > 0 && capacity >= limit)
        portalTarget = owner.root
        layer = Layer.OVERLAY
        layout.width = 320.dp
        layout.height = wrapContent
        layoutPolicy = columnLayout(8.dp)
        clipPolicy = ClipPolicy.CONTENT
        fitViewport()
    }

    private fun fitViewport() {
        val metrics = owner.host.metrics
        val scale = owner.settings.uiScale
        val safe = Rect(
            metrics.safeInsets.left / scale, metrics.safeInsets.top / scale,
            ((metrics.logicalSize.width - metrics.safeInsets.horizontal) / scale).coerceAtLeast(0f),
            ((metrics.logicalSize.height - metrics.safeInsets.vertical) / scale).coerceAtLeast(0f)
        )
        val insetX = minOf(24f, ((safe.width - minOf(320f, safe.width)) / 2).coerceAtLeast(0f))
        val insetY = minOf(24f, ((safe.height - minOf(72f, safe.height)) / 2).coerceAtLeast(0f))
        val availableWidth = (safe.width - insetX * 2).coerceAtLeast(0f)
        val availableHeight = (safe.height - insetY * 2).coerceAtLeast(0f)
        layout.maxWidth = availableWidth.dp
        layout.maxHeight = availableHeight.dp
        placement = Placement(offset = Vec2(safe.x + insetX, safe.y + insetY), referenceBox = ReferenceBox.BORDER)
        viewportWidth = availableWidth
        viewportHeight = availableHeight
    }

    fun show(timeout: Duration? = 4.seconds, pauseOnHoverOrFocus: Boolean = true, content: ContentScope.() -> Unit): Notification =
        showEntry(timeout, pauseOnHoverOrFocus, 72f, content)

    /** Taller structured toast; bounded FIFO promotion uses this height and the safe viewport. */
    fun showSlots(
        timeout: Duration? = 4.seconds,
        pauseOnHoverOrFocus: Boolean = true,
        height: Dp = 112.dp,
        content: NotificationSlots.() -> Unit
    ): Notification {
        require(height.value > 0f) { "Notification height must be positive" }
        var notification: Notification? = null
        return showEntry(timeout, pauseOnHoverOrFocus, height.value) {
            NotificationSlots(
                owner,
                parent
            ) { notification?.close() }.content()
        }.also { notification = it }
    }

    private fun showEntry(timeout: Duration?, pauseOnHoverOrFocus: Boolean, height: Float, content: ContentScope.() -> Unit): Notification {
        owner.assertThread()
        check(!isDisposed && !closing && owner.lifecycle != UiLifecycle.CLOSED) { "NotificationHost is closed" }
        require(timeout == null || timeout.isFinite() && !timeout.isNegative())
        val element = owner.components.surface().apply {
            layout.width = fillAvailable
            layout.height = height.dp
            layout.padding = Insets.all(12.dp)
            pointerPolicy = PointerPolicy.AUTO
            background = Painter { canvas, context -> canvas.fill(RoundedRectangle(8f), context.bounds, Solid(0x26354AFF.rgba)) }
        }
        val entry = Entry(element, timeout, pauseOnHoverOrFocus, height)
        try {
            // Build before queue mutation, so a failed builder neither evicts nor creates a ghost.
            ContentScope(owner, element).content()
            if (entries.size >= capacity) {
                when (overflow) {
                    NotificationOverflow.DROP_OLDEST -> entries.first().close()
                    NotificationOverflow.DROP_NEWEST -> {
                        entry.close()
                        return entry
                    }

                    NotificationOverflow.QUEUE -> error("Notification queue capacity $capacity exceeded")
                }
            }
            entries += entry
            promote()
            return entry
        } catch (error: Throwable) {
            try {
                entry.close()
            } catch (cleanup: Throwable) {
                if (cleanup !== error) error.addSuppressed(cleanup)
            }
            throw error
        }
    }

    private fun promote() {
        if (isDisposed || closing) return
        fitViewport()
        val visible = linkedSetOf<Entry>()
        var used = 0f
        if (viewportWidth > 0f && viewportHeight > 0f) for (entry in entries) {
            if (visible.size >= limit) break
            val height = minOf(entry.height, viewportHeight)
            val next = used + (if (visible.isEmpty()) 0f else 8f) + height
            if (visible.isNotEmpty() && next > viewportHeight) break
            visible += entry
            used = next
        }
        entries.filter { it !in visible && it.element.parent === this }.forEach { remove(it.element, false) }
        visible.forEach { entry ->
            entry.element.layout.height = minOf(entry.height, viewportHeight).dp
            if (entry.element.parent == null) add(entry.element)
        }
    }

    override fun update(delta: Duration): Boolean {
        promote()
        var changed = false
        entries.filter { it.element.parent === this }.toList().forEach { entry ->
            val focused = generateSequence(owner.focus.focused) { it.parent }.any { it === entry.element }
            if (!entry.pause || !entry.element.state.hovered && !focused) {
                entry.remaining = entry.remaining?.minus(delta)
                if (entry.remaining?.let { it <= Duration.ZERO } == true) {
                    entry.close()
                    changed = true
                }
            }
        }
        return changed
    }

    override fun close() {
        owner.assertThread()
        if (isDisposed || closing) return
        closing = true
        var failure: Throwable? = null
        fun cleanup(action: () -> Unit) {
            try {
                action()
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
        }
        entries.toList().forEach { entry -> cleanup { entry.close() } }
        entries.clear()
        cleanup { super.close() }
        failure?.let { throw it }
    }

    companion object {

        fun of(ui: Ui, limit: Int = 4, capacity: Int = 32, overflow: NotificationOverflow = NotificationOverflow.QUEUE) =
            NotificationHost(ui, limit, capacity, overflow).also { ui.root.add(it) }
    }
}
