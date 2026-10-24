package pixzel.component

import pixzel.resource.Subscription

/** Retained keyed pages; selecting a tab collapses other pages without disposing their state. */
interface Tabs : Element {

    var selectedKey: String?
    val keys: List<String>
    fun addTab(key: String, label: String, content: Element)
    fun removeTab(key: String)
    fun onSelectionChange(handler: (String?) -> Unit): Subscription

    companion object {

        fun of(components: ComponentFactory, id: String = "") = components.tabs(id)
    }
}

data class MenuItem<T>(
    val key: String, val label: String, val value: T, val enabled: Boolean = true,
    val content: (() -> Element)? = null
) {

    init {
        require(key.isNotBlank())
    }
}

interface Dropdown<T> : Button {

    var items: List<MenuItem<T>>
    var selectedKey: String?
    var placeholder: String
    var fieldContent: ((MenuItem<T>?) -> Element)?
    var emptyContent: (() -> Element)?
    fun onSelectionChange(handler: (MenuItem<T>?) -> Unit): Subscription
    fun openMenu()
    fun closeMenu()
}
