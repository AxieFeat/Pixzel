// Pixel geometry, palette and motion timings are authored as visual design data.
@file:Suppress("MagicNumber", "StringLiteralDuplication")

package pixzel.demo.cristalix

import arc.graphics.RenderContext
import pixzel.arc.graphics.ArcImageHandle
import pixzel.graphics.ImageHandle
import pixzel.resource.ResourceScope
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * Original voxel illustrations are baked once, then animated as ordinary Pixzel images.
 */
internal class MenuArt(context: RenderContext, resources: ResourceScope) {

    val background: ImageHandle? = javaClass.getResourceAsStream("/cristalix/hub.png")?.use { stream ->
        ImageIO.read(stream)?.let { upload(context, resources, it, "cristalix-hub") }
    }
    val icons: Map<String, ImageHandle> = (gameModes.map { it.icon } + "chest" + "avatar").distinct().associateWith { icon ->
        val image = BufferedImage(192, 192, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
            VoxelIllustration(graphics).draw(icon)
        } finally {
            graphics.dispose()
        }
        upload(context, resources, image, "cristalix-$icon")
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

private class VoxelIllustration(private val graphics: Graphics2D) {

    fun draw(icon: String) {
        when (icon) {
            "pickaxe", "sword", "trophy", "duel" -> drawTools(icon)
            "crate", "chest", "book", "server", "computer", "box", "controller" -> drawDevices(icon)
            "tower", "bars", "cuffs", "wither", "rifle", "bingo" -> drawEquipment(icon)
            else -> drawWorld(icon)
        }
    }

    private fun drawTools(icon: String) {
        when (icon) {
            "pickaxe" -> {
                tool(pickaxe, 0x52D6B1)
            }

            "sword" -> {
                tool(sword, 0xAFA6A1)
            }

            "trophy" -> {
                box(61, 154, 65, 12, 12, 0x9C4B22)
                box(77, 144, 33, 12, 9, 0xC46D31)
                tool(sword, 0xF69D53, 0, -5)
            }

            "duel" -> {
                box(38, 144, 111, 23, 18, 0x34343A)
                graphics.rotate(-0.55, 95.0, 95.0)
                tool(sword, 0xE9425B, -17, 0)
                graphics.rotate(1.1, 95.0, 95.0)
                tool(sword, 0x7461DA, 17, 0)
                graphics.rotate(-0.55, 95.0, 95.0)
            }
        }
    }

    private fun drawDevices(icon: String) {
        when (icon) {
            "crate", "chest" -> {
                crate(icon == "chest")
            }

            "book" -> {
                box(55, 35, 80, 125, 12, 0x6F2B16)
                box(53, 27, 75, 122, 8, 0xFA7937)
                box(56, 43, 14, 9, 3, 0xFFE18B)
                box(56, 125, 14, 9, 3, 0xFFE18B)
                pattern(listOf(".####.", "######", "#.##.#", "#.##.#", ".####.", "..##.."), 75, 66, 7, 0xF7F5E4)
            }

            "server", "computer" -> {
                box(46, 38, 92, 118, 17, 0x30313B)
                box(53, 45, 77, 103, 6, if (icon == "computer") 0x14151E else 0x4C5C88)
                val glow = if (icon == "computer") 0xDA63E6 else 0xACB5DF
                box(66, 62, 15, 18, 3, glow)
                box(101, 62, 15, 18, 3, glow)
                box(69, 119, 46, 5, 3, glow)
                if (icon == "server") repeat(4) { box(139 + it * 5, 34 - it, 3, 120, 2, 0x9198A8) }
            }

            "box" -> {
                box(49, 40, 90, 118, 18, 0xD9C18F)
                box(95, 40, 12, 118, 2, 0x303E70)
                pattern(listOf("#####", "#...#", "#.#.#", "#.#.#", "#####"), 58, 61, 6, 0x9299DA)
                pattern(listOf("#.#.#", ".#.#.", "##..#"), 56, 124, 6, 0x756342)
            }

            "controller" -> {
                box(40, 74, 106, 54, 12, 0xB6B9C1)
                box(34, 108, 25, 38, 7, 0x9DA3AE)
                box(126, 108, 25, 38, 7, 0x9DA3AE)
                box(59, 91, 11, 29, 3, 0x252B34)
                box(50, 101, 30, 10, 3, 0x252B34)
                box(100, 82, 29, 18, 4, 0x76BAE7)
                box(118, 108, 9, 9, 3, 0x5C86C7)
            }
        }
    }

    private fun drawEquipment(icon: String) {
        when (icon) {
            "tower" -> {
                box(55, 148, 77, 11, 14, 0x8D9290)
                box(68, 64, 50, 84, 10, 0xADB1AB)
                box(61, 52, 65, 18, 12, 0xB8B9AC)
                repeat(4) { box(58 + it * 19, 39, 12, 18, 9, 0xB8B9AC) }
                box(79, 83, 9, 42, 2, 0x626963)
                box(104, 85, 6, 22, 2, 0x9B3B39)
            }

            "bars" -> {
                box(48, 39, 98, 7, 8, 0xB8BDC8)
                box(48, 151, 98, 7, 8, 0xB8BDC8)
                repeat(3) { box(57 + it * 35, 46, 8, 106, 8, 0xC3CBD2) }
            }

            "cuffs" -> {
                graphics.rotate(0.48, 96.0, 96.0)
                ring(39, 107)
                ring(103, 39)
                box(83, 85, 9, 36, 5, 0x777E85)
                box(92, 74, 24, 9, 5, 0xA0A6AB)
                graphics.rotate(-0.48, 96.0, 96.0)
            }

            "wither" -> {
                box(86, 91, 17, 70, 8, 0x353638)
                box(43, 78, 106, 20, 9, 0x2C2D2E)
                repeat(3) { index ->
                    val x = 34 + index * 45
                    val y = if (index == 1) 42 else 63
                    box(x, y, 38, 36, 10, 0x252629)
                    box(x + 5, y + 10, 8, 7, 2, 0xD4D8D7)
                    box(x + 22, y + 10, 8, 7, 2, 0xD4D8D7)
                    box(x + 11, y + 27, 17, 4, 2, 0xB4B8B5)
                }
            }

            "rifle" -> {
                graphics.rotate(-0.15, 96.0, 96.0)
                box(26, 87, 128, 24, 7, 0x2B3035)
                box(68, 72, 51, 14, 6, 0x47494C)
                box(112, 91, 46, 15, 5, 0xD86C1F)
                box(68, 109, 18, 36, 5, 0x30353C)
                box(91, 111, 14, 28, 5, 0xB26123)
                box(17, 92, 26, 10, 3, 0x484C50)
                graphics.rotate(0.15, 96.0, 96.0)
            }

            "bingo" -> {
                box(58, 31, 77, 131, 8, 0xECEAE9)
                box(64, 39, 65, 20, 2, 0xA28CDA)
                repeat(5) { row ->
                    repeat(4) { column ->
                        box(
                            65 + column * 16, 68 + row * 16, 12, 12, 1,
                            if ((row + column) % 4 == 0) 0xA18ACF else 0xC9CDCB
                        )
                    }
                }
            }
        }
    }

    private fun drawWorld(icon: String) {
        when (icon) {
            "slime" -> {
                box(45, 48, 96, 107, 20, 0x78C96C)
                box(52, 55, 79, 89, 2, 0x88D87B)
                box(58, 111, 10, 13, 2, 0x29452D)
                box(105, 111, 10, 13, 2, 0x29452D)
                box(77, 129, 24, 5, 2, 0x3C7541)
            }

            "burger" -> {
                box(35, 157, 120, 7, 16, 0xD9DBCD)
                box(59, 138, 70, 14, 12, 0xC38C4F)
                box(53, 125, 82, 12, 11, 0x703A23)
                box(49, 113, 88, 10, 12, 0xA5C747)
                box(57, 102, 76, 11, 10, 0xEACA52)
                box(59, 80, 74, 21, 13, 0xE5B271)
                box(72, 66, 47, 15, 11, 0xEDC38C)
            }

            "bed" -> {
                box(37, 108, 117, 28, 13, 0x7C452E)
                box(40, 78, 111, 31, 14, 0xCC405D)
                box(43, 79, 30, 25, 10, 0xF2EADC)
                box(40, 134, 10, 22, 6, 0x7C452E)
                box(138, 134, 10, 22, 6, 0x7C452E)
            }

            "island" -> {
                box(49, 106, 86, 38, 24, 0x88613B)
                box(45, 98, 94, 13, 24, 0x85BB59)
                box(85, 58, 15, 43, 8, 0x78532F)
                box(63, 33, 57, 36, 19, 0x67A54C)
            }

            "boots" -> {
                box(49, 49, 31, 83, 13, 0xB57647)
                box(49, 125, 53, 20, 13, 0x7E5138)
                box(108, 36, 29, 81, 13, 0xD49B59)
                box(108, 110, 49, 21, 13, 0x92683F)
            }

            "avatar" -> {
                box(76, 18, 40, 40, 10, 0xE2AF9D)
                box(72, 15, 47, 11, 11, 0x604A46)
                box(65, 60, 61, 62, 10, 0xA38AAB)
                box(49, 64, 17, 61, 9, 0xD8C0B6)
                box(127, 64, 17, 61, 9, 0xD8C0B6)
                box(70, 123, 20, 49, 9, 0x3B3B50)
                box(101, 123, 20, 49, 9, 0x3B3B50)
                box(79, 66, 13, 44, 2, 0xEBCBD0)
            }

            else -> {
                ring(62, 71)
                pattern(listOf("..#..", ".###.", "#####", ".###.", "..#.."), 64, 74, 11, 0xE34F57)
            }
        }
    }

    private fun crate(special: Boolean) {
        box(43, 53, 100, 102, 19, if (special) 0x3B334E else 0x373740)
        box(51, 39, 85, 22, 20, if (special) 0x796886 else 0x51515C)
        box(52, 73, 82, 50, 5, 0xD2E4EA)
        box(59, 80, 31, 36, 2, if (special) 0x8F66B4 else 0x8CCAE9)
        box(96, 80, 31, 36, 2, 0xD88424)
        box(52, 127, 16, 28, 4, 0x67616F)
        box(119, 127, 16, 28, 4, 0x67616F)
        if (special) {
            box(78, 73, 20, 74, 5, 0x9D8BB7)
            box(84, 97, 9, 20, 3, 0xF6CB5F)
            box(41, 27, 24, 17, 11, 0x8F7B9F)
            box(121, 27, 24, 17, 11, 0x8F7B9F)
        }
    }

    private fun ring(x: Int, y: Int) {
        pattern(listOf(".####.", "##..##", "#....#", "#....#", "##..##", ".####."), x, y, 7, 0x9CA2A8)
    }

    private fun tool(rows: List<String>, color: Int, dx: Int = 0, dy: Int = 0) {
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, cell ->
                val tint = when (cell) {
                    '#' -> color
                    '+' -> 0x896243
                    'x' -> 0x513A32
                    else -> return@forEachIndexed
                }
                box(42 + x * 6 + dx, 22 + y * 6 + dy, 6, 6, 3, tint)
            }
        }
    }

    private fun pattern(rows: List<String>, left: Int, top: Int, size: Int, color: Int) {
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, cell ->
                if (cell == '#') box(left + x * size, top + y * size, size, size, 2, color)
            }
        }
    }

    @Suppress("LongParameterList")
    private fun box(x: Int, y: Int, width: Int, height: Int, depth: Int, rgb: Int) {
        val color = Color(rgb)
        graphics.color = color.brighter()
        graphics.fillPolygon(intArrayOf(x, x + depth, x + width + depth, x + width), intArrayOf(y, y - depth, y - depth, y), 4)
        graphics.color = color.darker()
        graphics.fillPolygon(
            intArrayOf(x + width, x + width + depth, x + width + depth, x + width),
            intArrayOf(y, y - depth, y + height - depth, y + height), 4
        )
        graphics.color = color
        graphics.fillRect(x, y, width, height)
    }

    companion object {

        private val sword = listOf(
            "........#........", ".......###.......", ".......###.......", ".......###.......", ".......###.......",
            ".......###.......", ".......###.......", ".......###.......", ".......###.......", ".......###.......",
            "...#...###...#...", "....#########....", ".....#######.....", ".......xxx.......", ".......+++.......",
            ".......+++.......", ".......xxx.......", ".......+++.......", ".......xxx.......", "........x........"
        )
        private val pickaxe = listOf(
            ".......++........", "......+##+.......", ".....+####+......", "....+##++##+.....", "...+##++++##+....",
            "..+##+..+..##+...", ".+##+...+...##+..", "..++....+....++..", "........+........", "........+........",
            "........+........", "........+........", "........+........", "........+........", "........+........",
            "........+........", "........+........", "........+........", "........x........", "........x........"
        )
    }
}
