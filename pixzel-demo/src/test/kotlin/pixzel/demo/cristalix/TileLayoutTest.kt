package pixzel.demo.cristalix

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TileLayoutTest {

    @Test
    fun packsSpanningTiles() {
        val rectangles = masonry(
            1170f, 6, 20f,
            List(6) { TileSize(3, 174f) } + listOf(TileSize(4, 300f), TileSize(2, 142f), TileSize(2, 138f)))
        assertEquals(575f, rectangles.first().width)
        assertEquals(0f, rectangles[0].y)
        assertEquals(0f, rectangles[1].y)
        assertEquals(582f, rectangles[6].y)
        assertEquals(582f, rectangles[7].y)
        assertEquals(744f, rectangles[8].y)
        assertEquals(rectangles[6].bottom, rectangles[8].bottom)
        rectangles.forEachIndexed { index, rect ->
            rectangles.drop(index + 1).forEach { other ->
                assertTrue(rect.right <= other.x || other.right <= rect.x || rect.bottom <= other.y || other.bottom <= rect.y)
            }
        }
    }

    @Test
    fun stacksNarrowTiles() {
        val rectangles = masonry(360f, 1, 20f, listOf(TileSize(6, 124f), TileSize(2, 142f), TileSize(4, 300f)))
        assertTrue(rectangles.all { it.width == 360f && it.x == 0f })
        assertEquals(listOf(0f, 144f, 306f), rectangles.map { it.y })
    }
}
