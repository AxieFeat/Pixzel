package pixzel.platform

import pixzel.input.InputEvent
import pixzel.resource.Subscription
import pixzel.surface.Insets
import pixzel.surface.Rect
import pixzel.surface.Size

data class HostMetrics(
    val logicalSize: Size,
    val framebufferSize: Size,
    val safeInsets: Insets = Insets.ZERO,
    val focused: Boolean = true,
    val minimized: Boolean = false,
    val generation: Long = 0
) {

    val drawable get() = logicalSize.width > 0 && logicalSize.height > 0 && framebufferSize.width > 0 && framebufferSize.height > 0 && !minimized
}

enum class Cursor {
    DEFAULT,
    POINTER,
    TEXT,
    CROSSHAIR,
    RESIZE_HORIZONTAL,
    RESIZE_VERTICAL
}
data class PlatformCapabilities(
    val unicodeCommit: Boolean = true,
    val clipboard: Boolean = false,
    val cursorShapes: Boolean = false,
    val imeComposition: Boolean = false,
    val imeCaret: Boolean = false,
    val inputMasking: Boolean = false
)

interface UiHost {

    val capabilities: PlatformCapabilities get() = PlatformCapabilities()
    val metrics: HostMetrics
    val hostId: String
    fun subscribe(listener: (InputEvent) -> Boolean): Subscription

    /**
     * Delivery is ordered on the owning UI thread; consumed events must not reach lower layers.
     */
    fun drainEvents()
    fun readClipboard(): Result<String>
    fun writeClipboard(text: String): Result<Unit>
    fun setCursor(cursor: Cursor): Result<Unit>

    /**
     * Blocks already held gameplay keys/buttons until release; physical polling remains available.
     */
    fun maskHeldInput(): Result<Unit> = Result.failure(UnsupportedOperationException("Held-input masking is not supported by this host"))

    /**
     * Candidate-window caret rectangle in window logical units, not framebuffer pixels.
     */
    fun setImeCaret(bounds: Rect): Result<Unit> = Result.failure(UnsupportedOperationException("IME composition is not supported by this host"))
}
