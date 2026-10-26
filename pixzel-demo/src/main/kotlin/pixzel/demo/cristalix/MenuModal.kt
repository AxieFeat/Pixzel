// Pixel geometry, palette and motion timings are authored as visual design data.
@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.Ui
import pixzel.component.Invalidation
import pixzel.component.Painter
import pixzel.graphics.Canvas
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.input.EventPhase
import pixzel.input.PointerAction
import pixzel.input.PointerEvent
import pixzel.input.PointerPolicy
import pixzel.layout.unit.dp
import pixzel.layout.unit.sp
import pixzel.resource.Subscription
import pixzel.surface.Insets
import pixzel.surface.Rect
import pixzel.surface.Transform
import pixzel.text.TextStyle
import kotlin.time.Duration

internal class MenuModal(owner: Ui, model: MenuModel) : MenuElement("modal", model) {

    private val opening = MenuSpring(frequency = 18f)
    private var scope: Subscription? = null
    private var mode = Mode.GAME
    private var game: GameMode? = null
    private var elapsed = 0f
    private var joining = false
    private var submitted = false
    var open = false
        private set
    private val closeButton = MenuButton("modal-close", model, "×", ::dismiss)
    private val primary = MenuButton("modal-primary", model, "Играть", ::activatePrimary)
    private val favorite = MenuButton("modal-favorite", model, "В избранное") {
        game?.let { model.toggleFavorite(it.id) }
    }
    val reviewInput = owner.components.textField("review-input").apply {
        style = TextStyle(size = 17.sp)
        placeholder = "Твой отзыв об игре…"
        layout.padding = Insets.all(10.dp)
        background = Painter { canvas, context -> canvas.pixelPanel(context.bounds, 0x172940.rgb, 0x41648E.rgb, 4f) }
        foreground = null
    }

    init {
        zIndex = 50
        opacity = 0f
        pointerPolicy = PointerPolicy.NONE
        add(closeButton)
        add(primary)
        add(favorite)
        add(reviewInput)
        children.forEach {
            it.pointerPolicy = PointerPolicy.NONE
            it.focusable = false
        }
        layoutPolicy = positionedLayout { id, size ->
            val panel = dialog(size.width, size.height)
            when (id) {
                "modal-close" -> Rect(panel.right - 46f, panel.y + 14f, 32f, 32f)
                "modal-primary" -> Rect(panel.x + 28f, panel.bottom - 64f, 238f, 38f)
                "modal-favorite" -> Rect(panel.x + 280f, panel.bottom - 64f, 238f, 38f)
                else -> Rect(panel.x + 28f, panel.bottom - 126f, panel.width - 56f, 43f)
            }
        }
        onEvent(EventPhase.BUBBLE) { routed ->
            val input = routed.event as? PointerEvent ?: return@onEvent
            if (open && routed.target === this && input.action == PointerAction.UP) {
                val position = routed.localPosition ?: return@onEvent
                if (!dialog(bounds.width, bounds.height).contains(position)) dismiss()
                routed.consume()
            }
        }
    }

    fun show(game: GameMode?, reviews: Boolean = false) {
        this.game = game
        mode = if (reviews) Mode.REVIEWS else Mode.GAME
        open = true
        opening.target = 1f
        joining = false
        submitted = false
        elapsed = 0f
        pointerPolicy = PointerPolicy.AUTO
        children.forEach {
            it.pointerPolicy = PointerPolicy.AUTO
            it.focusable = true
        }
        favorite.pointerPolicy = if (reviews || game == null) PointerPolicy.NONE else PointerPolicy.AUTO
        favorite.focusable = !reviews && game != null
        reviewInput.pointerPolicy = if (reviews) PointerPolicy.AUTO else PointerPolicy.NONE
        reviewInput.focusable = reviews
        scope?.close()
        scope = ui?.focus?.pushScope(this)
        ui?.focus?.request(primary)
        invalidate(Invalidation.MEASURE)
    }

    fun dismiss() {
        open = false
        opening.target = 0f
        pointerPolicy = PointerPolicy.NONE
        children.forEach {
            it.pointerPolicy = PointerPolicy.NONE
            it.focusable = false
        }
        scope?.close()
        scope = null
    }

    override fun close() {
        scope?.close()
        scope = null
        super.close()
    }

    private enum class Mode { GAME, REVIEWS }

    private fun activatePrimary() {
        if (mode == Mode.REVIEWS) {
            if (reviewInput.value.isNotBlank()) {
                submitted = true
                reviewInput.value = ""
            } else {
                ui?.focus?.request(reviewInput)
            }
        } else {
            joining = true
            elapsed = 0f
        }
    }

    override fun update(delta: Duration): Boolean {
        val dt = delta.inWholeNanoseconds / 1_000_000_000f
        if (joining) elapsed += dt
        if (model.reducedMotion) opening.snap()
        val moving = opening.step(dt)
        opacity = opening.value.coerceIn(0f, 1f)
        val scale = 0.96f + opacity * 0.04f
        paintTransform = Transform.translation(
            bounds.width * (1f - scale) / 2f,
            bounds.height * (1f - scale) / 2f + (1f - opacity) * 18f
        ) * Transform.scale(scale)

        primary.title = if (mode == Mode.REVIEWS) model.label("Отправить отзыв", "Send review") else
            if (joining) model.label("Ещё раз", "Play again") else model.label("Войти в игру", "Join game")
        favorite.title = if (game?.id in model.favorites) model.label("Убрать из избранного", "Remove favorite")
        else model.label("В избранное", "Add favorite")
        favorite.opacity = if (mode == Mode.REVIEWS || game == null) 0f else 1f
        reviewInput.opacity = if (mode == Mode.REVIEWS) 1f else 0f
        reviewInput.placeholder = model.label("Твой отзыв об игре…", "Your review of the game…")
        return moving || (joining && elapsed < 1.85f)
    }

    private fun dialog(width: Float, height: Float): Rect {
        val panelWidth = 548f.coerceAtMost(width - 24f)
        val panelHeight = if (mode == Mode.REVIEWS) 420f else 354f
        return Rect((width - panelWidth) / 2f, (height - panelHeight) / 2f, panelWidth, panelHeight)
    }

    override fun paint(canvas: Canvas) {
        canvas.rect(0f, 0f, bounds.width, bounds.height, 0x030813BB.rgba)
        val panel = dialog(bounds.width, bounds.height)
        canvas.pixelPanel(panel, 0x17253A.rgb, 0x4D75A8.rgb, 7f)
        with(canvas) {
            val title = if (mode == Mode.REVIEWS) model.label("Отзывы игроков", "Player reviews") else game?.name?.replace('\n', ' ') ?: "Арасака"
            label(title, panel.x + 28f, panel.y + 25f, 26f, shadow = true)
            rect(panel.x + 28f, panel.y + 70f, panel.width - 56f, 2f, 0x6DABEE55.rgba)

            if (mode == Mode.REVIEWS) {
                label("4.9", panel.x + 28f, panel.y + 87f, 36f, 0xFFE29CFF.rgba)
                repeat(5) { star(panel.x + 127f + it * 25f, panel.y + 100f, 18f, 0xFFD86AFF.rgba) }
                label(model.label("Демо-отзывы · 3 записи", "Sample reviews · 3 entries"), panel.x + 28f, panel.y + 143f, 16f, 0x99B0CDFF.rgba)
                label(
                    model.label(
                        "«Красивые режимы, вернусь ещё!»\n«С друзьями играть ещё интереснее.»\n«Мне нравится этот пиксельный стиль.»",
                        "“Great modes, I'll be back!”\n“Even better with friends.”\n“Love the pixel style.”"
                    ), panel.x + 28f, panel.y + 177f, 17f
                )
                if (submitted) label(
                    model.label("Отзыв сохранён в этом демо. Спасибо!", "Saved in this demo. Thank you!"),
                    panel.x + 28f, panel.y + 257f, 15f, 0x9FE3B1FF.rgba
                )
            } else if (joining) {
                val progress = (elapsed / 1.8f).coerceIn(0f, 1f)
                label(
                    if (progress < 1f) model.label("Готовим приключение…", "Preparing your adventure…") else
                        model.label("Режим выбран!", "Game selected!"), panel.x + 28f, panel.y + 102f, 22f
                )
                rect(panel.x + 28f, panel.y + 151f, panel.width - 56f, 9f, 0x0C1627FF.rgba)
                rect(panel.x + 28f, panel.y + 151f, (panel.width - 56f) * progress, 9f, 0x6FBAF4FF.rgba)
                label(
                    model.label(
                        "Это локальное демо меню.\nПодключение к серверам Cristalix не выполняется.",
                        "This is a local menu demo.\nNo connection to Cristalix servers is made."
                    ), panel.x + 28f, panel.y + 188f, 16f,
                    0xA9BCD4FF.rgba, width = panel.width - 56f
                )
            } else {
                label(
                    game?.description ?: model.label(
                        "Секретные разработки, редкие предметы\nи новые приключения ждут тебя.",
                        "Secret technologies, rare items\nand new adventures await."
                    ), panel.x + 28f, panel.y + 94f, 20f, width = panel.width - 56f
                )
                label(game?.let { model.label("Сейчас играют", "Playing now") + " ${it.online}" }
                    ?: model.label("Кейс · Разработки Арасаки", "Case · Arasaka technologies"), panel.x + 28f, panel.bottom - 103f,
                    17f, 0xA6D6FFFF.rgba)
            }
        }
    }
}
