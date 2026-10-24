package pixzel.graphics

import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.surface.Transform
import pixzel.surface.Vec2
import pixzel.text.FontFace
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Immutable straight-alpha ARGB bitmap; closing a rendering session never closes borrowed images. */
class SoftwareImage private constructor(override val id: String, val width: Int, val height: Int, pixels: IntArray) : ImageHandle {

    internal val pixels = pixels.copyOf()
    override val generation = 0L
    override val size = Size(width.toFloat(), height.toFloat())
    override var isClosed = false
        private set

    override fun close() {
        isClosed = true
    }

    companion object {

        fun of(width: Int, height: Int, pixels: IntArray, id: String = "software-image"): SoftwareImage {
            require(width >= 0 && height >= 0 && width.toLong() * height == pixels.size.toLong())
            return SoftwareImage(id, width, height, pixels)
        }
    }
}

/**
 * Independent pixel-center reference renderer. Supports shapes with contains(), transformed
 * geometric clips, solid/gradient brushes, images, composed group alpha and positioned glyph
 * rasters. Paths, shadows, custom commands and antialiasing are deliberately unsupported.
 * Transparent output begins each submitted frame; this backend does not own application assets.
 */
class SoftwareBackend private constructor(
    private val glyphs: SoftwareGlyphProvider,
    private val maxPixels: Int,
    private val maxGroupDepth: Int,
    maxGlyphBytes: Int
) : UiBackend {

    private val glyphCache = GlyphCache(maxGlyphBytes)
    val cachedGlyphBytes: Int get() = glyphCache.bytes
    val cachedGlyphCount: Int get() = glyphCache.size
    fun clearCaches() = glyphCache.clear()
    override val capabilities = BackendCapabilities(
        setOf(
            Capability.RECT_CLIP, Capability.SHAPE_CLIP,
            Capability.GRADIENT, Capability.IMAGE, Capability.TEXT, Capability.GROUP_OPACITY
        ), "Software pixel-center preview",
        BackendLimits(targetFormats = setOf(TargetFormat.RGBA8), textMode = TextRenderMode.GRAYSCALE_GLYPH_MASK)
    )
    var lastImage: BufferedImage? = null
        private set
    var activeSessions = 0
        private set

    fun writePng(path: Path) {
        val image = checkNotNull(lastImage) { "Submit a frame before exporting a preview." }
        check(ImageIO.write(image, "png", path.toFile())) { "No PNG writer is available." }
    }

    override fun openSession(): RenderSession {
        activeSessions++
        return object : RenderSession {
            private var closed = false
            override fun submit(frame: FrameInfo, commands: List<DrawCommand>) {
                check(!closed) { "Software session is closed." }
                validateCommands(frame, commands, capabilities)
                val width = ceil(frame.framebufferSize.width).toInt()
                val height = ceil(frame.framebufferSize.height).toInt()
                require(width > 0 && height > 0 && width.toLong() * height <= maxPixels) { "Software frame exceeds the configured pixel budget or has zero size." }
                val renderer = RasterFrame(width, height, frame, glyphs, glyphCache, maxGroupDepth)
                renderer.draw(commands)
                lastImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also {
                    it.setRGB(0, 0, width, height, renderer.pixels, 0, width)
                }
            }

            override fun close() {
                if (!closed) {
                    closed = true
                    activeSessions--
                    if (activeSessions == 0) clearCaches()
                }
            }
        }
    }

    companion object {

        fun of(
            glyphs: SoftwareGlyphProvider = Bitmap5x7GlyphProvider(), maxPixels: Int = 16_777_216,
            maxGroupDepth: Int = 8, maxGlyphBytes: Int = 8_388_608
        ): SoftwareBackend {
            require(maxPixels > 0 && maxGroupDepth >= 0 && maxGlyphBytes >= 0)
            return SoftwareBackend(glyphs, maxPixels, maxGroupDepth, maxGlyphBytes)
        }
    }
}

private class RasterFrame(
    private val width: Int,
    private val height: Int,
    frame: FrameInfo,
    private val glyphs: SoftwareGlyphProvider,
    private val glyphCache: GlyphCache,
    private val maxGroupDepth: Int
) {

    private data class Mask(val shape: Shape, val bounds: Rect, val inverse: Transform?)
    private data class State(val transform: Transform, val masks: List<Mask>)
    private data class Group(val parent: IntArray, val opacity: Float)

    var pixels = IntArray(width * height)
        private set
    private var state = State(Transform.scale(frame.pixelsPerDp.x, frame.pixelsPerDp.y), emptyList())
    private val saves = ArrayDeque<State>()
    private val groups = ArrayDeque<Group>()

    fun draw(commands: List<DrawCommand>) {
        commands.forEach { command ->
            when (command) {
                Save -> saves += state
                Restore -> state = saves.removeLast()
                is Concat -> state = state.copy(transform = state.transform * command.transform)
                is Clip -> state = state.copy(masks = state.masks + Mask(command.shape, command.bounds, state.transform.inverse()))
                is Fill -> paint(command.bounds, { command.shape.contains(it, command.bounds) }) { brush(command.brush, it) }
                is Stroke -> stroke(command)
                is DrawImage -> image(command)
                is DrawText -> text(command)
                is BeginGroup -> {
                    check(groups.size < maxGroupDepth) { "Software group depth exceeds $maxGroupDepth." }
                    groups += Group(pixels, command.opacity)
                    pixels = IntArray(width * height)
                }

                EndGroup -> {
                    val group = groups.removeLast()
                    val layer = pixels
                    pixels = group.parent
                    pixels.indices.forEach { pixels[it] = over(pixels[it], layer[it], group.opacity) }
                }

                else -> error("Unsupported software command ${command::class.simpleName}.")
            }
        }
    }

    private fun paint(bounds: Rect, contains: (Vec2) -> Boolean, color: (Vec2) -> Int) {
        val inverse = state.transform.inverse() ?: return
        val target = state.transform.mapRect(bounds)
        val left = floor(target.x).toInt().coerceIn(0, width)
        val top = floor(target.y).toInt().coerceIn(0, height)
        val right = ceil(target.right).toInt().coerceIn(left, width)
        val bottom = ceil(target.bottom).toInt().coerceIn(top, height)
        for (y in top until bottom) for (x in left until right) {
            val pixelPoint = Vec2(x + .5f, y + .5f)
            if (state.masks.any { mask -> mask.inverse == null || !mask.shape.contains(mask.inverse.map(pixelPoint), mask.bounds) }) continue
            val local = inverse.map(pixelPoint)
            if (!contains(local)) continue
            val index = y * width + x
            pixels[index] = over(pixels[index], color(local))
        }
    }

    private fun stroke(command: Stroke) {
        if (command.width == 0f) return
        require(command.shape === Rectangle || command.shape === Ellipse || command.shape is RoundedRectangle) {
            "Software strokes support rectangle, ellipse and rounded rectangle only."
        }
        val half = command.width / 2
        val b = command.bounds
        if (b.width == 0f || b.height == 0f) return
        val outer = Rect(b.x - half, b.y - half, b.width + command.width, b.height + command.width)
        val inner = Rect(b.x + half, b.y + half, (b.width - command.width).coerceAtLeast(0f), (b.height - command.width).coerceAtLeast(0f))
        // Supported shapes have closed contours, so line caps do not affect their geometry.
        // A rectangle's 90-degree miter has ratio sqrt(2); lower limits select a bevel.
        val join = if (command.join == StrokeJoin.MITER && command.miterLimit < sqrt(2f)) StrokeJoin.BEVEL else command.join
        val shape = command.shape
        val outerShape = when {
            shape is RoundedRectangle -> RoundedRectangle(shape.radius + half)
            shape === Rectangle && join == StrokeJoin.ROUND -> RoundedRectangle(half)
            shape === Rectangle && join == StrokeJoin.BEVEL -> object : Shape {
                override fun contains(point: Vec2, bounds: Rect): Boolean = bounds.contains(point) &&
                        point.x - bounds.x + point.y - bounds.y >= half &&
                        bounds.right - point.x + point.y - bounds.y >= half &&
                        bounds.right - point.x + bounds.bottom - point.y >= half &&
                        point.x - bounds.x + bounds.bottom - point.y >= half
            }

            else -> command.shape
        }
        val innerShape = (command.shape as? RoundedRectangle)?.let { RoundedRectangle((it.radius - half).coerceAtLeast(0f)) } ?: command.shape
        paint(outer, { outerShape.contains(it, outer) && !innerShape.contains(it, inner) }) { brush(command.brush, it) }
    }

    private fun image(command: DrawImage) {
        val image = command.image as? SoftwareImage ?: error("Software backend requires SoftwareImage handles, received '${command.image.id}'.")
        if (command.source.width == 0f || command.source.height == 0f || command.destination.width == 0f || command.destination.height == 0f) return
        fun pixel(x: Int, y: Int): Int = image.pixels[y.coerceIn(0, image.height - 1) * image.width + x.coerceIn(0, image.width - 1)]
        paint(command.destination, { command.destination.contains(it) }) { point ->
            val sx = command.source.x + (point.x - command.destination.x) / command.destination.width * command.source.width
            val sy = command.source.y + (point.y - command.destination.y) / command.destination.height * command.source.height
            val sample = if (command.sampling == Sampling.NEAREST) pixel(floor(sx).toInt(), floor(sy).toInt()) else {
                val x = sx - .5f
                val y = sy - .5f
                val ix = floor(x).toInt()
                val iy = floor(y).toInt()
                lerpArgb(
                    lerpArgb(pixel(ix, iy), pixel(ix + 1, iy), x - ix),
                    lerpArgb(pixel(ix, iy + 1), pixel(ix + 1, iy + 1), x - ix), y - iy
                )
            }
            tint(sample, command.tint)
        }
    }

    private fun text(command: DrawText) {
        require(glyphs.supports(command.layout.providerId)) {
            "Software glyph provider does not support layout '${command.layout.providerId}'. Supply a compatible raster provider."
        }
        val scale = max(hypot(state.transform.a, state.transform.b), hypot(state.transform.c, state.transform.d)).coerceAtLeast(.001f)
        command.layout.runs.forEach { run ->
            run.glyphs.forEach glyphLoop@{ positioned ->
                val key = GlyphKey(command.layout.providerId, run.face, positioned.glyphId, run.fontSize, scale)
                val glyph = glyphCache.get(key) { glyphs.rasterize(command.layout.providerId, run.face, positioned.glyphId, run.fontSize, scale) }
                if (glyph.width == 0 || glyph.height == 0) return@glyphLoop
                val bounds = Rect(
                    command.origin.x + positioned.x + glyph.left / scale,
                    command.origin.y + positioned.y - glyph.top / scale, glyph.width / scale, glyph.height / scale
                )
                paint(bounds, { bounds.contains(it) }) { point ->
                    val x = floor((point.x - bounds.x) * scale).toInt().coerceIn(0, glyph.width - 1)
                    val y = floor((point.y - bounds.y) * scale).toInt().coerceIn(0, glyph.height - 1)
                    val coverage = glyph.alpha[y * glyph.width + x].toInt() and 255
                    val color = command.color
                    ((color.alpha * coverage / 255) shl 24) or (color.red shl 16) or (color.green shl 8) or color.blue
                }
            }
        }
    }

    private fun brush(brush: Brush, point: Vec2): Int = when (brush) {
        is Solid -> argb(brush.color)
        is LinearGradient -> {
            val x = brush.to.x - brush.from.x
            val y = brush.to.y - brush.from.y
            val length = x * x + y * y
            val t = if (length == 0f) 1f else ((point.x - brush.from.x) * x + (point.y - brush.from.y) * y) / length
            gradient(brush.stops, t)
        }

        is RadialGradient -> gradient(brush.stops, hypot(point.x - brush.center.x, point.y - brush.center.y) / brush.radius)
        else -> error("Unsupported software brush ${brush::class.simpleName}.")
    }

    private fun gradient(stops: List<ColorStop>, fraction: Float): Int {
        if (fraction <= stops.first().offset) return argb(stops.first().color)
        val right = stops.indexOfFirst { it.offset >= fraction }
        if (right < 0) return argb(stops.last().color)
        val left = stops[right - 1]
        val next = stops[right]
        val width = next.offset - left.offset
        return lerpArgb(argb(left.color), argb(next.color), if (width == 0f) 1f else (fraction - left.offset) / width)
    }
}

private fun argb(color: Color) = (color.alpha shl 24) or (color.red shl 16) or (color.green shl 8) or color.blue

private fun tint(pixel: Int, color: Color): Int =
    ((((pixel ushr 24) and 255) * color.alpha / 255) shl 24) or
            ((((pixel ushr 16) and 255) * color.red / 255) shl 16) or
            ((((pixel ushr 8) and 255) * color.green / 255) shl 8) or ((pixel and 255) * color.blue / 255)

private fun lerpArgb(from: Int, to: Int, fraction: Float): Int {
    val fromAlpha = ((from ushr 24) and 255) / 255f
    val toAlpha = ((to ushr 24) and 255) / 255f
    val alpha = fromAlpha + (toAlpha - fromAlpha) * fraction
    if (alpha <= 0f) return 0
    var result = (alpha * 255).roundToInt().coerceIn(0, 255) shl 24
    for (shift in intArrayOf(0, 8, 16)) {
        val a = (from ushr shift) and 255
        val b = (to ushr shift) and 255
        val channel = (a * fromAlpha * (1 - fraction) + b * toAlpha * fraction) / alpha
        result = result or (channel.roundToInt().coerceIn(0, 255) shl shift)
    }
    return result
}

private data class GlyphKey(val provider: String, val face: FontFace, val glyphId: Int, val fontSize: Float, val scale: Float)

private class GlyphCache(private val budget: Int) {

    private val entries = linkedMapOf<GlyphKey, SoftwareGlyph>()
    var bytes: Int = 0
        private set
    val size: Int get() = entries.size
    fun get(key: GlyphKey, load: () -> SoftwareGlyph): SoftwareGlyph {
        entries.remove(key)?.let {
            entries[key] = it
            return it
        }
        val glyph = load()
        if (glyph.alpha.size > budget || budget == 0) return glyph
        while (entries.isNotEmpty() && (entries.size >= 256 || bytes.toLong() + glyph.alpha.size > budget)) {
            val oldest = entries.keys.first()
            bytes -= checkNotNull(entries.remove(oldest)).alpha.size
        }
        entries[key] = glyph
        bytes += glyph.alpha.size
        return glyph
    }

    fun clear() {
        entries.clear()
        bytes = 0
    }
}

private fun over(destination: Int, source: Int, opacity: Float = 1f): Int {
    val sourceAlpha = ((source ushr 24) and 255) / 255f * opacity
    if (sourceAlpha <= 0f) return destination
    val destinationAlpha = ((destination ushr 24) and 255) / 255f
    val alpha = sourceAlpha + destinationAlpha * (1f - sourceAlpha)
    var result = (alpha * 255).roundToInt().coerceIn(0, 255) shl 24
    for (shift in intArrayOf(0, 8, 16)) {
        val value = (((source ushr shift) and 255) * sourceAlpha +
                ((destination ushr shift) and 255) * destinationAlpha * (1f - sourceAlpha)) / alpha
        result = result or (value.roundToInt().coerceIn(0, 255) shl shift)
    }
    return result
}
