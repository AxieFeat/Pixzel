package pixzel.arc.platform

import arc.window.CursorShape
import arc.window.FocusEvent
import arc.window.KeyModifiers
import arc.window.KeyboardEvent
import arc.window.PointerBoundary
import arc.window.PointerButton
import arc.window.PointerMotion
import arc.window.PointerWheel
import arc.window.UnicodeCommit
import arc.window.Window
import pixzel.input.InputEvent
import pixzel.input.Key
import pixzel.input.KeyEvent
import pixzel.input.Modifiers
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.TextCommit
import pixzel.input.WheelEvent
import pixzel.input.WindowFocus
import pixzel.platform.Cursor
import pixzel.platform.HostMetrics
import pixzel.platform.PlatformCapabilities
import pixzel.platform.UiHost
import pixzel.resource.Subscription
import pixzel.surface.Size
import pixzel.surface.Vec2
import kotlin.time.Duration.Companion.nanoseconds
import arc.window.KeyAction as ArcKeyAction
import pixzel.input.KeyAction as PixzelKeyAction

class ArcUiHost private constructor(private val window: Window) : UiHost {

    private var pointer = Vec2.ZERO
    override val capabilities = PlatformCapabilities(clipboard = true, cursorShapes = true, inputMasking = true)
    override val hostId get() = "arc:${window.handle}"
    override val metrics: HostMetrics
        get() = window.metrics.let {
            HostMetrics(
                Size(it.logicalWidth.toFloat(), it.logicalHeight.toFloat()),
                Size(it.framebufferWidth.toFloat(), it.framebufferHeight.toFloat()),
                focused = it.focused,
                minimized = it.minimized,
                generation = it.generation
            )
        }

    override fun subscribe(listener: (InputEvent) -> Boolean): Subscription {
        val lease = window.events.deferAutomaticDelivery()
        val sub = window.events.subscribe(priority = 100) { source ->
            val time = source.timeNanos.nanoseconds
            val seq = source.sequence
            fun mods(m: KeyModifiers) = Modifiers(m.shift, m.control, m.alt, m.meta)
            val event: InputEvent? = when (source) {
                is PointerMotion -> {
                    pointer = Vec2(source.x.toFloat(), source.y.toFloat())
                    PointerEvent(PointerAction.MOVE, pointer, sequence = seq, timestamp = time, hostId = hostId)
                }

                is PointerBoundary -> PointerEvent(
                    if (source.entered) PointerAction.ENTER else PointerAction.LEAVE,
                    pointer,
                    sequence = seq,
                    timestamp = time,
                    hostId = hostId
                )

                is PointerButton -> PointerEvent(
                    if (source.action == ArcKeyAction.RELEASE) PointerAction.UP else PointerAction.DOWN,
                    Vec2(source.x.toFloat(), source.y.toFloat()),
                    source.button,
                    modifiers = mods(source.modifiers),
                    sequence = seq,
                    timestamp = time,
                    hostId = hostId
                )

                is PointerWheel -> WheelEvent(
                    Vec2(source.x.toFloat(), source.y.toFloat()),
                    Vec2(source.deltaX.toFloat(), source.deltaY.toFloat()),
                    seq,
                    time,
                    hostId
                )

                is KeyboardEvent -> KeyEvent(
                    Key.entries.first { it.name == source.symbol.name }, when (source.action) {
                        ArcKeyAction.PRESS -> PixzelKeyAction.DOWN
                        ArcKeyAction.REPEAT -> PixzelKeyAction.REPEAT
                        ArcKeyAction.RELEASE -> PixzelKeyAction.UP
                    }, mods(source.modifiers), source.physicalCode, seq, time, hostId
                )

                is UnicodeCommit -> TextCommit(source.text, seq, time, hostId)
                is FocusEvent -> WindowFocus(source.focused, seq, time, hostId)
                else -> null
            }
            event?.let(listener) ?: false
        }
        var closed = false
        return Subscription {
            if (!closed) {
                closed = true
                try {
                    sub.close()
                } finally {
                    lease.close()
                }
            }
        }
    }

    override fun drainEvents() = window.events.drain()
    override fun readClipboard() = runCatching { window.clipboardText }
    override fun writeClipboard(text: String) = runCatching { window.clipboardText = text }
    override fun setCursor(cursor: Cursor) = runCatching { window.setCursor(CursorShape.entries.first { it.name == cursor.name }) }
    override fun maskHeldInput() = runCatching { window.events.maskAll(priority = 100) }

    companion object {

        fun of(window: Window): ArcUiHost = ArcUiHost(window)
    }
}
