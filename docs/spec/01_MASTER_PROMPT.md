# Master prompt for GPT 6.1 Sol


You are the lead developer of **Pixzel**, a Kotlin UI library for games and applications. Implement the library and the necessary Arc Engine extensions according to this specification. Work against [AxieFeat/Arc](https://github.com/AxieFeat/Arc/tree/dev), branch `dev`. The documents were prepared against commit `419619a2ec7147e76d9b696af823d0ecdeb3e684`. Inspect the current checkout, record differences and adapt integration to actual signatures.


## Required outcome


Deliver a usable, extensible library with an expressive Kotlin DSL, nested Surfaces, responsive layout, arbitrary appearance, extensible components, correct input and high-quality text. The public API must support both DSL use and programmatic construction/mutation.


Pixzel integrates with Arc, but **is not part of arc-core and has no Arc dependency in its own core/common modules**. Prove this with a second test host/backend without Arc. Do not implement Pixzel on Compose, JavaFX, Swing or browser UI. A separate test software renderer is allowed; it does not become the production foundation.


Read every document in this set. Do not replace these requirements with a generic GUI framework design. Pay particular attention to:


- `02_ARC_ANALYSIS_AND_STYLE.md`: package naming, Factory/Provider, internal implementations and actual Arc limitations.
- `04_SURFACE_LAYOUT_RESPONSIVE.md`: a Surface is a coordinate/layout space, not an obligatory GPU texture.
- `07_TEXT_AND_OVERFLOW.md`: ellipsis followed by hover ping-pong scrolling is controlled by replaceable policies, not hardcoded in Button.
- `09_ARC_EXTENSION_PLAN.md`: missing Arc capabilities and authorized additions.
- `13_API_EXAMPLES.md`: required DSL expressiveness and low-level flexibility.


## Inspect the code first


1. Read `AGENTS.md` if present, `settings.gradle.kts`, convention plugins, the version catalog and the Arc modules listed in the analysis.
2. Inspect `Application.renderContext`, `RenderContext`, `Drawer`, `DrawBuffer`, `VertexBuffer`, `Window`, `WindowHandler`, `GlyphFont`, GLFW input, ObjectProvider and their implementations.
3. Do not use the outdated `application.renderSystem` name just because it appears in README. A Vulkan module's existence does not prove it works.
4. Establish a reproducible build baseline. Distinguish existing Arc failures from regressions introduced by your changes. Report failed checks.
5. Write brief ADRs for modules, DSL lifecycle, layout/Surface, text pipeline, event routing and the render-pass contract. Then implement; do not stop at a plan.


## Non-negotiable architecture


- Use the short root package `pixzel` and domain packages such as `pixzel.surface`, `pixzel.layout`, `pixzel.component` and `pixzel.graphics`. Do not use `com.axiefeat.pixzel` or `pixzel.core.component`. Follow Arc's structural intent rather than incidental exceptions.
- Public interfaces and `internal SimpleX` implementations in another module use the same domain package. Arc adapters live in `pixzel.arc.*` and use the `Arc` class prefix.
- Public core contains no `arc.*`, LWJGL, JOML, GL/Vulkan handles, GLFW keycodes, ByteBuffer, AWT or global Arc-provider dependency.
- Inject host, rendering, input, text, clock and platform services explicitly. Do not obtain the renderer through a hidden singleton. Each Ui owns its tree, focus, state and resources.
- Retain the tree between frames. The DSL declaratively constructs/updates it; do not rebuild the entire tree every frame. Dynamic branches use keyed scopes with controlled lifetimes.
- Separate layout, input, rendering and appearance contracts. External components can replace layout policy, renderer/skin, text overflow policy, hit shape and behavior.
- Surface supports nesting, anchor/pivot/offset, local transforms, DPI and layout constraints without allocating a framebuffer.
- Components support primitives, images, nine-slice, vector paths, custom drawing and content slots. A rectangle-plus-label appearance model is insufficient.
- External extension points are ordinary interfaces or `fun interface`, not sealed hierarchies that prevent user implementations.
- Optimizations preserve painter order, clipping, transparency, hit testing and lifecycle.


## Kotlin style


Use modern, readable Kotlin: receiver lambdas and `@DslMarker`; extensions; named/default arguments; null safety; delegates and observable state; justified `value class` units/IDs; immutable data objects; exhaustive `when`; small useful inline/reified APIs. Use operators/infix only where unambiguous.


“Use Kotlin's full range” means appropriate language features, not every feature in every file. Do not add compiler plugins, reflective rendering, experimental context features, a coroutine per widget, generated DSL/KSP or a DI framework without a concrete need. Readability takes priority over clever brevity.


Follow Arc patterns: meaningful `X.of(...)`/`X.from(...)`/`X.builder()`; Factory/Provider where implementations are replaceable; `SimpleX`/`AbstractX`/`EmptyX` where appropriate; English KDoc/error messages; `UPPER_SNAKE_CASE` enum entries. Pixzel factory injection stays explicit and local. Do not import Arc annotations into independent core.


## Required behavior


- HUDs, menus, notifications, modals, popups, tooltips and nested scroll containers.
- Elements can provide a Surface: a notification anchored to root's top-left creates its own area for centering its text.
- Resize, DPI, UI/text scale, safe areas and different aspect ratios; correct clipping and hit testing after geometry changes.
- An overflowing button label shows an ellipsis while idle. On hover, and optionally keyboard focus, the full text moves from leading to trailing edge and back. Configure delay, speed, pauses, exit behavior, reduced motion and algorithm replacement.
- Pointer activation/capture, focus, Tab/Shift+Tab, Enter/Space, scrolling, text input, clipboard and drag/drop. Gameplay does not receive consumed UI input.
- Standard components plus an external sample component with no internal access.
- No full relayout/rebuild/reshape on static frames; resource reuse and explicit cleanup.
- Arc/OpenGL demo and headless non-Arc host/backend; capability contracts and extension tests.
- Required Arc additions for scissor/render state, DPI/events, font metrics/shaping and safe streaming uploads.


## Implementation rules


Follow vertical stages, keeping each result buildable and verifiable. Do not deliver a giant core of empty interfaces or substitute `TODO()` for required behavior. First complete a small tree-to-screen path, then extend it until all mandatory requirements are met.


Design new Arc methods as neutral contracts, then implement them in supported backends. Do not hide `glScissor` or GLFW callbacks inside Pixzel. Do not place Pixzel-specific Surface/Node/Button types in arc-core. Split substantial Arc changes into coherent steps with migration guidance.


The production OpenGL path must work within Arc's existing 4.1 target. Do not require SSBO, bindless, compute or vendor extensions. GLES/ANGLE support requires probing and tests. Do not claim working Vulkan before implementation and verification. Completing Arc's entire Vulkan backend is not a Pixzel 1.0 requirement; accurate status is required.


Do not upgrade all dependencies or restructure Arc unnecessarily. Preserve user changes. If a check cannot run, explain the exact reason and next step while continuing independent work.


## Deliverables


1. Pixzel source with documented modules, dependencies and usage.
2. Concrete Arc patches and migration notes.
3. Compiling DSL/direct-API examples, interactive showcase and non-Arc example.
4. Meaningful unit/property/integration/visual checks and execution results.
5. Benchmark protocol recording environment, scene sizes, p50/p95/p99, allocations and draw calls.
6. Backend capability matrix, known limitations, ADRs and reproducible build instructions.
7. Final report: working behavior, launch instructions, verified checks and remaining limits. Do not claim unverified platforms.


Start with checkout inspection and initial ADRs, then implement all required stages. Do not repeatedly request permission for routine changes already covered by this assignment. Discuss substantive product-requirement changes separately.