package pixzel.component

import pixzel.graphics.Color
import pixzel.graphics.Ellipse
import pixzel.graphics.Rectangle
import pixzel.graphics.RoundedRectangle
import pixzel.graphics.Solid
import pixzel.graphics.group
import pixzel.graphics.rgba
import pixzel.input.InputBehavior
import pixzel.input.Key
import pixzel.input.KeyAction
import pixzel.input.KeyEvent
import pixzel.layout.unit.dp
import pixzel.resource.Subscription
import pixzel.style.isEffectivelyEnabled
import pixzel.surface.Insets
import pixzel.surface.Rect
import pixzel.text.TextAlignment

private class ChangeListeners<T> {

    private val listeners = linkedMapOf<Any, (T) -> Unit>()
    fun add(handler: (T) -> Unit): Subscription {
        val key = Any()
        listeners[key] = handler
        return Subscription { listeners.remove(key) }
    }

    fun notify(value: T) {
        var failure: Throwable? = null
        listeners.toList().forEach { (key, callback) ->
            if (listeners[key] === callback) {
                try {
                    callback(value)
                } catch (error: Throwable) {
                    failure = combine(failure, error)
                }
            }
        }
        failure?.let { throw it }
    }

    fun clear() = listeners.clear()
}

internal class SimpleCheckbox(id: String, factory: ComponentFactory) : SimpleButton(id, factory), Checkbox {

    private val changes = ChangeListeners<CheckState>()
    override var checkState: CheckState = CheckState.UNCHECKED
        set(value) {
            if (field == value) return
            invalidate(Invalidation.SEMANTICS)
            field = value
            setInteractionState(state.copy(checked = value == CheckState.CHECKED, indeterminate = value == CheckState.INDETERMINATE))
            semantics = semantics.copy(
                checked = when (value) {
                    CheckState.CHECKED -> true
                    CheckState.UNCHECKED -> false
                    else -> null
                },
                value = if (value == CheckState.INDETERMINATE) "mixed" else (value == CheckState.CHECKED).toString()
            )
            changes.notify(value)
        }

    init {
        configureSelection(this, false)
        semantics = semantics.copy(role = Role.CHECKBOX, checked = false)
    }

    override fun setLabel(value: String) = super.setLabel(value).also { it.horizontalAlignment = TextAlignment.START }
    override fun activate() {
        if (!canActivate()) return
        checkState = if (checkState == CheckState.CHECKED) CheckState.UNCHECKED else CheckState.CHECKED
        super.activate()
    }

    private fun canActivate() = isEffectivelyEnabled && ui != null
    override fun onCheckChange(handler: (CheckState) -> Unit) = changes.add(handler)
    override fun close() {
        if (isDisposed) return
        changes.clear()
        super.close()
    }
}

internal class SimpleToggle(id: String, factory: ComponentFactory) : SimpleButton(id, factory), Toggle {

    private val changes = ChangeListeners<Boolean>()
    override var checked: Boolean = false
        set(value) {
            if (field == value) return
            invalidate(Invalidation.SEMANTICS)
            field = value
            setInteractionState(state.copy(checked = value))
            semantics = semantics.copy(checked = value, value = value.toString())
            changes.notify(value)
        }

    init {
        layout.padding = Insets(left = 52f, right = 8f)
        semantics = semantics.copy(role = Role.CHECKBOX, checked = false)
        background = Painter { canvas, context ->
            val center = context.bounds.height / 2
            canvas.fill(
                RoundedRectangle(10f), Rect(5f, center - 10f, 36f, 20f),
                Solid(if (context.state.disabled) 0x555555FF.rgba else if (checked) 0x517BEAFF.rgba else 0x515868FF.rgba)
            )
            canvas.fill(Ellipse, Rect(if (checked) 23f else 7f, center - 8f, 16f, 16f), Solid(Color.WHITE))
        }
    }

    override fun setLabel(value: String) = super.setLabel(value).also { it.horizontalAlignment = TextAlignment.START }
    override fun activate() {
        if (!isEffectivelyEnabled || ui == null) return
        checked = !checked
        super.activate()
    }

    override fun onCheckedChange(handler: (Boolean) -> Unit) = changes.add(handler)
    override fun close() {
        if (isDisposed) return
        changes.clear()
        super.close()
    }
}

internal class SimpleRadio(id: String, factory: ComponentFactory) : SimpleButton(id, factory), Radio {

    private val changes = ChangeListeners<Boolean>()
    private var selection = false
    private var membership: RadioGroup? = null
    override var selected: Boolean
        get() = selection
        set(value) {
            val owner = membership
            if (owner != null && (value && owner.selected !== this || !value && owner.selected === this)) {
                owner.select(if (value) this else null)
                return
            }
            if (selection == value) return
            invalidate(Invalidation.SEMANTICS)
            selection = value
            setInteractionState(state.copy(selected = value, checked = value))
            semantics = semantics.copy(selected = value, checked = value)
            changes.notify(value)
        }
    override var group: RadioGroup?
        get() = membership
        set(value) {
            if (membership === value) return
            invalidate(Invalidation.INPUT)
            val old = membership
            membership = null
            old?.remove(this)
            membership = value
            value?.add(this)
            if (value == null) focusable = true
        }

    init {
        configureSelection(this, true)
        semantics = semantics.copy(role = Role.RADIO, checked = false)
        val action = behavior
        behavior = InputBehavior { element, event ->
            val key = event.event as? KeyEvent
            val owner = group
            if (key != null && owner != null &&
                key.key in setOf(Key.LEFT, Key.RIGHT, Key.UP, Key.DOWN, Key.HOME, Key.END)
            ) {
                if (key.action != KeyAction.UP) {
                    val target = when (key.key) {
                        Key.HOME -> owner.members.firstOrNull { it.enabled && !it.isDisposed && it.visibility == Visibility.VISIBLE && it.ui === ui }
                        Key.END -> owner.members.lastOrNull { it.enabled && !it.isDisposed && it.visibility == Visibility.VISIBLE && it.ui === ui }
                        else -> owner.move(this, key.key == Key.RIGHT || key.key == Key.DOWN)
                    }
                    target?.let {
                        owner.select(it)
                        ui?.focus?.request(it)
                    }
                }
                event.consume()
            } else action?.handle(element, event)
        }
    }

    override fun setLabel(value: String) = super.setLabel(value).also { it.horizontalAlignment = TextAlignment.START }
    override fun activate() {
        if (!isEffectivelyEnabled || ui == null) return
        selected = true
        super.activate()
    }

    override fun onSelectedChange(handler: (Boolean) -> Unit) = changes.add(handler)
    override fun close() {
        if (isDisposed) return
        changes.clear()
        try {
            group = null
        } finally {
            super.close()
        }
    }
}

private fun configureSelection(element: Button, radio: Boolean) {
    element.layout.width = 180.dp
    element.layout.height = 36.dp
    element.layout.padding = Insets(left = 34f, right = 8f)
    element.background = Painter { canvas, context ->
        val bounds = Rect(7f, (context.bounds.height - 20f) / 2, 20f, 20f)
        val shape = if (radio) Ellipse else RoundedRectangle(4f)
        canvas.fill(
            shape,
            bounds,
            Solid(if (context.state.disabled) 0x4A4A4AFF.rgba else if (context.state.checked || context.state.indeterminate) 0x517BEAFF.rgba else 0x303947FF.rgba)
        )
        canvas.stroke(shape, bounds, Solid(0xB0B8C8FF.rgba), 1f)
        if (context.state.checked) {
            if (radio) canvas.fill(Ellipse, Rect(bounds.x + 5f, bounds.y + 5f, 10f, 10f), Solid(Color.WHITE))
            else canvas.fill(RoundedRectangle(1f), Rect(bounds.x + 5f, bounds.y + 5f, 10f, 10f), Solid(Color.WHITE))
        } else if (context.state.indeterminate) canvas.fill(Rectangle, Rect(bounds.x + 4f, bounds.y + 9f, 12f, 2f), Solid(Color.WHITE))
    }
}

fun radioGroup(allowNone: Boolean = false): RadioGroup = SimpleRadioGroup(allowNone)

private class SimpleRadioGroup(initialAllowNone: Boolean) : RadioGroup {

    private val radios = mutableListOf<Radio>()
    private var selectionVersion = 0L
    override val members: List<Radio> get() = radios.toList()
    override var selected: Radio? = null
        private set
    override var allowNone: Boolean = initialAllowNone
        set(value) {
            checkThreads()
            field = value
            if (!value && selected == null) radios.firstOrNull(::eligible)?.let(::select)
        }

    private fun eligible(radio: Radio) = radio.enabled && !radio.isDisposed && radio.visibility == Visibility.VISIBLE
    private fun checkThreads() {
        radios.forEach { it.ui?.assertThread() }
    }

    override fun add(radio: Radio) {
        checkThreads()
        radio.ui?.assertThread()
        require(!radio.isDisposed) { "Disposed radio cannot join a group" }
        if (radio.group !== this) {
            radio.group = this
            return
        }
        if (radio in radios) return
        radios += radio
        if (radio.selected || selected == null && !allowNone && eligible(radio)) select(radio) else updateFocus()
    }

    override fun remove(radio: Radio) {
        checkThreads()
        radio.ui?.assertThread()
        if (radio.group === this) {
            radio.group = null
            return
        }
        val index = radios.indexOf(radio)
        if (index < 0) return
        radios.removeAt(index)
        var failure: Throwable? = null
        if (selected === radio) {
            selected = null
            try {
                radio.selected = false
            } catch (error: Throwable) {
                failure = combine(failure, error)
            }
            val candidate = radios.drop(index).firstOrNull(::eligible) ?: radios.take(index).lastOrNull(::eligible)
            if (!allowNone && candidate != null) try {
                select(candidate)
            } catch (error: Throwable) {
                failure = combine(failure, error)
            }
        }
        updateFocus()
        failure?.let { throw it }
    }

    override fun select(radio: Radio?) {
        checkThreads()
        require(radio == null || radio in radios) { "Radio must be a member of this group" }
        require(radio != null || allowNone || radios.none(::eligible)) { "This radio group requires a selection" }
        if (selected === radio) {
            updateFocus()
            return
        }
        selected = radio
        val version = ++selectionVersion
        var failure: Throwable? = null
        for (member in radios.toList()) {
            if (version != selectionVersion) break
            if (!member.isDisposed && member.group === this) {
                try {
                    member.selected = member === radio
                } catch (error: Throwable) {
                    failure = combine(failure, error)
                }
            }
        }
        updateFocus()
        failure?.let { throw it }
    }

    private fun updateFocus() {
        val tabStop = selected?.takeIf(::eligible) ?: radios.firstOrNull(::eligible)
        radios.forEach { if (!it.isDisposed) it.focusable = it === tabStop }
    }

    override fun move(current: Radio, forward: Boolean): Radio? {
        val candidates = radios.filter { eligible(it) && it.ui != null && it.ui === current.ui }
        if (candidates.isEmpty()) return null
        val currentIndex = candidates.indexOf(current)
        val index = if (currentIndex < 0) 0 else (currentIndex + if (forward) 1 else candidates.size - 1) % candidates.size
        return candidates[index].also(::select)
    }
}

private fun combine(previous: Throwable?, next: Throwable): Throwable {
    if (previous == null) return next
    if (previous !== next) previous.addSuppressed(next)
    return previous
}
