package pixzel.input

import pixzel.component.Element
import pixzel.resource.Subscription
import pixzel.surface.Vec2
import kotlin.time.Duration

enum class PointerAction {
    MOVE,
    DOWN,
    UP,
    CANCEL,
    ENTER,
    LEAVE
}
enum class PointerType {
    MOUSE,
    TOUCH,
    STYLUS
}
enum class KeyAction {
    DOWN,
    REPEAT,
    UP
}
enum class InputMode {
    POINTER,
    KEYBOARD,
    NAVIGATION
}
enum class NavigationDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN
}
enum class NavigationCommand {
    NEXT,
    PREVIOUS,
    LEFT,
    RIGHT,
    UP,
    DOWN,
    ACTIVATE,
    CANCEL
}
enum class Key {
    TAB,
    ENTER,
    SPACE,
    ESCAPE,
    LEFT,
    RIGHT,
    UP,
    DOWN,
    HOME,
    END,
    BACKSPACE,
    DELETE,
    A,
    C,
    V,
    X,
    Y,
    Z,
    UNKNOWN
}
data class Modifiers(val shift: Boolean = false, val control: Boolean = false, val alt: Boolean = false, val meta: Boolean = false)
interface InputEvent {

    val sequence: Long
    val timestamp: Duration
    val hostId: String
}

data class PointerEvent(
    val action: PointerAction,
    val position: Vec2,
    val button: Int = 0,
    val pointerId: Int = 0,
    val type: PointerType = PointerType.MOUSE,
    val modifiers: Modifiers = Modifiers(),
    override val sequence: Long = 0,
    override val timestamp: Duration = Duration.ZERO,
    override val hostId: String = "default"
) : InputEvent

enum class WheelUnit {
    LINE,
    DP,
    PAGE
}
enum class WheelPhase {
    START,
    UPDATE,
    END,
    MOMENTUM
}
data class WheelEvent(
    val position: Vec2,
    val delta: Vec2,
    override val sequence: Long = 0,
    override val timestamp: Duration = Duration.ZERO,
    override val hostId: String = "default",
    val unit: WheelUnit = WheelUnit.LINE,
    val phase: WheelPhase = WheelPhase.UPDATE
) : InputEvent

data class KeyEvent(
    val key: Key,
    val action: KeyAction,
    val modifiers: Modifiers = Modifiers(),
    val physicalCode: Int = 0,
    override val sequence: Long = 0,
    override val timestamp: Duration = Duration.ZERO,
    override val hostId: String = "default"
) : InputEvent

data class TextCommit(
    val text: String,
    override val sequence: Long = 0,
    override val timestamp: Duration = Duration.ZERO,
    override val hostId: String = "default"
) : InputEvent

data class WindowFocus(
    val focused: Boolean,
    override val sequence: Long = 0,
    override val timestamp: Duration = Duration.ZERO,
    override val hostId: String = "default"
) : InputEvent

data class Composition(
    val preedit: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val committed: Boolean = false,
    val cancelled: Boolean = false,
    override val sequence: Long = 0,
    override val timestamp: Duration = Duration.ZERO,
    override val hostId: String = "default",
    val replacementStart: Int? = null,
    val replacementEnd: Int? = null
) : InputEvent

enum class EventPhase {
    CAPTURE,
    TARGET,
    BUBBLE
}
enum class PointerPolicy {
    AUTO,
    PASS_THROUGH,
    NONE
}
class RoutedEvent(
    val event: InputEvent,
    val target: Element,
    val current: Element,
    val phase: EventPhase,
    val localPosition: Vec2?,
    private val shared: RoutingFlags
) {

    fun stopPropagation() {
        shared.stopped = true
    }

    fun preventDefault() {
        shared.prevented = true
    }

    fun consume() {
        shared.consumed = true
    }

    fun capturePointer() {
        shared.capture = current
    }

    val scrollRemaining get() = shared.scrollRemainder
    fun consumeScroll(used: Vec2) {
        val delta = shared.scrollRemainder ?: (event as? WheelEvent)?.delta ?: return
        shared.scrollRemainder = delta - used
        if (used.x != 0f || used.y != 0f) shared.consumed = true
    }

    val isConsumed get() = shared.consumed
}

class RoutingFlags {

    var stopped = false
    var prevented = false
    var consumed = false
    var capture: Element? = null
    var scrollRemainder: Vec2? = null
}

fun interface InputBehavior {

    fun handle(element: Element, event: RoutedEvent)
}

interface FocusManager {

    val focused: Element?
    fun request(element: Element, visible: Boolean = true): Boolean
    fun clear()
    fun move(forward: Boolean = true): Boolean
    fun move(direction: NavigationDirection): Boolean

    /**
     * Traps navigation in a logical subtree and restores prior focus on idempotent close.
     */
    fun pushScope(root: Element, restoreFocus: Boolean = true): Subscription
}
