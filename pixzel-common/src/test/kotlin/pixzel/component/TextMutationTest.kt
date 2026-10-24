package pixzel.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import pixzel.graphics.RecordingBackend
import pixzel.platform.TestHost
import pixzel.state.bind
import pixzel.state.stateOf
import pixzel.state.stateRuntime
import pixzel.text.FixedFontTextService
import pixzel.ui
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

class TextMutationTest {

    @Test
    fun `rejected worker writes leave existing binding and paint state intact`() {
        val host = TestHost.of()
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        try {
            val runtime = stateRuntime()
            val count = runtime.stateOf(1)
            val binding = runtime.bind { "Count ${count.value}" }
            val replacement = runtime.bind { "Other ${count.value}" }
            val text = ui.components.text()
            text.bindValue(binding)
            ui.root.add(text)
            ui.frame(Duration.ZERO)
            val version = text.paintVersion
            fun rejected(action: () -> Unit) {
                val failure = AtomicReference<Throwable>()
                val worker = Thread {
                    try {
                        action()
                    } catch (error: Throwable) {
                        failure.set(error)
                    }
                }
                worker.start()
                worker.join()
                assertInstanceOf(IllegalStateException::class.java, failure.get())
            }
            rejected { text.value = "Worker overwrite" }
            rejected { text.bindValue(replacement) }
            rejected { text.invalidate(Invalidation.MEASURE) }
            assertEquals(version, text.paintVersion)
            assertFalse(binding.isClosed)
            assertFalse(replacement.isClosed)
            count.value = 2
            ui.frame(Duration.ZERO)
            assertEquals("Count 2", text.value)
            text.bindValue(binding)
            assertFalse(binding.isClosed)
            replacement.close()
        } finally {
            ui.close()
            host.close()
        }
    }
}
