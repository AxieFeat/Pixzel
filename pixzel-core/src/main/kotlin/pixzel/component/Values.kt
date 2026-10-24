package pixzel.component

import pixzel.resource.Subscription

/**
 * Finite inclusive range. A zero step is continuous; positive steps are anchored at min.
 */
interface RangedValue {

    var min: Float
    var max: Float
    var step: Float
    var value: Float

    /**
     * Atomically changes both bounds and clamps the value; min==max is valid.
     */
    fun setRange(min: Float, max: Float)
    fun onValueChange(handler: (Float) -> Unit): Subscription
}

interface Slider : Element, RangedValue {
    companion object {

        fun of(components: ComponentFactory, id: String = "") = components.slider(id)
    }
}

interface Progress : Element, RangedValue {

    var indeterminate: Boolean

    companion object {

        fun of(components: ComponentFactory, id: String = "") = components.progress(id)
    }
}
