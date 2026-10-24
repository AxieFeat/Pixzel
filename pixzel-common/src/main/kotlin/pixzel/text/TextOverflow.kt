package pixzel.text

/**
 * Standard policies are ordinary public extension strategies, independent of Text/Button.
 */
object TextOverflow {

    val CLIP = TextOverflowPolicy { c -> TextOverflowResult(c.full, true, overflows(c.full, c)) }
    val VISIBLE = TextOverflowPolicy { c -> TextOverflowResult(c.full, false, overflows(c.full, c)) }
    val ELLIPSIS: TextOverflowPolicy = SimpleEllipsisPolicy(EllipsisPosition.END)
    val ELLIPSIS_START: TextOverflowPolicy = SimpleEllipsisPolicy(EllipsisPosition.START)
    val ELLIPSIS_MIDDLE: TextOverflowPolicy = SimpleEllipsisPolicy(EllipsisPosition.MIDDLE)
    val WRAP = TextOverflowPolicy { c ->
        val wrapped = c.service.layout(c.request.copy(width = c.viewport.width, wrap = true))
        if (!wrapped.isTruncated || wrapped.lines.isEmpty()) {
            TextOverflowResult(wrapped, true, overflows(wrapped, c))
        } else {
            val finalLine = wrapped.lines.last()
            val preceding = recompose(c.request, listOf(SourcePiece(0, finalLine.start)))
            val finalRequest = recompose(c.request, listOf(SourcePiece(finalLine.start, finalLine.end))).copy(
                width = Float.POSITIVE_INFINITY, wrap = false, maxLines = Int.MAX_VALUE
            )
            val finalFull = c.service.layout(finalRequest)
            // Force truncation when hidden lines exist, even if the retained final line fits.
            val truncated = ellipsize(c.copy(request = finalRequest, full = finalFull), EllipsisPosition.END, force = true)
            val joinedSpans = preceding.spans + truncated.request.spans.map {
                TextSpan(it.range.start + preceding.text.length, it.range.end + preceding.text.length, it.style)
            }
            val result = c.service.layout(
                c.request.copy(
                    text = preceding.text + truncated.layout.text,
                    spans = TextSpans.from(joinedSpans), width = c.viewport.width, wrap = true
                )
            )
            TextOverflowResult(result, true, true)
        }
    }

    private fun overflows(layout: TextLayout, c: TextOverflowContext) = layout.isTruncated ||
            layout.size.width > c.viewport.width + c.epsilon || layout.size.height > c.viewport.height + c.epsilon
}

private enum class EllipsisPosition {
    START,
    END,
    MIDDLE
}

private class SimpleEllipsisPolicy(private val position: EllipsisPosition) : TextOverflowPolicy {

    override fun resolve(context: TextOverflowContext): TextOverflowResult {
        val full = context.full
        if (full.size.width <= context.viewport.width + context.epsilon && !full.isTruncated) {
            return TextOverflowResult(full, true, false)
        }
        return TextOverflowResult(ellipsize(context, position).layout, true, true)
    }
}

private data class OverflowCandidate(val request: TextRequest, val layout: TextLayout)
private interface TextPiece
private data class SourcePiece(val start: Int, val end: Int) : TextPiece
private data class MarkerPiece(val text: String) : TextPiece

/**
 * Rebase source span ranges when truncation creates a different shaping context.
 */
private fun recompose(request: TextRequest, pieces: List<TextPiece>): TextRequest {
    val text = StringBuilder()
    val spans = mutableListOf<TextSpan>()
    for (piece in pieces) when (piece) {
        is MarkerPiece -> text.append(piece.text)
        is SourcePiece -> {
            val offset = text.length
            text.append(request.text, piece.start, piece.end)
            request.spans.forEach { span ->
                val start = maxOf(span.range.start, piece.start)
                val end = minOf(span.range.end, piece.end)
                if (end > start) spans += TextSpan(offset + start - piece.start, offset + end - piece.start, span.style)
            }
        }
    }
    return request.copy(text = text.toString(), spans = TextSpans.from(spans))
}

private fun ellipsize(c: TextOverflowContext, requestedPosition: EllipsisPosition, force: Boolean = false): OverflowCandidate {
    // A shortened AUTO substring can have a different first strong character. Preserve the
    // original paragraph base so logical marker placement keeps its intended visual edge.
    val request = c.request.copy(
        width = Float.POSITIVE_INFINITY, wrap = false, maxLines = Int.MAX_VALUE,
        direction = if (c.request.direction == TextDirection.AUTO) c.full.direction else c.request.direction
    )
    var marker = c.marker
    if (marker == "\u2026" && !c.service.canDisplay(marker, request.style)) marker = "..."
    val markerRequest = request.copy(text = marker, spans = TextSpans.EMPTY)
    val markerLayout = c.service.layout(markerRequest)
    if (c.viewport.width <= 0f || markerLayout.size.width > c.viewport.width + c.epsilon) {
        return if (c.tooNarrow == TooNarrowPolicy.EMPTY) {
            val empty = request.copy(text = "", spans = TextSpans.EMPTY)
            OverflowCandidate(empty, c.service.layout(empty))
        } else OverflowCandidate(markerRequest, markerLayout)
    }
    val ranges = UnicodeGraphemeSegmenter.segment(request.text)
    // The provider places the logical marker on the proper visual edge for RTL.
    val position = requestedPosition
    fun candidate(count: Int): TextRequest = when (position) {
        EllipsisPosition.END -> recompose(request, listOf(SourcePiece(0, if (count == 0) 0 else ranges[count - 1].end), MarkerPiece(marker)))
        EllipsisPosition.START -> recompose(
            request,
            listOf(MarkerPiece(marker), SourcePiece(if (count == 0) request.text.length else ranges[ranges.size - count].start, request.text.length))
        )

        EllipsisPosition.MIDDLE -> {
            val leftCount = (count + 1) / 2
            val rightCount = count / 2
            recompose(
                request, listOf(
                    SourcePiece(0, if (leftCount == 0) 0 else ranges[leftCount - 1].end), MarkerPiece(marker),
                    SourcePiece(if (rightCount == 0) request.text.length else ranges[ranges.size - rightCount].start, request.text.length)
                )
            )
        }
    }

    var low = 0
    var high = (ranges.size - if (force) 0 else 1).coerceAtLeast(0)
    var best = OverflowCandidate(markerRequest, markerLayout)
    // Each candidate is reshaped: old advances are never summed to estimate a new context.
    while (low <= high) {
        val count = low + (high - low) / 2
        val candidate = candidate(count)
        val shaped = c.service.layout(candidate)
        if (shaped.size.width <= c.viewport.width + c.epsilon) {
            best = OverflowCandidate(candidate, shaped)
            low = count + 1
        } else high = count - 1
    }
    return best
}
