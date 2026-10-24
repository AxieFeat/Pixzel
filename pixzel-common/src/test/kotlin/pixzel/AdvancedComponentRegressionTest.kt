package pixzel

import org.junit.jupiter.api.Test
import pixzel.component.Button
import pixzel.component.NotificationHost
import pixzel.component.ScrollAxes
import pixzel.component.StateSlot
import pixzel.component.virtualList
import pixzel.dsl.KeyedItems
import pixzel.graphics.RecordingBackend
import pixzel.layout.unit.dp
import pixzel.layout.unit.fillAvailable
import pixzel.platform.TestHost
import pixzel.surface.Insets
import pixzel.surface.Placement
import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.surface.Transform
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class AdvancedComponentRegressionTest {

    @Test
    fun virtualRowsHaveIndependentIdScopesAndRetainTheirMeasuredAnchor() {
        val host = TestHost.of(320, 240)
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val list = virtualList(ui, (0 until 100).toList(), { it }, estimatedHeight = 20f) { current ->
            ui.components.button("row").apply {
                layout.width = fillAvailable
                layout.height = (20 + current.value % 3).dp
                setLabel(current.value.toString())
            }
        }
        list.layout.height = 100.dp
        ui.root.add(list)
        ui.frame(Duration.ZERO)
        check(list.materializedCount > 2 && list.children.all { it.children.single().id == "row" })
        list.scrollToKey(50)
        ui.frame(Duration.ZERO)
        fun row50() = list.children.flatMap { it.children }.first { it.semantics.label == "50" }
        val row = row50()
        check(abs(row.surface.toRoot(Vec2.ZERO).y) < .01f)
        list.items = list.items.reversed()
        ui.frame(Duration.ZERO)
        check(row50() === row && abs(row.surface.toRoot(Vec2.ZERO).y) < .01f)
        val slot = StateSlot<Int>("thread-check")
        var failure: Throwable? = null
        val worker = Thread { failure = runCatching { list.itemState(0, slot) { 0 } }.exceptionOrNull() }
        worker.start()
        worker.join()
        check(failure is IllegalStateException)
        ui.close()
        check(list.materializedCount == 0)
    }

    @Test
    fun horizontalScrollAndTransformAwareRevealUseTheConfiguredAxis() {
        val host = TestHost.of(320, 240)
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val scroll = ui.components.scrollView().apply {
            axes = ScrollAxes.HORIZONTAL
            layout.width = 100.dp
            layout.height = 40.dp
        }
        val content = ui.components.surface().apply {
            layout.width = 500.dp
            layout.height = 20.dp
            paintTransform = Transform.scale(2f)
        }
        val target = ui.components.surface().apply {
            layout.width = 20.dp
            layout.height = 10.dp
            placement = Placement(offset = Vec2(150f, 0f))
        }
        content.add(target)
        scroll.add(content)
        ui.root.add(scroll)
        ui.frame(Duration.ZERO)
        check(scroll.extent.width == 500f)
        scroll.scrollIntoView(target)
        check(scroll.scrollOffset == Vec2(240f, 0f))
        ui.frame(Duration.ZERO)
        check(target.surface.toRoot(Vec2.ZERO).x == 60f)
        check(scroll.scrollBy(Vec2(0f, 12f)) == Vec2(0f, 12f))
        ui.close()
    }

    @Test
    fun notificationsFitSafeViewportPauseForDescendantFocusAndQueueOverflow() {
        val host = TestHost.of(320, 150)
        host.resize(Size(320f, 150f), safeInsets = Insets(10f, 5f, 20f, 5f))
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val notifications = NotificationHost.of(ui, limit = 4, capacity = 8)
        lateinit var button: Button
        val first = notifications.show(10.milliseconds) { button = button("focused") }
        val second = notifications.show(100.milliseconds) { text("Second") }
        ui.frame(Duration.ZERO)
        check(ui.focus.request(button))
        ui.frame(20.milliseconds)
        check(!first.element.isDisposed && second.element.parent == null)
        val bounds = notifications.surface.mapRect(Rect(0f, 0f, notifications.surface.bounds.width, notifications.surface.bounds.height))
        check(bounds.x >= 10 && bounds.right <= 300 && bounds.y >= 5 && bounds.bottom <= 145)
        ui.focus.clear()
        ui.frame(20.milliseconds)
        check(first.element.isDisposed && second.element.parent === notifications)
        host.resize(100, 40)
        ui.frame(Duration.ZERO)
        check(notifications.surface.bounds.width <= 100f && notifications.surface.bounds.height <= 40f)
        check(second.element.surface.bounds.height <= 40f)
        ui.close()
    }

    @Test
    fun notificationAndKeyedBuilderFailuresDisposePartialSubtrees() {
        val host = TestHost.of()
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val notifications = NotificationHost.of(ui)
        var toastClosed = false
        check(runCatching {
            notifications.show {
                surface("partial") { element.resources.own(AutoCloseable { toastClosed = true }) }
                error("toast builder")
            }
        }.isFailure)
        check(toastClosed && notifications.children.isEmpty())
        val holder = ui.components.surface("keyed")
        ui.root.add(holder)
        var rowClosed = false
        var fail = true
        val managed = KeyedItems<Int, Int>(ui, holder, { it }) {
            surface("partial") { element.resources.own(AutoCloseable { rowClosed = true }) }
            if (fail) error("row builder")
        }
        check(runCatching { managed.update(listOf(1)) }.isFailure)
        check(rowClosed && holder.children.isEmpty())
        fail = false
        managed.update(listOf(1))
        check(holder.children.size == 1)
        managed.close()
        check(holder.children.isEmpty() && runCatching { managed.update(listOf(2)) }.isFailure)
        notifications.close()
        check(runCatching { notifications.show { text("After close") } }.isFailure)
        ui.close()
    }
}
