package pixzel.resource

import pixzel.Ui
import pixzel.UiLifecycle
import pixzel.component.Image
import pixzel.graphics.ImageHandle
import pixzel.layout.unit.dp
import pixzel.state.State
import pixzel.state.stateRuntime
import pixzel.surface.Size
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Source/decoder/executor/uploader are borrowed; the asset belongs to [ui]'s resource scope.
 */
fun imageAsset(
    ui: Ui,
    source: () -> ByteArray,
    decoder: ImageDecoder,
    uploader: ImageUploader,
    executor: Executor,
    placeholderSize: Size = Size.ZERO,
    ownership: Ownership = Ownership.OWNED,
    maxDecodedBytes: Long = 64L * 1024 * 1024
): ImageAsset {
    ui.assertThread()
    check(ui.lifecycle != UiLifecycle.CLOSED && !ui.root.resources.isClosed) { "Ui is closed" }
    require(maxDecodedBytes > 0)
    val asset = AsyncImageAsset(ui, source, decoder, uploader, executor, placeholderSize, ownership, maxDecodedBytes)
    ui.root.resources.own(asset)
    asset.retry()
    return asset
}

private class AsyncImageAsset(
    private val ui: Ui,
    private val source: () -> ByteArray,
    private val decoder: ImageDecoder,
    private val uploader: ImageUploader,
    private val executor: Executor,
    override val placeholderSize: Size,
    private val ownership: Ownership,
    private val maxDecodedBytes: Long
) : ImageAsset {

    private val state = stateRuntime().stateOf<ImageAssetStatus>(ImageAssetStatus.Loading(0), "image asset")
    override val status: State<ImageAssetStatus> get() = state
    private val generation = AtomicLong()
    private val closed = AtomicBoolean()
    private var current: ImageHandle? = null
    override val isClosed: Boolean get() = closed.get()
    override val intrinsicSize: Size get() = (state.value as? ImageAssetStatus.Ready)?.intrinsicSize ?: placeholderSize
    private fun isCurrent(token: Long) = !closed.get() && generation.get() == token
    private fun release(image: ImageHandle?) {
        if (ownership == Ownership.OWNED) image?.close()
    }

    override fun retry() {
        ui.assertThread()
        check(!closed.get()) { "Image asset is closed" }
        val token = generation.incrementAndGet()
        val old = current
        current = null
        var failure: Throwable? = null
        try {
            state.value = ImageAssetStatus.Loading(token)
        } catch (error: Throwable) {
            failure = error
        }
        try {
            release(old)
        } catch (error: Throwable) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
        if (isCurrent(token)) {
            try {
                executor.execute { load(token) }
            } catch (error: Throwable) {
                if (isCurrent(token)) try {
                    state.value = ImageAssetStatus.Failed(token, error)
                } catch (observerError: Throwable) {
                    if (failure == null) failure = observerError else failure.addSuppressed(observerError)
                }
            }
        }
        failure?.let { throw it }
    }

    private fun load(token: Long) {
        if (!isCurrent(token)) return
        val result = try {
            val decoded = decoder.decode(source().copyOf())
            require(decoded.byteCount.toLong() <= maxDecodedBytes) { "Decoded image exceeds $maxDecodedBytes byte budget" }
            decoded
        } catch (error: Throwable) {
            deliver(token) { state.value = ImageAssetStatus.Failed(token, error) }
            return
        }
        deliver(token) { upload(token, result) }
    }

    private fun deliver(token: Long, action: () -> Unit) {
        if (!isCurrent(token)) return
        try {
            ui.post { if (isCurrent(token)) action() }
        } catch (_: IllegalStateException) {
            // Ui.post rejects work while closing. Only managed decoded pixels exist here;
            // the Ui owns this asset and closes it before its render session.
        }
    }

    private fun upload(token: Long, decoded: DecodedImage) {
        ui.assertThread()
        val uploaded = try {
            uploader.upload(decoded)
        } catch (error: Throwable) {
            if (isCurrent(token)) state.value = ImageAssetStatus.Failed(token, error)
            return
        }
        if (!isCurrent(token)) {
            release(uploaded)
            return
        }
        if (uploaded.isClosed) {
            state.value = ImageAssetStatus.Failed(token, IllegalStateException("Uploader returned a closed image"))
            return
        }
        current = uploaded
        // Observer failures propagate through the Ui error policy; they are not upload failures.
        state.value = ImageAssetStatus.Ready(token, uploaded, decoded.intrinsicSize)
    }

    override fun close() {
        ui.assertThread()
        if (!closed.compareAndSet(false, true)) return
        generation.incrementAndGet()
        val old = current
        current = null
        var failure: Throwable? = null
        try {
            state.value = ImageAssetStatus.Closed
        } catch (error: Throwable) {
            failure = error
        }
        try {
            release(old)
        } catch (error: Throwable) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
        failure?.let { throw it }
    }
}

enum class ImageAssetSizing {
    PRESERVE_LAYOUT,
    PLACEHOLDER,
    INTRINSIC
}

/**
 * The Image borrows the asset's handle. Closing the Image disconnects this observation.
 */
fun Image.bindAsset(asset: ImageAsset, sizing: ImageAssetSizing = ImageAssetSizing.PLACEHOLDER): Subscription {
    ui?.assertThread()
    check(!isDisposed) { "Image is disposed" }
    fun apply(status: ImageAssetStatus) {
        image = (status as? ImageAssetStatus.Ready)?.image
        val size = when (sizing) {
            ImageAssetSizing.PRESERVE_LAYOUT -> null
            ImageAssetSizing.PLACEHOLDER -> asset.placeholderSize
            ImageAssetSizing.INTRINSIC -> (status as? ImageAssetStatus.Ready)?.intrinsicSize ?: asset.placeholderSize
        }
        if (size != null) {
            layout.width = size.width.dp
            layout.height = size.height.dp
        }
    }
    apply(asset.status.value)
    return resources.own(asset.status.observe(::apply))
}
