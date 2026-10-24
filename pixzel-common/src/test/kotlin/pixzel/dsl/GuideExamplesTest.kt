package pixzel.dsl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pixzel.Ui
import pixzel.component.Button
import pixzel.component.Image
import pixzel.component.Text
import pixzel.component.TextField
import pixzel.graphics.RecordingBackend
import pixzel.graphics.SoftwareImage
import pixzel.input.NavigationCommand
import pixzel.layout.unit.dp
import pixzel.platform.TestHost
import pixzel.resource.DecodedImage
import pixzel.resource.ImageAssetSizing
import pixzel.resource.ImageDecoder
import pixzel.resource.ImageUploader
import pixzel.resource.bindAsset
import pixzel.resource.imageAsset
import pixzel.state.bind
import pixzel.state.stateOf
import pixzel.state.stateRuntime
import pixzel.surface.Size
import pixzel.text.FixedFontTextService
import pixzel.text.RevealTrigger
import pixzel.text.TextOverflow
import pixzel.text.hoverReveal
import pixzel.ui
import java.util.concurrent.Executor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** These functions are copied verbatim into USER_GUIDE.md (with the import prelude). */
fun directCounter(ui: Ui): Button {
    val runtime = stateRuntime()
    val count = stateOf(0, runtime)
    val button = Button.of(ui.components, "counter")
    button.layout.width = 240.dp
    button.setLabel("").bindValue(bind(runtime) { "Players: ${count.value}" })
    button.resources.own(button.onClick { count.value++ })
    ui.root.add(button)
    return button
}

fun dslPanel(ui: Ui): TextField {
    val runtime = stateRuntime()
    val count = stateOf(0, runtime)
    lateinit var field: TextField
    ui.content {
        column("panel", spacing = 8.dp) {
            layout {
                width = 320.dp
                height = 220.dp
            }
            text {
                value(bind(runtime) { "Players: ${count.value}" })
                layout { height = 28.dp }
            }
            button("add") {
                layout {
                    width = 240.dp
                    height = 44.dp
                }
                label("Add another player to the server") {
                    overflow = TextOverflow.ELLIPSIS
                    reveal = hoverReveal {
                        trigger = RevealTrigger.HOVER_OR_FOCUS
                        delay = 600.milliseconds
                    }
                }
                onClick { count.value++ }
            }
            field = textField(id = "name") {
                placeholder = "Player name"
                layout.width = 240.dp
            }
        }
    }
    return field
}

fun loadImage(
    ui: Ui, executor: Executor, source: () -> ByteArray,
    decoder: ImageDecoder, uploader: ImageUploader
): Image {
    val asset = imageAsset(ui, source, decoder, uploader, executor, placeholderSize = Size(96f, 64f))
    val image = Image.of(ui.components, "portrait")
    ui.root.add(image)
    image.bindAsset(asset, ImageAssetSizing.PLACEHOLDER)
    return image
}

class GuideExamplesTest {

    @Test
    fun `guide direct and DSL state examples retain activation and replace previous content`() {
        val host = TestHost.of(400, 260)
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        try {
            val direct = directCounter(ui)
            ui.frame(Duration.ZERO)
            ui.focus.request(direct)
            assertTrue(ui.navigate(NavigationCommand.ACTIVATE))
            assertEquals("Players: 1", (direct.content as Text).value)
            val field = dslPanel(ui)
            ui.frame(Duration.ZERO)
            assertTrue(direct.isDisposed)
            assertEquals("Player name", field.placeholder)
            assertSame(ui, field.ui)
        } finally {
            ui.close()
            host.close()
        }
    }

    @Test
    fun `guide image pipeline applies posted upload and Ui owns the resulting handle`() {
        val host = TestHost.of(160, 120)
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val uploaded = SoftwareImage.of(1, 1, intArrayOf(-1))
        try {
            val image = loadImage(
                ui, Executor { it.run() }, { byteArrayOf(1) },
                ImageDecoder { DecodedImage(1, 1, byteArrayOf(-1, -1, -1, -1)) }, ImageUploader { uploaded })
            assertNull(image.image)
            ui.frame(Duration.ZERO)
            assertSame(uploaded, image.image)
            assertEquals(Size(96f, 64f), image.surface.bounds.size)
        } finally {
            ui.close()
            host.close()
        }
        assertTrue(uploaded.isClosed)
    }
}
