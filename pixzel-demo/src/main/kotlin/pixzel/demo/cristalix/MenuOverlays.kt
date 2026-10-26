// Pixel geometry, palette and motion timings are authored as visual design data.
@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.graphics.Canvas
import pixzel.graphics.Concat
import pixzel.graphics.DrawImage
import pixzel.graphics.Sampling
import pixzel.graphics.saved
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.input.PointerPolicy
import pixzel.input.EventPhase
import pixzel.input.WheelEvent
import pixzel.component.ClipPolicy
import pixzel.component.Invalidation
import pixzel.surface.Rect
import pixzel.surface.Transform
import pixzel.surface.Vec2
import pixzel.text.TextAlignment
import pixzel.text.TextRequest
import pixzel.text.TextStyle
import pixzel.layout.unit.sp
import kotlin.time.Duration

internal class MenuDropdown(model: MenuModel, private val changed: () -> Unit) : MenuElement("dropdown", model) {

    private val opening = MenuSpring(frequency = 20f)
    private var selected = 0
    private var kind = MenuDropdown.CATEGORIES
    private var firstRow = 0
    var anchor = Vec2.ZERO
    var open = false
        private set
    val menuWidth get() = if (kind == MenuDropdown.CATEGORIES) 166f else 82f
    val menuHeight get() = children.size.coerceAtMost(5) * 48f

    init {
        zIndex = 30
        opacity = 0f
        clipPolicy = ClipPolicy.BOUNDS
        layoutPolicy = positionedLayout { id, size ->
            Rect(3f, (id.substringAfterLast('-').toInt() - firstRow) * 48f, size.width - 6f, 48f)
        }
        onEvent(EventPhase.BUBBLE) { routed ->
            val wheel = routed.event as? WheelEvent ?: return@onEvent
            if (open) {
                firstRow = (firstRow - wheel.delta.y.toInt()).coerceIn(0, (children.size - 5).coerceAtLeast(0))
                invalidate(Invalidation.MEASURE)
                routed.consume()
            }
        }
    }

    fun show(kind: String, anchor: Vec2) {
        if (open && kind == this.kind) {
            dismiss()
            return
        }
        this.kind = kind
        this.anchor = anchor
        children.toList().forEach { remove(it) }
        firstRow = 0
        val options = if (kind == MenuDropdown.CATEGORIES) categories else languages
        options.forEachIndexed { index, value ->
            add(object : MenuButton("option-$index", model, value) {
                override fun paint(canvas: Canvas) {
                    if (state.hovered || index == selected) canvas.rect(0f, 0f, bounds.width, bounds.height, 0x72B0FF35.rgba)
                    with(canvas) {
                        label(title, Rect(11f, 0f, bounds.width - 22f, bounds.height), 22f, horizontal = TextAlignment.START)
                    }
                }
            }.apply {
                action = { choose(index) }
            })
        }
        selected = if (kind == MenuDropdown.CATEGORIES) options.indexOf(model.category).coerceAtLeast(0) else languages.indexOf(model.language)
        open = true
        opening.target = 1f
        invalidate()
    }

    fun move(direction: Int) {
        selected = (selected + direction + children.size) % children.size
        if (selected < firstRow) firstRow = selected
        if (selected >= firstRow + 5) firstRow = selected - 4
        invalidate(Invalidation.MEASURE)
        ui?.focus?.request(children[selected])
    }

    fun choose(index: Int = selected) {
        if (!open) return
        if (kind == MenuDropdown.CATEGORIES) model.category = categories[index]
        else model.language = languages[index]
        dismiss()
        changed()
    }

    fun dismiss() {
        open = false
        opening.target = 0f
        children.forEach {
            it.focusable = false
            it.pointerPolicy = PointerPolicy.NONE
        }
    }

    override fun update(delta: Duration): Boolean {
        if (model.reducedMotion) opening.snap()
        val moving = opening.step(delta.inWholeNanoseconds / 1_000_000_000f)
        opacity = opening.value.coerceIn(0f, 1f)
        paintTransform = Transform.translation(0f, (1f - opacity) * -10f) * Transform.scale(1f, 0.94f + opacity * 0.06f)
        children.forEachIndexed { index, child ->
            child.opacity = ((opacity - index * 0.045f) / 0.7f).coerceIn(0f, 1f)
        }
        return moving
    }

    override fun paint(canvas: Canvas) {
        canvas.pixelPanel(bounds, 0x2362BE.rgb, 0x5899ED.rgb, 4f, 2f)
        if (children.size > 5) {
            canvas.rect(bounds.width - 9f, 6f, 4f, bounds.height - 12f, 0x3478D2.rgb)
            canvas.rect(bounds.width - 9f, 6f + firstRow * (bounds.height - 45f) / (children.size - 5), 4f, 39f, 0x78ACF2.rgb)
        }
    }

    companion object {

        const val CATEGORIES = "categories"
        const val LANGUAGES = "languages"
        private val languages = listOf("RU", "US", "UA", "AM", "TR", "KZ", "BY", "DE", "ES")
        private val categories = listOf(MenuCategory.ALL, MenuCategory.HUBS, MenuCategory.TESTING, MenuCategory.ARCADE, MenuCategory.TABLETOP)
    }
}

@Suppress("StringLiteralDuplication") // Labels in the reference game's tag combinations.
internal class GameTooltip(model: MenuModel, private val art: MenuArt) : MenuElement("tooltip", model) {

    var game: GameMode? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate(Invalidation.MEASURE)
        }
    var show = false

    val contentHeight: Float
        get() = descriptionLayout()?.size?.height?.plus(147f + (tagPlacements().lastOrNull()?.second?.y ?: 0f)) ?: 260f

    init {
        zIndex = 20
        pointerPolicy = PointerPolicy.NONE
        opacity = 0f
    }

    private fun descriptionLayout() = ui?.text?.layout(
        TextRequest(
            game?.description.orEmpty(),
            TextStyle(size = 20.5f.sp, lineHeight = 24.5f), width = 408f, wrap = true
        )
    )

    private fun tagPlacements(): List<Pair<String, Rect>> {
        val owner = ui ?: return emptyList()
        val tags = when (game?.id) {
            "skyblock" -> listOf("PVP", "PVE", "КООП", "СЕЗОННЫЙ", "РАЗВИТИЕ")
            "prison" -> listOf("PVP", "СОЛО", "СИМУЛЯТОР", "ДОБЫЧА РЕСУРСОВ", "ЭКШЕН", "РАЗВИТИЕ")
            else -> listOf("PVP", "СОЛО", "КОМАНДЫ", "АРКАДА", "ЭКШЕН", "СЕССИОННЫЙ")
        }
        var left = 18f
        var top = 0f
        return tags.map { title ->
            val width = owner.text.layout(TextRequest(title, TextStyle(size = 15.2f.sp))).size.width + 10f
            if (left + width > 426f) {
                left = 18f; top += 28f
            }
            (title to Rect(left, top, width, 23f)).also { left += width + 6f }
        }
    }


    override fun update(delta: Duration): Boolean {
        opacity = if (show) 1f else 0f
        return false
    }


    override fun paint(canvas: Canvas) {
        val game = game ?: return
        val accent = game.color.rgb.shade(1.8f)
        canvas.pixelPanel(bounds, 0x343434.rgb, 0x343434.rgb, 4f, 2f)
        with(canvas) {
            repeat(2) { side ->
                repeat(2) { vertical ->

                    // The source ornament is the top-right corner. Reflect it around each edge.
                    saved {
                        emit(
                            Concat(
                                Transform.translation(
                                    if (side == 0) 50f else bounds.width - 50f,
                                    if (vertical == 0) 2f else bounds.height - 2f
                                ) *
                                        Transform.scale(if (side == 0) -1f else 1f, if (vertical == 0) 1f else -1f)
                            )
                        )
                        emit(
                            DrawImage(
                                art.corner, Rect(0f, 0f, art.corner.size.width, art.corner.size.height), Rect(0f, 0f, 48f, 48f),
                                tint = accent, sampling = Sampling.NEAREST
                            )
                        )
                    }
                }
            }
            val title = game.name.replace('\n', ' ')
            label(title, 18f, 15f, 20.5f)
            val titleWidth = ui!!.text.layout(TextRequest(title, TextStyle(size = 20.5f.sp))).size.width
            label(game.online.toString(), 25f + titleWidth, 15f, 20.5f, 0x488FEE.rgb)
            rect(18f, 43f, bounds.width - 36f, 2f, accent)
            descriptionLayout()?.let { text(it, Vec2(18f, 51f), 0xFFFFFF.rgb) }
            val tags = tagPlacements()
            val top = bounds.height - 41f - (tags.lastOrNull()?.second?.y ?: 0f)
            rect(18f, top - 33f, 9f, 14f, 0xEEEEEE.rgb)
            rect(22f, top - 33f, 4f, 10f, 0xEB5D4A.rgb)
            label("Открыть отзывы", 35f, top - 34f, 22f, 0xF1ECEB.rgb)
            tags.forEach { (title, position) ->
                val tag = Rect(position.x, top + position.y, position.width, position.height)
                val color = when (title) {
                    "PVP" -> 0xF34D59.rgb
                    "СЕЗОННЫЙ", "РАЗВИТИЕ", "ЭКШЕН", "СЕССИОННЫЙ" -> 0x737373.rgb
                    else -> 0x00ADC2.rgb
                }
                pixelPanel(tag, color, color.shade(1.35f), 2f, 1f)
                label(title, tag, 15.2f)
            }
        }
    }
}
