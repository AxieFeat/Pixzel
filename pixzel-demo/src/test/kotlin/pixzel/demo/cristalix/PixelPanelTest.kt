package pixzel.demo.cristalix

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import pixzel.graphics.Canvas
import pixzel.graphics.DrawCommand
import pixzel.graphics.Fill
import pixzel.graphics.Solid
import pixzel.graphics.rgba
import pixzel.surface.Rect
import pixzel.surface.Vec2

class PixelPanelTest {

    @Test
    fun borderCoversEveryCorner() {
        val bounds = Rect(10.25f, 6.75f, 48f, 32f)
        val color = 0x16385A80.rgba
        val edge = 0x99CCFF80.rgba
        for (cut in listOf(0f, 3f, 5f)) {
            val commands = mutableListOf<DrawCommand>()
            val canvas = object : Canvas {
                override fun emit(command: DrawCommand) { commands += command }
            }
            canvas.pixelPanel(bounds, color, edge, cut)
            val fills = commands.filterIsInstance<Fill>()
            val border = fills.single { (it.brush as Solid).color == edge }
            val interior = fills.single { (it.brush as Solid).color == color }
            for (y in -1..32) for (x in -1..48) {
                val point = Vec2(bounds.x + x + 0.5f, bounds.y + y + 0.5f)
                val isInterior = (-3..3).all { dy -> (-3..3).all { dx -> inside(x + dx, y + dy, cut) } }
                val isBorder = inside(x, y, cut) && !isInterior
                val message = "cut=$cut; pixel=($x, $y)"
                assertEquals(isBorder, border.shape.contains(point, border.bounds), message)
                assertEquals(isInterior, interior.shape.contains(point, interior.bounds), message)
                // Neither the panel fill nor its lighting may cover the translucent border.
                if (isBorder) fills.filter { it !== border }.forEach {
                    assertFalse(it.shape.contains(point, it.bounds), message)
                }
            }
        }
    }

    private fun inside(x: Int, y: Int, cut: Float): Boolean = x >= 0 && x < 48 && y >= 0 && y < 32 &&
            ((x >= cut && x < 48 - cut) || (y >= cut && y < 32 - cut))

    @Test
    fun emptyAndTinyPanels() {
        val commands = mutableListOf<DrawCommand>()
        val canvas = object : Canvas {
            override fun emit(command: DrawCommand) { commands += command }
        }
        canvas.pixelPanel(Rect.ZERO, 0x112233FF.rgba)
        assertEquals(0, commands.size)
        canvas.pixelPanel(Rect(4f, 5f, 1f, 2f), 0x112233FF.rgba, 0xAABBCCFF.rgba)
        val border = commands.filterIsInstance<Fill>().single()
        assertEquals(true, border.shape.contains(Vec2(4.5f, 5.5f), border.bounds))
        assertEquals(false, border.shape.contains(Vec2(3.5f, 5.5f), border.bounds))
    }
}
