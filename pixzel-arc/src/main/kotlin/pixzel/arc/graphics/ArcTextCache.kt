package pixzel.arc.graphics

import arc.graphics.RenderContext
import arc.texture.Texture
import pixzel.arc.text.ArcTextService
import pixzel.surface.Rect
import pixzel.text.TextLayout
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private data class TextKey(val layout: TextLayout, val scale: Float)
internal data class TextTile(val bounds: Rect, val texture: Texture)
internal data class PreparedText(val tiles: List<TextTile>, val bytes: Long)

/**
 * Raster tiles are reused across frames and released with their owning session.
 */
internal class ArcTextCache(
    private val context: RenderContext,
    private val text: ArcTextService,
    private val onUpload: (Long) -> Unit,
) : AutoCloseable {

    private val texts = object : LinkedHashMap<TextKey, PreparedText>(64, .75f, true) {}
    var textBytes = 0L
        private set
    val tileCount get() = texts.values.sumOf { it.tiles.size }

    fun get(layout: TextLayout, scale: Float): PreparedText {
        require(layout.providerId == text.capabilities.providerId) { "Text layout and Arc raster provider do not match" }

        val key = TextKey(layout, scale)
        return texts[key] ?: prepareText(layout, scale).also {
            texts[key] = it
            textBytes += it.bytes

            while ((texts.size > 256 || textBytes > 32L * 1024 * 1024) && texts.size > 1) {
                val first = texts.entries.iterator()
                val removed = first.next().value
                removed.tiles.forEach { tile -> tile.texture.cleanup() }
                textBytes -= removed.bytes
                first.remove()
            }
        }
    }

    private fun prepareText(layout: TextLayout, scale: Float): PreparedText {
        data class Raster(val x: Int, val y: Int, val width: Int, val height: Int, val bytes: ByteArray)

        val rasters = layout.runs.flatMap { run ->
            run.glyphs.mapNotNull { glyph ->
                val raster = text.rasterize(run.face, glyph.glyphId, run.fontSize, scale)
                if (raster.width == 0 || raster.height == 0) null else Raster(
                    floor(glyph.x * scale).toInt() + raster.left,
                    floor(glyph.y * scale).toInt() - raster.top, raster.width, raster.height, raster.alpha
                )
            }
        }

        if (rasters.isEmpty()) return PreparedText(emptyList(), 0)

        // Keep a transparent pixel around coverage so linear sampling cannot crop a glyph edge.
        val left = rasters.minOf { it.x } - 1
        val top = rasters.minOf { it.y } - 1
        val right = rasters.maxOf { it.x + it.width } + 1
        val bottom = rasters.maxOf { it.y + it.height } + 1
        require((right - left).toLong() * (bottom - top) * 4 <= 32L * 1024 * 1024) { "One text layout exceeds its 32 MiB raster budget" }

        val tiles = mutableListOf<TextTile>()
        var byteCount = 0L

        try {
            // Tile long layouts rather than requiring one texture as wide as the entire line.
            var y = top
            while (y < bottom) {
                val height = min(1024, bottom - y)
                var x = left
                while (x < right) {
                    val width = min(1024, right - x)
                    val rgba = ByteArray(width * height * 4)
                    rasters.forEach { raster ->
                        val x0 = max(x, raster.x)
                        val y0 = max(y, raster.y)
                        val x1 = min(x + width, raster.x + raster.width)
                        val y1 = min(y + height, raster.y + raster.height)

                        for (py in y0 until y1) for (px in x0 until x1) {
                            val source = raster.bytes[(py - raster.y) * raster.width + px - raster.x].toInt() and 255
                            val index = ((py - y) * width + px - x) * 4
                            val old = rgba[index + 3].toInt() and 255
                            rgba[index] = (-1).toByte()
                            rgba[index + 1] = (-1).toByte()
                            rgba[index + 2] = (-1).toByte()
                            // Combining marks may overlap: compose coverage instead of replacing it.
                            rgba[index + 3] = (source + old * (255 - source) / 255).toByte()
                        }
                    }

                    tiles += TextTile(Rect(x / scale, y / scale, width / scale, height / scale), context.createRgbaTexture(width, height, rgba))
                    onUpload(rgba.size.toLong())
                    byteCount += rgba.size
                    x += width
                }
                y += height
            }
        } catch (failure: Throwable) {
            tiles.forEach { it.texture.cleanup() }
            throw failure
        }

        return PreparedText(tiles, byteCount)
    }

    override fun close() {
        var failure: Throwable? = null

        for (prepared in texts.values) for (tile in prepared.tiles) {
            try {
                tile.texture.cleanup()
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure.addSuppressed(error)
            }
        }

        texts.clear()
        textBytes = 0
        failure?.let { throw it }
    }
}
