package pixzel.component

import pixzel.Ui
import pixzel.graphics.Canvas
import pixzel.graphics.Ellipse
import pixzel.graphics.Shape
import pixzel.input.EventPhase
import pixzel.input.InputBehavior
import pixzel.input.PointerPolicy
import pixzel.input.RoutedEvent
import pixzel.layout.Constraints
import pixzel.layout.LayoutConfig
import pixzel.layout.LayoutPolicy
import pixzel.layout.Measurable
import pixzel.resource.AttachmentScope
import pixzel.resource.ResourceScope
import pixzel.resource.Subscription
import pixzel.state.MutableState
import pixzel.state.StateKey
import pixzel.style.ResolvedTokens
import pixzel.style.StyleScope
import pixzel.surface.Placement
import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.surface.Surface
import pixzel.surface.SurfaceElement
import pixzel.surface.Transform
import pixzel.surface.Vec2
import kotlin.time.Duration

enum class Invalidation {
    TREE,
    MEASURE,
    ARRANGE,
    TRANSFORM,
    PAINT,
    INPUT,
    SEMANTICS
}
enum class Visibility {
    VISIBLE,
    HIDDEN,
    COLLAPSED
}
enum class ClipPolicy {
    NONE,
    BOUNDS,
    CONTENT,
    SHAPE
}
enum class Layer {
    CONTENT,
    OVERLAY,
    MODAL,
    TOOLTIP,
    DEBUG
}
enum class Role {
    GROUP,
    TEXT,
    BUTTON,
    CHECKBOX,
    RADIO,
    SLIDER,
    PROGRESS,
    TEXT_FIELD,
    LIST,
    LIST_ITEM,
    TAB,
    MENU,
    DIALOG,
    IMAGE
}
data class Semantics(
    val role: Role = Role.GROUP,
    val label: String = "",
    val value: String = "",
    val checked: Boolean? = null,
    val selected: Boolean = false,
    val actions: Map<String, () -> Unit> = emptyMap()
)

data class ElementState(
    val hovered: Boolean = false,
    val pressed: Boolean = false,
    val focused: Boolean = false,
    val focusVisible: Boolean = false,
    val disabled: Boolean = false,
    val selected: Boolean = false,
    val checked: Boolean = false,
    val indeterminate: Boolean = false,
    val invalid: Boolean = false,
    val dragging: Boolean = false
)

data class ElementInspection(val path: String, val constraints: Constraints?, val measuredSize: Size?, val lastInvalidation: Invalidation)
fun interface HitShape {

    fun contains(point: Vec2, size: Size): Boolean
}

val RectangleHitShape = HitShape { p, s -> Rect(0f, 0f, s.width, s.height).contains(p) }
val CircleHitShape = HitShape { p, s -> Ellipse.contains(p, Rect(0f, 0f, s.width, s.height)) }

data class PaintContext(val element: Element, val bounds: Rect, val state: ElementState, val tokens: ResolvedTokens = element.resolvedTokens)
fun interface Painter {

    fun paint(canvas: Canvas, context: PaintContext)
}

interface ComponentSkin<T : Element> {

    fun paint(canvas: Canvas, component: T, context: PaintContext)
}

/** Public extension boundary; one logical parent and one computed Surface per element. */
interface Element : AutoCloseable, Measurable {

    override val id: String
    val generation: Long
    val paintVersion: Long
    val inspection: ElementInspection? get() = null
    val parent: Element?
    val children: List<Element>
    val ui: Ui?
    val surface: Surface
    override val layout: LayoutConfig
    var layoutPolicy: LayoutPolicy
    override var placement: Placement
    var paintTransform: Transform

    /** Optional geometric attachment; logical parent still controls lifetime and event routes. */
    var portalTarget: Element?
    var opacity: Float
    var visibility: Visibility
    var enabled: Boolean
    var pointerPolicy: PointerPolicy
    var focusable: Boolean
    var tabIndex: Int
    var zIndex: Int
    var layer: Layer
    var clipPolicy: ClipPolicy
    var clipShape: Shape
    var hitShape: HitShape
    var semantics: Semantics
    var background: Painter?
    var foreground: Painter?
    var behavior: InputBehavior?
    val state: ElementState
    var styleScope: StyleScope
    val resolvedTokens: ResolvedTokens
    val resources: ResourceScope
    val attachments: AttachmentScope
    val frameUpdates: FrameUpdates
    val isDisposed: Boolean
    fun add(child: Element): Element
    fun remove(child: Element, dispose: Boolean = true): Boolean
    fun reorder(children: List<Element>)
    fun onEvent(phase: EventPhase = EventPhase.BUBBLE, handler: (RoutedEvent) -> Unit): Subscription
    fun invalidate(reason: Invalidation = Invalidation.PAINT)

    /** Runtime lifecycle hook for public custom implementations; tree mutations use add/remove. */
    fun attach(parent: Element?, ui: Ui?)
    fun setInteractionState(state: ElementState)
    fun dispatch(event: RoutedEvent)
    fun update(delta: Duration): Boolean
    fun <T> state(key: StateKey<T>, initial: () -> T): MutableState<T>
    fun paint(canvas: Canvas)
    override fun close()
}

interface ComponentFactory {

    fun surface(id: String = ""): SurfaceElement
    fun text(id: String = ""): Text
    fun button(id: String = ""): Button
    fun textField(id: String = ""): TextField
    fun scrollView(id: String = ""): ScrollView
    fun image(id: String = ""): Image
    fun checkbox(id: String = ""): Checkbox
    fun toggle(id: String = ""): Toggle
    fun radio(id: String = ""): Radio
    fun slider(id: String = ""): Slider
    fun progress(id: String = ""): Progress
    fun tabs(id: String = ""): Tabs
    fun scrollBar(id: String = ""): ScrollBar
}

interface Button : Element {

    /** Slot elements are owned by this button; replacing a slot disposes its previous element. */
    val leading: Element?
    val content: Element?
    val trailing: Element?
    fun setLeading(content: Element?)
    fun setTrailing(content: Element?)
    fun setLabel(value: String): Text
    fun setContent(content: Element)
    fun onClick(handler: () -> Unit): Subscription
    fun replaceOnClick(handler: () -> Unit): Subscription
    fun activate()

    companion object {

        fun of(components: ComponentFactory, id: String = "") = components.button(id)
    }
}
