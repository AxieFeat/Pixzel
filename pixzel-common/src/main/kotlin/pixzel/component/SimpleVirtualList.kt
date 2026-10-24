package pixzel.component

import pixzel.Ui
import pixzel.input.PointerPolicy
import pixzel.layout.ChildPlacement
import pixzel.layout.Constraints
import pixzel.layout.LayoutPlan
import pixzel.layout.LayoutPolicy
import pixzel.state.MutableState
import pixzel.state.State
import pixzel.state.getValue
import pixzel.state.stateOf
import pixzel.surface.Size
import pixzel.surface.Vec2
import kotlin.time.Duration

/** Bounded materialization; height/index caches may scale with model size, live nodes do not. */
fun <T, K> virtualList(
    ui: Ui,
    items: List<T>,
    key: (T) -> K,
    estimatedHeight: Float = 24f,
    overscan: Int = 2,
    row: (State<T>) -> Element
): VirtualList<T, K> =
    SimpleVirtualList(ui, items, key, estimatedHeight, overscan, row)

private class HeightIndex(size: Int, initial: Float) {

    private val values = FloatArray(size) { initial }
    private val tree = FloatArray(size + 1)

    init {
        values.indices.forEach { i ->
            var j = i + 1
            while (j < tree.size) {
                tree[j] += initial
                j += j and -j
            }
        }
    }

    fun prefix(end: Int): Float {
        var i = end
        var sum = 0f
        while (i > 0) {
            sum += tree[i]
            i -= i and -i
        }
        return sum
    }

    fun set(index: Int, value: Float) {
        val delta = value - values[index]
        values[index] = value
        var j = index + 1
        while (j < tree.size) {
            tree[j] += delta
            j += j and -j
        }
    }

    fun value(index: Int): Float = values[index]
    fun at(offset: Float): Int {
        if (values.isEmpty()) return 0
        var index = 0
        var sum = 0f
        var bit = Integer.highestOneBit(values.size)
        while (bit != 0) {
            val next = index + bit
            if (next < tree.size && sum + tree[next] <= offset) {
                index = next
                sum += tree[next]
            }
            bit = bit ushr 1
        }
        return index.coerceAtMost(values.lastIndex)
    }
}

internal class SimpleVirtualList<T, K>(
    private val owner: Ui,
    initial: List<T>,
    private val key: (T) -> K,
    private val estimate: Float,
    private val overscan: Int,
    private val row: (State<T>) -> Element
) : SimpleScrollView(""), VirtualList<T, K> {

    private data class Cell<T>(val element: Element, val current: MutableState<T>)

    private var model = initial.toList()
    private var keys = model.map(key)
    private var positions = keys.withIndex().associate { it.value to it.index }
    private var heights = HeightIndex(model.size, estimate)
    private val cells = linkedMapOf<K, Cell<T>>()
    private val states = mutableMapOf<K, MutableMap<StateSlot<*>, MutableState<*>>>()
    private var nextCellId = 0L

    /** This implementation virtualizes vertical rows; ordinary ScrollView supports all axes. */
    override var axes: ScrollAxes
        get() = super.axes
        set(value) {
            require(value == ScrollAxes.VERTICAL) { "VirtualList supports vertical rows only" }
            super.axes = value
        }
    override var items: List<T>
        get() = model
        set(value) {
            owner.assertThread()
            check(!isDisposed) { "VirtualList is disposed" }
            val next = value.toList()
            val nextKeys = next.map(key)
            require(nextKeys.toSet().size == nextKeys.size) { "Duplicate VirtualList key" }
            val oldPositions = positions
            val oldHeights = heights
            val anchorIndex = if (model.isEmpty()) 0 else heights.at(scrollOffset.y)
            val anchorKey = keys.getOrNull(anchorIndex)
            val anchorOffset = scrollOffset.y - heights.prefix(anchorIndex)
            model = next
            keys = nextKeys
            positions = keys.withIndex().associate { it.value to it.index }
            heights = HeightIndex(model.size, estimate)
            keys.forEachIndexed { i, k -> oldPositions[k]?.let { heights.set(i, oldHeights.value(it)) } }
            states.keys.retainAll(keys.toSet())
            extent = Size(extent.width, heights.prefix(model.size))
            invalidate(Invalidation.MEASURE)
            val nextAnchor = positions[anchorKey] ?: anchorIndex.coerceAtMost(model.lastIndex).coerceAtLeast(0)
            scrollOffset = Vec2(0f, heights.prefix(nextAnchor) + anchorOffset)
            cells.forEach { (k, c) -> positions[k]?.let { c.current.value = model[it] } }
        }
    override val materializedCount get() = cells.size

    init {
        require(estimate.isFinite() && estimate > 0 && overscan >= 0)
        require(keys.toSet().size == keys.size) { "Duplicate VirtualList key" }
        semantics = Semantics(Role.LIST)
        extent = Size(0f, heights.prefix(model.size))
        layoutPolicy = LayoutPolicy { children, c, e ->
            val anchor = if (model.isEmpty()) 0 else heights.at(scrollOffset.y)
            val anchorOffset = scrollOffset.y - heights.prefix(anchor)
            val measured = cells.mapNotNull { (k, cell) -> positions[k]?.let { i -> i to cell.element.measure(Constraints(maxWidth = c.maxWidth)) } }
            measured.forEach { (i, p) -> heights.set(i, p.size.height.coerceAtLeast(.01f)) }
            extent = Size(measured.maxOfOrNull { it.second.size.width } ?: c.minWidth, heights.prefix(model.size))
            val viewport = c.constrain(Size(extent.width, extent.height))
            val nextOffset = (heights.prefix(anchor) + anchorOffset).coerceIn(0f, (extent.height - viewport.height).coerceAtLeast(0f))
            if (nextOffset != scrollOffset.y) scrollOffset = Vec2(scrollOffset.x, nextOffset)
            LayoutPlan(viewport, measured.map { (i, p) -> ChildPlacement(p, Vec2(-scrollOffset.x, heights.prefix(i) - scrollOffset.y)) })
        }
    }

    override fun update(delta: Duration): Boolean {
        if (ui == null) return false
        val size = surface.contentBox.size
        val needed = linkedSetOf<K>()
        if (model.isNotEmpty() && size.height > 0) {
            val first = (heights.at(scrollOffset.y) - overscan).coerceAtLeast(0)
            val last = (heights.at(scrollOffset.y + size.height) + overscan).coerceAtMost(model.lastIndex)
            for (i in first..last) needed += keys[i]
        }
        // Pin at most one focused row; keyed state survives ordinary row unmounts separately.
        cells.forEach { (k, c) -> if (generateSequence(owner.focus.focused) { it.parent }.any { it === c.element } && k in positions) needed += k }
        var changed = false
        (cells.keys - needed).toList().forEach { k ->
            cells.remove(k)?.let { remove(it.element) }
            changed = true
        }
        needed.forEach { k ->
            if (k !in cells) {
                val current = stateOf(model[positions.getValue(k)])
                val content = row(current)
                require(content.parent == null && content.ui == null && !content.isDisposed) { "VirtualList row factory must return a live detached element" }
                val element = owner.components.surface("virtual-cell-${++nextCellId}")
                try {
                    element.add(content)
                    add(element)
                } catch (error: Throwable) {
                    try {
                        element.close()
                    } catch (cleanup: Throwable) {
                        if (cleanup !== error) error.addSuppressed(cleanup)
                    }
                    throw error
                }
                cells[k] = Cell(element, current)
                changed = true
            }
        }
        val order = cells.entries.sortedBy { positions[it.key] ?: Int.MAX_VALUE }.map { it.value.element }
        if (children != order) {
            reorder(order)
            changed = true
        }
        return changed
    }

    override fun scrollToKey(key: K) {
        owner.assertThread()
        check(!isDisposed)
        val index = positions[key] ?: error("Unknown VirtualList key '$key'")
        scrollOffset = Vec2(0f, heights.prefix(index))
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V> itemState(key: K, slot: StateSlot<V>, initial: () -> V): MutableState<V> {
        owner.assertThread()
        check(!isDisposed)
        require(key in positions)
        return states.getOrPut(key) { mutableMapOf() }.getOrPut(slot) { stateOf(initial()) } as MutableState<V>
    }

    override fun close() {
        if (isDisposed) return
        try {
            super.close()
        } finally {
            cells.clear()
            states.clear()
            model = emptyList()
            keys = emptyList()
            positions = emptyMap()
            heights = HeightIndex(0, estimate)
        }
    }
}
