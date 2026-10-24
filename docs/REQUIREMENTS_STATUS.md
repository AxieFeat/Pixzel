# Requirement coverage

This audit maps the original mandatory specification to executable behavior. It distinguishes automated execution, visual inspection and unsupported optional platforms. The final verification commands/results are recorded in [VERIFICATION.md](VERIFICATION.md); architecture decisions are in [ARCHITECTURE.md](ARCHITECTURE.md). All PZ/ARC rows below are implemented and verified for the required independent JVM and Windows desktop OpenGL scope. Optional and unverified platform boundaries are listed separately.

| ID | Implementation | Evidence |
| --- | --- | --- |
| PZ-01 | Pixzel project, module names and Maven coordinates | [Settings](../settings.gradle.kts), [build/publications](../build.gradle.kts); verified source JARs/local artifacts |
| PZ-02 | Real Arc application scene followed by retained UI | [ArcDemo](../pixzel-demo/src/arc/kotlin/pixzel/demo/ArcDemo.kt); eight-frame native runs and [GPU scene assertions](../pixzel-demo/src/test/kotlin/pixzel/demo/ArcGraphicsChecks.kt) |
| PZ-03 | Core/common require no Arc; second host/recording/software backend | [TestHost](../pixzel-test/src/main/kotlin/pixzel/platform/TestHost.kt), [independent preview](visuals/headless-preview.png), default build/boundaries PASS |
| PZ-04 | Public interfaces/factories, internal Simple implementations, public extension base | [Element/Button contracts](../pixzel-core/src/main/kotlin/pixzel/component/Element.kt), [public base](../pixzel-common/src/main/kotlin/pixzel/component/AbstractElement.kt), external sample compile PASS |
| PZ-05 | Short domain packages and isolated `pixzel.arc.*` adapter | [Boundary checks](../build.gradle.kts) and [adapter dependencies](../pixzel-arc/build.gradle.kts), verified source/POM |
| PZ-06 | Typed units/scopes, DSL marker, extensions, explicit state/bindings | [TypedScopeTest](../pixzel-common/src/test/kotlin/pixzel/dsl/TypedScopeTest.kt), [GuideExamplesTest](../pixzel-common/src/test/kotlin/pixzel/dsl/GuideExamplesTest.kt), state checks PASS |
| PZ-07 | Retained DSL creates ordinary public handles, keyed/dynamic managed regions | [DSL/scopes](../pixzel-common/src/main/kotlin/pixzel/dsl/ContentScope.kt), [integration tests](../pixzel-common/src/test/kotlin/pixzel/UiIntegrationTest.kt) PASS |
| PZ-08 | Direct factories and arbitrary public composition/policies | [Tested direct guide functions](../pixzel-common/src/test/kotlin/pixzel/dsl/GuideExamplesTest.kt), [HealthBar](../pixzel-samples/src/main/kotlin/sample/HealthBar.kt) |
| PZ-09 | Replaceable layers/skin/hit shape/content; all required visual operations | [Three-skin showcase](../pixzel-demo/src/arc/kotlin/pixzel/demo/ArcDemo.kt), [GPU pixels](../pixzel-demo/src/test/kotlin/pixzel/demo/ArcGraphicsChecks.kt) PASS and visually inspected |
| PZ-10 | Window/container/input-mode rules, row/column reflow, scale/text scale/insets | [Responsive policy](../pixzel-common/src/main/kotlin/pixzel/layout/ResponsiveLayout.kt), [layout checks](../pixzel-common/src/test/kotlin/pixzel/layout/LayoutChecks.kt), [runtime acceptance](../pixzel-common/src/test/kotlin/pixzel/RuntimeAcceptanceTest.kt) PASS |
| PZ-11 | Measured cluster-safe overflow with actual remaining Button slot width | [TextPipelineTest](../pixzel-common/src/test/kotlin/pixzel/text/TextPipelineTest.kt), [ComponentSlotTest](../pixzel-common/src/test/kotlin/pixzel/component/ComponentSlotTest.kt) PASS |
| PZ-12 | Timed hover/focus reveal with delay, endpoint pauses and deterministic runtime | [Timing tests](../pixzel-common/src/test/kotlin/pixzel/text/TextPipelineTest.kt), [zero-shaping benchmark](PERFORMANCE.md) PASS |
| PZ-13 | Open overflow/reveal interfaces; theme/scope/instance policies; NONE/reduced motion | [Defaults/policy tests](../pixzel-common/src/test/kotlin/pixzel/component/ButtonTextDefaultsTest.kt), [visual animation tests](../pixzel-common/src/test/kotlin/pixzel/animation/VisualAnimationTest.kt) PASS |
| PZ-14 | Read-only nested Surface coordinates, transforms, anchors/pivots/reference boxes | [Surface contract](../pixzel-core/src/main/kotlin/pixzel/surface/Surface.kt), [layout assertions](../pixzel-common/src/test/kotlin/pixzel/layout/LayoutChecks.kt) PASS |
| PZ-15 | Ordinary top-left notification Surface at (24,24) | [Normative layout test](../pixzel-common/src/test/kotlin/pixzel/layout/LayoutChecks.kt), [independent preview](visuals/headless-preview.png) |
| PZ-16 | Nested centered text root origin (84,48) | [Normative layout test](../pixzel-common/src/test/kotlin/pixzel/layout/LayoutChecks.kt) PASS; ordinary Surface has no target |
| PZ-17 | Neutral Arc extensions, separate delivery/migration | ARC table below, [migration](ARC_MIGRATION.md), [verified source bundle](delivery/README.md) |
| PZ-18 | Complete original Markdown specification retained, implementation guides added | [Original index](spec/README.md), [tested user guide](USER_GUIDE.md), [decisions](ARCHITECTURE.md) |
| PZ-19 | Routing/capture, directional focus, scopes, composition contract, gameplay masks | [Runtime acceptance](../pixzel-common/src/test/kotlin/pixzel/RuntimeAcceptanceTest.kt), [context menu tests](../pixzel-common/src/test/kotlin/pixzel/component/ContextMenuTest.kt), [native callbacks](../pixzel-demo/src/test/kotlin/pixzel/demo/ArcInputChecks.kt) PASS |
| PZ-20 | Incremental caches, bounded lists/buffers/targets, attachment/disposal cleanup | [Benchmark harness](../pixzel-demo/src/main/kotlin/pixzel/demo/Benchmarks.kt), [measured report](PERFORMANCE.md), [lifetime tests](../pixzel-common/src/test/kotlin/pixzel/UiLifetimeTest.kt) PASS |
| PZ-21 | Accurate instance capabilities/limits and platform boundaries | [Support matrix](CAPABILITIES.md), [graphics metadata](../pixzel-core/src/main/kotlin/pixzel/graphics/Graphics.kt), explicit unsupported diagnostics |
| PZ-22 | External component/skin/layout/text-policy extension without internal access | [HealthBar source](../pixzel-samples/src/main/kotlin/sample/HealthBar.kt), [separate lifetime test](../pixzel-samples/src/test/kotlin/sample/HealthBarLifetimeTest.kt) PASS |
| PZ-23 | Real FT/HB fonts, metrics, kerning/ligatures, bidi/fallback, grapheme/source maps | [ArcTextServiceTest](../pixzel-arc/src/test/kotlin/pixzel/arc/text/ArcTextServiceTest.kt) and native tests PASS; [pinned fonts](../assets/fonts/README.md) |
| PZ-24 | Scoped actual-target/state restoration, UI-owned stencil/effects, no scene clear | [Native restoration/collision/pixel checks](../pixzel-demo/src/test/kotlin/pixzel/demo/ArcGraphicsChecks.kt) PASS; [renderer contract](ARC_GRAPHICS.md) |

## Neutral Arc extensions

| ID | Implementation and verification |
| --- | --- |
| ARC-01 | [Baseline/toolchain](IMPLEMENTATION_LEDGER.md) verified before editing; compatible versions retained; [delivered dependencies/source manifest](delivery/README.md) |
| ARC-02 | Neutral typed states/pass ownership, scoped snapshot/restore and raw-call boundary; actual GL target/program/bindings/depth/cull/blend/write/raster [exception tests](../pixzel-demo/src/test/kotlin/pixzel/demo/ArcGraphicsChecks.kt) PASS |
| ARC-03 | Target-pixel scissors/stencil and UI-owned exact masks; empty/five-level/transformed/path [pixel checks](../pixzel-demo/src/test/kotlin/pixzel/demo/ArcGraphicsChecks.kt) PASS |
| ARC-04 | Logical/framebuffer/content-scale/focus/minimized metrics and generations; legacy dimensions retained; [scale tests](../pixzel-common/src/test/kotlin/pixzel/InputEdgeTest.kt) and [real GLFW metrics](../pixzel-demo/src/test/kotlin/pixzel/demo/ArcInputChecks.kt) PASS |
| ARC-05 | Window callback multiplexer, ordered delivery, Unicode/repeat, priorities/paired release masks and physical polling; [native callback checks](../pixzel-demo/src/test/kotlin/pixzel/demo/ArcInputChecks.kt) PASS |
| ARC-06 | Neutral real fonts, FT/HB, face versions/provider identity, positioned glyphs/raster cleanup; [adapter tests](../pixzel-arc/src/test/kotlin/pixzel/arc/text/ArcTextServiceTest.kt) and [native delivery/tests](delivery/README.md) PASS |
| ARC-07 | Active bytes/count/capacity, orphaned streaming, local native scratch, empty/grow/reuse/cleanup; [readback/counters](ARC_GRAPHICS.md) and standalone regression PASS |
| ARC-08 | Owned targets/borrowed views, restoration without clear, resize/cleanup and budgeted pool; [overlap/effects/1000-cycle checks](../pixzel-demo/src/test/kotlin/pixzel/demo/ArcGraphicsChecks.kt) PASS |
| ARC-09 | [Actual desktop limits and support](CAPABILITIES.md); GLES source compilation PASS; unsupported platforms explicit |

## Technical acceptance beyond the ID summary

- Layout includes margins/padding/min/max/aspect constraints, weighted minima/overflow, text-width measurement, real baselines, wrapping flow and grid, unbounded-reference diagnostics, portal ordering and bounded responsive oscillation recovery. Public policies operate on measurables/placeables.
- Themes provide immutable standard tokens and ordered state rules. Logical inheritance includes typography and button text policy. Explicit instance values remain final. Color/policy-only changes avoid text measurement; custom skins receive resolved tokens and overlapping state.
- Foundation/display/action/selection/value/input/scroll/data/navigation/overlay/message components have direct APIs. Button, dialog, notification and dropdown slots accept arbitrary content. ContextMenu supports pointer and explicit keyboard invocation. Radio groups define removal/none behavior; sliders define clamp/step/equal bounds.
- Text reveal caches full shaping and changes paint offsets. Text supports styled display spans and font variants. Single-line TextField supports grapheme-safe editing, selection/copy/read-only/password, word navigation, validation, bounded undo/redo, horizontal caret scrolling and separate IME preedit/candidate geometry. Unsupported styled editing is explicit.
- Image assets expose Loading/Ready/Failed/Closed, retry, immutable worker decode and UI-thread upload, generation cancellation, budgets, owned/borrowed handles and stable placeholders. Fixed-size handle replacement invalidates paint; intrinsic sizing can invalidate measurement.
- Tween/keyframe transitions include numeric/color/opacity/transform interpolation, cancellation/retarget/queue and Element-owned clocks. Detached/suspended clocks pause; reduced motion selects the latest target. Sample subscriptions reopen on attachment and close on detach/disposal.
- The inspector can be disabled and displays path/generation, constraints/measured size, border/content, anchor/pivot/clip, state, focus/capture, last invalidation, text-cache statistics and available render metrics. Unsupported metrics remain unknown, not fabricated.
- JVM bytes/frame and CPU percentiles are measured separately from native renderer submission. GPU high-water values are requested attachment/mesh/text storage estimates; driver overhead, native allocation counts and timer-query GPU durations are explicitly unavailable.

## Optional or unverified platform work

No implementation is claimed for Pixzel GLES/ANGLE/Vulkan shaders, native OS IME composition/candidate bridges, OS screen readers, native multitouch/gamepad adapters, mobile/Kotlin Native, world-space UI, rich/multiline editors, SVG/CSS, docking/tables or reference-resolution FIT/FILL/STRETCH. Independent navigation commands and IME/semantics contracts are present now.

Windows desktop OpenGL is exercised on actual hardware. macOS/Linux native runtime execution, live multi-monitor density transitions and manual OS keyboard/mouse walkthroughs have not been performed in this environment. Automated fixtures exercise equivalent coordinate/event contracts; compilation alone does not establish support on those platforms.
