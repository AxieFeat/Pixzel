package pixzel.component

/**
 * This interface represents an ui component.
 *
 * This component must have one instance and represents a "type" or group of elements with the same behavior.
 * For this reason, it does not contain any information about position, size, color, etc.
 * It only has a function for drawing, to which specific values will be passed.
 */
interface ComponentType {

    /**
     * Draw this component via some context.
     *
     * @param context Context with parameters.
     */
    fun draw(context: ComponentContext)

}