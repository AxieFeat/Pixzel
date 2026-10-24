# Kotlin DSL, state and updates


## 1. Declarative description, retained execution


Execute the DSL when constructing the tree and explicitly updating a bounded dynamic scope. Static content lambdas do not execute every frame. Results are ordinary components with public handles.


Support equivalent paths:


- `ui.content { surface { button { ... } } }`;
- `Button.of(...)`, property configuration, `parent.add(child)` and subsequent mutation without DSL.


Ui.content replaces root content by default and disposes the old tree correctly. Use properties/bindings/keyed scopes for targeted changes. Repeated calls must not silently accumulate duplicates.


## 2. Scopes and syntax


Use @PixzelDsl/@DslMarker to restrict receiver leakage. Scope types include ContentScope, SurfaceScope, RowScope, ColumnScope, ButtonScope and TextScope.


surface/row/column/button/text/image return created-element handles. Use named/default arguments and trailing receiver lambdas. Group large parameter sets into independent value objects, not boolean flags.


Separate configuration concerns:


```kotlin
button(id = "connect") {
    layout {
        width = 240.dp
        height = 48.dp
    }
    appearance {
        background = solid(0x304FFEFF.rgba)
        corners = 12.dp
    }
    label("Connect") {
        overflow = TextOverflow.ELLIPSIS
        reveal = hoverReveal {
            delay = 600.milliseconds
            speed = 32.dpPerSecond
        }
    }
    onClick { connect() }
}
```


Color contract: `0xRRGGBBAA.rgba`. For example, `0x304FFEFF.rgba` is opaque blue. rgb adds alpha 255. Provide appropriate Int/Long hex-literal overloads without changing channel order implicitly.


Make units/ownership clear. Use typed values rather than strings such as "100%" or "center". No syntax requiring Kotlin compiler changes.


These fragments describe a target API. Turn every example into a compiling sample with imports and real implemented methods.


## 3. State and binding


Minimum model:


- State<T>: read-only observable value;
- MutableState<T>: controlled writable value;
- stateOf and property delegation;
- derivedStateOf with tracking of states read;
- bind for computed property bindings with tracked dependencies;
- listeners return owner-scoped Subscription handles.


```kotlin
val online = stateOf(0)
text {
    value(bind { "Players online: "+online.value })
}
button {
    label("Add")
    onClick { online.value++ }
}
```


Builders overload value(String) and value(Binding<String>). Direct Text.value remains String; bindValue installs a binding explicitly. Direct assignment removes the old binding under a documented rule. Do not use Any or an impossible implicit String-to-wrapper conversion. `text(online.value.toString())` is a snapshot, not automatically reactive.


Recompute expressions only after dependency changes. Default equality is structural. In-place mutation of an ordinary mutable collection is not detected magically; require observable mutations or immutable snapshot replacement.


Batch/transaction groups writes into one invalidation wave so observers see consistent state. It does not provide database rollback. An exception completes the batch under documented rules without permanently blocking the scheduler.


## 4. Invalidation


| Change | Minimum invalidation |
| --- | --- |
| Text/font/available text width | TEXT_LAYOUT + MEASURE + PAINT |
| Padding/sizing/children | MEASURE + ARRANGE + PAINT |
| Anchor/offset | ARRANGE + HIT_TEST + PAINT |
| Paint translation/rotation | TRANSFORM + HIT_TEST + PAINT |
| Color/hover brush | PAINT |
| Marquee offset | PAINT, not shaping or layout |
| Disabled/visibility/focus | Semantics/input + paint; layout only as visibility requires |
| Loaded-image size | PAINT; MEASURE only for intrinsic sizing |
| Theme token | Dependent properties, not the whole tree |


Propagate invalidation to the nearest required boundaries. Unchanged measured size need not remeasure unaffected ancestors. Diagnose binding cycles with dependency chains. Restrict reading layout results during their own computation.


## 5. Dynamic branches and identity


whenState/dynamic creates managed scopes with tracked state. `items(collection, key = { ... })` performs keyed reconciliation within a scope. Do not promise implicit Compose-style recomposition of arbitrary Kotlin functions.


Requirements:


- keys are stable, scope-unique and independent of index for reorderable lists;
- reordering preserves item state, focus, selection and animation;
- removal closes bindings/listeners/tasks and releases owned resources;
- insertion does not recreate other items;
- duplicate keys report the scope path;
- replacing a node type under the same key uses an explicit replacement path;
- reused-node handlers receive current items, not stale closures.


Register local state in its scope, e.g. `scope.state(key) { initial }`, not a global map. VirtualList separates keyed item-state lifetime from short-lived materialized nodes. Recycling must not transfer a previous item's state/handlers to another item.


## 6. DSL extension and slots


External modules extend public scopes and use public insertion APIs:


```kotlin
fun ContentScope.healthBar(
    value: State<Float>,
    configure: HealthBarScope.() -> Unit = {}
): HealthBar
```


Components are not limited to one label. Button supports content, leading/trailing slots and arbitrary painters. label is a convenience for its standard text slot. Custom content preserves keyboard activation.


Replace appearance and behavior independently. A new component must not require editing a giant renderer when(elementType). Public scopes allow external content creation without internal casts.


## 7. Threading and callbacks


All tree/state/focus/animation-scheduler mutations belong to one UI thread, initially Arc's render thread. `ui.post { ... }` explicitly delivers work from workers, FIFO at boundaries, with ownership checks and cancellation on close. Direct cross-thread setters fail in debug.


Input callbacks must not mutate UI from GLFW worker pools. Async loading posts immutable results through the scheduler; GPU uploads stay on the render thread. Optional coroutine adapters may use owner scopes; a coroutine is not required per element.


Provide explicit accumulate/replace handler registration. Repeated DSL updates must not duplicate handlers silently. Event-time mutations must prevent default actions on removed nodes.


## 8. Kotlin quality contracts


- Inline only small hot/helper APIs; public inline must not expose internals.
- Value-class units/IDs do not guarantee zero allocation in generic containers; measure boxing.
- Seal finite internal states, but keep user strategy interfaces open.
- Separate immutable configuration from mutable runtime state; no per-instance animation cursor in shared theme tokens.
- Use fun interface for single-method strategies and receiver lambdas for compositional builders.
- Delegates do not secretly poll properties every frame.
- DSL extensions and direct APIs have identical semantics.
- English KDoc documents units, threading, lifetime, failures and snapshot/binding differences.


## 9. General animations and transitions


Version 1.0 includes cancellable tween/keyframe transitions for color, opacity, transforms and numeric properties, beyond text reveal. Easing/duration are typed. Spring may be an optional public AnimationSpec strategy.


Animation handles belong to owner scopes. Retarget policy chooses current-value start, cancel/replace or queue. Hover changes must not restart movement every frame. Theme changes, disable and disposal leave no orphan animations.


Paint-property animation avoids measurement by default. Width/padding animation is an explicit layout-cost choice with correct invalidation. Reduced motion may use zero duration or another spec. Configuration is immutable/shareable; running state is per instance.