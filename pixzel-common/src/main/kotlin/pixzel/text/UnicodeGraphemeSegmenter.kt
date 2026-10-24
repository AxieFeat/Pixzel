package pixzel.text

/** JVM implementation using Java 21's Unicode extended-grapheme matcher (UAX #29). */
object UnicodeGraphemeSegmenter : GraphemeSegmenter {

    private val grapheme = Regex("\\X")
    override fun segment(text: String): List<TextRange> = grapheme.findAll(text)
        .map { TextRange(it.range.first, it.range.last + 1) }.toList()
}

fun GraphemeSegmenter.previousBoundary(text: String, index: Int): Int {
    require(index in 0..text.length)
    return segment(text).lastOrNull { it.start < index }?.start ?: 0
}

fun GraphemeSegmenter.nextBoundary(text: String, index: Int): Int {
    require(index in 0..text.length)
    return segment(text).firstOrNull { it.end > index }?.end ?: text.length
}
