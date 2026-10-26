// Pixel geometry, palette and motion timings are authored as visual design data.
@file:Suppress("MagicNumber", "StringLiteralDuplication")

package pixzel.demo.cristalix

import arc.graphics.RenderContext
import pixzel.arc.graphics.ArcImageHandle
import pixzel.graphics.ImageHandle
import pixzel.resource.ResourceScope
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * Original client textures are uploaded once and owned by the Pixzel resource scope.
 */
internal class MenuArt(context: RenderContext, resources: ResourceScope) {

    val background: ImageHandle? = javaClass.getResourceAsStream("/cristalix/hub.png")?.use { stream ->
        ImageIO.read(stream)?.let { upload(context, resources, it, "cristalix-hub") }
    }
    val banners = listOf("nightmares", "arasaka").map { load(context, resources, it) }
    val reviewsBackground = load(context, resources, "reviews-background")
    val favoriteStar = load(context, resources, "star")
    val favoriteOutline = load(context, resources, "star-outline")
    val corner = load(context, resources, "corner")
    val avatars = (0..7).map { load(context, resources, "avatar-$it") }
    val thumbUp = load(context, resources, "thumb-up")
    val thumbDown = load(context, resources, "thumb-down")
    val icons: Map<String, ImageHandle> = gameModes.map { it.icon }.distinct().associateWith { load(context, resources, it) }

    private fun load(context: RenderContext, resources: ResourceScope, name: String): ImageHandle =
        javaClass.getResourceAsStream("/cristalix/reference/$name.png")!!.use {
            upload(context, resources, ImageIO.read(it), "cristalix-$name")
        }

    private fun upload(context: RenderContext, resources: ResourceScope, image: BufferedImage, id: String): ImageHandle {
        val pixels = ByteArray(image.width * image.height * 4)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val value = image.getRGB(x, y)
                val index = (y * image.width + x) * 4
                pixels[index] = (value ushr 16).toByte()
                pixels[index + 1] = (value ushr 8).toByte()
                pixels[index + 2] = value.toByte()
                pixels[index + 3] = (value ushr 24).toByte()
            }
        }

        return resources.own(ArcImageHandle.rgba(context, image.width, image.height, pixels, id))
    }
}
