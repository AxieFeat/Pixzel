package pixzel.style

import pixzel.component.Button
import pixzel.component.ComponentSkin
import pixzel.component.Element
import pixzel.component.ElementState
import pixzel.component.Visibility
import pixzel.graphics.Color
import pixzel.graphics.rgba
import pixzel.layout.unit.Dp
import pixzel.text.TextOverflowPolicy
import pixzel.text.TextRevealBehavior
import pixzel.text.TextStyle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class Palette(val background: Color, val panel: Color, val primary: Color, val text: Color, val disabled: Color, val focus: Color)

/**
 * Null fields inherit the enclosing component defaults; common supplies ellipsis/hover reveal.
 */
data class ButtonTextDefaults(val overflow: TextOverflowPolicy? = null, val reveal: TextRevealBehavior? = null) {

    fun applyTo(base: ButtonTextDefaults) = ButtonTextDefaults(overflow ?: base.overflow, reveal ?: base.reveal)
}

data class Theme(
    val palette: Palette,
    val typography: TextStyle = TextStyle(),
    val spacing: Dp = Dp(8f),
    val cornerRadius: Dp = Dp(6f),
    val transition: Duration = 150.milliseconds,
    val buttonSkin: ComponentSkin<Button>? = null,
    val buttonText: ButtonTextDefaults = ButtonTextDefaults()
) {

    val tokens = ResolvedTokens(palette, typography, spacing, cornerRadius, transition, buttonText)

    companion object {

        val DARK = Theme(Palette(0x10151FFF.rgba, 0x202B3AFF.rgba, 0x385FDCFF.rgba, Color.WHITE, 0x555A65FF.rgba, 0xFFE082FF.rgba))
        val LIGHT = Theme(Palette(0xF1F4FAFF.rgba, Color.WHITE, 0x2F55CDFF.rgba, 0x18212BFF.rgba, 0xA8ADB8FF.rgba, 0x6F45CFFF.rgba))
        val GAME = Theme(Palette(0x131E17FF.rgba, 0x31452CFF.rgba, 0x7A962EFF.rgba, 0xFFE9A8FF.rgba, 0x62664FFF.rgba, 0xFFBD49FF.rgba))
    }
}

/**
 * Immutable standard scope overrides. A null token inherits the enclosing scope/theme value.
 */
data class StyleTokens(
    val palette: Palette? = null,
    val typography: TextStyle? = null,
    val spacing: Dp? = null,
    val cornerRadius: Dp? = null,
    val transition: Duration? = null,
    val buttonText: ButtonTextDefaults? = null
) {

    fun applyTo(base: ResolvedTokens): ResolvedTokens {
        val result = ResolvedTokens(
            palette ?: base.palette,
            typography ?: base.typography,
            spacing ?: base.spacing,
            cornerRadius ?: base.cornerRadius,
            transition ?: base.transition,
            buttonText?.applyTo(base.buttonText) ?: base.buttonText
        )
        return if (result == base) base else result
    }
}

data class ResolvedTokens(
    val palette: Palette,
    val typography: TextStyle,
    val spacing: Dp,
    val cornerRadius: Dp,
    val transition: Duration,
    val buttonText: ButtonTextDefaults = ButtonTextDefaults()
)

/**
 * Rules are applied in declaration order after inherited tokens; explicit component values win.
 */
data class StyleScope(val tokens: StyleTokens = StyleTokens(), val rules: List<StateStyle<StyleTokens>> = emptyList()) {

    fun resolve(base: ResolvedTokens, state: ElementState): ResolvedTokens {
        var result = tokens.applyTo(base)
        rules.forEach { if (it.matches(state)) result = it.value.applyTo(result) }
        return result
    }
}

val Element.isEffectivelyEnabled: Boolean
    get() = generateSequence(this as Element?) { it.parent }.all {
        it.enabled && it.visibility == Visibility.VISIBLE && !it.isDisposed
    }

data class StateStyle<T>(val matches: (ElementState) -> Boolean, val value: T)

/**
 * Defaults -> theme -> inherited -> ordered state rules -> instance (including instance states).
 */
fun <T> resolveStyle(
    default: T,
    theme: T? = null,
    inherited: T? = null,
    rules: List<StateStyle<T>> = emptyList(),
    state: ElementState,
    instance: T? = null,
    instanceRules: List<StateStyle<T>> = emptyList()
): T {
    var value = inherited ?: theme ?: default
    rules.forEach { if (it.matches(state)) value = it.value }
    if (instance != null) value = instance
    instanceRules.forEach { if (it.matches(state)) value = it.value }
    return value
}
