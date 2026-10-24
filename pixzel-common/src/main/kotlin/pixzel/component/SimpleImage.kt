package pixzel.component

import pixzel.graphics.Canvas
import pixzel.graphics.Clip
import pixzel.graphics.Color
import pixzel.graphics.DrawImage
import pixzel.graphics.ImageHandle
import pixzel.graphics.Rectangle
import pixzel.graphics.Sampling
import pixzel.graphics.saved
import pixzel.layout.Constraints
import pixzel.layout.unit.WrapContent
import pixzel.surface.Rect
import pixzel.surface.Size

internal class SimpleImage(id: String) : AbstractElement(id), Image {

    override var image: ImageHandle? = null
        set(value) {
            if (field !== value) {
                invalidate(if (layout.width == WrapContent || layout.height == WrapContent) Invalidation.MEASURE else Invalidation.PAINT)
                field = value
            }
        }
    override var tint: Color by property(Color.WHITE)
    override var sampling: Sampling by property(Sampling.LINEAR)
    override var fit: ImageFit by property(ImageFit.CONTAIN)

    init {
        semantics = Semantics(Role.IMAGE)
    }

    override fun intrinsic(constraints: Constraints) = image?.intrinsicSize ?: Size.ZERO
    override fun paint(canvas: Canvas) {
        val resource = image ?: return
        check(!resource.isClosed) { "Image '${resource.id}' has been closed" }
        val box = surface.contentBox
        val s = resource.size
        if (s.width == 0f || s.height == 0f) return
        val factor = when (fit) {
            ImageFit.COVER -> maxOf(box.width / s.width, box.height / s.height)
            ImageFit.CONTAIN -> minOf(box.width / s.width, box.height / s.height)
            else -> 1f
        }
        val destination = if (fit == ImageFit.STRETCH) box else Rect(
            box.x + (box.width - s.width * factor) / 2,
            box.y + (box.height - s.height * factor) / 2,
            s.width * factor,
            s.height * factor
        )
        canvas.saved {
            emit(Clip(Rectangle, box))
            emit(DrawImage(resource, Rect(0f, 0f, s.width, s.height), destination, tint, sampling))
        }
    }
}
