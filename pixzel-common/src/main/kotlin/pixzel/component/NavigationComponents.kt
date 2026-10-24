package pixzel.component

import pixzel.Ui
import pixzel.input.InputBehavior
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.layout.columnLayout
import pixzel.layout.rowLayout
import pixzel.layout.unit.dp
import pixzel.layout.unit.fillAvailable
import pixzel.layout.unit.wrapContent
import pixzel.resource.Subscription
import pixzel.surface.Insets
import pixzel.text.TextAlignment
import kotlin.math.max
import kotlin.time.Duration

internal class SimpleTabs(id: String, private val factory: ComponentFactory) : AbstractElement(id), Tabs {
    private data class Tab(val key: String, val header: Button, val content: Element)

    private val tabs = mutableListOf<Tab>()
    private val headerViewport = factory.scrollView("headers")
    private val headers = factory.surface("header-row")
    private val pages = factory.surface("pages")
    private val listeners = linkedMapOf<Any, (String?) -> Unit>()
    private var revealHeader = false
    private var lastHeaderWidth = -1f
    override val keys: List<String> get() = tabs.map { it.key }
    override var selectedKey: String? = null
        set(value) {
            require(value == null && tabs.isEmpty() || tabs.any { it.key == value }) { "Unknown tab key '$value'" }
            if (field == value) return
            invalidate(Invalidation.SEMANTICS)
            val focused = ui?.focus?.focused
            val oldPage = tabs.firstOrNull { it.key == field }?.content
            val focusWasInside = focused != null && oldPage != null && generateSequence(focused) { it.parent }.any { it === oldPage }
            field = value
            revealHeader = true
            tabs.forEach { tab ->
                val selected = tab.key == value
                tab.content.visibility = if (selected) Visibility.VISIBLE else Visibility.COLLAPSED
                tab.header.focusable = selected
                tab.header.setInteractionState(tab.header.state.copy(selected = selected))
                tab.header.semantics = tab.header.semantics.copy(role = Role.TAB, selected = selected)
            }
            semantics = semantics.copy(value = value.orEmpty())
            tabs.firstOrNull { it.key == value }?.header?.let {
                if (headerViewport.surface.isAttached && it.surface.isAttached) headerViewport.scrollIntoView(it)
            }
            if (focusWasInside) tabs.firstOrNull { it.key == value }?.header?.let { ui?.focus?.request(it) }
            listeners.toList().forEach { (key, listener) -> if (!isDisposed && listeners[key] === listener) listener(value) }
        }

    init {
        layout.width = fillAvailable
        layout.height = fillAvailable
        layoutPolicy = columnLayout()
        headerViewport.layout.width = fillAvailable
        headerViewport.layout.height = 36.dp
        headerViewport.axes = ScrollAxes.HORIZONTAL
        headers.layout.width = wrapContent
        headers.layout.height = 36.dp
        headers.layoutPolicy = rowLayout()
        pages.layout.width = fillAvailable
        pages.layout.height = fillAvailable
        headerViewport.add(headers)
        add(headerViewport)
        add(pages)
    }

    override fun addTab(key: String, label: String, content: Element) {
        invalidate(Invalidation.MEASURE)
        require(key.isNotBlank() && tabs.none { it.key == key }) { "Tab keys must be nonblank and unique" }
        require(content.parent == null && content.ui == null && !content.isDisposed) { "Tab content must be detached" }
        val header = factory.button("tab-$key")
        header.layout.width = 120.dp
        header.layout.height = 36.dp
        header.setLabel(label)
        header.semantics = header.semantics.copy(role = Role.TAB)
        val page = factory.surface("page-$key")
        page.layout.width = fillAvailable
        page.layout.height = fillAvailable
        page.add(content)
        val entry = Tab(key, header, page)
        tabs += entry
        headers.add(header)
        pages.add(page)
        header.resources.own(header.onClick { selectedKey = key })
        val activation = header.behavior
        header.behavior = InputBehavior { element, event ->
            val input = event.event as? KeyEvent
            if (input != null && input.key in setOf(Key.LEFT, Key.RIGHT, Key.HOME, Key.END)) {
                if (input.action != KeyAction.UP) {
                    val eligible = tabs.filter { it.header.enabled && !it.header.isDisposed }
                    val current = eligible.indexOf(entry)
                    val index = when (input.key) {
                        Key.HOME -> 0
                        Key.END -> eligible.lastIndex
                        Key.LEFT -> (max(0, current) + eligible.size - 1) % eligible.size
                        else -> (max(0, current) + 1) % eligible.size
                    }
                    eligible.getOrNull(index)?.let {
                        selectedKey = it.key
                        ui?.focus?.request(it.header)
                    }
                }
                event.consume()
            } else activation?.handle(element, event)
        }
        page.visibility = Visibility.COLLAPSED
        header.focusable = false
        if (selectedKey == null) selectedKey = key
    }

    override fun removeTab(key: String) {
        invalidate(Invalidation.MEASURE)
        val index = tabs.indexOfFirst { it.key == key }
        if (index < 0) return
        val entry = tabs.removeAt(index)
        val focused = ui?.focus?.focused
        val restoreFocus = focused != null && (generateSequence(focused) { it.parent }.any { it === entry.content || it === entry.header })
        headers.remove(entry.header)
        pages.remove(entry.content)
        if (selectedKey == key) selectedKey = tabs.getOrNull(index)?.key ?: tabs.lastOrNull()?.key
        if (restoreFocus) tabs.firstOrNull { it.key == selectedKey }?.header?.let { ui?.focus?.request(it) }
    }

    override fun update(delta: Duration): Boolean {
        val width = headerViewport.surface.contentBox.width
        if (!revealHeader && width == lastHeaderWidth) return false
        lastHeaderWidth = width
        revealHeader = false
        val header = tabs.firstOrNull { it.key == selectedKey }?.header ?: return false
        if (!header.surface.isAttached || !headerViewport.surface.isAttached) return false
        val before = headerViewport.scrollOffset
        headerViewport.scrollIntoView(header)
        return before != headerViewport.scrollOffset
    }

    override fun onSelectionChange(handler: (String?) -> Unit): Subscription {
        invalidate(Invalidation.INPUT)
        val token = Any()
        listeners[token] = handler
        return Subscription { listeners.remove(token) }
    }

    override fun close() {
        if (isDisposed) return
        listeners.clear()
        super.close()
    }
}

/** One open menu owns its focus scope and row callbacks. The anchor owns its popup subtree. */
class Menu<T> private constructor(
    private val ui: Ui,
    private val anchor: Element,
    private val items: List<MenuItem<T>>,
    private val initiallySelected: String?,
    private val emptyContent: (() -> Element)?,
    private val onSelect: (MenuItem<T>) -> Unit
) : AutoCloseable {

    private val popup = Popup.of(ui, anchor)
    val element: Element get() = popup.element
    val isClosed: Boolean get() = closed || element.isDisposed
    private var closed = false
    private val rows = linkedMapOf<String, Button>()
    private var scope: Subscription? = null

    private fun build() {
        element.semantics = Semantics(role = Role.MENU)
        element.layout.width = max(180f, anchor.surface.bounds.width).dp
        element.layout.padding = Insets.all(4.dp)
        val viewport = ui.components.scrollView("menu-viewport")
        viewport.layout.width = fillAvailable
        viewport.layout.height = wrapContent
        val rootHeight = ui.root.surface.contentBox.height.takeIf { it > 0f }
            ?: ((ui.host.metrics.logicalSize.height - ui.host.metrics.safeInsets.vertical) / ui.settings.uiScale)
        viewport.layout.maxHeight = (rootHeight - 8f).coerceAtLeast(0f).dp
        val column = ui.components.surface("menu-items")
        column.layout.width = fillAvailable
        column.layout.height = wrapContent
        column.layoutPolicy = columnLayout()
        viewport.add(column)
        element.add(viewport)
        if (items.isEmpty()) {
            val empty = emptyContent?.invoke() ?: ui.components.text().apply { value = "No items" }
            require(empty.parent == null && empty.ui == null && !empty.isDisposed) { "Empty menu content must be detached" }
            column.add(empty)
        }
        for (item in items) {
            val button = ui.components.button("item-${item.key}")
            button.layout.width = fillAvailable
            button.layout.height = 36.dp
            button.enabled = item.enabled
            val itemContent = item.content
            if (itemContent != null) {
                val content = itemContent.invoke()
                require(content.parent == null && content.ui == null && !content.isDisposed) { "Menu item content must be detached" }
                button.setContent(content)
            } else button.setLabel(item.label).horizontalAlignment = TextAlignment.START
            button.semantics = button.semantics.copy(role = Role.LIST_ITEM, label = item.label, value = item.key)
            column.add(button)
            rows[item.key] = button
            button.resources.own(button.onClick { choose(item) })
            val activation = button.behavior
            button.behavior = InputBehavior { target, event ->
                val key = event.event as? KeyEvent
                if (key != null && key.key in setOf(Key.UP, Key.DOWN, Key.HOME, Key.END)) {
                    if (key.action != KeyAction.UP) {
                        val enabled = items.filter { it.enabled }
                        val current = enabled.indexOfFirst { it.key == item.key }
                        val index = when (key.key) {
                            Key.HOME -> 0
                            Key.END -> enabled.lastIndex
                            Key.UP -> (max(0, current) + enabled.size - 1) % enabled.size
                            else -> (max(0, current) + 1) % enabled.size
                        }
                        enabled.getOrNull(index)?.let { candidate ->
                            rows[candidate.key]?.let { row ->
                                ui.focus.request(row)
                                viewport.scrollIntoView(row)
                            }
                        }
                    }
                    event.consume()
                } else activation?.handle(target, event)
            }
        }
        element.focusable = items.none { it.enabled }
        scope = ui.focus.pushScope(element)
        element.resources.own(checkNotNull(scope))
        val initial = rows[initiallySelected]?.takeIf { it.enabled } ?: rows.values.firstOrNull { it.enabled }
        if (initial != null) ui.focus.request(initial) else ui.focus.request(element)
    }

    private fun choose(item: MenuItem<T>) {
        if (isClosed || !item.enabled || anchor.ui !== ui) return
        try {
            onSelect(item)
        } finally {
            close()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        popup.close()
    }

    companion object {

        fun <T> of(
            ui: Ui, anchor: Element, items: List<MenuItem<T>>, selectedKey: String? = null,
            emptyContent: (() -> Element)? = null, onSelect: (MenuItem<T>) -> Unit
        ): Menu<T> {
            ui.assertThread()
            require(anchor.ui === ui && !anchor.isDisposed) { "Menu anchor must be attached to this Ui" }
            require(items.map { it.key }.distinct().size == items.size) { "Menu keys must be unique" }
            val menu = Menu(ui, anchor, items.toList(), selectedKey, emptyContent, onSelect)
            try {
                menu.build()
                return menu
            } catch (failure: Throwable) {
                try {
                    menu.close()
                } catch (cleanup: Throwable) {
                    if (cleanup !== failure) failure.addSuppressed(cleanup)
                }
                throw failure
            }
        }
    }
}

fun <T> dropdown(ui: Ui, items: List<MenuItem<T>> = emptyList(), id: String = "", configure: Dropdown<T>.() -> Unit = {}): Dropdown<T> {
    ui.assertThread()
    return SimpleDropdown<T>(id, ui.components).apply {
        this.items = items
        configure()
    }
}

private class SimpleDropdown<T>(id: String, factory: ComponentFactory) : SimpleButton(id, factory), Dropdown<T> {

    private val listeners = linkedMapOf<Any, (MenuItem<T>?) -> Unit>()
    private var menu: Menu<T>? = null
    private var model = emptyList<MenuItem<T>>()
    override var items: List<MenuItem<T>>
        get() = model
        set(value) {
            require(value.map { it.key }.distinct().size == value.size) { "Dropdown keys must be unique" }
            invalidate(Invalidation.SEMANTICS)
            val previous = model.firstOrNull { it.key == selectedKey }
            closeMenu()
            model = value.toList()
            if (selectedKey != null && model.none { it.key == selectedKey }) selectedKey = null
            else {
                refreshField()
                if (previous != selectedItem()) notifySelection()
            }
        }
    override var selectedKey: String? = null
        set(value) {
            require(value == null || model.any { it.key == value }) { "Unknown dropdown key '$value'" }
            if (field == value) return
            invalidate(Invalidation.SEMANTICS)
            field = value
            refreshField()
            notifySelection()
        }
    override var placeholder: String = "Select..."
        set(value) {
            if (field != value) {
                invalidate(Invalidation.MEASURE)
                field = value
                refreshField()
            }
        }
    override var fieldContent: ((MenuItem<T>?) -> Element)? = null
        set(value) {
            invalidate(Invalidation.MEASURE)
            field = value
            refreshField()
        }
    override var emptyContent: (() -> Element)? = null
        set(value) {
            invalidate()
            field = value
            closeMenu()
        }

    init {
        refreshField()
    }

    private fun selectedItem() = model.firstOrNull { it.key == selectedKey }
    private fun refreshField() {
        val item = selectedItem()
        val custom = fieldContent
        if (custom == null) setLabel(item?.label ?: placeholder) else {
            val content = custom(item)
            require(content.parent == null && content.ui == null && !content.isDisposed) { "Dropdown field content must be detached" }
            setContent(content)
        }
        semantics = semantics.copy(
            value = item?.label.orEmpty(), actions = semantics.actions +
                mapOf("expand" to { openMenu() }, "collapse" to { closeMenu() })
        )
    }

    private fun notifySelection() {
        val item = selectedItem()
        listeners.toList().forEach { (key, listener) -> if (!isDisposed && listeners[key] === listener) listener(item) }
    }

    override fun onSelectionChange(handler: (MenuItem<T>?) -> Unit): Subscription {
        invalidate(Invalidation.INPUT)
        val token = Any()
        listeners[token] = handler
        return Subscription { listeners.remove(token) }
    }

    override fun activate() {
        if (!enabled || isDisposed || ui == null) return
        if (menu?.isClosed == false) closeMenu() else openMenu()
        super.activate()
    }

    override fun openMenu() {
        val owner = ui ?: return
        if (!enabled || isDisposed || menu?.isClosed == false) return
        menu = Menu.of(owner, this, model, selectedKey, emptyContent) { selected -> selectedKey = selected.key }
    }

    override fun closeMenu() {
        val previous = menu
        menu = null
        previous?.close()
    }

    override fun attach(parent: Element?, ui: Ui?) {
        if (ui !== this.ui) closeMenu()
        super.attach(parent, ui)
    }

    override fun close() {
        if (isDisposed) return
        listeners.clear()
        try {
            closeMenu()
        } finally {
            super.close()
        }
    }
}
