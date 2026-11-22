package pixzel.demo.cristalix

import pixzel.surface.Rect

internal data class TileSize(val span: Int, val height: Float)

/**
 * Content-independent masonry layout. Column spans belong to the data, not card classes or indices.
 */
internal fun masonry(width: Float, columns: Int, gap: Float, items: List<TileSize>): List<Rect> {
    require(columns > 0 && width >= gap * (columns - 1))
    val unit = (width - gap * (columns - 1)) / columns
    val heights = FloatArray(columns)
    return items.map { item ->
        val span = item.span.coerceIn(1, columns)
        val column = (0..columns - span).minBy { start -> (start until start + span).maxOf { heights[it] } }
        val top = (column until column + span).maxOf { heights[it] }
        for (index in column until column + span) heights[index] = top + item.height + gap
        Rect(column * (unit + gap), top, unit * span + gap * (span - 1), item.height)
    }
}
