package pixzel.component

import pixzel.graphics.Color
import pixzel.state.Binding
import pixzel.text.TextAlignment
import pixzel.text.TextDirection
import pixzel.text.TextOverflowPolicy
import pixzel.text.TextRange
import pixzel.text.TextRevealBehavior
import pixzel.text.TextSpans
import pixzel.text.TextStyle
import pixzel.text.VerticalAlignment

interface Text : Element {

    var value: String
    var style: TextStyle
    var spans: TextSpans
    var direction: TextDirection
    var locale: String
    var color: Color
    var overflow: TextOverflowPolicy
    var reveal: TextRevealBehavior
    var ellipsisMarker: String
    var wrap: Boolean
    var maxLines: Int
    var horizontalAlignment: TextAlignment
    var verticalAlignment: VerticalAlignment
    fun bindValue(binding: Binding<String>)

    companion object {

        fun of(components: ComponentFactory, id: String = "") = components.text(id)
    }
}

/**
 * Selection and caret indices are UTF-16 grapheme boundaries.
 */
interface TextField : Text {

    val composition: TextComposition?
    var selectionStart: Int
    var selectionEnd: Int
    var readOnly: Boolean
    var password: Boolean
    var placeholder: String
    var validator: (String) -> Boolean
    fun undo()
    fun redo()

    companion object {

        fun of(components: ComponentFactory, id: String = "") = components.textField(id)
    }
}

data class TextComposition(val preedit: String, val selectionStart: Int, val selectionEnd: Int, val replacement: TextRange) {
    init {
        require(selectionStart in 0..preedit.length && selectionEnd in selectionStart..preedit.length)
    }
}
