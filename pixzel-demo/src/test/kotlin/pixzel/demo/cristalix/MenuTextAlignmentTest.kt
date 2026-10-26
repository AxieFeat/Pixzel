package pixzel.demo.cristalix

import arc.font.NativeFontProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pixzel.arc.text.ArcTextService
import pixzel.graphics.DrawText
import pixzel.graphics.RecordingBackend
import pixzel.layout.unit.dp
import pixzel.platform.TestHost
import pixzel.surface.Placement
import pixzel.surface.Vec2
import pixzel.text.TextStyle
import pixzel.ui
import kotlin.time.Duration

class MenuTextAlignmentTest {

    @Test
    fun centersRealFontButtons() {
        NativeFontProvider.of().use { fonts ->
            val bytes = javaClass.getResourceAsStream("/cristalix/Monocraft.ttf")!!.use { it.readBytes() }
            val text = ArcTextService.of(listOf(fonts.load("monocraft", bytes)))
            val host = TestHost.of(800, 600)
            val backend = RecordingBackend.of()
            ui(host, backend, text).use { ui ->
                val model = MenuModel(null)
                val buttons = listOf(
                    MenuButton("toolbar", model, "Избранное"),
                    MenuButton("close", model, "×"),
                    MenuButton("motion", model, "Анимации: вкл")
                )
                buttons.forEachIndexed { index, button ->
                    button.layout.width = if (index == 1) 32.dp else 136.dp
                    button.layout.height = if (index == 0) 38.dp else 32.dp
                    button.placement = Placement(offset = Vec2(0f, index * 60f))
                    ui.root.add(button)
                }
                val standard = ui.components.button("standard").apply {
                    layout.height = 44.dp
                    setLabel("Кнопка").style = TextStyle()
                }
                ui.root.add(standard)
                for (scale in listOf(1f, 1.25f, 2f)) {
                    ui.settings = ui.settings.copy(textScale = scale)
                    ui.frame(Duration.ZERO)
                    val draws = backend.lastFrame!!.commands.filterIsInstance<DrawText>()
                    buttons.forEach { button ->
                        val draw = draws.last { it.layout.text == button.title }
                        val height = button.surface.bounds.height
                        // Oversized text stays at the top instead of receiving a negative offset.
                        assertEquals((height - draw.layout.size.height).coerceAtLeast(0f) / 2f, draw.origin.y)
                        assertEquals(button.surface.bounds.width / 2f, draw.origin.x + draw.layout.size.width / 2f, 0.01f)
                    }
                    val draw = draws.single { it.layout.text == "Кнопка" }
                    assertEquals((44f - draw.layout.size.height).coerceAtLeast(0f) / 2f, draw.origin.y)
                }
            }
            host.close()
        }
    }

    @Test
    fun centersDropdownRows() {
        val host = TestHost.of(800, 600)
        val backend = RecordingBackend.of()
        ui(host, backend, pixzel.text.FixedFontTextService.of()).use { ui ->
            val model = MenuModel(null).apply { reducedMotion = true }
            val dropdown = MenuDropdown(model) {}
            dropdown.layout.width = 166.dp
            dropdown.layout.height = 198.dp
            ui.root.add(dropdown)
            dropdown.show(MenuDropdown.CATEGORIES, Vec2.ZERO)
            ui.frame(Duration.ZERO)
            val draws = backend.lastFrame!!.commands.filterIsInstance<DrawText>().filter { it.color.alpha == 255 }
            assertEquals(dropdown.children.size, draws.size)
            draws.forEach { draw ->
                assertEquals((48f - draw.layout.size.height) / 2f, draw.origin.y)
            }
        }
        host.close()
    }
}
