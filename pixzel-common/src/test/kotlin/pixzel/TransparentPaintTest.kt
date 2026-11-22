package pixzel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pixzel.component.AbstractElement
import pixzel.graphics.BeginGroup
import pixzel.graphics.Canvas
import pixzel.graphics.Fill
import pixzel.graphics.RecordingBackend
import pixzel.graphics.Rectangle
import pixzel.graphics.Solid
import pixzel.graphics.rgba
import pixzel.layout.unit.dp
import pixzel.platform.TestHost
import pixzel.surface.Rect
import pixzel.text.FixedFontTextService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class TransparentPaintTest {

    @Test
    fun transparentSubtreesResume() {
        val backend = RecordingBackend.of()
        val owner = ui(TestHost.of(), backend, FixedFontTextService.of())
        var updates = 0
        var paints = 0
        val overlay = object : AbstractElement("overlay") {
            override fun update(delta: Duration): Boolean {
                updates++
                return false
            }
        }
        val child = object : AbstractElement("child") {
            override fun paint(canvas: Canvas) {
                paints++
                canvas.fill(Rectangle, Rect(0f, 0f, 20f, 20f), Solid(0xFFFFFFFF.rgba))
            }
        }
        overlay.layout.width = 80.dp
        overlay.layout.height = 40.dp
        overlay.add(child)
        overlay.opacity = 0f
        owner.root.add(overlay)
        try {
            owner.frame(16.milliseconds)
            assertTrue(updates > 0)
            assertEquals(0, paints)
            assertFalse(backend.lastFrame!!.commands.any { it is BeginGroup || it is Fill })
            val previousUpdates = updates
            overlay.opacity = .5f
            owner.frame(16.milliseconds)
            assertTrue(updates > previousUpdates)
            assertEquals(1, paints)
            assertTrue(backend.lastFrame!!.commands.any { it is BeginGroup && it.opacity == .5f })
            overlay.opacity = 0f
            owner.frame(16.milliseconds)
            assertFalse(backend.lastFrame!!.commands.any { it is BeginGroup || it is Fill })
            overlay.opacity = 1f
            owner.frame(16.milliseconds)
            assertTrue(backend.lastFrame!!.commands.any { it is Fill })
            overlay.opacity = 1f
            owner.frame(16.milliseconds)
            assertEquals(0, owner.statistics.paintedNodes)
        } finally {
            owner.close()
        }
    }
}
