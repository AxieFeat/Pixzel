package pixzel.input

import pixzel.Ui
import pixzel.component.Element
import pixzel.component.Layer
import pixzel.component.Visibility
import pixzel.resource.Subscription
import pixzel.surface.Placement
import pixzel.surface.ReferenceBox
import pixzel.surface.Vec2

/** Identity token for a payload type; two tokens with the same label remain distinct types. */
class DragType<T>(val name: String)
data class DragResult<T>(val value: T, val source: Element, val target: Element?, val accepted: Boolean, val cancelled: Boolean)

/** Typed in-process dragging. Native file drops are a separate platform capability. */
class DragDropManager(private val ui: Ui) : AutoCloseable {

    private data class Target(val element: Element, val type: Any, val accepts: (Any?) -> Boolean, val drop: (Any?) -> Unit)
    private data class Active(val source: Element, val pointerId: Int, val move: (Vec2) -> Unit, val finish: (Vec2?, Boolean) -> Unit)

    private val targets = mutableListOf<Target>()
    private var active: Active? = null
    private val sources = mutableListOf<Subscription>()
    private var closed = false

    init {
        ui.root.resources.own(this)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> target(element: Element, type: DragType<T>, accepts: (T) -> Boolean = { true }, drop: (T) -> Unit): Subscription {
        ui.assertThread()
        check(!closed)
        val entry = Target(element, type, { accepts(it as T) }, { drop(it as T) })
        targets += entry
        var attached = true
        return Subscription {
            if (attached) {
                attached = false
                targets.remove(entry)
            }
        }.also { element.resources.own(it) }
    }

    fun <T> source(
        element: Element,
        type: DragType<T>,
        value: () -> T,
        threshold: Float = 6f,
        preview: (T) -> Element? = { null },
        complete: (DragResult<T>) -> Unit = {}
    ): Subscription {
        require(threshold.isFinite() && threshold >= 0)
        ui.assertThread()
        check(!closed)
        var origin: Vec2? = null
        var pointerId = -1
        val sub = element.onEvent(EventPhase.TARGET) { event ->
            val pointer = event.event as? PointerEvent ?: return@onEvent
            when (pointer.action) {
                PointerAction.DOWN -> if (pointer.button == 0) {
                    origin = pointer.position
                    pointerId = pointer.pointerId
                    event.capturePointer()
                    event.consume()
                }

                PointerAction.MOVE -> {
                    val start = origin ?: return@onEvent
                    val dx = pointer.position.x - start.x
                    val dy = pointer.position.y - start.y
                    if (active == null && pointer.pointerId == pointerId && dx * dx + dy * dy >= threshold * threshold) {
                        val payload = value()
                        val view = preview(payload)
                        if (view != null) {
                            require(view.parent == null && view.ui == null)
                            view.portalTarget = ui.root
                            view.layer = Layer.OVERLAY
                            view.pointerPolicy = PointerPolicy.NONE
                            element.add(view)
                        }
                        element.setInteractionState(element.state.copy(dragging = true, pressed = false))
                        active = Active(
                            element,
                            pointerId,
                            { point ->
                                if (view != null && !view.isDisposed) view.placement =
                                    Placement(offset = point + Vec2(8f, 8f), referenceBox = ReferenceBox.BORDER)
                            },
                            { point, cancelled ->
                                val target = if (!cancelled && point != null) find(type, point) else null
                                var accepted = false
                                try {
                                    if (target != null && target.accepts(payload)) {
                                        target.drop(payload)
                                        accepted = true
                                    }
                                } finally {
                                    try {
                                        if (view != null && !view.isDisposed) view.close()
                                    } finally {
                                        if (!element.isDisposed) element.setInteractionState(element.state.copy(dragging = false, pressed = false))
                                        complete(DragResult(payload, element, target?.element, accepted, cancelled))
                                    }
                                }
                            })
                    }
                    active?.takeIf { it.source === element && it.pointerId == pointer.pointerId }?.let {
                        it.move(pointer.position)
                        event.preventDefault()
                        event.consume()
                    }
                }

                PointerAction.UP -> if (pointer.button == 0 && pointer.pointerId == pointerId) {
                    origin = null
                    active?.takeIf { it.source === element }?.let {
                        active = null
                        event.preventDefault()
                        event.consume()
                        it.finish(pointer.position, false)
                    }
                }

                PointerAction.CANCEL -> {
                    origin = null
                    active?.takeIf { it.source === element }?.let {
                        active = null
                        event.preventDefault()
                        event.consume()
                        it.finish(null, true)
                    }
                }

                else -> {}
            }
        }
        var subscribed = true
        return object : Subscription {
            override fun close() {
                ui.assertThread()
                if (subscribed) {
                    subscribed = false
                    sources.remove(this)
                    try {
                        sub.close()
                    } finally {
                        active?.takeIf { it.source === element }?.let {
                            active = null
                            it.finish(null, true)
                        }
                    }
                }
            }
        }.also {
            sources += it
            element.resources.own(it)
        }
    }

    /** A keyboard accessibility action can move the same payload without pointer simulation. */
    fun <T> perform(source: Element, target: Element, type: DragType<T>, value: T): Boolean {
        ui.assertThread()
        check(!closed)
        fun usable(element: Element) =
            generateSequence(element as Element?) { it.parent }.all { it.ui === ui && !it.isDisposed && it.enabled && it.visibility == Visibility.VISIBLE }
        if (!usable(source) || !usable(target)) return false
        val registered = targets.lastOrNull { it.element === target && it.type === type } ?: return false
        if (!registered.accepts(value)) return false
        registered.drop(value)
        return true
    }

    private fun find(type: Any, point: Vec2): Target? {
        val route = generateSequence(ui.hitTest(point)) { it.parent }.toList()
        return route.firstNotNullOfOrNull { element -> targets.lastOrNull { it.element === element && it.type === type } }
    }

    override fun close() {
        if (closed) return
        ui.assertThread()
        closed = true
        val session = active
        active = null
        var failure: Throwable? = null
        fun attempt(action: () -> Unit) {
            try {
                action()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
        }
        attempt { session?.finish?.invoke(null, true) }
        sources.toList().forEach { attempt { it.close() } }
        sources.clear()
        targets.clear()
        failure?.let { throw it }
    }
}
