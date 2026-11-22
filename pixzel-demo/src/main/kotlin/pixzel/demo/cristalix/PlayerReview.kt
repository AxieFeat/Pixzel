@file:Suppress("MagicNumber")

package pixzel.demo.cristalix

internal data class PlayerReview(
    val author: String, val hours: String, val date: String, val comment: String, val positive: Boolean = false,
    val span: Int = 2, val height: Float = 142f, val expandedHeight: Float = 260f
)
