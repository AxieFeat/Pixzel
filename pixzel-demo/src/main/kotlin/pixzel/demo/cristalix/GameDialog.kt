@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.Ui
import pixzel.component.Element
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.input.EventPhase
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.surface.Transform

internal class GameDialog(val root: Element, private val overlay: Overlay, private val select: (GameMode?) -> Unit) {

    val open get() = overlay.open
    fun show(game: GameMode?) {
        select(game); overlay.show()
    }

    fun dismiss() = overlay.dismiss()
}

internal fun gameDialog(ui: Ui, model: MenuModel, play: (GameMode) -> Unit): GameDialog {
    val root = ui.compose("game-dialog").apply { zIndex = 60 }
    var game: GameMode? = null
    var elapsed = 0f
    var joining = false
    val opening = MenuSpring(frequency = 18f)
    val primary = ui.pixelButton("modal-primary", "Войти в игру") {
        joining = true
        elapsed = 0f
        game?.let(play)
    }
    val overlay = Overlay(root) { primary }
    val close = ui.pixelButton("modal-close", "×", clicked = overlay::dismiss)
    val favorite = ui.pixelButton("modal-favorite", "В избранное") { game?.let { model.toggleFavorite(it.id) } }
    listOf(primary, close, favorite).forEach(root::add)
    fun panel(size: Size): Rect {
        val width = minOf(548f, size.width - 24f)
        return Rect((size.width - width) / 2, (size.height - 354f) / 2, width, 354f)
    }
    root.arrange {
        place(close) { val rect = panel(it); Rect(rect.right - 46f, rect.y + 14f, 32f, 32f) }
        place(primary) { val rect = panel(it); Rect(rect.x + 28f, rect.bottom - 64f, 238f, 38f) }
        place(favorite) { val rect = panel(it); Rect(rect.x + 280f, rect.bottom - 64f, 238f, 38f) }
    }
    root.onEvent(EventPhase.BUBBLE) { routed ->
        val event = routed.event as? PointerEvent
        if (routed.target === root && event?.action == PointerAction.UP) {
            if (routed.localPosition?.let { panel(root.bounds.size).contains(it) } == false) overlay.dismiss()
            routed.consume()
        }
    }
    root.onFrame { seconds ->
        if (joining) elapsed += seconds
        opening.target = 1f
        if (model.reducedMotion) opening.snap()
        val moving = opening.step(seconds)
        root.opacity = opening.value.coerceIn(0f, 1f)
        val scale = .96f + root.opacity * .04f
        root.paintTransform = Transform.translation(
            root.bounds.width * (1 - scale) / 2,
            root.bounds.height * (1 - scale) / 2 + (1 - root.opacity) * 18f
        ) * Transform.scale(scale)
        primary.title = if (joining) "Ещё раз" else "Войти в игру"
        favorite.title = if (game?.id in model.favorites) "Убрать из избранного" else "В избранное"
        favorite.showIf(game != null)
        moving || joining && elapsed < 1.85f
    }
    root.draw(ui) {
        paintGameDialog(game, elapsed, joining, panel(bounds.size))
    }
    return GameDialog(root, overlay) { value -> game = value; joining = false; elapsed = 0f; opening.snap(0f) }
}

private fun PixelCanvas.paintGameDialog(game: GameMode?, elapsed: Float, joining: Boolean, panel: Rect) {
    rect(0f, 0f, bounds.width, bounds.height, 0x030813BB.rgba)
    val rect = panel
    pixelPanel(rect, 0x17253A.rgb, 0x4D75A8.rgb, 7f)
    label(game?.name?.replace('\n', ' ') ?: "Арасака", rect.x + 28f, rect.y + 25f, 26f, shadow = true)
    rect(rect.x + 28f, rect.y + 70f, rect.width - 56f, 2f, 0x6DABEE55.rgba)
    if (joining) {
        val progress = (elapsed / 1.8f).coerceIn(0f, 1f)
        label(if (progress < 1) "Готовим приключение…" else "Режим выбран!", rect.x + 28f, rect.y + 102f, 22f)
        rect(rect.x + 28f, rect.y + 151f, rect.width - 56f, 9f, 0x0C1627FF.rgba)
        rect(rect.x + 28f, rect.y + 151f, (rect.width - 56f) * progress, 9f, 0x6FBAF4FF.rgba)
        label(
            "Это локальное демо меню.\nПодключение к серверам Cristalix не выполняется.", rect.x + 28f,
            rect.y + 188f, 16f, 0xA9BCD4FF.rgba, width = rect.width - 56f
        )
    } else {
        label(
            game?.description ?: "Секретные разработки, редкие предметы\nи новые приключения ждут тебя.",
            rect.x + 28f, rect.y + 94f, 20f, width = rect.width - 56f
        )
        label(game?.let { "Сейчас играют ${it.online}" } ?: "Кейс · Разработки Арасаки",
            rect.x + 28f, rect.bottom - 103f, 17f, 0xA6D6FFFF.rgba)
    }
}
