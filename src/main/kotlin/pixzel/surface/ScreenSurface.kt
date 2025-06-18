package pixzel.surface

/**
 * This abstract class represents the root surface of all others.
 *
 * @property width Width of screen.
 * @property height Height of screen.
 */
abstract class ScreenSurface(
    override val width: Int,
    override val height: Int
) : Surface {

    override val root: Surface = this

    override val x: Int = -1
    override val y: Int = -1

}