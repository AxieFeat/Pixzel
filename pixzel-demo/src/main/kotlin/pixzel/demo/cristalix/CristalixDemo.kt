// Pixel geometry, palette and motion timings are authored as visual design data.
@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import arc.Application
import arc.ArcObjectProvider
import arc.font.NativeFontProvider
import arc.gl.OpenGL
import arc.input.GlfwInputEngine
import pixzel.UiSettings
import pixzel.Ui
import pixzel.arc.graphics.ArcUiBackend
import pixzel.arc.platform.ArcUiHost
import pixzel.arc.text.ArcTextService
import pixzel.demo.captureShowcase
import pixzel.resource.ResourceScope
import pixzel.surface.Vec2
import pixzel.text.CachingTextService
import pixzel.ui
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

fun main(args: Array<String>) {
    val options = MenuOptions.from(args)
    ArcObjectProvider.install()
    ArcObjectProvider.bootstrap()
    OpenGL.preload()
    val application = Application.find()
    application.init()
    application.window.name = "Cristalix · Pixzel animated menu"
    application.window.resize(options.width, options.height)
    if (options.smoke) application.window.isHidden = true
    GlfwInputEngine.hook(application.window)

    val resources = ResourceScope()
    try {
        runFrames(createSession(application, resources, options), options)
    } finally {
        try {
            resources.close()
        } finally {
            GlfwInputEngine.unhook()
            application.close()
        }
    }
}

private data class MenuSession(val application: Application, val ui: Ui, val menu: CristalixMenu, val model: MenuModel)

private fun createSession(application: Application, resources: ResourceScope, options: MenuOptions): MenuSession {
    val fonts = resources.own(NativeFontProvider.of())
    val fontBytes = CristalixMenu::class.java.getResourceAsStream("/cristalix/Monocraft.ttf")!!.use { it.readBytes() }
    val pixelFont = fonts.load("monocraft", fontBytes)
    require(pixelFont.contains('Я'.code)) { "The bundled pixel font must cover Cyrillic" }
    val text = ArcTextService.of(listOf(pixelFont))
    val context = application.renderContext
    val art = MenuArt(context, resources)
    val ui = resources.own(
        ui(
            ArcUiHost.of(application.window), ArcUiBackend.of(context, text),
            CachingTextService.of(text), UiSettings(uiScale = options.scale)
        )
    )
    val model = MenuModel(if (options.smoke) null else Path.of("build", "cristalix", "favorites.txt"))
    model.reducedMotion = options.reducedMotion
    val menu = CristalixMenu(ui, model, art)
    ui.root.add(menu)
    return MenuSession(application, ui, menu, model)
}

private fun runFrames(session: MenuSession, options: MenuOptions) {
    val application = session.application
    val ui = session.ui
    val context = application.renderContext
    val verification = if (options.verify) MenuVerification(application, ui, session.menu, session.model) else null
    val maximumFrames = if (options.verify) 1050 else options.frames
    var previous = System.nanoTime()
    var frame = 0

    while (!application.window.shouldClose() && (!options.smoke || frame < maximumFrames)) {
        context.beginFrame()
        try {
            if (frame == 35 && verification == null) showScenario(session, options.scenario)
            verification?.beforeFrame(frame)
            val now = System.nanoTime()
            ui.frame(if (options.smoke) 16.milliseconds else (now - previous).nanoseconds)
            previous = now
            verification?.afterFrame(frame)

            if (options.smoke && verification == null && frame == maximumFrames - 1) {
                captureShowcase(
                    application.window.width, application.window.height,
                    Path.of("build", "reports", "cristalix", "${options.scenario}.png")
                )
            }
        } finally {
            context.endFrame()
        }
        frame++
    }
    verification?.finish()
    println("Cristalix menu: frames=$frame; nodes=${ui.statistics.totalNodes}; diagnostics=${ui.diagnostics.messages.size}")
}

private fun showScenario(session: MenuSession, scenario: String) {
    val menu = session.menu
    when (scenario) {
        "dropdown" -> menu.dropdown.show(MenuDropdown.CATEGORIES, menu.toolbar.categories.surface.toRoot(Vec2(0f, 36f)))
        "favorites" -> session.model.category = MenuCategory.FAVORITES
        "reviews" -> menu.modal.show(null, true)
    }
}

private data class MenuOptions(
    val width: Int, val height: Int, val scale: Float, val smoke: Boolean,
    val verify: Boolean, val reducedMotion: Boolean, val scenario: String, val frames: Int
) {

    companion object {

        fun from(args: Array<String>): MenuOptions {
            val width = args.option("width")?.toInt() ?: 1600
            val height = args.option("height")?.toInt() ?: 900
            val scale = args.option("scale")?.toFloat() ?: (height / 1095f).coerceIn(0.6f, 1.5f)
            val frames = args.option("frames")?.toInt() ?: 150
            require(width >= 640 && height >= 480 && frames > 0)
            return MenuOptions(
                width, height, scale, "--smoke" in args, "--verify" in args,
                "--reduced-motion" in args, args.option("scenario") ?: "menu", frames
            )
        }
    }
}

private fun Array<String>.option(name: String) = firstOrNull { it.startsWith("--$name=") }?.substringAfter('=')
