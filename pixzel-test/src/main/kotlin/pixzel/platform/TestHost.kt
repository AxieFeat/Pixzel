package pixzel.platform

import pixzel.input.Composition
import pixzel.input.InputEvent
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.TextCommit
import pixzel.input.WheelEvent
import pixzel.input.WindowFocus
import pixzel.resource.Subscription
import pixzel.surface.Insets
import pixzel.surface.Rect
import pixzel.surface.Size
import kotlin.time.Duration

/** Ordered, thread-confined host fixture with separate physical and gameplay polling views. */
class TestHost private constructor(initialMetrics: HostMetrics, override val hostId: String) : UiHost, AutoCloseable {

    override val capabilities =
        PlatformCapabilities(clipboard = true, cursorShapes = true, imeComposition = true, imeCaret = true, inputMasking = true)
    private val thread = Thread.currentThread()
    private val listeners = linkedMapOf<Any, (InputEvent) -> Boolean>()
    private val events = ArrayDeque<InputEvent>()
    private var lastSequence = 0L
    private var draining = false
    private var closed = false
    private var clipboard = ""
    private val keys = mutableSetOf<Key>()
    private val blockedKeys = mutableSetOf<Key>()
    private val buttons = mutableSetOf<Int>()
    private val blockedButtons = mutableSetOf<Int>()
    private val consumed = mutableListOf<InputEvent>()
    private val gameplay = mutableListOf<InputEvent>()
    override var metrics: HostMetrics = initialMetrics
        private set
    var cursor: Cursor = Cursor.DEFAULT
        private set
    var imeCaret: Rect? = null
        private set
    var clipboardAvailable: Boolean = true
    val listenerCount: Int get() = listeners.size
    val queuedEventCount: Int get() = events.size
    val consumedEvents: List<InputEvent> get() = consumed.toList()
    val gameplayEvents: List<InputEvent> get() = gameplay.toList()

    private fun checkOpen() {
        check(Thread.currentThread() === thread) { "TestHost must be used on its owning UI thread." }
        check(!closed) { "TestHost is closed." }
    }

    override fun subscribe(listener: (InputEvent) -> Boolean): Subscription {
        checkOpen()
        val token = Any()
        listeners[token] = listener
        return Subscription {
            check(Thread.currentThread() === thread)
            listeners.remove(token)
        }
    }

    /** Zero/default event identities are assigned in queue order; explicit sequence must increase. */
    fun queue(event: InputEvent) {
        checkOpen()
        require(event.timestamp.isFinite() && event.timestamp >= Duration.ZERO) { "Event timestamp must be finite and nonnegative." }
        require(event.hostId == hostId || event.hostId == "default") { "Event belongs to host '${event.hostId}', expected '$hostId'." }
        val sequence = if (event.sequence == 0L) lastSequence + 1 else event.sequence
        require(sequence > lastSequence) { "Host event sequence must increase in queue order." }
        val normalized = when (event) {
            is PointerEvent -> event.copy(sequence = sequence, hostId = hostId)
            is WheelEvent -> event.copy(sequence = sequence, hostId = hostId)
            is KeyEvent -> event.copy(sequence = sequence, hostId = hostId)
            is TextCommit -> event.copy(sequence = sequence, hostId = hostId)
            is WindowFocus -> event.copy(sequence = sequence, hostId = hostId)
            is Composition -> event.copy(sequence = sequence, hostId = hostId)
            else -> {
                require(event.sequence == sequence && event.hostId == hostId) { "Custom events need explicit host identity and sequence." }
                event
            }
        }
        lastSequence = sequence
        events += normalized
    }

    override fun drainEvents() {
        checkOpen()
        check(!draining) { "TestHost event draining cannot be reentered." }
        draining = true
        try {
            while (events.isNotEmpty()) {
                val event = events.removeFirst()
                val blocked = when (event) {
                    is KeyEvent -> event.key in blockedKeys
                    is PointerEvent -> event.button in blockedButtons && event.action in setOf(PointerAction.DOWN, PointerAction.UP)
                    else -> false
                }
                when (event) {
                    is KeyEvent -> if (event.action == KeyAction.UP) keys -= event.key else keys += event.key
                    is PointerEvent -> when (event.action) {
                        PointerAction.DOWN -> buttons += event.button
                        PointerAction.UP -> buttons -= event.button
                        PointerAction.CANCEL -> {
                            buttons.clear()
                            blockedButtons.clear()
                        }

                        else -> Unit
                    }

                    is WindowFocus -> {
                        metrics = metrics.copy(focused = event.focused, generation = metrics.generation + 1)
                        if (!event.focused) {
                            keys.clear()
                            blockedKeys.clear()
                            buttons.clear()
                            blockedButtons.clear()
                        }
                    }
                }
                var wasConsumed = false
                for ((token, listener) in listeners.toList()) {
                    if (listeners[token] === listener && listener(event)) {
                        wasConsumed = true
                        break
                    }
                }
                wasConsumed = wasConsumed || blocked
                if (wasConsumed) consumed += event else gameplay += event
                when (event) {
                    is KeyEvent -> if (event.action == KeyAction.UP) blockedKeys -= event.key
                    else if (wasConsumed) blockedKeys += event.key

                    is PointerEvent -> if (event.action == PointerAction.UP) blockedButtons -= event.button
                    else if (event.action == PointerAction.DOWN && wasConsumed) blockedButtons += event.button
                }
            }
        } finally {
            draining = false
        }
    }

    fun isPhysicalKeyDown(key: Key): Boolean = key in keys
    fun isGameplayKeyDown(key: Key): Boolean = key in keys && key !in blockedKeys
    fun isPhysicalButtonDown(button: Int): Boolean = button in buttons
    fun isGameplayButtonDown(button: Int): Boolean = button in buttons && button !in blockedButtons
    fun clearEventRecords() {
        checkOpen()
        consumed.clear()
        gameplay.clear()
    }

    fun resize(logicalSize: Size, framebufferSize: Size = logicalSize, safeInsets: Insets = metrics.safeInsets) {
        checkOpen()
        metrics = metrics.copy(
            logicalSize = logicalSize, framebufferSize = framebufferSize, safeInsets = safeInsets,
            minimized = framebufferSize.width == 0f || framebufferSize.height == 0f, generation = metrics.generation + 1
        )
    }

    fun resize(width: Int, height: Int, framebufferWidth: Int = width, framebufferHeight: Int = height) =
        resize(Size(width.toFloat(), height.toFloat()), Size(framebufferWidth.toFloat(), framebufferHeight.toFloat()))

    override fun readClipboard(): Result<String> {
        checkOpen()
        return if (clipboardAvailable) Result.success(clipboard) else Result.failure(UnsupportedOperationException("Test clipboard is disabled."))
    }

    override fun writeClipboard(text: String): Result<Unit> {
        checkOpen()
        return if (clipboardAvailable) {
            clipboard = text
            Result.success(Unit)
        } else Result.failure(UnsupportedOperationException("Test clipboard is disabled."))
    }

    override fun setCursor(cursor: Cursor): Result<Unit> {
        checkOpen()
        this.cursor = cursor
        return Result.success(Unit)
    }

    override fun setImeCaret(bounds: Rect): Result<Unit> {
        checkOpen()
        imeCaret = bounds
        return Result.success(Unit)
    }

    override fun maskHeldInput(): Result<Unit> {
        checkOpen()
        blockedKeys.addAll(keys)
        blockedButtons.addAll(buttons)
        return Result.success(Unit)
    }

    override fun close() {
        if (closed) return
        checkOpen()
        closed = true
        listeners.clear()
        events.clear()
        keys.clear()
        blockedKeys.clear()
        buttons.clear()
        blockedButtons.clear()
    }

    companion object {

        fun of(
            width: Int = 1280, height: Int = 720, framebufferWidth: Int = width, framebufferHeight: Int = height,
            hostId: String = "test"
        ): TestHost = TestHost(
            HostMetrics(
                Size(width.toFloat(), height.toFloat()),
                Size(framebufferWidth.toFloat(), framebufferHeight.toFloat())
            ), hostId
        )
    }
}
