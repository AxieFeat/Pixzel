package pixzel.arc.graphics

import arc.graphics.RenderContext
import arc.texture.Texture
import pixzel.graphics.ImageHandle
import pixzel.surface.Size
import java.util.concurrent.atomic.AtomicLong

/** Arc texture bridge. Closing a borrowed image never deletes the application's texture. */
class ArcImageHandle private constructor(
    private var currentTexture: Texture,
    initialSize: Size,
    override val id: String,
    private val owned: Boolean,
    val premultiplied: Boolean,
    val flipVertically: Boolean,
    private val sourceContext: RenderContext?,
    private var source: ByteArray?,
    initialIntrinsicSize: Size,
) : ImageHandle {

    private val owner = Thread.currentThread()
    private var nativeGeneration = generationOf(currentTexture, sourceContext)
    override var size = initialSize
        private set
    override var intrinsicSize = initialIntrinsicSize
        private set
    override var generation = generations.incrementAndGet()
        private set
    val texture: Texture
        get() {
            check(!isClosed) { "Image '$id' is closed" }
            return if (owned) resolve(sourceContext!!) else currentTexture
        }
    override var isClosed = false
        private set

    override fun close() {
        check(Thread.currentThread() === owner) { "Arc image lifetime operations require the creating thread" }
        if (isClosed) return
        if (owned) currentTexture.cleanup()
        source = null
        isClosed = true
        generation = generations.incrementAndGet()
    }

    internal fun resolve(context: RenderContext): Texture {
        check(Thread.currentThread() === owner) { "Arc images draw on their creating thread" }
        check(!isClosed) { "Image '$id' is closed" }
        val current = context.resourceGeneration
        check(current != 0L) { "No graphics context is current for image '$id'" }
        if (current != nativeGeneration) {
            check(owned) { "Borrowed image '$id' belongs to a stale context; rebind its replacement texture" }
            check(context === sourceContext) { "Owned image '$id' belongs to a different Arc context provider" }
            val replacement = context.createRgbaTexture(size.width.toInt(), size.height.toInt(), source ?: error("Image source was released"))
            currentTexture.cleanup()
            currentTexture = replacement
            nativeGeneration = current
            generation = generations.incrementAndGet()
        }
        return currentTexture
    }

    /** Application hook after recreating a borrowed texture. The old borrowed texture is never deleted here. */
    fun rebind(texture: Texture, size: Size = this.size, context: RenderContext? = sourceContext, intrinsicSize: Size = this.intrinsicSize) {
        check(Thread.currentThread() === owner)
        check(!isClosed && !owned) { "Only an open borrowed image can be rebound" }
        val native = generationOf(texture, context)
        currentTexture = texture
        this.size = size
        this.intrinsicSize = intrinsicSize
        nativeGeneration = native
        generation = generations.incrementAndGet()
    }

    companion object {

        private val generations = AtomicLong()
        private fun generationOf(texture: Texture, context: RenderContext?): Long {
            val generation = context?.resourceGeneration ?: texture.resourceGeneration
            require(generation != 0L) { "Provide the creating context for a texture without generation tracking" }
            require(texture.resourceGeneration == 0L || texture.resourceGeneration == generation) { "Texture belongs to a different or stale context generation" }
            return generation
        }

        fun borrowed(
            texture: Texture, size: Size, id: String = "arc-image", premultiplied: Boolean = false,
            flipVertically: Boolean = false, context: RenderContext? = null, intrinsicSize: Size = size
        ): ArcImageHandle {
            return ArcImageHandle(texture, size, id, false, premultiplied, flipVertically, context, null, intrinsicSize)
        }

        /** RGBA8 straight-alpha bytes, top row first. Creation/close run on the render thread. */
        fun rgba(
            context: RenderContext, width: Int, height: Int, bytes: ByteArray, id: String = "arc-image", sourceBudgetBytes: Long = 32L * 1024 * 1024,
            intrinsicSize: Size = Size(width.toFloat(), height.toFloat())
        ): ArcImageHandle {
            require(sourceBudgetBytes > 0 && bytes.size <= sourceBudgetBytes) { "Image restoration source exceeds its $sourceBudgetBytes-byte budget" }
            val retained = bytes.copyOf()
            return ArcImageHandle(
                context.createRgbaTexture(width, height, retained),
                Size(width.toFloat(), height.toFloat()),
                id,
                true,
                false,
                false,
                context,
                retained,
                intrinsicSize
            )
        }
    }
}
