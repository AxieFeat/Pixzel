package pixzel.text

data class TextRange(val start: Int, val end: Int) {
    init {
        require(start >= 0 && end >= start)
    }
}

/**
 * Segments extended graphemes; ranges use UTF-16 offsets and cover the complete input.
 */
fun interface GraphemeSegmenter {

    fun segment(text: String): List<TextRange>
}
