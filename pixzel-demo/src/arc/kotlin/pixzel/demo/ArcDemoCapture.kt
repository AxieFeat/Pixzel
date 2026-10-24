package pixzel.demo

import org.lwjgl.opengl.GL11
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/** Desktop showcase evidence only. Production adapters use Arc's neutral API. */
internal fun captureShowcase(width: Int, height: Int, path: Path) {
    val pixels = ByteBuffer.allocateDirect(Math.multiplyExact(Math.multiplyExact(width, height), 4))
    GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels)
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until height) for (x in 0 until width) {
        val index = ((height - 1 - y) * width + x) * 4
        fun channel(offset: Int) = pixels.get(index + offset).toInt() and 255
        image.setRGB(x, y, (channel(3) shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2))
    }
    Files.createDirectories(path.toAbsolutePath().parent)
    check(ImageIO.write(image, "png", path.toFile()))
    println("Showcase capture: ${path.toAbsolutePath()}")
}
