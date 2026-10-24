# Final verification record

Verified on 2026-10-24, Windows 11, Ryzen 7 7800X3D, Oracle JDK 21.0.10, Kotlin 2.4.10, Gradle 9.6.1, LWJGL 3.4.2 and NVIDIA GeForce RTX 4060 Ti / driver 617.42. The native fixtures requested desktop OpenGL 4.1. Tests ran against the shared integrated sources, with separately owned workstreams and independent source review before final integration.

## Maven and convention-plugin migration

All six modules are now included unconditionally. `build-logic` follows Arc's precompiled convention-plugin structure, imports the shared version catalog and provides typed `libs`/`projects` accessors. Arc dependencies use GitHub Packages at the pinned commit version. The former conditional/composite commands recorded below are historical and no longer select a different build.

The independent modules passed build, Detekt, boundary checks, state checks and animation checks after this migration. A full build plus native graphics and input checks also passed against a temporary Maven repository containing the reviewed Arc sources, without dependency substitution. `verifyCodeStyle` checks multiline KDoc as part of `check`. Per-module Detekt baselines record existing findings from adopting Arc's rules; baseline generation is explicit.

Arc's readability/publication changes passed Detekt, the real OpenGL regression, GLES compilation and native-font tests before the fast-forward to [`229da48`](https://github.com/AxieFeat/Arc/commit/229da488a3e0e50f2e2003cee2f431b7fa8ca73b) on `master`. Its Maven publications now include `arc-annotations`, and dependency versions match the commit-versioned artifacts.

The [Arc GitHub Actions run](https://github.com/AxieFeat/Arc/actions/runs/40071765470) completed both Detekt and publication successfully. The publication job compiled and published the library modules in 208 executed tasks. Arc version `229da48`, including its font and GLFW input modules, is available from GitHub Packages.

The final Pixzel invocation resolved Arc directly from GitHub Packages, without an init script, composite build or local Arc repository:

```powershell
.\gradlew.bat build verifyBoundaries verifyCodeStyle :pixzel-common:stateChecks :pixzel-common:animationChecks :pixzel-demo:graphicsChecks :pixzel-demo:inputChecks :pixzel-demo:preview publishAllPublicationsToLocalArtifactsRepository :pixzel-demo:run --args="--smoke --scale=1.25" --refresh-dependencies --console=plain
```

**PASS, 1 minute 23 seconds**, 69 tasks. The XML reports contain **121 Pixzel tests**, zero failures/errors. GPU/context-generation/lifecycle checks, real GLFW input checks, software preview, scaled native smoke demo and all five local Maven publications passed. POM versions were checked for both Pixzel intermodule references and Arc's `229da48` dependencies. The intermediate attempt while GitHub Actions was still publishing stopped at the then-unavailable input artifact; this successful invocation supersedes it.

## Code-style revalidation and publication

The later formatting pass covers 132 live Pixzel and 57 owned Arc Kotlin files, plus authored PowerShell and guide examples. It follows original Arc indentation, explicit imports, separate statements, multiline blocks and enum entries. All owned Kotlin sources now use explicit imports; 2,299 optional statement separators were removed. The production GLSL vertex/fragment sources were formatted too.

Independent build, boundaries, state/animation checks and software preview passed in **36 seconds**. After the final source cleanup, the full composite/native command below passed in **26 seconds**, and the standalone Arc command passed in **17 seconds**. The XML reports still contain **125 methods**, with zero failures, errors or skips. Native GPU/input fixtures and the scale-1.25 showcase passed, including 1,000 GPU sessions and both context-generation fixtures. These are functional rechecks; the earlier performance measurements below were not rerun.

An independent Kotlin PSI comparison against the normalized preformatter sources found identical syntax trees, ordered significant tokens, comments, literal payloads and import nodes in **188 files**. The remaining renderer file differs only in its two GLSL literal payloads: all **110 vertex + 589 fragment tokens**, directives and numeric spellings are identical, and the surrounding Kotlin structure is identical. Supplemental compiled-class comparison was inspected separately; raw byte identity is not assumed because Kotlin changes debug mappings and temporary-variable code generation when statements move.

Arc is committed in [`e5d04a8`](https://github.com/AxieFeat/Arc/commit/e5d04a8de4eec543e67eff7778d37f88ae29d824) on `codex/pixzel-integration`. The final [delivery](delivery/README.md) was regenerated from that committed checkout. Pixzel's `dev` retains the original remote `master` history. Build outputs, local tooling experiments and historical delivery attempts are excluded from version control; original Arc IDE/Vulkan files remain outside the integration commit.

## Historical independent execution

From the Pixzel workspace, without `-ParcPath`:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
.\gradlew.bat build verifyBoundaries :pixzel-common:stateChecks :pixzel-common:animationChecks :pixzel-demo:run publishAllPublicationsToLocalArtifactsRepository --offline --console=plain
```

**PASS, 10 seconds** on the final independent invocation. Jupiter XML reports **109 common + 2 reference-backend + 1 external-sample = 112 tests**, zero failures, errors or skips; the [machine-readable XML summary](verification/test-summary.json) includes adapter/native counts too. State and animation executable checks passed separately. The shared retained builder generated an eight-node software preview. Default configuration does not include Arc, GLFW or native font modules. Source import checks passed. Local Maven publications/source JARs were generated; no remote publication occurred.

Tests cover the normative `(24,24)`/`(84,48)` geometry, all anchors, inverse transforms, padding/constraints/weights/baselines/flow/grid, scaled input, incremental layout, responsive identity, portals/clips, keyed state/focus, nested fractional scrolling, variable-height VirtualList bounds, button/content slots, selection/value/navigation controls, context menus/modals/toasts, paired input releases, exception-safe cleanup, IME simulation, semantics/actions, assets/retry/generations, text/reveal policies and typed visual transitions. Guide functions compile and execute in GuideExamplesTest; public extension lifetime executes in the separate sample module.

## Historical composite/native execution

The final combined command completed successfully in **11 seconds**:

```powershell
.\gradlew.bat -ParcPath=E:/Projects/arc-engine build verifyBoundaries :pixzel-common:stateChecks :pixzel-common:animationChecks :pixzel-demo:graphicsChecks :pixzel-demo:inputChecks publishAllPublicationsToLocalArtifactsRepository :pixzel-demo:run --args="--smoke --scale=1.25" --offline --console=plain
```

The adapter XML reports **9 tests**: seven real-font/text cases and two tessellation/budget cases, zero failures/errors/skips. Demo native fixtures are explicit JavaExec entry points rather than JUnit tests; the demo test task permits empty discovery intentionally. They were actually executed by the command above.

`graphicsChecks` verifies real glyph pixels, gradients, alpha/group overlap, shadows, source crops/tint, undersized nine-slice, stroke joins/caps, fractional DPI borders, independent corners, path holes/intersections, five nested/transformed exact clips, custom render operations/errors, actual application state/target restoration and a subsequent application draw. It also verifies:

- Static resubmission uploads zero bytes.
- 1,002 transformed/multicolor/translucent compatible solids become two draws and produce byte-identical pixels to separate submissions.
- CPU/GPU mesh cache eviction respects a deliberately tiny byte budget.
- 1,000 sessions delete their native program/buffer/framebuffer allocations, reset owned statistics and preserve borrowed images/custom lease cleanup.
- Two unshared contexts deliberately reuse numeric native IDs; stale submission/pass restore fails and stale cleanup leaves replacement texture/buffer/VAO/program/sampler/FBO/renderbuffer IDs alive. Owned images restore, borrowed images require rebind, and no-current cleanup performs no native calls.

`inputChecks` invokes the real installed GLFW callback objects in an actual application window. It verifies Cyrillic commits, repeat/order, UI pointer action without gameplay activation, masked polling, compatibility-handler wheel consumption, equal-priority gesture boundaries, repeats/releases after the consuming listener or Ui closes, fresh unblocked gestures and listener remount cleanup. It does not inject OS keyboard/mouse events. After the final navigation/demo callback adjustment, input checks and the showcase were run again successfully in **13 seconds**.

From the separate Arc checkout:

```powershell
.\gradlew.bat :arc-opengl:graphicsRegression :arc-opengles:compileKotlin :arc-extensions:arc-font:arc-font-native:test --offline --no-daemon --console=plain
```

**PASS, 13 seconds**. The native font provider reports **4/4 tests**. The Arc regression covers GPU streaming/readback and exceptional native state restoration, including inherited wireframe/rasterizer-discard state. GLES source compilation is not a GLES Pixzel runtime claim. Native font tests plus all Pixzel Jupiter tests total **125 methods**; native fixture assertions and executable state/animation checks are additional evidence, not included in that count.

## Runtime visuals and measurements

The real showcase ran eight frames per variant at scales **1, 1.25 and 2** with a **1100×800** window and at **320×240** with scale 1. Each completed with 77 retained nodes and zero measured nodes on the last frame. Captures were inspected: three different skins, Cyrillic ellipsis, UI over the scene, scaled typography and retained row-to-column reflow are visible. Small viewport content scrolls; it is intentionally clipped by its viewport.

Preserved evidence:

- [Independent preview](visuals/headless-preview.png)
- [Showcase scale 1](visuals/arc-showcase-1.0-1100x800.png), [scale 1.25](visuals/arc-showcase-1.25-1100x800.png), [scale 2](visuals/arc-showcase-2.0-1100x800.png), [small viewport](visuals/arc-showcase-1.0-320x240.png)
- [GPU appearance fixture](visuals/arc-graphics.png)
- [CPU protocol, percentiles, allocations and historical misses](PERFORMANCE.md)
- [Native 1080p five-clip/effect submission benchmark](ARC_GRAPHICS_BENCHMARK.md)

Final CPU warmup/sample counts are 300/500, plus 1,000 lifecycle cycles. Static HUD p95 is **0.137 ms**; live-data p95 is **1.895 ms**. Reveal shaping is zero during measured animation-only frames; VirtualList ends with 35 rows (at most 36 allowed). Every independent lifecycle cycle leaves zero listeners/sessions. The native clip/effect scene uses 100 warmup/300 samples, p95 **0.8475 ms CPU submission**, 17 draws and zero static uploads. GPU duration, native allocation counts and driver/device total-memory high-water are unavailable; documented storage estimates are not presented as those metrics.

## Delivery and remaining verification boundaries

The [Arc delivery](delivery/README.md) contains 37 tracked changes and 29 new files. Its binary patch/new-file/line-ending bundle was applied to a separately archived exact baseline; all 66 source hashes and 34 package checksums match. Arc index/HEAD/refs and original IDE/Vulkan files were preserved. The exporter and migration workflow are included.

Intermediate integration failures were repaired: a concrete demo screenshot dependency was made explicit; empty JUnit discovery for native fixture entry points was configured correctly; pointer/keyboard pairing, shutdown during cancellation, font versions, policy defaults and fixed-image invalidation have regression evidence. The successful results above supersede those failed intermediate commands. Existing KSP/Gradle deprecation, unnecessary-null-assertion and Windows OSHI performance-counter warnings do not fail these builds.

Manual OS interaction, live multi-monitor changes, Linux/macOS execution and native IME/screen-reader bridges were not performed/implemented here. The full optional/unsupported matrix is in [CAPABILITIES.md](CAPABILITIES.md). No unexplained mandatory feature omission remains within the required independent JVM and Windows desktop OpenGL scope; these platform boundaries are not inferred from compilation.
