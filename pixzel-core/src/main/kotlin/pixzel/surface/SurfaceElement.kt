package pixzel.surface

import pixzel.component.ComponentFactory
import pixzel.component.Element

/** A retained container owning one Surface, with no implicit framebuffer or window. */
interface SurfaceElement : Element {

    companion object {

        fun of(components: ComponentFactory, id: String = "") = components.surface(id)
    }
}
