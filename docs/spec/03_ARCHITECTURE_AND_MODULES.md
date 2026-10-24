# Architecture, modules and public contracts


## Library model


Pixzel is a retained UI with a declarative Kotlin DSL. Tree/state persist between frames; drawing runs in the game's loop. The application owns its window/event loop. Pixzel does not start a loop or poll/swap/begin/end the application frame.


The first implementation targets Kotlin/JVM and JDK 21, with a Kotlin version compatible with working Arc. Keep independent contracts portable, avoiding unnecessary JVM/native types. Kotlin Multiplatform is a later migration; portable interfaces do not establish Android/iOS/Native support.


Prefer an independent Gradle project/repository for Pixzel. Change Arc separately. A composite build with explicit dependency substitution is allowed for joint development. Do not embed Pixzel in arc-core or introduce a dependency cycle.


## Modules


| Gradle module | Contents | Depends on |
| --- | --- | --- |
| `pixzel-core` | Ui, Surface, layout/input/text/render contracts, independent value types | Kotlin stdlib; minimal annotations if needed |
| `pixzel-common` | Shared algorithms, internal implementations, builders, state, layout, standard components | `api(pixzel-core)` |
| `pixzel-arc` | Arc host/render/font/input/asset adapters | `api(pixzel-core)` and required Arc API modules |
| `pixzel-test` | FakeClock, TestHost, RecordingBackend, minimal software preview, test utilities | `api(pixzel-core)` and test/support dependencies |
| `pixzel-demo` | Showcase, samples, Arc/OpenGL application | common, arc and selected Arc implementations |


pixzel-test is a publishable support artifact/fixture, not a core runtime dependency. Java2D/AWT is allowed only in its software preview behind an explicit JVM boundary. Do not add java.awt to pixzel-common for colors or metrics.


pixzel-arc does not depend on arc-opengl, arc-input-glfw or native GLFW. The application/demo chooses technology implementations. Add missing Arc API before depending on it.


Extension contracts belong in core; standard implementations/builders belong in common. DSL extensions depending only on contracts may live in core. Split modules by dependency/platform or independent delivery, not one per primitive.


## Packages


| Package | Contents |
| --- | --- |
| `pixzel` | Ui, UiSettings, entry points, root services |
| `pixzel.component` | Element, components, content slots, lifecycle |
| `pixzel.surface` | Surface, placement, anchors, coordinates |
| `pixzel.layout` | Constraints, measurement, policies |
| `pixzel.layout.unit` | Dp, Sp, percentages, units |
| `pixzel.graphics` | Backend, draw commands, canvas, transforms |
| `pixzel.graphics.paint` | Brushes, shapes, strokes, paths |
| `pixzel.style` | Theme, tokens, skins, state styles |
| `pixzel.text` | TextLayout, shaping, fonts, overflow policies |
| `pixzel.input` | Events, routing, capture, consumption |
| `pixzel.focus` | Focus scopes, navigation |
| `pixzel.state` | Observable state, subscriptions |
| `pixzel.animation` | Timeline, transitions, clock |
| `pixzel.platform` | Host, metrics, clipboard, cursor, capabilities |
| `pixzel.resource` | Images, font handles, ownership |
| `pixzel.dsl` | DSL marker, scopes |
| `pixzel.util.pattern` | Small independent lifecycle/pattern contracts |
| `pixzel.arc.*` | Arc integration only |
| `pixzel.demo.*` | Samples |


Do not add api/impl/core/common package segments merely to mirror Gradle modules. Consumer packages are outside this naming requirement.


## Main contracts


These are target names; ADRs refine signatures consistently across source, documentation and demos.


| Contract | Responsibility |
| --- | --- |
| Ui | Root, frame lifecycle, content, scheduler, services |
| UiHost | Host metrics, ordered input stream, platform services |
| UiBackend | Capabilities, render target, resources, draw-list submission |
| TextService | Measurement, shaping, layout, drawing data |
| Element | State, children, Surface, behavior |
| Surface | Geometry/coordinates; read-only computed layout result |
| LayoutPolicy | Measure and place children under constraints |
| Painter | Appearance through an independent canvas |
| ComponentSkin<T> | Component/slot appearance for current state |
| HitShape | Precise local-coordinate hit testing |
| TextOverflowPolicy | Static constrained-text result |
| TextRevealBehavior | Time-dependent text offset/display state |
| Subscription | Listener/binding cancellation; idempotent close |


Inject services explicitly: `ui(host, backend, text, settings, clock, ...)` in common and `Ui.of(factory, host, backend, text, ...)` in core. TextService is a separate dependency; backend/text services agree on glyph/resource contracts when opening a session. Never obtain them implicitly from global Arc. Component factories are local to Ui and available through ui.components.


Ui.Factory, if needed for replacing Ui implementations, receives every dependency explicitly. Ui.of may delegate to a supplied factory; common supplies the standard ui facade. Core has no singleton requiring bootstrap. Arc bootstrap only prepares Arc services.


## Trees and identity


- Each materialized Element owns exactly one Surface. `surface { ... }` creates a container owning that Surface.
- Ui has a root element/root Surface and one layout root. Independent windows use separate Ui instances.
- The logical tree defines lifetime, inheritance, event routes and scopes.
- Geometric parent/paint attachment normally follows the logical tree. Portals can attach geometry to an overlay while preserving logical ownership.
- Surface owns no GPU target; render-to-texture is a separate effect/adapter.
- Internal generation-tagged handles reject stale references after disposal/reuse. User ElementId is stable within its scope.
- A node cannot have two parents. Duplicate keys produce path-based diagnostics, not silent replacement.
- Defer unsafe child mutations to a mutation boundary. A node removed by a callback receives no subsequent default action.


Detached nodes have no root transform; reject root/world-coordinate operations until attachment. Reparent explicitly and recompute inheritance, focus/capture and subscriptions. Resource ownership does not transfer accidentally to another renderer.


## External extension


An external module must be able to:


1. Construct an element through public factories/constructors.
2. Supply layout policies, skins/painters, hit shapes and overflow/reveal strategies.
3. Compose components from slots and standard elements.
4. Add them through extensions on public DSL scopes.
5. Implement a backend/host/text provider without Arc.
6. Change themes, defaults and per-instance policy.
7. Add resources/custom render operations with capability negotiation.


Do not require inheritance from internal classes. Public AbstractElement may be a convenience, but all required functionality remains available through public contracts.


## Errors and boundaries


Use check for invalid internal state and require for invalid parameters. Unsupported capabilities yield structured diagnostics identifying the element and fallback policy. A faulty handler must not corrupt push/pop stacks or GPU state; release error policy is configurable.


Required operations cannot be unchecked successful no-ops. Empty objects need documented valid empty semantics, not concealed missing implementations.