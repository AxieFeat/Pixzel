package pixzel.demo

import arc.Application
import arc.ArcObjectProvider
import arc.asset.asRuntimeAsset
import arc.font.NativeFontProvider
import arc.gl.OpenGL
import arc.graphics.BlendState
import arc.graphics.CullMode
import arc.graphics.DepthState
import arc.graphics.DrawerMode
import arc.graphics.RenderContext
import arc.graphics.vertex.VertexFormat
import arc.graphics.vertex.VertexFormatElement
import arc.input.GlfwInputEngine
import arc.input.KeyCode
import arc.shader.ShaderInstance
import pixzel.Ui
import pixzel.UiSettings
import pixzel.arc.graphics.ArcImageHandle
import pixzel.arc.graphics.ArcUiBackend
import pixzel.arc.platform.ArcUiHost
import pixzel.arc.text.ArcTextService
import pixzel.component.Button
import pixzel.component.CheckState
import pixzel.component.Modal
import pixzel.component.NotificationHost
import pixzel.component.Text
import pixzel.component.tooltip
import pixzel.component.virtualList
import pixzel.debug.UiInspector
import pixzel.dsl.ContentScope
import pixzel.dsl.ElementScope
import pixzel.dsl.checkbox
import pixzel.dsl.content
import pixzel.dsl.scrollView
import pixzel.dsl.slider
import pixzel.graphics.Color
import pixzel.graphics.Solid
import pixzel.graphics.rgba
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.layout.adaptiveLayout
import pixzel.layout.columnLayout
import pixzel.layout.rowLayout
import pixzel.layout.unit.dp
import pixzel.layout.unit.fillAvailable
import pixzel.layout.unit.sp
import pixzel.layout.unit.wrapContent
import pixzel.resource.ResourceScope
import pixzel.state.stateOf
import pixzel.style.Theme
import pixzel.surface.Insets
import pixzel.text.CachingTextService
import pixzel.text.RevealTrigger
import pixzel.text.TextDirection
import pixzel.text.TextOverflow
import pixzel.text.dpPerSecond
import pixzel.text.hoverReveal
import pixzel.ui
import sample.GameButtonSkin
import sample.SteppedReveal
import sample.healthBar
import sample.radialButton
import sample.useSkin
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

fun main(args: Array<String>) {
    val smoke = "--smoke" in args
    val scale = args.firstOrNull { it.startsWith("--scale=") }?.substringAfter('=')?.toFloat() ?: 1f
    val width = args.firstOrNull { it.startsWith("--width=") }?.substringAfter('=')?.toInt() ?: 1100
    val height = args.firstOrNull { it.startsWith("--height=") }?.substringAfter('=')?.toInt() ?: 800
    require(width > 0 && height > 0)
    ArcObjectProvider.install()
    ArcObjectProvider.bootstrap()
    OpenGL.preload()
    val application = Application.find()
    application.init()
    application.window.name = "Pixzel · Arc/OpenGL showcase"
    application.window.resize(width, height)
    if (smoke) application.window.isHidden = true
    GlfwInputEngine.hook(application.window)
    val resources = ResourceScope()
    try {
        val fonts = resources.own(NativeFontProvider.of())
        val root = Path.of(System.getProperty("pixzel.fonts", "../assets/fonts"))
        val latin = fonts.load("noto", Files.readAllBytes(root.resolve("NotoSans-Regular.ttf")))
        val arabic = fonts.load("arabic", Files.readAllBytes(root.resolve("NotoSansArabic-Regular.ttf")))
        val text = ArcTextService.of(listOf(latin, arabic))
        val backend = ArcUiBackend.of(application.renderContext, text)
        val ui = resources.own(ui(ArcUiHost.of(application.window), backend, CachingTextService.of(text), UiSettings(uiScale = scale)))
        val shader = resources.own(
            ShaderInstance.of(
                """
            #version 410 core
            layout(location=0) in vec3 Position;
            layout(location=1) in vec4 Color;
            out vec4 tint;
            void main(){gl_Position=vec4(Position,1.0);tint=Color;}
        """.trimIndent().asRuntimeAsset(), """
            #version 410 core
            in vec4 tint;out vec4 outputColor;
            void main(){outputColor=tint;}
        """.trimIndent().asRuntimeAsset()
            ).also { it.compileShaders() })
        val context = application.renderContext
        val format = VertexFormat.builder().add(VertexFormatElement.POSITION).add(VertexFormatElement.COLOR).build()
        val triangle = resources.own(context.drawer.begin(DrawerMode.TRIANGLES, format, 64).use { buffer ->
            buffer.addVertex(-.95f, -.8f, .5f).setColor(35, 70, 110, 255)
            buffer.addVertex(.95f, -.8f, .5f).setColor(110, 45, 80, 255)
            buffer.addVertex(0f, .95f, .5f).setColor(35, 100, 85, 255)
            buffer.buildArray()
        })
        buildShowcase(ui, resources, context)
        var previous = System.nanoTime()
        var frames = 0
        while (!application.window.shouldClose() && (!smoke || frames < 8)) {
            context.beginFrame()
            try {
                context.setDepth(DepthState(testEnabled = true, writeEnabled = true))
                context.setCull(CullMode.NONE)
                context.setBlend(BlendState.DISABLED)
                context.bindShader(shader)
                context.drawer.draw(triangle)
                val now = System.nanoTime()
                ui.frame(if (smoke) 16.milliseconds else (now - previous).nanoseconds)
                previous = now
                if (smoke && frames == 7) captureShowcase(
                    application.window.width,
                    application.window.height,
                    Path.of("build", "reports", "arc-showcase-$scale-${width}x$height.png")
                )
                // Game update/polling uses the masked view after the UI has consumed this frame's events.
                if (GlfwInputEngine.keyboard.isPressed(KeyCode.KEY_ESCAPE)) break
            } finally {
                context.endFrame()
            }
            frames++
        }
        println("Pixzel Arc showcase ran $frames frames; nodes=${ui.statistics.totalNodes}, lastMeasured=${ui.statistics.measuredNodes}")
        // ResourceScope closes in reverse order; close Ui before scene/font resources and native context.
        ui.close()
    } finally {
        try {
            resources.close()
        } finally {
            GlfwInputEngine.unhook()
            application.close()
        }
    }
}

private fun buildShowcase(ui: Ui, resources: ResourceScope, context: RenderContext) {
    val health = stateOf(.7f)
    var revealSpeed = 32f
    var revealDelay = 600f
    var endpointPause = 700f
    lateinit var longLabel: Text
    lateinit var notifications: NotificationHost
    fun ElementScope.responsiveControls() {
        layout {
            width = fillAvailable
            height = wrapContent
        }
        adaptiveLayout(reference = ui.root.surface) {
            defaultPolicy = rowLayout(8.dp)
            whenWidthAtMost(760.dp, columnLayout(8.dp))
        }
    }

    fun updateReveal() {
        longLabel.reveal = hoverReveal {
            trigger = RevealTrigger.HOVER_OR_FOCUS
            speed = revealSpeed.dpPerSecond
            delay = revealDelay.toInt().milliseconds
            pauseAtEnd = endpointPause.toInt().milliseconds
            pauseAtStart = endpointPause.toInt().milliseconds
        }
    }

    val pixels = ByteArray(24 * 24 * 4)
    for (y in 0 until 24) for (x in 0 until 24) {
        val i = (y * 24 + x) * 4
        val edge = x < 4 || y < 4 || x >= 20 || y >= 20
        pixels[i] = (if (edge) 180 else 65).toByte()
        pixels[i + 1] = (if (edge) 145 else 88).toByte()
        pixels[i + 2] = (if (edge) 66 else 44).toByte()
        pixels[i + 3] = 255.toByte()
    }
    val gameImage = resources.own(ArcImageHandle.rgba(context, 24, 24, pixels, "game-button"))
    ui.content {
        val viewport = insert(ui.components.scrollView("showcase-scroll").apply {
            layout.width = fillAvailable
            layout.height = fillAvailable
        })
        ContentScope(ui, viewport).column("showcase") {
            layout {
                width = fillAvailable
                height = wrapContent
                padding = Insets.all(24.dp)
            }
            text("Pixzel · independent retained UI + Arc") {
                typography { size = 24.sp }
                layout { height = 40.dp }
            }
            text("Tab / Shift+Tab · Enter / Space · hover or focus the server label") { layout { height = 28.dp } }
            row {
                responsiveControls()
                button("flat") { label("Flat") }
                insert(Button.of(ui.components).apply {
                    useSkin(GameButtonSkin(gameImage, Insets(4f, 4f, 4f, 4f)))
                    setLabel("Nine-slice")
                    onClick { ui.theme = Theme.GAME }
                })
                insert(radialButton(ui) { health.value = (health.value - .1f).coerceAtLeast(0f) }.apply {
                    layout.width = 64.dp
                    layout.height = 64.dp
                })
            }
            val server = button("server") {
                layout {
                    width = 360.dp
                    height = 48.dp
                }
                longLabel = label("Длинное название игрового сервера · EU West · friends welcome") { ellipsisMarker = "..." }
                onClick { notifications.show { text("Connected") } }
            }
            server.tooltip("Full server name: Длинное название игрового сервера · EU West · friends welcome")
            updateReveal()
            row {
                responsiveControls()
                text("Label width") {
                    layout {
                        width = 110.dp
                        height = 32.dp
                    }
                }
                insert(ui.components.slider().apply {
                    setRange(100f, 600f)
                    value = 360f
                    onValueChange { server.layout.width = it.dp }
                })
                button {
                    label("Wrap / ellipsis")
                    onClick {
                        longLabel.wrap = !longLabel.wrap
                        longLabel.maxLines = if (longLabel.wrap) 2 else 1
                        longLabel.overflow = if (longLabel.wrap) TextOverflow.WRAP else TextOverflow.ELLIPSIS
                    }
                }
            }
            row {
                responsiveControls()
                text("Speed") {
                    layout {
                        width = 90.dp
                        height = 32.dp
                    }
                }
                insert(ui.components.slider().apply {
                    setRange(0f, 120f)
                    value = 32f
                    onValueChange {
                        revealSpeed = it
                        updateReveal()
                    }
                })
                text("Delay") {
                    layout {
                        width = 90.dp
                        height = 32.dp
                    }
                }
                insert(ui.components.slider().apply {
                    setRange(0f, 1500f)
                    value = 600f
                    onValueChange {
                        revealDelay = it
                        updateReveal()
                    }
                })
            }
            row {
                responsiveControls()
                button {
                    label("Marker")
                    onClick { longLabel.ellipsisMarker = if (longLabel.ellipsisMarker == "...") "…" else "..." }
                }
                button {
                    label("End pause")
                    onClick {
                        endpointPause = if (endpointPause == 700f) 0f else 700f
                        updateReveal()
                    }
                }
                button {
                    label("Custom reveal")
                    onClick { longLabel.reveal = SteppedReveal() }
                }
                button {
                    label("RTL / LTR")
                    onClick { longLabel.direction = if (longLabel.direction == TextDirection.RTL) TextDirection.LTR else TextDirection.RTL }
                }
            }
            row {
                responsiveControls()
                insert(ui.components.checkbox().apply {
                    setLabel("Reduced motion")
                    onCheckChange { ui.settings = ui.settings.copy(isReducedMotion = it == CheckState.CHECKED) }
                })
                button {
                    label("Scale")
                    onClick { ui.settings = ui.settings.copy(uiScale = if (ui.settings.uiScale == 1f) 1.25f else 1f) }
                }
                button {
                    label("Text scale")
                    onClick { ui.settings = ui.settings.copy(textScale = if (ui.settings.textScale == 1f) 1.2f else 1f) }
                }
                button {
                    label("Light / dark")
                    onClick { ui.theme = if (ui.theme == Theme.DARK) Theme.LIGHT else Theme.DARK }
                }
            }
            textField(id = "unicode") {
                placeholder = "Unicode text, clipboard, selection, undo"
                layout.width = 360.dp
            }
            healthBar(health)
            row {
                responsiveControls()
                button {
                    label("Modal")
                    onClick {
                        val modal = Modal.of(ui)
                        modal.content {
                            column {
                                text("Keyboard focus is trapped here")
                                textField()
                                button {
                                    label("Close")
                                    onClick { modal.close() }
                                }
                            }
                        }
                    }
                }
                button {
                    label("Notifications")
                    onClick { notifications.show { text("Saved successfully") } }
                }
            }
            val list = virtualList(ui, (0 until 100_000).toList(), { it }) { item ->
                ui.components.button().apply {
                    layout.width = fillAvailable
                    layout.height = (24 + item.value % 3).dp
                    setLabel("Item ${item.value}")
                }
            }
            list.layout.height = 140.dp
            insert(list)
        }
    }
    notifications = NotificationHost.of(ui, limit = 3)
    val inspector = resources.own(UiInspector.of(ui))
    ui.root.resources.own(ui.root.onEvent { event ->
        val key = event.event as? KeyEvent
        if (key?.key == Key.HOME && key.modifiers.control && key.action == KeyAction.DOWN) {
            inspector.enabled = !inspector.enabled
            inspector.selected = ui.focus.focused
            event.consume()
            event.preventDefault()
        }
    })
}
