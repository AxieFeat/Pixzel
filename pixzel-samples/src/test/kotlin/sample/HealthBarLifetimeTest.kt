package sample

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pixzel.UiSettings
import pixzel.graphics.Fill
import pixzel.graphics.RecordingBackend
import pixzel.platform.TestHost
import pixzel.resource.Subscription
import pixzel.state.State
import pixzel.text.FixedFontTextService
import pixzel.ui
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class HealthBarLifetimeTest {
    private class Model(initial: Float) : State<Float> {

        override var value = initial
            private set
        private val listeners = linkedSetOf<(Float) -> Unit>()
        val observers get() = listeners.size
        override fun observe(listener: (Float) -> Unit): Subscription {
            listeners += listener
            return Subscription { listeners -= listener }
        }

        fun set(next: Float) {
            value = next
            listeners.toList().forEach { it(next) }
        }
    }

    @Test
    fun `external component observes only while attached and resynchronizes borrowed model`() {
        val host = TestHost.of(400, 240)
        val backend = RecordingBackend.of()
        val ui = ui(host, backend, FixedFontTextService.of())
        try {
            val model = Model(.2f)
            val bar = HealthBar.of(model)
            assertEquals(0, model.observers)
            ui.root.add(bar)
            ui.frame(Duration.ZERO)
            assertEquals(1, model.observers)
            model.set(.8f)
            ui.frame(100.milliseconds)
            fun fillWidth() = backend.lastFrame!!.commands.filterIsInstance<Fill>().last().bounds.width
            assertEquals(120f, fillWidth(), .0001f)
            ui.root.remove(bar, dispose = false)
            assertEquals(0, model.observers)
            model.set(.1f)
            ui.frame(500.milliseconds)
            assertEquals("0.8", bar.semantics.value)
            ui.root.add(bar)
            assertEquals(1, model.observers)
            assertEquals("0.1", bar.semantics.value)
            ui.settings = UiSettings(isReducedMotion = true)
            ui.frame(Duration.ZERO)
            assertEquals(24f, fillWidth(), .0001f)
            bar.close()
            assertEquals(0, model.observers)
            model.set(.9f)
        } finally {
            ui.close()
            host.close()
        }
    }
}
