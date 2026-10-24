package pixzel.graphics

import org.junit.jupiter.api.Test
import pixzel.animation.FakeClock
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.input.TextCommit
import pixzel.input.WindowFocus
import pixzel.platform.TestHost
import pixzel.surface.Rect
import pixzel.surface.Size
import pixzel.surface.Transform
import pixzel.surface.Vec2
import pixzel.text.FixedFontTextService
import pixzel.text.FontFace
import pixzel.text.TextRequest
import kotlin.time.Duration.Companion.milliseconds

fun runReferenceBackendChecks() {
    val host = TestHost.of(800, 600, 1600, 1200)
    val delivered = mutableListOf<Long>()
    val ui = host.subscribe { event ->
        delivered += event.sequence
        event is KeyEvent && event.key == Key.SPACE
    }
    host.queue(KeyEvent(Key.SPACE, KeyAction.DOWN))
    host.queue(TextCommit("Привет"))
    host.queue(KeyEvent(Key.ENTER, KeyAction.DOWN))
    host.drainEvents()
    check(delivered == listOf(1L, 2L, 3L))
    check(host.consumedEvents.size == 1 && host.gameplayEvents.size == 2)
    check(host.isPhysicalKeyDown(Key.SPACE) && !host.isGameplayKeyDown(Key.SPACE))
    check(host.isGameplayKeyDown(Key.ENTER))
    host.queue(KeyEvent(Key.SPACE, KeyAction.UP))
    host.queue(KeyEvent(Key.SPACE, KeyAction.DOWN))
    host.queue(KeyEvent(Key.SPACE, KeyAction.UP))
    host.drainEvents()
    check(!host.isPhysicalKeyDown(Key.SPACE))
    host.queue(WindowFocus(false))
    host.drainEvents()
    check(!host.isPhysicalKeyDown(Key.ENTER))
    check(host.writeClipboard("copied").isSuccess && host.readClipboard().getOrThrow() == "copied")
    host.clipboardAvailable = false
    check(host.readClipboard().isFailure && host.writeClipboard("discarded").isFailure)
    val oldGeneration = host.metrics.generation
    host.resize(640, 480, 1280, 960)
    check(host.metrics.generation > oldGeneration && host.metrics.framebufferSize == Size(1280f, 960f))
    ui.close()
    ui.close()
    check(host.listenerCount == 0)
    repeat(1_000) { host.subscribe { false }.close() }
    check(host.listenerCount == 0)

    val clock = FakeClock.of()
    check(clock.advance(16.milliseconds) == 16.milliseconds)
    clock.isPaused = true
    check(clock.advance(200.milliseconds) == 16.milliseconds)
    clock.isPaused = false
    check(clock.advance(4.milliseconds) == 20.milliseconds)

    val frame = FrameInfo(Size(32f, 24f), Size(32f, 24f), Vec2(1f, 1f))
    val recording = RecordingBackend.of()
    val recorded = recording.openSession()
    val commands = listOf(Save, Clip(Rectangle, Rect(0f, 0f, 20f, 20f)), Fill(Rectangle, Rect(0f, 0f, 20f, 20f), Solid(0xFF0000FF.rgba)), Restore)
    recorded.submit(frame, commands)
    check(recording.lastFrame?.commands == commands)
    check(runCatching { recorded.submit(frame, listOf(Restore)) }.isFailure)
    check(runCatching { recorded.submit(frame, listOf(Save, BeginGroup(.5f), Restore, EndGroup)) }.isFailure)
    check(recording.frames.size == 1)
    recorded.close()
    recorded.close()
    check(recording.activeSessions == 0 && runCatching { recorded.submit(frame, emptyList()) }.isFailure)

    val software = SoftwareBackend.of()
    val session = software.openSession()
    val red = Solid(0xFF0000FF.rgba)
    val blue = Solid(0x0000FFFF.rgba)
    val green = Solid(0x00FF00FF.rgba)
    // Group alpha must compose overlapping opaque children before applying half opacity.
    session.submit(
        frame, listOf(
            Fill(Rectangle, Rect(0f, 0f, 32f, 24f), blue), BeginGroup(.5f),
            Fill(Rectangle, Rect(2f, 2f, 12f, 12f), red), Fill(Rectangle, Rect(6f, 2f, 12f, 12f), red), EndGroup
        )
    )
    var image = checkNotNull(software.lastImage)
    check(image.getRGB(3, 3) == image.getRGB(7, 3)) { "Group alpha was applied independently to children." }
    check(((image.getRGB(7, 3) ushr 16) and 255) in 127..128)
    check((image.getRGB(7, 3) and 255) in 127..128)

    session.submit(
        frame, listOf(
            Save, Concat(Transform.translation(10f, 0f)), Clip(Rectangle, Rect(0f, 0f, 5f, 10f)),
            Fill(Rectangle, Rect(0f, 0f, 20f, 20f), green), Restore
        )
    )
    image = checkNotNull(software.lastImage)
    check(image.getRGB(10, 2) == 0xFF00FF00.toInt())
    check(image.getRGB(9, 2) == 0 && image.getRGB(15, 2) == 0)
    session.submit(frame, listOf(Clip(Ellipse, Rect(0f, 0f, 20f, 20f)), Fill(Rectangle, Rect(0f, 0f, 20f, 20f), red)))
    image = checkNotNull(software.lastImage)
    check(image.getRGB(0, 0) == 0 && image.getRGB(10, 10) == 0xFFFF0000.toInt())

    val borrowedImage = SoftwareImage.of(2, 1, intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()))
    session.submit(frame, listOf(DrawImage(borrowedImage, Rect(0f, 0f, 2f, 1f), Rect(0f, 0f, 4f, 2f), sampling = Sampling.NEAREST)))
    image = checkNotNull(software.lastImage)
    check(image.getRGB(0, 0) == 0xFFFF0000.toInt() && image.getRGB(3, 0) == 0xFF0000FF.toInt())
    val layout = FixedFontTextService.of().layout(TextRequest("ABC"))
    session.submit(frame, listOf(DrawText(layout, Vec2.ZERO, Color.WHITE)))
    image = checkNotNull(software.lastImage)
    check((0 until image.width).any { x -> (0 until image.height).any { y -> image.getRGB(x, y) != 0 } })
    val beforeFailure = software.lastImage
    check(runCatching { session.submit(frame, listOf(Shadow(Rectangle, Rect(0f, 0f, 2f, 2f), Color.WHITE, 1f, 0f, Vec2.ZERO))) }.isFailure)
    check(software.lastImage === beforeFailure)
    check(runCatching {
        session.submit(
            frame,
            listOf(Fill(VectorPath(listOf(MoveTo(Vec2.ZERO), LineTo(Vec2(2f, 2f)))), Rect(0f, 0f, 2f, 2f), red))
        )
    }.isFailure)
    val magnified = FrameInfo(Size(8f, 8f), Size(32f, 32f), Vec2(4f, 4f))
    val border = Rect(2f, 2f, 4f, 4f)
    session.submit(magnified, listOf(Stroke(Rectangle, border, red, 2f, join = StrokeJoin.ROUND)))
    check(checkNotNull(software.lastImage).getRGB(5, 5) != 0 && checkNotNull(software.lastImage).getRGB(4, 4) == 0)
    session.submit(magnified, listOf(Stroke(Rectangle, border, red, 2f, join = StrokeJoin.BEVEL)))
    check(checkNotNull(software.lastImage).getRGB(5, 5) == 0)
    session.submit(magnified, listOf(Stroke(Rectangle, border, red, 2f, join = StrokeJoin.MITER, miterLimit = 4f)))
    check(checkNotNull(software.lastImage).getRGB(4, 4) != 0)
    session.submit(magnified, listOf(Stroke(Rectangle, border, red, 2f, join = StrokeJoin.MITER, miterLimit = 1f)))
    check(checkNotNull(software.lastImage).getRGB(5, 5) == 0)
    session.close()
    session.close()
    check(software.activeSessions == 0 && !borrowedImage.isClosed)
    borrowedImage.close()

    var rasterizations = 0
    val fixture = Bitmap5x7GlyphProvider()
    val counted = SoftwareBackend.of(object : SoftwareGlyphProvider {
        override fun supports(providerId: String) = fixture.supports(providerId)
        override fun rasterize(face: FontFace, glyphId: Int, fontSize: Float, pixelsPerDp: Float): SoftwareGlyph {
            rasterizations++
            return fixture.rasterize(face, glyphId, fontSize, pixelsPerDp)
        }
    })
    val cacheSession = counted.openSession()
    cacheSession.submit(frame, listOf(DrawText(layout, Vec2.ZERO, Color.WHITE)))
    cacheSession.submit(frame, listOf(DrawText(layout, Vec2.ZERO, Color.WHITE)))
    check(rasterizations == 3) { "Unchanged glyphs were rasterized again." }
    cacheSession.close()
    check(counted.cachedGlyphBytes == 0 && counted.cachedGlyphCount == 0)
    var largeRasters = 0
    val byteBudget = SoftwareBackend.of(object : SoftwareGlyphProvider {
        override fun supports(providerId: String) = true
        override fun rasterize(face: FontFace, glyphId: Int, fontSize: Float, pixelsPerDp: Float): SoftwareGlyph {
            largeRasters++
            return SoftwareGlyph(32, 32, 0, 32, ByteArray(1024))
        }
    }, maxGlyphBytes = 2048)
    val budgetSession = byteBudget.openSession()
    budgetSession.submit(frame, listOf(DrawText(layout, Vec2.ZERO, Color.WHITE)))
    check(largeRasters == 3 && byteBudget.cachedGlyphBytes == 2048 && byteBudget.cachedGlyphCount == 2)
    budgetSession.close()
    check(byteBudget.cachedGlyphBytes == 0)
    host.close()
}

class ReferenceBackendTest {

    @Test
    fun referenceHostAndRendering() = runReferenceBackendChecks()

    @Test
    fun recordingValidatesCustomCapabilitiesAndImageBrushLifetime() {
        val frame = FrameInfo(Size(4f, 4f), Size(4f, 4f), Vec2(1f, 1f))
        val custom = object : CustomCommand {
            override val operationId = "test.path-operation"
            override val requiredCapabilities = setOf(Capability.PATH)
        }
        val limited = RecordingBackend.of(BackendCapabilities(setOf(Capability.CUSTOM), "Custom-only recorder"))
        limited.openSession().use { session ->
            check(runCatching { session.submit(frame, listOf(custom)) }.isFailure)
            check(limited.frames.isEmpty())
        }
        val complete = RecordingBackend.of(BackendCapabilities(setOf(Capability.CUSTOM, Capability.PATH), "Path recorder"))
        complete.openSession().use { session -> session.submit(frame, listOf(custom)) }
        check(complete.lastFrame?.commands == listOf(custom))

        val image = SoftwareImage.of(2, 2, IntArray(4) { -1 })
        val images = RecordingBackend.of(BackendCapabilities(setOf(Capability.IMAGE), "Image recorder"))
        images.openSession().use { session ->
            session.submit(frame, listOf(Fill(Rectangle, Rect(0f, 0f, 4f, 4f), ImageBrush(image))))
            check(runCatching {
                session.submit(
                    frame, listOf(
                        Fill(
                            Rectangle, Rect.ZERO,
                            ImageBrush(image, Rect(1f, 1f, 2f, 2f))
                        )
                    )
                )
            }.isFailure)
            image.close()
            check(runCatching { session.submit(frame, listOf(Fill(Rectangle, Rect.ZERO, ImageBrush(image)))) }.isFailure)
        }
        check(images.frames.size == 1)
    }
}

fun main() {
    runReferenceBackendChecks()
    println("Reference backend checks passed")
}
