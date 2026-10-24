package pixzel.resource

import pixzel.graphics.ImageHandle
import pixzel.state.State
import pixzel.surface.Size

/**
 * Managed, immutable straight-alpha RGBA8 pixels. Decoders never return native resources.
 */
class DecodedImage(
    val width: Int,
    val height: Int,
    rgba: ByteArray,
    val intrinsicSize: Size = Size(width.toFloat(), height.toFloat())
) {

    private val pixels: ByteArray

    init {
        require(width > 0 && height > 0) { "Decoded image dimensions must be positive" }
        val pixelCount = width.toLong() * height
        require(pixelCount <= Int.MAX_VALUE / 4 && pixelCount * 4 == rgba.size.toLong()) {
            "RGBA8 requires four bytes per pixel within a managed byte array"
        }
        pixels = rgba.copyOf()
    }

    val rgba: ByteArray get() = pixels.copyOf()
    val byteCount: Int get() = pixels.size
}

/**
 * Invoked on the injected worker executor; implementations must support concurrent retries.
 */
fun interface ImageDecoder {

    fun decode(bytes: ByteArray): DecodedImage
}

/**
 * Invoked on the Ui thread. Its result has the ownership selected when creating the asset.
 */
fun interface ImageUploader {

    fun upload(image: DecodedImage): ImageHandle
}

sealed interface ImageAssetStatus {
    data class Loading(val generation: Long) : ImageAssetStatus
    data class Ready(val generation: Long, val image: ImageHandle, val intrinsicSize: Size) : ImageAssetStatus
    data class Failed(val generation: Long, val cause: Throwable) : ImageAssetStatus
    data object Closed : ImageAssetStatus
}

/**
 * Ui-owned asset. Retry, observation and close run on the Ui thread. Worker results use Ui.post.
 */
interface ImageAsset : AutoCloseable {

    val status: State<ImageAssetStatus>
    val placeholderSize: Size
    val intrinsicSize: Size
    val isClosed: Boolean

    /**
     * Supersedes pending work and releases the previous owned image before reloading.
     */
    fun retry()
    override fun close()
}
