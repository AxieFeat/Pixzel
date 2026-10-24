package pixzel.demo

import pixzel.Ui
import pixzel.dsl.content
import pixzel.graphics.SoftwareBackend
import pixzel.graphics.rgba
import pixzel.graphics.solid
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.layout.unit.dp
import pixzel.platform.TestHost
import pixzel.state.bind
import pixzel.state.stateOf
import pixzel.surface.Anchor
import pixzel.surface.Insets
import pixzel.surface.Pivot
import pixzel.text.FixedFontTextService
import pixzel.text.RevealTrigger
import pixzel.text.TextAlignment
import pixzel.text.hoverReveal
import pixzel.ui
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * One builder is shared by independent and Arc entry points.
 */
fun buildMenu(ui: Ui) {
    val online = stateOf(12)
    ui.content {
        surface("notification") {
            layout {
                width = 320.dp
                height = 72.dp
                padding = Insets.all(12.dp)
            }
            placement { offset(24.dp, 24.dp) }
            appearance {
                background = solid(0x18212BFF.rgba)
                corners = 12.dp
            }
            text("Connection established") {
                layout {
                    width = 200.dp
                    height = 24.dp
                }
                placement {
                    anchor = Anchor.CENTER
                    pivot = Pivot.CENTER
                }
                horizontalAlignment = TextAlignment.CENTER
            }
        }
        column("menu") {
            layout {
                width = 360.dp
                height = 240.dp
            }
            placement {
                anchor = Anchor.CENTER
                pivot = Pivot.CENTER
            }
            text { value(bind { "Players online: ${online.value}" }) }
            button("connect") {
                layout {
                    width = 260.dp
                    height = 48.dp
                }
                label("A long and descriptive game server name") {
                    ellipsisMarker = "..."
                    reveal = hoverReveal { trigger = RevealTrigger.HOVER_OR_FOCUS }
                }
                onClick { online.value++ }
            }
            textField(id = "name") { placeholder = "Your name" }
        }
    }
}

fun main(args: Array<String>) {
    val host = TestHost.of(960, 540)
    val backend = SoftwareBackend.of()
    ui(host, backend, FixedFontTextService.of()).use { ui ->
        buildMenu(ui)
        ui.frame(Duration.ZERO)
        host.queue(KeyEvent(Key.TAB, KeyAction.DOWN))
        host.queue(KeyEvent(Key.ENTER, KeyAction.DOWN))
        ui.frame(16.milliseconds)
        val output = Path.of(args.firstOrNull() ?: "build/preview.png").toAbsolutePath()
        backend.writePng(output)
        println("Rendered ${ui.statistics.totalNodes} independent retained nodes to $output; measured=${ui.statistics.measuredNodes}")
    }
    host.close()
}
