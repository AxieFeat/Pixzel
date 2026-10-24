package pixzel.dsl

import pixzel.component.Button
import pixzel.component.Element
import pixzel.component.Image
import pixzel.component.ScrollView
import pixzel.graphics.Color
import pixzel.graphics.ImageHandle
import pixzel.graphics.Solid
import pixzel.graphics.rgba
import pixzel.layout.unit.Dp
import pixzel.layout.unit.SizeSpec
import pixzel.layout.unit.dp
import pixzel.layout.unit.fillAvailable
import pixzel.surface.Placement

fun ContentScope.box(id: String = "", configure: ElementScope.() -> Unit = {}) = surface(id, configure)
fun ContentScope.icon(image: ImageHandle, id: String = "", configure: Image.() -> Unit = {}): Image =
    insert(Image.of(ui.components, id).apply { this.image = image }, configure)

fun ContentScope.image(image: ImageHandle, id: String = "", configure: Image.() -> Unit = {}): Image = icon(image, id, configure)
fun ContentScope.iconButton(image: ImageHandle, id: String = "", configure: ButtonScope.() -> Unit = {}): Button = button(id) {
    insert(Image.of(ui.components).apply {
        this.image = image
        layout.width = 24.dp
        layout.height = 24.dp
        placement = Placement.center()
    })
    configure()
}

fun ContentScope.separator(width: SizeSpec = fillAvailable, thickness: Dp = 1.dp, color: Color = 0x667080FF.rgba): Element = surface {
    layout {
        this.width = width
        height = thickness
    }
    appearance { background = Solid(color) }
}

fun ContentScope.scrollView(id: String = "", configure: ElementScope.() -> Unit = {}): ScrollView = ScrollView.of(ui.components, id).also {
    parent.add(it)
    ElementScope(ui, it).configure()
}
