package pixzel.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pixzel.graphics.Concat
import pixzel.graphics.DrawText
import pixzel.graphics.RecordingBackend
import pixzel.graphics.Restore
import pixzel.graphics.Save
import pixzel.layout.Constraints
import pixzel.layout.unit.dp
import pixzel.platform.TestHost
import pixzel.surface.Insets
import pixzel.surface.Placement
import pixzel.surface.Size
import pixzel.surface.Transform
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import pixzel.text.TextAlignment
import pixzel.text.TextDirection
import pixzel.text.VerticalAlignment
import pixzel.ui
import kotlin.time.Duration

class TextAlignmentTest {

    @Test
    fun textAlignmentAndBaseline() {
        val host = TestHost.of()
        val backend = RecordingBackend.of()
        val ui = ui(host, backend, FixedFontTextService.of())
        try {
            val text = ui.components.text().apply {
                value = "ab"
                layout.width = 100.dp
                layout.height = 40.dp
                layout.padding = Insets(6f, 4f, 10f, 8f)
            }
            ui.root.add(text)
            for ((alignment, y) in listOf(
                VerticalAlignment.TOP to 4f,
                VerticalAlignment.CENTER to 10f,
                VerticalAlignment.BOTTOM to 16f
            )) {
                text.verticalAlignment = alignment
                for (direction in listOf(TextDirection.LTR, TextDirection.RTL)) {
                    text.direction = direction
                    for ((horizontal, x) in listOf(
                        TextAlignment.LEFT to 6f,
                        TextAlignment.CENTER to 40f,
                        TextAlignment.RIGHT to 74f,
                        TextAlignment.START to if (direction == TextDirection.RTL) 74f else 6f,
                        TextAlignment.END to if (direction == TextDirection.RTL) 6f else 74f
                    )) {
                        text.horizontalAlignment = horizontal
                        ui.frame(Duration.ZERO)
                        assertEquals(Vec2(x, y), backend.lastFrame!!.commands.filterIsInstance<DrawText>().single().origin)
                        assertEquals(y + 12f, text.measure(Constraints.fixed(Size(100f, 40f))).baseline)
                    }
                }
            }
        } finally {
            ui.close()
            host.close()
        }
    }

    @Test
    fun standardControlsCenterLabels() {
        val host = TestHost.of()
        val backend = RecordingBackend.of()
        val ui = ui(host, backend, FixedFontTextService.of())
        try {
            val controls = listOf(
                ui.components.button("button"), ui.components.checkbox("checkbox"),
                ui.components.radio("radio"), ui.components.toggle("toggle"),
                dropdown<String>(ui, id = "dropdown")
            )
            controls.forEachIndexed { index, button ->
                button.setLabel(button.id)
                button.layout.height = (40 + index * 4).dp
                button.placement = Placement(offset = Vec2(0f, index * 60f))
                ui.root.add(button)
            }
            val tabs = ui.components.tabs().apply {
                addTab("tab", "tab", ui.components.surface())
                placement = Placement(offset = Vec2(200f, 0f))
                layout.width = 200.dp
                layout.height = 100.dp
            }
            ui.root.add(tabs)
            ui.frame(Duration.ZERO)
            val header = tabs.children.first().children.first().children.single() as Button
            val rows = controls + header
            for (scale in listOf(1f, 1.25f, 2f)) {
                ui.settings = ui.settings.copy(uiScale = scale, textScale = 1.25f)
                ui.frame(Duration.ZERO)
                val centers = drawnCenters(backend)
                rows.forEach { button ->
                    val box = button.surface.contentBox
                    val expected = button.surface.toRoot(Vec2(box.x, box.y + box.height / 2f)).y
                    assertEquals(expected, centers.getValue((button.content as Text).value))
                }
            }
            Menu.of(ui, controls.first(), listOf(MenuItem("menu", "menu", "menu"))) {}.use {
                ui.frame(Duration.ZERO)
                val row = descendants(it.element).first { element -> element.semantics.role == Role.LIST_ITEM }
                val box = row.surface.contentBox
                assertEquals(row.surface.toRoot(Vec2(box.x, box.y + box.height / 2f)).y, drawnCenters(backend).getValue("menu"))
            }
        } finally {
            ui.close()
            host.close()
        }
    }

    private fun descendants(element: Element): List<Element> = listOf(element) + element.children.flatMap(::descendants)

    private fun drawnCenters(backend: RecordingBackend): Map<String, Float> {
        var transform = Transform.IDENTITY
        val stack = ArrayDeque<Transform>()
        val centers = mutableMapOf<String, Float>()
        backend.lastFrame!!.commands.forEach { command ->
            when (command) {
                Save -> stack.addLast(transform)
                Restore -> transform = stack.removeLast()
                is Concat -> transform *= command.transform
                is DrawText -> centers[command.layout.text] = transform.map(command.origin + Vec2(0f, command.layout.size.height / 2f)).y
                else -> Unit
            }
        }
        return centers
    }
}
