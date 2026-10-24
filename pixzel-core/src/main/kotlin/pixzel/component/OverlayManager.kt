package pixzel.component

import pixzel.resource.Subscription

/**
 * Per-Ui dismissal ordering. Detached overlays are inactive and retain their logical lifetime.
 */
interface OverlayManager : AutoCloseable {

    fun register(element: Element, dismissOnEscape: Boolean = true, dismissOnOutside: Boolean = false, dismiss: () -> Unit): Subscription
    fun dismissTop(): Boolean
    override fun close()
}
