// Pixel geometry, palette and motion timings are authored as visual design data.
@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import arc.Application
import arc.ArcObjectProvider
import arc.font.NativeFontProvider
import arc.gl.OpenGL
import arc.input.GlfwInputEngine
import org.lwjgl.glfw.GLFW
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
import java.util.concurrent.locks.LockSupport
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
    if (options.smoke) GLFW.glfwSetWindowAttrib(application.window.handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE)
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

private data class MenuSession(val application: Application, val ui: Ui, val menu: CristalixMenu, val model: MenuModel, val backend: MenuBackend)

private fun createSession(application: Application, resources: ResourceScope, options: MenuOptions): MenuSession {
    val fonts = resources.own(NativeFontProvider.of())
    val fontBytes = CristalixMenu::class.java.getResourceAsStream("/cristalix/MinecraftSeven.otf")!!.use { it.readBytes() }
    val pixelFont = fonts.load("minecraft-seven", fontBytes)
    require(pixelFont.contains('Я'.code)) { "The bundled pixel font must cover Cyrillic" }
    val headingFont =
        fonts.load("minecraft-ten", CristalixMenu::class.java.getResourceAsStream("/cristalix/MinecraftTen.ttf")!!.use { it.readBytes() })
    val smallFont =
        fonts.load("minecraft-five", CristalixMenu::class.java.getResourceAsStream("/cristalix/MinecraftFive.otf")!!.use { it.readBytes() })
    val text = ArcTextService.of(listOf(pixelFont, headingFont, smallFont))
    val context = application.renderContext
    val data = MenuData.load()
    val art = MenuArt.load(context, resources, data)
    val backend = MenuBackend(ArcUiBackend.of(context, text, supersampling = 1))
    val ui = resources.own(
        ui(
            ArcUiHost.of(application.window), backend,
            CachingTextService.of(text), UiSettings(uiScale = options.scale)
        )
    )
    val model = MenuModel(if (options.smoke) null else Path.of("build", "cristalix", "favorites.txt"), data)
    model.reducedMotion = options.reducedMotion
    val menu = CristalixMenu(ui, model, art)
    ui.root.add(menu.root)
    return MenuSession(application, ui, menu, model, backend)
}

private fun runFrames(session: MenuSession, options: MenuOptions) {
    val application = session.application
    val ui = session.ui
    val context = application.renderContext
    val verification = if (options.verify) MenuVerification(application, ui, session.menu, session.model) else null
    val profile = if (options.profile) MenuProfile(ui, session.backend) else null
    val maximumFrames = if (options.verify) 1050 else options.frames
    var previous = System.nanoTime()
    var frame = 0

    while (!application.window.shouldClose() && (!options.smoke || frame < maximumFrames)) {
        val frameStart = System.nanoTime()
        context.beginFrame()
        try {
            if (frame == 35 && verification == null) showScenario(session, options.scenario)
            verification?.beforeFrame(frame)
            val now = System.nanoTime()
            ui.frame(if (options.smoke) 16.milliseconds else (now - previous).nanoseconds)
            profile?.frame(frameStart, frame)
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
        limitFrameRate(frameStart, options.smoke)
    }
    verification?.finish()
    profile?.finish(options.scenario)
    println("Cristalix menu: frames=$frame; nodes=${ui.statistics.totalNodes}; diagnostics=${ui.diagnostics.messages.size}")
}

private fun limitFrameRate(frameStart: Long, smoke: Boolean) {
    if (smoke) return
    // Vsync can be disabled by the driver. Never run this menu in an unbounded busy loop.
    val remaining = 16_666_667L - (System.nanoTime() - frameStart)
    if (remaining > 0) LockSupport.parkNanos(remaining)
}

private fun showScenario(session: MenuSession, scenario: String) {
    val menu = session.menu
    when (scenario) {
        "dropdown" -> {
            menu.dropdown.show(
                listOf(MenuCategory.ALL, MenuCategory.HUBS, MenuCategory.TESTING, MenuCategory.ARCADE, MenuCategory.TABLETOP),
                session.model.category, menu.toolbar.categories.surface.toRoot(Vec2(0f, 36f)), 166f
            ) { session.model.category = it }
        }

        "hover" -> {
            val card = menu.grid.cards.first { it.game.id == "skyblock" }
            val point = card.root.surface.toRoot(Vec2(99f, 155f))
            session.application.window.events.let { events ->
                events.enqueue(
                    arc.window.PointerMotion(
                        (point.x * session.ui.settings.uiScale).toDouble(),
                        (point.y * session.ui.settings.uiScale).toDouble(),
                        events.nextSequence(),
                        System.nanoTime(),
                        session.application.window.handle
                    )
                )
            }
        }

        "scroll" -> {
            menu.grid.scrollBy(700f)
        }

        "favorites" -> {
            session.model.category = MenuCategory.FAVORITES
        }

        "reviews" -> {
            menu.reviews.show(null)
        }

        "game-reviews" -> {
            menu.reviews.show(session.model.data.games.first { it.id == "castle" })
        }

        "review-compose" -> {
            val game = session.model.data.games.first { it.id == "castle" }
            menu.reviews.show(game)
            menu.reviews.composer.show(game)
        }

        "arcade" -> {
            session.model.category = MenuCategory.ARCADE
        }

        "tabletop" -> {
            session.model.category = MenuCategory.TABLETOP
        }

        "hubs" -> {
            session.model.category = MenuCategory.HUBS
        }

        "testing" -> {
            session.model.category = MenuCategory.TESTING
        }
    }
}

private data class MenuOptions(
    val width: Int, val height: Int, val scale: Float, val smoke: Boolean,
    val verify: Boolean, val reducedMotion: Boolean, val scenario: String, val frames: Int, val profile: Boolean
) {

    companion object {

        fun from(args: Array<String>): MenuOptions {
            val width = args.option("width")?.toInt() ?: 2560
            val height = args.option("height")?.toInt() ?: 1440
            val scale = args.option("scale")?.toFloat() ?: (height / 1095f).coerceIn(0.6f, 1.5f)
            val frames = args.option("frames")?.toInt() ?: 150
            require(width >= 640 && height >= 480 && frames > 0)
            return MenuOptions(
                width, height, scale, "--smoke" in args, "--verify" in args,
                "--reduced-motion" in args, args.option("scenario") ?: "menu", frames, "--profile" in args
            )
        }
    }
}

private fun Array<String>.option(name: String) = firstOrNull { it.startsWith("--$name=") }?.substringAfter('=')
