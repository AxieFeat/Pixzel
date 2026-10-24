package pixzel

import pixzel.component.ComponentFactory
import pixzel.component.Element
import pixzel.component.Invalidation
import pixzel.component.OverlayManager
import pixzel.graphics.RenderMetrics
import pixzel.graphics.UiBackend
import pixzel.input.FocusManager
import pixzel.input.InputMode
import pixzel.input.NavigationCommand
import pixzel.platform.UiHost
import pixzel.style.Theme
import pixzel.surface.Vec2
import pixzel.text.TextService
import kotlin.time.Duration

data class UiSettings(
    val uiScale: Float = 1f,
    val textScale: Float = 1f,
    val isReducedMotion: Boolean = false,
    val debug: Boolean = true,
    val errorPolicy: UiErrorPolicy = UiErrorPolicy { ErrorDecision.THROW }
) {

    init {
        require(uiScale.isFinite() && uiScale > 0 && textScale.isFinite() && textScale > 0)
    }

    companion object {

        fun of(uiScale: Float = 1f, textScale: Float = 1f, isReducedMotion: Boolean = false) = UiSettings(uiScale, textScale, isReducedMotion)
    }
}

enum class UiLifecycle {
    CREATED,
    ATTACHED,
    ACTIVE,
    SUSPENDED,
    CLOSED
}
data class FrameStatistics(
    val totalNodes: Int = 0,
    val measuredNodes: Int = 0,
    val paintedNodes: Int = 0,
    val commands: Int = 0,
    val layoutPasses: Int = 0,
    val visibleNodes: Int = 0
)

interface Ui : AutoCloseable {

    val root: Element
    val host: UiHost
    val backend: UiBackend
    val text: TextService
    val components: ComponentFactory
    val focus: FocusManager
    val overlays: OverlayManager
    val diagnostics: DiagnosticLog
    var settings: UiSettings
    var theme: Theme
    val layoutVersion: Long
    val lifecycle: UiLifecycle
    val statistics: FrameStatistics
    val capturedPointers: Map<Int, Element>
    val inputMode: InputMode
    val renderMetrics: RenderMetrics?
    fun frame(delta: Duration)
    fun post(action: () -> Unit)
    fun invalidate(reason: Invalidation)
    fun assertThread()
    fun hitTest(point: Vec2): Element?

    /**
     * Host/gamepad adapters can navigate without manufacturing physical keyboard events.
     */
    fun navigate(command: NavigationCommand): Boolean

    /**
     * Sends cancel and releases captures owned by this element.
     */
    fun releasePointerCapture(element: Element)

    /**
     * Releases capture/focus owned by an element subtree before host detachment.
     */
    fun elementDetached(element: Element)

    /**
     * Runtime arrangement hook: geometry attached to another branch commits after its target.
     */
    fun deferPlacement(element: Element, target: Element, placement: () -> Unit)
    override fun close()
    fun interface Factory {

        fun create(host: UiHost, backend: UiBackend, text: TextService, settings: UiSettings): Ui
    }

    companion object {

        fun of(factory: Factory, host: UiHost, backend: UiBackend, text: TextService, settings: UiSettings = UiSettings()) =
            factory.create(host, backend, text, settings)
    }
}
