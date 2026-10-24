package pixzel.layout

import pixzel.Diagnostic
import pixzel.component.Element
import pixzel.dsl.ElementScope
import pixzel.input.InputMode
import pixzel.layout.unit.Dp
import pixzel.surface.Surface

data class ResponsiveRule(
    val minWidth: Float = 0f,
    val maxWidth: Float = Float.POSITIVE_INFINITY,
    val minHeight: Float = 0f,
    val maxHeight: Float = Float.POSITIVE_INFINITY,
    val minAspect: Float = 0f,
    val maxAspect: Float = Float.POSITIVE_INFINITY,
    val policy: LayoutPolicy,
    val inputMode: InputMode? = null
) {

    init {
        require(minWidth >= 0 && maxWidth >= minWidth && minHeight >= 0 && maxHeight >= minHeight && minAspect >= 0 && maxAspect >= minAspect)
    }

    fun matches(width: Float, height: Float, mode: InputMode? = null): Boolean {
        val aspect = if (height > 0) width / height else 0f
        return (inputMode == null || inputMode == mode) && width in minWidth..maxWidth && height in minHeight..maxHeight && aspect in minAspect..maxAspect
    }
}

class ResponsiveLayoutScope {

    var defaultPolicy: LayoutPolicy = overlayLayout()
    var fallbackPolicy: LayoutPolicy? = null
    val rules = mutableListOf<ResponsiveRule>()
    fun whenWidthAtMost(width: Dp, policy: LayoutPolicy) {
        rules += ResponsiveRule(maxWidth = width.value, policy = policy)
    }

    fun whenWidthAtLeast(width: Dp, policy: LayoutPolicy) {
        rules += ResponsiveRule(minWidth = width.value, policy = policy)
    }

    fun rule(rule: ResponsiveRule) {
        rules += rule
    }
}

/** Ordered matching rules use committed geometry; instance override wins. Reflow retains children. */
fun Element.adaptiveLayout(reference: Surface = surface, instanceOverride: LayoutPolicy? = null, configure: ResponsiveLayoutScope.() -> Unit) {
    val config = ResponsiveLayoutScope().apply(configure)
    layoutPolicy = instanceOverride ?: config.defaultPolicy
    var previous: LayoutPolicy = layoutPolicy
    var alternations = 0
    val recent = ArrayDeque<LayoutPolicy>()
    var latched = false
    var environment: Any? = null
    resources.own(frameUpdates.add {
        if (!reference.isAttached) return@add false
        val nextEnvironment = Triple(ui?.host?.metrics?.generation, ui?.settings, ui?.inputMode)
        if (environment != nextEnvironment) {
            environment = nextEnvironment
            latched = false
            recent.clear()
            alternations = 0
        }
        if (latched) return@add false
        val size = reference.bounds.size
        val selected =
            instanceOverride ?: config.rules.lastOrNull { it.matches(size.width, size.height, ui?.inputMode) }?.policy ?: config.defaultPolicy
        if (selected === previous) {
            alternations = 0
            recent.clear()
            return@add false
        }
        recent += selected
        if (recent.size > 4) recent.removeFirst()
        if (recent.size == 4 && recent[0] === recent[2] && recent[1] === recent[3]) alternations++
        if (alternations >= 2) {
            latched = true
            previous = config.fallbackPolicy ?: config.defaultPolicy
            layoutPolicy = previous
            ui?.diagnostics?.report(
                Diagnostic(
                    "responsive-oscillation",
                    "Responsive rules oscillated at ${size.width}x${size.height}; stable fallback selected",
                    id
                )
            )
            return@add true
        }
        previous = selected
        layoutPolicy = selected
        true
    })
}

fun ElementScope.adaptiveLayout(reference: Surface = element.surface, configure: ResponsiveLayoutScope.() -> Unit) =
    element.adaptiveLayout(reference, configure = configure)
