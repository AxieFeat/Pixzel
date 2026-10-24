package pixzel.text

import pixzel.surface.Size

enum class TooNarrowPolicy {
    CLIP_MARKER,
    EMPTY
}

data class TextOverflowContext(
    val service: TextService,
    val request: TextRequest,
    val full: TextLayout,
    val viewport: Size,
    val marker: String = "\u2026",
    val tooNarrow: TooNarrowPolicy = TooNarrowPolicy.CLIP_MARKER,
    val epsilon: Float = 0.001f
) {

    init {
        require(epsilon.isFinite() && epsilon >= 0)
    }
}

data class TextOverflowResult(val layout: TextLayout, val clip: Boolean, val overflowed: Boolean)

/** Static overflow is independent of component type and time-dependent reveal. */
fun interface TextOverflowPolicy {

    fun resolve(context: TextOverflowContext): TextOverflowResult
}
