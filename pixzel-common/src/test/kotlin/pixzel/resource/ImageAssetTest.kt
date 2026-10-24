package pixzel.resource

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pixzel.Ui
import pixzel.component.AbstractElement
import pixzel.component.Image
import pixzel.graphics.ImageHandle
import pixzel.graphics.RecordingBackend
import pixzel.platform.TestHost
import pixzel.surface.Size
import pixzel.text.FixedFontTextService
import pixzel.ui
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlin.time.Duration

class ImageAssetTest {
    private class Workers : Executor {

        private val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun runNext() {
            val command = tasks.removeFirst()
            var failure: Throwable? = null
            val thread = Thread {
                try {
                    command.run()
                } catch (error: Throwable) {
                    failure = error
                }
            }
            thread.start()
            thread.join()
            failure?.let { throw it }
        }

        val size get() = tasks.size
    }

    private class Handle(override val id: String, override val intrinsicSize: Size = Size(2f, 1f)) : ImageHandle {

        override val size = Size(4f, 2f)
        override val generation = 1L
        var closes = 0
        override val isClosed get() = closes > 0
        override fun close() {
            closes++
        }
    }

    private class Fixture : AutoCloseable {

        val host = TestHost.of(320, 200)
        val ui: Ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        val workers = Workers()
        val uploads = mutableListOf<Handle>()
        var decodes = 0
        val decoder = ImageDecoder { bytes ->
            decodes++
            DecodedImage(4, 2, ByteArray(32) { bytes.single() }, Size(2f, 1f))
        }
        val uploader = ImageUploader { decoded ->
            ui.assertThread()
            Handle("image-${uploads.size}", decoded.intrinsicSize).also { uploads += it }
        }

        fun asset(source: () -> ByteArray = { byteArrayOf(7) }, ownership: Ownership = Ownership.OWNED) =
            imageAsset(ui, source, decoder, uploader, workers, Size(30f, 20f), ownership)

        fun frame() = ui.frame(Duration.ZERO)
        override fun close() {
            ui.close()
            host.close()
        }
    }

    @Test
    fun `managed pixels are immutable and intrinsic metadata differs from pixel dimensions`() {
        val source = ByteArray(8) { 1 }
        val decoded = DecodedImage(2, 1, source, Size(1f, .5f))
        source[0] = 9
        decoded.rgba[1] = 9
        assertArrayEquals(ByteArray(8) { 1 }, decoded.rgba)
        assertEquals(Size(1f, .5f), decoded.intrinsicSize)
        assertThrows(IllegalArgumentException::class.java) { DecodedImage(2, 2, ByteArray(15)) }
        assertThrows(IllegalArgumentException::class.java) { DecodedImage(0, 0, ByteArray(0)) }
        assertThrows(IllegalArgumentException::class.java) { DecodedImage(Int.MAX_VALUE, Int.MAX_VALUE, ByteArray(0)) }
    }

    @Test
    fun `worker decode posts a FIFO UI upload and stable placeholder can precede intrinsic layout`() {
        Fixture().use { f ->
            val asset = f.asset()
            val image = Image.of(f.ui.components)
            f.ui.root.add(image)
            image.bindAsset(asset)
            f.frame()
            assertEquals(Size(30f, 20f), image.surface.bounds.size)
            val measured = (image as AbstractElement).measureCount
            f.workers.runNext()
            assertTrue(asset.status.value is ImageAssetStatus.Loading)
            assertTrue(f.uploads.isEmpty())
            val order = mutableListOf<String>()
            asset.status.observe { if (it is ImageAssetStatus.Ready) order += "ready" }
            f.ui.post { order += "after" }
            f.frame()
            assertEquals(listOf("ready", "after"), order)
            assertSame(f.uploads.single(), image.image)
            assertEquals(Size(2f, 1f), asset.intrinsicSize)
            assertEquals(Size(30f, 20f), image.surface.bounds.size)
            assertEquals(measured, image.measureCount)
            assertEquals(0, f.ui.statistics.measuredNodes)
            val intrinsic = Image.of(f.ui.components)
            intrinsic.bindAsset(asset, ImageAssetSizing.INTRINSIC)
            f.ui.root.add(intrinsic)
            f.frame()
            assertEquals(Size(2f, 1f), intrinsic.surface.bounds.size)
            intrinsic.close()
            asset.retry()
            f.frame()
            assertNull(image.image)
            assertEquals(1, f.uploads.single().closes)
        }
    }

    @Test
    fun `superseded queued and decoded generations never upload`() {
        Fixture().use { f ->
            val asset = f.asset()
            asset.retry()
            f.workers.runNext()
            assertEquals(0, f.decodes)
            f.workers.runNext()
            assertEquals(1, f.decodes)
            asset.retry()
            f.frame()
            assertTrue(f.uploads.isEmpty())
            f.workers.runNext()
            f.frame()
            val ready = asset.status.value as ImageAssetStatus.Ready
            assertEquals(3L, ready.generation)
            assertEquals(2, f.decodes)
            assertEquals(1, f.uploads.size)
        }
    }

    @Test
    fun `disposal drops queued completion and owned or borrowed handles have explicit cleanup`() {
        Fixture().use { f ->
            val pending = f.asset()
            f.workers.runNext()
            pending.close()
            pending.close()
            f.frame()
            assertTrue(f.uploads.isEmpty())
            assertEquals(ImageAssetStatus.Closed, pending.status.value)
            assertThrows(IllegalStateException::class.java) { pending.retry() }
            val owned = f.asset()
            f.workers.runNext()
            f.frame()
            val ownHandle = f.uploads.last()
            owned.close()
            owned.close()
            assertEquals(1, ownHandle.closes)
            val borrowed = f.asset(ownership = Ownership.BORROWED)
            f.workers.runNext()
            f.frame()
            val borrowHandle = f.uploads.last()
            borrowed.retry()
            f.workers.runNext()
            f.frame()
            borrowed.close()
            assertEquals(0, borrowHandle.closes)
            assertEquals(0, f.uploads.last().closes)
            f.ui.close()
            assertEquals(1, ownHandle.closes)
        }
    }

    @Test
    fun `decode and upload errors are observable and retry replaces failure`() {
        Fixture().use { f ->
            var failSource = true
            val asset = f.asset({ if (failSource) error("source unavailable") else byteArrayOf(1) })
            f.workers.runNext()
            f.frame()
            assertEquals("source unavailable", (asset.status.value as ImageAssetStatus.Failed).cause.message)
            failSource = false
            asset.retry()
            f.workers.runNext()
            f.frame()
            assertTrue(asset.status.value is ImageAssetStatus.Ready)
            val failedUpload = imageAsset(
                f.ui, { byteArrayOf(1) }, f.decoder,
                ImageUploader { error("upload unavailable") }, f.workers
            )
            f.workers.runNext()
            f.frame()
            assertEquals("upload unavailable", (failedUpload.status.value as ImageAssetStatus.Failed).cause.message)
            val rejected = imageAsset(
                f.ui, { byteArrayOf(1) }, f.decoder, f.uploader,
                Executor { throw RejectedExecutionException("executor unavailable") })
            assertTrue((rejected.status.value as ImageAssetStatus.Failed).cause is RejectedExecutionException)
            val oversized = imageAsset(f.ui, { byteArrayOf(1) }, f.decoder, f.uploader, f.workers, maxDecodedBytes = 16)
            f.workers.runNext()
            f.frame()
            assertTrue((oversized.status.value as ImageAssetStatus.Failed).cause is IllegalArgumentException)
        }
    }

    @Test
    fun `reentrant uploader retry closes its stale owned upload and preserves newer work`() {
        Fixture().use { f ->
            lateinit var asset: ImageAsset
            var first = true
            asset = imageAsset(f.ui, { byteArrayOf(1) }, f.decoder, ImageUploader { decoded ->
                f.ui.assertThread()
                if (first) {
                    first = false
                    asset.retry()
                }
                Handle("reentrant", decoded.intrinsicSize).also { f.uploads += it }
            }, f.workers)
            f.workers.runNext()
            f.frame()
            assertEquals(1, f.uploads.single().closes)
            assertTrue(asset.status.value is ImageAssetStatus.Loading)
            f.workers.runNext()
            f.frame()
            assertEquals(2L, (asset.status.value as ImageAssetStatus.Ready).generation)
            assertEquals(0, f.uploads.last().closes)
        }
    }

    @Test
    fun `Ui close releases ready image and worker completion after close never reaches uploader`() {
        Fixture().use { f ->
            val ready = f.asset()
            f.workers.runNext()
            f.frame()
            val pending = f.asset()
            f.ui.close()
            f.workers.runNext()
            assertTrue(ready.isClosed)
            assertTrue(pending.isClosed)
            assertEquals(1, f.uploads.single().closes)
            assertEquals(0, f.workers.size)
        }
    }

    @Test
    fun `observer error does not turn a successful upload into failure or prevent owned cleanup`() {
        Fixture().use { f ->
            val asset = f.asset()
            val observation = asset.status.observe { if (it is ImageAssetStatus.Ready) error("observer failed") }
            f.workers.runNext()
            assertEquals("observer failed", assertThrows(IllegalStateException::class.java) { f.frame() }.message)
            assertTrue(asset.status.value is ImageAssetStatus.Ready)
            assertEquals(0, f.uploads.single().closes)
            observation.close()
            asset.close()
            assertEquals(1, f.uploads.single().closes)
        }
    }
}
