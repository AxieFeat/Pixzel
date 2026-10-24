package pixzel

import org.junit.jupiter.api.Test
import pixzel.component.StateSlot
import pixzel.component.virtualList
import pixzel.graphics.RecordingBackend
import pixzel.layout.unit.dp
import pixzel.layout.unit.fillAvailable
import pixzel.platform.TestHost
import pixzel.text.FixedFontTextService
import kotlin.time.Duration

class VirtualListTest {

    @Test
    fun boundsMaterializationAndPreservesKeyedState() {
        val ui = ui(TestHost.of(320, 240), RecordingBackend.of(), FixedFontTextService.of())
        val list = virtualList(ui, (0 until 100_000).toList(), { it }, estimatedHeight = 20f) { item ->
            ui.components.button().apply {
                layout.height = (20 + item.value % 3).dp
                layout.width = fillAvailable
            }
        }
        ui.root.add(list)
        ui.frame(Duration.ZERO)
        check(list.materializedCount in 10..18) { "Materialized ${list.materializedCount}" }
        val slot = StateSlot<String>("edit")
        list.itemState(2, slot) { "initial" }.value = "edited"
        list.scrollToKey(90_000)
        ui.frame(Duration.ZERO)
        check(list.materializedCount < 20 && list.itemState(2, slot) { "wrong" }.value == "edited")
        list.scrollToKey(2)
        ui.frame(Duration.ZERO)
        check(list.materializedCount < 20)
        ui.close()
    }
}
