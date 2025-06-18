package pixzel.surface

/**
 * This interface represents some surface, where your can draw components.
 */
interface Surface {

    /**
     * Root surface, that surface where the current is located.
     *
     * Can return current instance if not have a root.
     */
    val root: Surface

    /**
     * Width of this surface in pixels.
     *
     * Cannot be more than [root] surface width. If it is more, it will be clipped by root size.
     */
    val width: Int

    /**
     * Height of this surface in pixels.
     *
     * Cannot be more than [root] surface height. If it is more, it will be clipped by root size.
     */
    val height: Int

    /**
     * X position relative to the root surface. The position is considered to be the upper left corner of the surface rectangle.
     *
     * Can be -1 if this is the lowest surface.
     */
    val x: Int

    /**
     * Y position relative to the root surface. The position is considered to be the upper left corner of the surface rectangle.
     *
     * Can be -1 if this is the lowest surface.
     */
    val y: Int

}