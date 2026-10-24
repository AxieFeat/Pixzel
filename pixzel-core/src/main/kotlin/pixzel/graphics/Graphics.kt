package pixzel.graphics

import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.surface.Transform
import pixzel.surface.Vec2
import pixzel.text.TextLayout

/** Straight-alpha sRGB inputs, RRGGBBAA. Backends convert to their blend representation once. */
@JvmInline
value class Color(val rgba: Long) {

    val red get() = ((rgba ushr 24) and 255).toInt()
    val green get() = ((rgba ushr 16) and 255).toInt()
    val blue get() = ((rgba ushr 8) and 255).toInt()
    val alpha get() = (rgba and 255).toInt()

    companion object {

        val WHITE = Color(0xFFFFFFFFL)
        val TRANSPARENT = Color(0)
    }
}

val Int.rgba get() = Color(toLong() and 0xFFFFFFFFL)
val Long.rgba get() = Color(this and 0xFFFFFFFFL)
val Int.rgb get() = Color(((toLong() and 0xFFFFFFL) shl 8) or 255)

interface Brush
data class Solid(val color: Color) : Brush
data class ColorStop(val offset: Float, val color: Color) { init {
    require(offset in 0f..1f)
}
}

data class LinearGradient(val from: Vec2, val to: Vec2, val stops: List<ColorStop>) : Brush { init {
    require(stops.size >= 2 && stops.zipWithNext().all { it.first.offset <= it.second.offset })
}
}

data class RadialGradient(val center: Vec2, val radius: Float, val stops: List<ColorStop>) : Brush { init {
    require(radius > 0 && radius.isFinite() && stops.size >= 2)
}
}

/** Maps cropped-image coordinates (origin 0,0) into local canvas coordinates; edge pixels clamp. */
data class ImageBrush(
    val image: ImageHandle, val source: Rect = Rect(0f, 0f, image.size.width, image.size.height),
    val sampling: Sampling = Sampling.LINEAR, val transform: Transform = Transform.IDENTITY
) : Brush {

    init {
        require(source.width > 0 && source.height > 0)
        require(transform.inverse() != null) { "Image brush transform must be invertible" }
    }
}

fun solid(color: Color): Brush = Solid(color)
interface Shape {

    fun contains(point: Vec2, bounds: Rect): Boolean
}

data object Rectangle : Shape {

    override fun contains(point: Vec2, bounds: Rect) = bounds.contains(point)
}

data object Ellipse : Shape {

    override fun contains(point: Vec2, bounds: Rect): Boolean {
        if (bounds.width <= 0 || bounds.height <= 0) return false
        val x = (point.x - bounds.x) / bounds.width * 2 - 1
        val y = (point.y - bounds.y) / bounds.height * 2 - 1
        return x * x + y * y <= 1
    }
}

data class RoundedRectangle(val radius: Float) : Shape {
    init {
        require(radius.isFinite() && radius >= 0)
    }

    override fun contains(point: Vec2, bounds: Rect): Boolean {
        if (!bounds.contains(point)) return false
        val r = minOf(radius, bounds.width / 2, bounds.height / 2)
        val x = point.x.coerceIn(bounds.x + r, bounds.right - r)
        val y = point.y.coerceIn(bounds.y + r, bounds.bottom - r)
        return (point.x - x) * (point.x - x) + (point.y - y) * (point.y - y) <= r * r
    }
}

data class CornerRadii(val topLeft: Float = 0f, val topRight: Float = 0f, val bottomRight: Float = 0f, val bottomLeft: Float = 0f) {
    init {
        require(listOf(topLeft, topRight, bottomRight, bottomLeft).all { it.isFinite() && it >= 0 })
    }

    /** Uniformly scales conflicting adjacent radii until every pair fits its edge. */
    fun fit(size: Size): CornerRadii {
        fun ratio(length: Float, sum: Float) = if (sum == 0f) 1f else length / sum
        val factor = minOf(
            1f, ratio(size.width, topLeft + topRight), ratio(size.width, bottomLeft + bottomRight),
            ratio(size.height, topLeft + bottomLeft), ratio(size.height, topRight + bottomRight)
        )
        return CornerRadii(topLeft * factor, topRight * factor, bottomRight * factor, bottomLeft * factor)
    }

    companion object {

        fun all(radius: Float) = CornerRadii(radius, radius, radius, radius)
    }
}

data class RoundedCorners(val radii: CornerRadii) : Shape {

    override fun contains(point: Vec2, bounds: Rect): Boolean {
        if (!bounds.contains(point)) return false
        val r = radii.fit(bounds.size)
        fun corner(x: Float, y: Float, radius: Float): Boolean {
            val dx = point.x - x
            val dy = point.y - y
            return dx * dx + dy * dy <= radius * radius
        }
        return when {
            point.x < bounds.x + r.topLeft && point.y < bounds.y + r.topLeft -> corner(bounds.x + r.topLeft, bounds.y + r.topLeft, r.topLeft)
            point.x > bounds.right - r.topRight && point.y < bounds.y + r.topRight -> corner(
                bounds.right - r.topRight,
                bounds.y + r.topRight,
                r.topRight
            )

            point.x > bounds.right - r.bottomRight && point.y > bounds.bottom - r.bottomRight -> corner(
                bounds.right - r.bottomRight,
                bounds.bottom - r.bottomRight,
                r.bottomRight
            )

            point.x < bounds.x + r.bottomLeft && point.y > bounds.bottom - r.bottomLeft -> corner(
                bounds.x + r.bottomLeft,
                bounds.bottom - r.bottomLeft,
                r.bottomLeft
            )

            else -> true
        }
    }
}

enum class FillRule {
    NON_ZERO,
    EVEN_ODD
}
interface PathSegment
data class MoveTo(val point: Vec2) : PathSegment
data class LineTo(val point: Vec2) : PathSegment
data class QuadTo(val control: Vec2, val end: Vec2) : PathSegment
data class CubicTo(val first: Vec2, val second: Vec2, val end: Vec2) : PathSegment
data object ClosePath : PathSegment
data class VectorPath(val segments: List<PathSegment>, val fillRule: FillRule = FillRule.NON_ZERO) : Shape {

    private val hitContours by lazy { PathGeometry.flatten(this) }
    override fun contains(point: Vec2, bounds: Rect): Boolean = PathGeometry.contains(hitContours, point, fillRule)
}

enum class Capability {
    RECT_CLIP,
    SHAPE_CLIP,
    PATH,
    GRADIENT,
    IMAGE,
    TEXT,
    GROUP_OPACITY,
    SHADOW,
    CUSTOM
}
enum class TargetFormat {
    RGBA8,
    RGBA8_DEPTH24_STENCIL8
}
enum class TextRenderMode {
    GRAYSCALE_GLYPH_MASK,
    APPROXIMATE_GLYPH_BOUNDS
}

/** Null values mean unknown; formats are null when the backend does not expose storage information. */
data class BackendLimits(
    val maxTextureSize: Int? = null, val maxClipDepth: Int? = null, val maxGradientStops: Int? = null,
    val maxShapeEdges: Int? = null, val maxTessellationVertices: Int? = null,
    val targetFormats: Set<TargetFormat>? = null, val textMode: TextRenderMode? = null
) {

    init {
        require(listOfNotNull(maxTextureSize, maxClipDepth, maxGradientStops, maxShapeEdges, maxTessellationVertices).all { it >= 0 })
    }
}

data class BackendCapabilities(val supported: Set<Capability>, val name: String, val limits: BackendLimits = BackendLimits()) {

    fun require(capability: Capability) {
        require(capability in supported) { "$name does not support $capability" }
    }
}

interface ImageHandle : AutoCloseable {

    val id: String
    val generation: Long
    val size: Size

    /** Logical intrinsic units; [size] remains source-image pixels for cropping and atlas regions. */
    val intrinsicSize: Size get() = size
    val isClosed: Boolean
}

enum class Sampling {
    NEAREST,
    LINEAR
}
data class FrameInfo(val logicalSize: Size, val framebufferSize: Size, val pixelsPerDp: Vec2, val generation: Long = 0)
interface DrawCommand

/** An external operation executed only by an explicitly registered backend renderer. */
interface CustomCommand : DrawCommand {

    val operationId: String
    val requiredCapabilities: Set<Capability>
}

data object Save : DrawCommand
data object Restore : DrawCommand
data class Concat(val transform: Transform) : DrawCommand
data class Clip(val shape: Shape, val bounds: Rect) : DrawCommand
data class Fill(val shape: Shape, val bounds: Rect, val brush: Brush) : DrawCommand
enum class StrokeJoin {
    MITER,
    BEVEL,
    ROUND
}
enum class StrokeCap {
    BUTT,
    SQUARE,
    ROUND
}
data class Stroke(
    val shape: Shape, val bounds: Rect, val brush: Brush, val width: Float,
    val join: StrokeJoin = StrokeJoin.ROUND, val cap: StrokeCap = StrokeCap.ROUND, val miterLimit: Float = 4f
) : DrawCommand {

    init {
        require(width.isFinite() && width >= 0)
        require(miterLimit.isFinite() && miterLimit >= 1)
    }
}

data class DrawText(val layout: TextLayout, val origin: Vec2, val color: Color) : DrawCommand
data class DrawImage(
    val image: ImageHandle,
    val source: Rect,
    val destination: Rect,
    val tint: Color = Color.WHITE,
    val sampling: Sampling = Sampling.LINEAR
) : DrawCommand

data class BeginGroup(val opacity: Float) : DrawCommand { init {
    require(opacity in 0f..1f)
}
}

data object EndGroup : DrawCommand
data class Shadow(val shape: Shape, val bounds: Rect, val color: Color, val blur: Float, val spread: Float, val offset: Vec2) : DrawCommand
interface Canvas {

    fun emit(command: DrawCommand)
    fun fill(shape: Shape, bounds: Rect, brush: Brush) = emit(Fill(shape, bounds, brush))
    fun stroke(
        shape: Shape,
        bounds: Rect,
        brush: Brush,
        width: Float,
        join: StrokeJoin = StrokeJoin.ROUND,
        cap: StrokeCap = StrokeCap.ROUND,
        miterLimit: Float = 4f
    ) =
        emit(Stroke(shape, bounds, brush, width, join, cap, miterLimit))

    fun text(layout: TextLayout, origin: Vec2, color: Color) = emit(DrawText(layout, origin, color))
}

inline fun Canvas.saved(block: Canvas.() -> Unit) {
    emit(Save)
    try {
        block()
    } finally {
        emit(Restore)
    }
}

inline fun Canvas.group(opacity: Float, block: Canvas.() -> Unit) {
    emit(BeginGroup(opacity))
    try {
        block()
    } finally {
        emit(EndGroup)
    }
}

/** A backend is borrowed; each Ui owns and closes its session. */
interface UiBackend {

    val capabilities: BackendCapabilities

    /** Changes when context-owned session resources must be recreated. Zero means no native context tracking. */
    val resourceGeneration: Long get() = 0L
    fun openSession(): RenderSession
}

interface RenderSession : AutoCloseable {

    fun submit(frame: FrameInfo, commands: List<DrawCommand>)
    override fun close()
}

/** Per-submission backend observations. Null means the backend cannot measure a field accurately. */
data class RenderMetrics(
    val drawCalls: Int? = null, val batches: Int? = null, val vertices: Int? = null, val uploadedBytes: Long? = null,
    val textureSwitches: Int? = null, val transientTargets: Int? = null, val ownedResources: Int? = null
)

interface RenderMetricsProvider {

    val renderMetrics: RenderMetrics
}
