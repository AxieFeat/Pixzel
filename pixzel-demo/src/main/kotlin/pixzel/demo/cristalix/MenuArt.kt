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
internal data class MenuArt(
    val background: ImageHandle?,
    val banners: List<ImageHandle>,
    val reviewsBackground: ImageHandle,
    val favoriteStar: ImageHandle,
    val favoriteOutline: ImageHandle,
    val corner: ImageHandle,
    val avatars: List<ImageHandle>,
    val thumbUp: ImageHandle,
    val thumbDown: ImageHandle,
    val icons: Map<String, ImageHandle>
) {

    companion object {

        fun load(context: RenderContext, resources: ResourceScope, data: MenuData): MenuArt {
            fun texture(name: String) = load(context, resources, name)
            val background = MenuArt::class.java.getResourceAsStream("/cristalix/hub.png")?.use { stream ->
                ImageIO.read(stream)?.let { upload(context, resources, it, "cristalix-hub") }
            }
            return MenuArt(
                background, data.banners.map { texture(it.image) }, texture("reviews-background"),
                texture("star"), texture("star-outline"), texture("corner"), (0..7).map { texture("avatar-$it") },
                texture("thumb-up"), texture("thumb-down"), data.games.map { it.icon }.distinct().associateWith(::texture)
            )
        }

        private fun load(context: RenderContext, resources: ResourceScope, name: String): ImageHandle =
            MenuArt::class.java.getResourceAsStream("/cristalix/reference/$name.png")!!.use {
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
}
