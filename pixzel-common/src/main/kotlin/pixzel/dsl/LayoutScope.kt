package pixzel.dsl

import pixzel.layout.LayoutConfig
import pixzel.layout.unit.Dp
import pixzel.layout.unit.SizeSpec
import pixzel.surface.Insets

/**
 * Common child geometry. Parent data is deliberately absent: use RowScope/ColumnScope.weight
 * or GridScope.cell in the enclosing container. Direct APIs retain Element.layout for custom policies.
 */
@PixzelDsl
class LayoutScope internal constructor(private val config: LayoutConfig) {

    var width: SizeSpec
        get() = config.width
        set(value) {
            config.width = value
        }
    var height: SizeSpec
        get() = config.height
        set(value) {
            config.height = value
        }
    var minWidth: Dp
        get() = config.minWidth
        set(value) {
            config.minWidth = value
        }
    var minHeight: Dp
        get() = config.minHeight
        set(value) {
            config.minHeight = value
        }
    var maxWidth: Dp?
        get() = config.maxWidth
        set(value) {
            config.maxWidth = value
        }
    var maxHeight: Dp?
        get() = config.maxHeight
        set(value) {
            config.maxHeight = value
        }
    var padding: Insets
        get() = config.padding
        set(value) {
            config.padding = value
        }
    var margin: Insets
        get() = config.margin
        set(value) {
            config.margin = value
        }

    /**
     * Metadata for a custom policy; standard row/column spacing is their typed builder argument.
     */
    var spacing: Dp
        get() = config.spacing
        set(value) {
            config.spacing = value
        }
    var aspectRatio: Float?
        get() = config.aspectRatio
        set(value) {
            config.aspectRatio = value
        }
}
