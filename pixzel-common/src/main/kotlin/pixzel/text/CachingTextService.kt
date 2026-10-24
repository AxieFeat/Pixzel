package pixzel.text

import java.util.LinkedHashMap


data class TextCacheStatistics(val hits: Long, val misses: Long, val entries: Int, val estimatedBytes: Long)

/**
 * UI-thread LRU. Both entry count and an approximate retained-data byte budget are enforced.
 * The delegate remains borrowed; clear does not dispose font resources.
 */
class CachingTextService private constructor(
    private val delegate: TextService,
    private val maxEntries: Int,
    private val maxBytes: Long
) : TextService {

    private data class Entry(val layout: TextLayout, val bytes: Long)

    private val cache = LinkedHashMap<TextRequest, Entry>(16, 0.75f, true)
    private var bytes = 0L
    private var hits = 0L
    private var misses = 0L
    override val capabilities get() = delegate.capabilities
    val statistics get() = TextCacheStatistics(hits, misses, cache.size, bytes)

    override fun canDisplay(text: String, style: TextStyle) = delegate.canDisplay(text, style)

    override fun layout(request: TextRequest): TextLayout {
        cache[request]?.let {
            hits++
            return it.layout
        }
        misses++
        val layout = delegate.layout(request)
        check(layout.providerId == capabilities.providerId) { "Text provider returned incompatible drawing data" }
        val estimate = 192L + request.text.length * 2L + request.spans.size * 96L + layout.clusters.size * 64L +
                layout.lines.size * 64L + layout.runs.sumOf { 64L + it.glyphs.size * 48L }
        if (maxEntries == 0 || estimate > maxBytes) return layout
        cache[request] = Entry(layout, estimate)
        bytes += estimate
        val iterator = cache.entries.iterator()
        while (cache.size > maxEntries || bytes > maxBytes) {
            val entry = iterator.next()
            bytes -= entry.value.bytes
            iterator.remove()
        }
        return layout
    }

    fun clear() {
        cache.clear()
        bytes = 0
    }

    companion object {

        fun of(delegate: TextService, maxEntries: Int = 512, maxBytes: Long = 8L * 1024 * 1024): CachingTextService {
            require(maxEntries >= 0 && maxBytes >= 0)
            return CachingTextService(delegate, maxEntries, maxBytes)
        }
    }
}
