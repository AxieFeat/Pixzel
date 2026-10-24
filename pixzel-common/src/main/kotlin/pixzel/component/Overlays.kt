package pixzel.component

import pixzel.Ui
import pixzel.dsl.ContentScope
import pixzel.graphics.Rectangle
import pixzel.graphics.RoundedRectangle
import pixzel.graphics.Solid
import pixzel.graphics.rgba
import pixzel.input.InputBehavior
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.PointerPolicy
import pixzel.layout.columnLayout
import pixzel.layout.rowLayout
import pixzel.layout.unit.dp
import pixzel.layout.unit.fillAvailable
import pixzel.layout.unit.wrapContent
import pixzel.resource.Subscription
import pixzel.surface.Insets
import pixzel.surface.Placement
import pixzel.surface.Rect
import pixzel.surface.ReferenceBox
import pixzel.surface.Size
import pixzel.surface.Vec2
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

enum class PopupSide {
    ABOVE,
    BELOW,
    LEFT,
    RIGHT
}

/** Flips the preferred side then shifts within viewport; caller constrains popup dimensions. */
fun popupPosition(anchor: Rect, popup: Size, viewport: Rect, side: PopupSide = PopupSide.BELOW): Vec2 {
    var x = anchor.x
    var y = anchor.bottom
    when (side) {
        PopupSide.BELOW -> {
            y = if (anchor.bottom + popup.height <= viewport.bottom) anchor.bottom else anchor.y - popup.height
        }

        PopupSide.ABOVE -> {
            y = if (anchor.y - popup.height >= viewport.y) anchor.y - popup.height else anchor.bottom
        }

        PopupSide.LEFT -> {
            x = if (anchor.x - popup.width >= viewport.x) anchor.x - popup.width else anchor.right
            y = anchor.y
        }

        PopupSide.RIGHT -> {
            x = if (anchor.right + popup.width <= viewport.right) anchor.right else anchor.x - popup.width
            y = anchor.y
        }
    }
    return Vec2(
        x.coerceIn(viewport.x, (viewport.right - popup.width).coerceAtLeast(viewport.x)),
        y.coerceIn(viewport.y, (viewport.bottom - popup.height).coerceAtLeast(viewport.y))
    )
}

class Popup private constructor(private val ui: Ui, private val anchor: Element, private val side: PopupSide, private val dismissOutside: Boolean) :
    AutoCloseable {

    val element = object : AbstractElement() {
        override fun update(delta: Duration): Boolean {
            if (anchor.isDisposed || anchor.ui !== ui) {
                close()
                return false
            }
            if (!anchor.surface.isAttached || !surface.isAttached) return false
            val rect = anchor.surface.mapRect(Rect(0f, 0f, anchor.surface.bounds.width, anchor.surface.bounds.height))
            val viewport = this@Popup.ui.root.surface.contentBox
            val next = Placement(offset = popupPosition(rect, surface.bounds.size, viewport, side), referenceBox = ReferenceBox.BORDER)
            if (placement != next) {
                placement = next
                return true
            }
            return false
        }
    }
    private var closed = false
    private val outside: Subscription

    init {
        element.portalTarget = ui.root
        element.layer = Layer.OVERLAY
        element.pointerPolicy = PointerPolicy.AUTO
        element.layout.maxWidth = (ui.host.metrics.logicalSize.width / ui.settings.uiScale).dp
        element.layout.maxHeight = (ui.host.metrics.logicalSize.height / ui.settings.uiScale).dp
        element.background = Painter { c, ctx -> c.fill(RoundedRectangle(6f), ctx.bounds, Solid(0x202B3AFF.rgba)) }
        anchor.add(element)
        outside = ui.overlays.register(element, dismissOnOutside = dismissOutside) { close() }
        element.resources.own(outside)
    }

    fun content(build: ContentScope.() -> Unit) {
        ContentScope(ui, element).build()
    }

    override fun close() {
        if (closed) return
        closed = true
        outside.close()
        element.close()
    }

    companion object {

        fun of(ui: Ui, anchor: Element, side: PopupSide = PopupSide.BELOW, dismissOutside: Boolean = true) = Popup(ui, anchor, side, dismissOutside)
    }
}

class Modal private constructor(private val ui: Ui, owner: Element, private val outsideDismiss: Boolean) : AutoCloseable {

    val element = ui.components.surface().apply {
        portalTarget = this@Modal.ui.root
        layer = Layer.MODAL
        layout.width = fillAvailable
        layout.height = fillAvailable
        pointerPolicy = PointerPolicy.AUTO
        semantics = Semantics(Role.DIALOG)
        background = Painter { c, ctx -> c.fill(Rectangle, ctx.bounds, Solid(0x00000088.rgba)) }
    }

    /** Dialog shell; header/body/actions are ordinary replaceable-content Surfaces. */
    val dialog = ui.components.surface("dialog").apply {
        layout.width = 360.dp
        layout.height = 240.dp
        layout.padding = Insets.all(16.dp)
        placement = Placement.center()
        pointerPolicy = PointerPolicy.AUTO
        layoutPolicy = columnLayout()
        background = Painter { c, ctx -> c.fill(RoundedRectangle(12f), ctx.bounds, Solid(0x202B3AFF.rgba)) }
    }
    val header = ui.components.surface("header").apply {
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
    private var closed = false
    private val scope: Subscription

    init {
        owner.add(element)
        element.add(dialog)
        dialog.add(header)
        dialog.add(body)
        dialog.add(actions)
        scope = ui.focus.pushScope(element)
        element.resources.own(scope)
        element.resources.own(ui.overlays.register(element) { close() })
        element.behavior = InputBehavior { _, event ->
            event.consume()
            if (outsideDismiss && (event.event as? PointerEvent)?.action == PointerAction.UP) close()
        }
        element.resources.own(element.onEvent { event ->
            event.consume()
            val key = event.event as? KeyEvent
            if (key?.key == Key.ESCAPE && key.action == KeyAction.DOWN) {
                event.preventDefault()
                close()
            }
        })
    }

    fun content(build: ContentScope.() -> Unit) {
        ContentScope(ui, body).build()
        ui.focus.move()
    }

    override fun close() {
        if (closed) return
        closed = true
        var failure: Throwable? = null
        try {
            scope.close()
        } catch (error: Throwable) {
            failure = error
        }
        try {
            element.close()
        } catch (error: Throwable) {
            if (failure == null) failure = error else if (failure !== error) failure.addSuppressed(error)
        }
        failure?.let { throw it }
    }

    companion object {

        fun of(ui: Ui, owner: Element = ui.root, outsideDismiss: Boolean = false) = Modal(ui, owner, outsideDismiss)
    }
}

/** Controller belongs to its anchor. Tooltip content never captures focus or pointers. */
fun Element.tooltip(value: String, delay: Duration = 600.milliseconds, showWhenDisabled: Boolean = true) {
    require(delay.isFinite() && !delay.isNegative())
    val target = this
    val controller = object : AbstractElement() {
        private var elapsed = Duration.ZERO
        private var popup: Popup? = null
        override fun update(delta: Duration): Boolean {
            val active = (target.state.hovered || target.state.focused) && (showWhenDisabled || target.enabled)
            if (!active) {
                elapsed = Duration.ZERO
                popup?.close()
                popup = null
                return false
            }
            elapsed += delta
            if (elapsed >= delay && popup == null) {
                popup = Popup.of(ui!!, target, dismissOutside = false).also { p ->
                    p.element.layer = Layer.TOOLTIP
                    p.element.pointerPolicy = PointerPolicy.NONE
                    p.content { text(value) { layout { padding = Insets.all(8.dp) } } }
                }
                return true
            }
            return false
        }
    }
    add(controller)
}
