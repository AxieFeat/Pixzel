package pixzel.component

import pixzel.surface.Rect

data class SemanticsNode(
    val id: String,
    val generation: Long,
    val role: Role,
    val label: String,
    val value: String,
    val enabled: Boolean,
    val checked: Boolean?,
    val selected: Boolean,
    val focusable: Boolean,
    val focused: Boolean,
    val bounds: Rect,
    val actions: Set<String>,
    val children: List<SemanticsNode>
)
