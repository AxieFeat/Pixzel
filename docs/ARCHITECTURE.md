# Architecture and decisions

Pixzel is a separate Kotlin/JVM project, built with JDK 21, Kotlin 2.4.10 and Gradle 9.6.1. Arc remains a separate checkout/composite dependency. The baseline was Arc commit `419619a2ec7147e76d9b696af823d0ecdeb3e684`; its selected core/common/OpenGL/font/input modules compiled offline before edits. Existing untracked `.idea/` and Vulkan `vk.kt` were preserved. Empty extension build scripts inherit real dependencies from Arc conventions and were not classified as baseline failures.

## Module boundary

`pixzel-core` holds public values and contracts and depends only on Kotlin stdlib. `pixzel-common` supplies the retained implementation and algorithms. `pixzel-test` supplies a second host and two independent backends. `pixzel-samples` compiles external components against public APIs. `pixzel-arc` depends on neutral Arc core/font/input contracts; the application chooses OpenGL, GLFW and native font implementations. Every module is included unconditionally; Arc artifacts come from GitHub Packages at the version pinned in `gradle/libs.versions.toml`. `verifyBoundaries` checks forbidden imports in independent sources and concrete backend/window imports in the adapter.

All library modules have Maven publications and source JARs. The example application is not a library publication. No external repository has been published to; the supplied publication task writes a local repository under `build/repository`.

## Retained execution

The DSL creates ordinary elements once. Direct factories create the same runtime types. Bindings explicitly track state dependencies; dynamic/keyed scopes update bounded regions. Per-key item state outlives VirtualList materialization. No compiler plugin, reflection-based recomposition or per-element coroutine is required. `Ui.post` is the worker-to-owner-thread boundary.

`SimpleUi` coordinates frames, layout and lifetime. Its internal collaborators keep input/capture (`UiInput`), focus scopes/navigation (`UiFocus`), retained paint layers (`UiPainter`) and component construction (`DefaultComponents`) separate. The Arc adapter likewise separates the backend factory, rendering session, material/shader bindings, geometry cache and text raster cache. These are implementation details; public contracts remain in the independent modules.

Each element owns one computed, read-only Surface. Logical parents determine ownership, inheritance, input routes and semantics. A portal changes geometric attachment only. Public `Measurable`, `Placeable`, `LayoutPolicy`, `Painter`, `InputBehavior`, `ComponentSkin`, text strategies and `AbstractElement` support external implementations. Renderers consume draw operations rather than switching on component classes.

## Coordinates and layout

X increases right and Y down in logical dp. A host reports window-logical size separately from framebuffer pixels. Root dp size is `logicalSize / uiScale`; pointer conversion is `windowPosition / uiScale`; pixels per dp are `framebufferSize / rootDpSize`. Text scale resolves sp before raster scale. Insets enter the root once. Anchors reference the selected parent box, pivots reference the child border box. Layout preserves fractional coordinates.

Measurement caches constraints/text scale and propagates required invalidation. Placement commits transforms and geometry; paint cannot mutate them. Fixed/weighted/wrap/grid/flow policies diagnose unbounded fill/percent references. Row baseline alignment uses actual measured text baselines. Responsive rules use committed Surface geometry, ordered matching and an explicit override, with a stable fallback for oscillation. Optional reference-resolution FIT/FILL/STRETCH is not implemented.

## Rendering and color

Common code emits an ordered independent command stream. Each node retains its immutable paint chunk until its geometry, paint version, tokens or environment changes. Arc batches only adjacent compatible solid geometry, with barriers for clip/group/material changes. No transparent reordering is permitted. A reusable UI target owns the UI stencil; isolated groups/shadows use budgeted pooled targets. Ordinary Surfaces own no framebuffer.

`Color` and CPU images store straight-alpha encoded sRGB, using `0xRRGGBBAA.rgba`. The shader converts to premultiplied output once; intermediate composition textures already contain premultiplied color. Gradients and color interpolation use the documented encoded-sRGB policy, not a claimed linear-light/HDR pipeline. Scoped neutral Arc passes restore the actual application target and native state, including exceptional/custom paths. See [the renderer guide](ARC_GRAPHICS.md).

## Text and platform choices

Production text uses FreeType plus HarfBuzz through LWJGL's existing 3.4.2 bindings. Arc exposes neutral font metrics/shaping/raster contracts; the adapter handles bidi, line layout, fallback and source clusters. Real Noto fixtures are pinned with checksums and OFL licensing. The measured positioned layout is the renderer input. Marquee changes paint offsets without shaping or glyph upload. The independent synthetic bitmap font is a deterministic fixture with explicitly smaller capabilities.

Text static overflow and timed reveal are separate replaceable policies. Shared configurations contain no running cursor. Editable TextField is a single-line, single-style editor; styled display spans belong to Text. IME preedit/candidate contracts exist and are tested by TestHost; GLFW supplies Unicode commits and does not claim native composition.

## Input and lifetime

Ordered input uses capture, target and bubble through logical ancestry, followed by default behavior if allowed. Consumption blocks lower gameplay listeners; held-input masking remains distinct from physical polling. Focus scopes trap and restore focus and mask already held gameplay inputs. Pointer capture is per pointer ID, with explicit cancellation/release and cleanup on focus loss, detach, disable, suspension and close. Directional navigation also has an independent command API for future gamepad adapters.

Injected host/backend/text services remain borrowed. Ui owns its render session/tree/listeners. Element disposal closes resources; AttachmentScope leases reopen on attachment and close on detach. Cleanup aggregates failures so later resources still release. Context generations reject stale work and skip unsafe native deletion after context destruction; retained owned image bytes permit recreation, while borrowed images require explicit application rebind. Physical GPU reset detection belongs to the host.

## Verification policy

Tests assert geometry, pixels, event delivery, ownership and bounded work. Independent suites, executable state/animation checks, external samples, real GL readbacks/context-collision tests, actual GLFW callback delivery and showcase smoke runs are distinct evidence. Manual OS interaction and untested platforms are not inferred from compilation. [Performance](PERFORMANCE.md), [capabilities](CAPABILITIES.md) and [requirement status](REQUIREMENTS_STATUS.md) record the practical boundaries.
