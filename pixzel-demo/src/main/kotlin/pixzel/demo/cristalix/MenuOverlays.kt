// Pixel geometry, palette and motion timings are authored as visual design data.
@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

import pixzel.graphics.Canvas
import pixzel.graphics.rgb
import pixzel.graphics.rgba
import pixzel.input.PointerPolicy
import pixzel.surface.Rect
import pixzel.surface.Transform
import pixzel.surface.Vec2
import pixzel.text.TextAlignment
import kotlin.time.Duration

internal class MenuDropdown(model: MenuModel, private val changed: () -> Unit) : MenuElement("dropdown", model) {

    private val opening = MenuSpring(frequency = 20f)
    private var selected = 0
    private var kind = MenuDropdown.CATEGORIES
    var anchor = Vec2.ZERO
    var open = false
        private set
    val menuWidth get() = if (kind == MenuDropdown.CATEGORIES) 166f else 148f
    val menuHeight get() = children.size * 38f + 8f

    init {
        zIndex = 30
        opacity = 0f
        layoutPolicy = positionedLayout { id, size -> Rect(3f, 4f + id.substringAfterLast('-').toInt() * 38f, size.width - 6f, 38f) }
    }

    fun show(kind: String, anchor: Vec2) {
        if (open && kind == this.kind) {
            dismiss()
            return
        }
        this.kind = kind
        this.anchor = anchor
        children.toList().forEach { remove(it) }
        val options = if (kind == MenuDropdown.CATEGORIES) categories else listOf("Русский", "English")
        options.forEachIndexed { index, value ->
            add(object : MenuButton("option-$index", model, value) {
                override fun paint(canvas: Canvas) {
                    if (state.hovered || index == selected) canvas.rect(0f, 0f, bounds.width, bounds.height, 0x72B0FF35.rgba)
                    with(canvas) {
                        label(title, Rect(11f, 0f, bounds.width - 22f, bounds.height), 19f, horizontal = TextAlignment.START, shadow = true)
                    }
                }
            }.apply {
                action = { choose(index) }
            })
        }
        selected = if (kind == MenuDropdown.CATEGORIES) options.indexOf(model.category).coerceAtLeast(0) else if (model.english) 1 else 0
        open = true
        opening.target = 1f
        invalidate()
    }

    fun move(direction: Int) {
        selected = (selected + direction + children.size) % children.size
        ui?.focus?.request(children[selected])
    }

    fun choose(index: Int = selected) {
        if (!open) return
        if (kind == MenuDropdown.CATEGORIES) model.category = categories[index]
        else model.english = index == 1
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
        opening.step(delta.inWholeNanoseconds / 1_000_000_000f)
        opacity = opening.value.coerceIn(0f, 1f)
        paintTransform = Transform.translation(0f, (1f - opacity) * -10f) * Transform.scale(1f, 0.94f + opacity * 0.06f)
        children.forEachIndexed { index, child ->
            child.opacity = ((opacity - index * 0.045f) / 0.7f).coerceIn(0f, 1f)
            child.invalidate()
        }
        invalidate()
        return true
    }

    override fun paint(canvas: Canvas) {
        canvas.rect(5f, 7f, bounds.width, bounds.height, 0x00000060.rgba)
        canvas.pixelPanel(bounds, 0x2862AD.rgb, 0x5E9CE4.rgb, 4f)
    }

    companion object {

        const val CATEGORIES = "categories"
        const val LANGUAGES = "languages"
        private val categories = listOf(MenuCategory.ALL, MenuCategory.HUBS, MenuCategory.TESTING, MenuCategory.ARCADE, MenuCategory.TABLETOP)
    }
}

internal class GameTooltip(model: MenuModel) : MenuElement("tooltip", model) {

    private val opening = MenuSpring(frequency = 20f)
    var game: GameMode? = null
    var show = false

    init {
        zIndex = 20
        pointerPolicy = PointerPolicy.NONE
        opacity = 0f
    }

    override fun update(delta: Duration): Boolean {
        opening.target = if (show) 1f else 0f
        if (model.reducedMotion) opening.snap()
        opening.step(delta.inWholeNanoseconds / 1_000_000_000f)
        opacity = if (opening.value > 0.99f) 1f else opening.value.coerceIn(0f, 1f)
        paintTransform = Transform.translation(0f, (1f - opacity) * 9f)
        invalidate()
        return true
    }

    override fun paint(canvas: Canvas) {
        val game = game ?: return
        val accent = game.color.rgb.shade(1.5f)
        canvas.rect(6f, 8f, bounds.width, bounds.height, 0x00000055.rgba)
        canvas.pixelPanel(bounds, 0x343434.rgb, 0x61605A.rgb, 5f)
        with(canvas) {
            repeat(2) { side ->
                repeat(2) { vertical ->
                    val x = if (side == 0) 6f else bounds.width - 18f
                    val y = if (vertical == 0) 6f else bounds.height - 16f
                    rect(x, y, 12f, 2f, accent)
                    rect(x, y, 2f, 10f, accent)
                    rect(x + 5f, y + 5f, 4f, 4f, accent)
                }
            }
            label(game.name.replace('\n', ' '), 18f, 17f, 20f)
            if (game.badge.isNotEmpty()) {
                val width = game.badge.length * 8f + 12f
                val badge = Rect(bounds.width - width - 18f, 17f, width, 25f)
                pixelPanel(badge, 0xE64A60.rgb, 0xF87686.rgb, 3f)
                label(game.badge, badge, 14f)
            }
            rect(18f, 49f, bounds.width - 66f, 2f, accent)
            label(game.description, 18f, 59f, 19f, width = bounds.width - 36f)
            label(model.label("ПКМ · открыть отзывы", "Right click · reviews"), 18f, bounds.height - 95f, 18f, 0xF1ECEBFF.rgba)
            val tags = listOf("PVP" to 0xE64A60, "СОЛО" to 0x00ACBA, "КОМАНДЫ" to 0x00ACBA, "АРКАДА" to 0x00ACBA)
            var left = 18f
            tags.forEach { (title, color) ->
                val width = title.length * 8f + 12f
                val tag = Rect(left, bounds.height - 63f, width, 25f)
                pixelPanel(tag, color.rgb, color.rgb.shade(1.35f), 3f)
                label(title, tag, 14f)
                left += width + 6f
            }
            pixelPanel(Rect(18f, bounds.height - 33f, 88f, 23f), 0x737373.rgb, 0xAAAAAA.rgb, 3f)
            label("ЭКШЕН", Rect(18f, bounds.height - 33f, 88f, 23f), 14f)
            pixelPanel(Rect(112f, bounds.height - 33f, 134f, 23f), 0x737373.rgb, 0xAAAAAA.rgb, 3f)
            label("СЕССИОННЫЙ", Rect(112f, bounds.height - 33f, 134f, 23f), 14f)
        }
    }
}
