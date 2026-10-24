package pixzel.graphics

import pixzel.surface.Insets
import pixzel.surface.Rect

/**
 * Undersized borders shrink proportionally; source and target insets have independent units.
 */
fun Canvas.nineSlice(
    image: ImageHandle,
    destination: Rect,
    sourceInsets: Insets,
    targetInsets: Insets = sourceInsets,
    tint: Color = Color.WHITE,
    sampling: Sampling = Sampling.LINEAR
) {
    require(sourceInsets.horizontal <= image.size.width && sourceInsets.vertical <= image.size.height)
    val xScale = if (targetInsets.horizontal > destination.width) destination.width / targetInsets.horizontal else 1f
    val yScale = if (targetInsets.vertical > destination.height) destination.height / targetInsets.vertical else 1f
    val sx = listOf(0f, sourceInsets.left, image.size.width - sourceInsets.right, image.size.width)
    val sy = listOf(0f, sourceInsets.top, image.size.height - sourceInsets.bottom, image.size.height)
    val dx = listOf(destination.x, destination.x + targetInsets.left * xScale, destination.right - targetInsets.right * xScale, destination.right)
    val dy = listOf(destination.y, destination.y + targetInsets.top * yScale, destination.bottom - targetInsets.bottom * yScale, destination.bottom)
    for (y in 0..2) for (x in 0..2) if (dx[x + 1] > dx[x] && dy[y + 1] > dy[y]) emit(
        DrawImage(
            image,
            Rect(sx[x], sy[y], sx[x + 1] - sx[x], sy[y + 1] - sy[y]),
            Rect(dx[x], dy[y], dx[x + 1] - dx[x], dy[y + 1] - dy[y]),
            tint,
            sampling
        )
    )
}
