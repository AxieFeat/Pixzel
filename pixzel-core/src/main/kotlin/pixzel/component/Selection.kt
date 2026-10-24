package pixzel.component

import pixzel.resource.Subscription

enum class CheckState {
    UNCHECKED,
    CHECKED,
    INDETERMINATE
}

interface Checkbox : Button {

    var checkState: CheckState

    /** User activation resolves INDETERMINATE to CHECKED; direct changes also notify. */
    fun onCheckChange(handler: (CheckState) -> Unit): Subscription

    companion object {

        fun of(components: ComponentFactory, id: String = "") = components.checkbox(id)
    }
}

interface Toggle : Button {

    var checked: Boolean
    fun onCheckedChange(handler: (Boolean) -> Unit): Subscription

    companion object {

        fun of(components: ComponentFactory, id: String = "") = components.toggle(id)
    }
}

interface Radio : Button {

    var selected: Boolean
    var group: RadioGroup?
    fun onSelectedChange(handler: (Boolean) -> Unit): Subscription

    companion object {

        fun of(components: ComponentFactory, id: String = "") = components.radio(id)
    }
}

/**
 * Explicit selection owner. Removing a selected member chooses the next enabled member at its
 * former position, or the last eligible member. An empty/fully disabled group can select none.
 * Arrow navigation wraps and uses one tab stop; setting allowNone=false selects a valid member.
 */
interface RadioGroup {

    val members: List<Radio>
    val selected: Radio?
    var allowNone: Boolean
    fun add(radio: Radio)
    fun remove(radio: Radio)
    fun select(radio: Radio?)
    fun move(current: Radio, forward: Boolean = true): Radio?
}
