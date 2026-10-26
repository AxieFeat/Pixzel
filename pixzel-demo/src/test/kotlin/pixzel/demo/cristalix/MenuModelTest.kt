package pixzel.demo.cristalix

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

class MenuModelTest {

    @Test
    fun searchIsTrimmedAndCaseless() {
        val model = MenuModel()
        model.query = "  pRiSoN  "

        assertEquals(setOf("prison", "oldschool", "classic"), model.visibleModes().map { it.id }.toSet())
    }

    @Test
    fun searchCrossesLineBreaks() {
        val model = MenuModel()
        model.query = "Мастера Мечей"

        assertEquals(listOf("swords"), model.visibleModes().map { it.id })
    }

    @Test
    fun categoryAndSearchAreCombined() {
        val model = MenuModel()
        model.category = "Аркады"
        model.query = "Prison"

        assertTrue(model.visibleModes().isEmpty())
        model.query = "wars"
        assertEquals(setOf("wool", "bedwars", "lucky", "ant"), model.visibleModes().map { it.id }.toSet())
    }

    @Test
    fun favoritesKeepOrder() {
        val model = MenuModel()
        model.category = "Избранное"
        assertTrue(model.visibleModes().isEmpty())

        model.toggleFavorite("slime")
        model.toggleFavorite("wool")
        assertEquals(listOf("wool", "slime"), model.visibleModes().map { it.id })

        model.toggleFavorite("wool")
        assertEquals(listOf("slime"), model.visibleModes().map { it.id })
        assertEquals(3, model.revision)
    }

    @Test
    fun favoritesPersistKnownModes(@TempDir directory: Path) {
        val path = directory.resolve("preferences/favorites.txt")
        MenuModel(path).toggleFavorite("wool")
        Files.writeString(path, Files.readString(path) + "\nremoved-server\n")

        val restored = MenuModel(path)
        assertEquals(setOf("wool"), restored.favorites)
    }

    @Test
    fun motionIsFrameRateIndependent() {
        fun run(rate: Int): Float {
            val spring = MenuSpring().apply { target = 1f }
            repeat(rate) { spring.step(1f / rate) }
            return spring.value
        }

        val values = listOf(30, 60, 120).map(::run)
        assertTrue(values.all { abs(it - 1f) < 0.001f })
        assertTrue(values.max() - values.min() < 0.0001f)
    }

    @Test
    fun motionSettlesAndSnaps() {
        val spring = MenuSpring().apply { target = 150f }
        repeat(240) { spring.step(1f / 60) }
        assertFalse(spring.step(1f / 60))
        assertEquals(150f, spring.value)

        spring.target = -20f
        spring.snap()
        assertEquals(-20f, spring.value)
        assertFalse(spring.step(0f))
    }
}
