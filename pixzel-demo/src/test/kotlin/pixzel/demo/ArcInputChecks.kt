package pixzel.demo

import arc.Application
import arc.ArcObjectProvider
import arc.gl.OpenGL
import arc.input.GlfwInputEngine
import arc.input.InputContext
import arc.input.KeyCode
import arc.input.keyboard
import arc.input.mouse
import arc.window.KeyModifiers
import arc.window.KeySymbol
import arc.window.KeyboardEvent
import arc.window.PointerButton
import arc.window.WindowEvents
import arc.window.WindowHandler
import org.lwjgl.glfw.GLFW
import pixzel.arc.platform.ArcUiHost
import pixzel.graphics.RecordingBackend
import pixzel.input.EventPhase
import pixzel.input.WheelEvent
import pixzel.surface.Placement
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import pixzel.ui
import kotlin.time.Duration
import arc.window.KeyAction as ArcKeyAction

object ArcInputChecks {

    @JvmStatic
    fun main(args: Array<String>) {
        pairedReleasePriorityChecks()
        ArcObjectProvider.install()
        ArcObjectProvider.bootstrap()
        OpenGL.preload()
        val application = Application.find()
        application.init()
        val window = application.window
        window.isHidden = true
        val originalHandler = window.handler
        var legacyScrolls = 0
        window.handler = object : WindowHandler by originalHandler {
            override fun scroll(x: Double, y: Double) {
                legacyScrolls++
                originalHandler.scroll(x, y)
            }
        }
        GlfwInputEngine.hook(window)
        val host = ArcUiHost.of(window)
        var clicks = 0
        var gameplay = 0
        var gameplayKeys = 0
        val context = InputContext.of(window, -50)
        context.subscribe {
            if (it is PointerButton) gameplay++
            if (it is KeyboardEvent && it.physicalCode == GLFW.GLFW_KEY_SPACE) gameplayKeys++
            false
        }
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        try {
            val button = ui.components.button("action")
            button.setLabel("Кнопка")
            button.onClick { clicks++ }
            ui.root.add(button)
            val field = ui.components.textField("field")
            field.placement = Placement(offset = Vec2(0f, 60f))
            ui.root.add(field)
            val consumeWheel = button.onEvent(EventPhase.TARGET) { if (it.event is WheelEvent) it.consume() }
            ui.frame(Duration.ZERO)
            // Invoke the real installed native callback objects; controlled fixture requires no OS input injection.
            val mouse = checkNotNull(GLFW.glfwSetMouseButtonCallback(window.handle, null))
            GLFW.glfwSetMouseButtonCallback(window.handle, mouse)
            GLFW.glfwSetCursorPos(window.handle, 10.0, 10.0)
            GLFW.glfwPollEvents()
            mouse.invoke(window.handle, 0, GLFW.GLFW_PRESS, 0)
            ui.frame(Duration.ZERO)
            check(!GlfwInputEngine.mouse.isPressed(KeyCode.fromId(0)))
            mouse.invoke(window.handle, 0, GLFW.GLFW_RELEASE, 0)
            ui.frame(Duration.ZERO)
            check(clicks == 1 && gameplay == 0)
            val scroll = checkNotNull(GLFW.glfwSetScrollCallback(window.handle, null))
            GLFW.glfwSetScrollCallback(window.handle, scroll)
            scroll.invoke(window.handle, 0.0, -.25)
            ui.frame(Duration.ZERO)
            check(legacyScrolls == 0)
            consumeWheel.close()
            scroll.invoke(window.handle, 0.0, -.25)
            ui.frame(Duration.ZERO)
            check(legacyScrolls == 1)
            ui.focus.request(field)
            val chars = checkNotNull(GLFW.glfwSetCharCallback(window.handle, null))
            GLFW.glfwSetCharCallback(window.handle, chars)
            "Привет".codePoints().forEach { chars.invoke(window.handle, it) }
            ui.frame(Duration.ZERO)
            check(field.value == "Привет")
            val key = checkNotNull(GLFW.glfwSetKeyCallback(window.handle, null))
            GLFW.glfwSetKeyCallback(window.handle, key)
            key.invoke(window.handle, GLFW.GLFW_KEY_SPACE, 0, GLFW.GLFW_PRESS, 0)
            ui.frame(Duration.ZERO)
            check(!context.isPressed(KeyCode.fromId(GLFW.GLFW_KEY_SPACE)))
            key.invoke(window.handle, GLFW.GLFW_KEY_SPACE, 0, GLFW.GLFW_REPEAT, 0)
            key.invoke(window.handle, GLFW.GLFW_KEY_SPACE, 0, GLFW.GLFW_RELEASE, 0)
            ui.frame(Duration.ZERO)
            check(gameplayKeys == 0)
            val count = window.events.listenerCount
            key.invoke(window.handle, GLFW.GLFW_KEY_SPACE, 0, GLFW.GLFW_PRESS, 0)
            ui.frame(Duration.ZERO)
            ui.close()
            check(window.events.listenerCount == count - 1)
            key.invoke(window.handle, GLFW.GLFW_KEY_SPACE, 0, GLFW.GLFW_RELEASE, 0)
            window.events.drain()
            check(gameplayKeys == 0)
            repeat(10) { ui(host, RecordingBackend.of(), FixedFontTextService.of()).close() }
            check(window.events.listenerCount == count - 1)
            println("Arc real-window callback, Unicode commit, ordered delivery, consumed gameplay polling and listener lifetime checks passed; metrics=${window.metrics}")
        } finally {
            ui.close()
            context.close()
            GlfwInputEngine.unhook()
            window.handler = originalHandler
            application.close()
        }
    }

    private fun pairedReleasePriorityChecks() {
        val events = WindowEvents()
        var lower = 0
        var observer = 0
        events.subscribe(20) {
            observer++
            false
        }
        val consumer = events.subscribe(10) { it is KeyboardEvent || it is PointerButton }
        events.subscribe(10) {
            lower++
            false
        }
        fun keyboard(action: ArcKeyAction, sequence: Long) = KeyboardEvent(KeySymbol.ENTER, 13, 0, action, KeyModifiers(), sequence, 0, 1)
        events.enqueue(keyboard(ArcKeyAction.PRESS, 1))
        events.enqueue(PointerButton(0, ArcKeyAction.PRESS, 0.0, 0.0, KeyModifiers(), 2, 0, 1))
        events.drain()
        consumer.close()
        events.enqueue(keyboard(ArcKeyAction.REPEAT, 3))
        events.enqueue(keyboard(ArcKeyAction.RELEASE, 4))
        events.enqueue(PointerButton(0, ArcKeyAction.RELEASE, 0.0, 0.0, KeyModifiers(), 5, 0, 1))
        events.drain()
        check(lower == 0 && observer == 5) { "A paired release/repeat leaked to a later equal-priority listener" }
        events.enqueue(keyboard(ArcKeyAction.PRESS, 6))
        events.enqueue(keyboard(ArcKeyAction.RELEASE, 7))
        events.drain()
        check(lower == 2)
        events.close()
    }
}
