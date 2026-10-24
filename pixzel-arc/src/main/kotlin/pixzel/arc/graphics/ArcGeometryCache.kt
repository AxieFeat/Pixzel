package pixzel.arc.graphics

import arc.graphics.vertex.VertexBuffer
import pixzel.graphics.Shape
import pixzel.graphics.StrokeCap
import pixzel.graphics.StrokeJoin
import pixzel.surface.Rect

internal data class GeometryKey(
    val shape: Shape, val bounds: Rect, val stroke: Float?, val tolerance: Float,
    val join: StrokeJoin, val cap: StrokeCap, val miterLimit: Float
)

/**
 * Session-owned LRU caches account for CPU geometry and GPU meshes separately.
 */
internal class ArcGeometryCache(private val meshBudgetBytes: Long) : AutoCloseable {

    private val meshes = object : LinkedHashMap<Any, VertexBuffer>(64, .75f, true) {}
    var meshBytes = 0L
        private set
    private val cpuGeometry = object : LinkedHashMap<GeometryKey, FloatArray>(64, .75f, true) {}
    var cpuGeometryBytes = 0L
        private set

    val keys: Set<Any> get() = meshes.keys
    val size get() = meshes.size

    operator fun get(key: Any): VertexBuffer? = meshes[key]

    fun points(key: GeometryKey): FloatArray {
        cpuGeometry[key]?.let { return it }

        val vertices = ShapeTessellator.triangles(key.shape, key.bounds, key.stroke, key.tolerance, key.join, key.cap, key.miterLimit)
        require(vertices.size.toLong() * 8 <= meshBudgetBytes) { "CPU geometry exceeds its $meshBudgetBytes-byte budget" }

        val points = FloatArray(vertices.size * 2)
        vertices.forEachIndexed { index, point ->
            points[index * 2] = point.x
            points[index * 2 + 1] = point.y
        }

        cpuGeometry[key] = points
        cpuGeometryBytes += points.size.toLong() * 4

        // Access-order maps put the least recently used allocation first.
        while (cpuGeometry.size > 512 || cpuGeometryBytes > meshBudgetBytes) {
            val entry = cpuGeometry.entries.iterator()
            val first = entry.next()
            cpuGeometryBytes -= first.value.size.toLong() * 4
            entry.remove()
        }

        return points
    }

    fun put(key: Any, buffer: VertexBuffer): VertexBuffer {
        meshes.put(key, buffer)?.let {
            meshBytes -= it.activeBytes
            it.cleanup()
        }

        meshBytes += buffer.activeBytes
        while (meshes.size > 512 || meshBytes > meshBudgetBytes) {
            val entry = meshes.entries.iterator()
            val first = entry.next()
            meshBytes -= first.value.activeBytes
            first.value.cleanup()
            entry.remove()
        }

        return buffer
    }

    override fun close() {
        var failure: Throwable? = null

        for (buffer in meshes.values) {
            try {
                buffer.cleanup()
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure.addSuppressed(error)
            }
        }

        meshes.clear()
        cpuGeometry.clear()
        meshBytes = 0
        cpuGeometryBytes = 0
        failure?.let { throw it }
    }
}
