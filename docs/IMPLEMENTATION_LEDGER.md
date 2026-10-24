# Pixzel implementation ledger

Status: required independent JVM and desktop Arc/OpenGL scope implemented and verified. No external blocker or unexplained mandatory API omission remains. Optional/platform boundaries are explicit in [CAPABILITIES.md](CAPABILITIES.md). Final acceptance and exact evidence are in [REQUIREMENTS_STATUS.md](REQUIREMENTS_STATUS.md) and [VERIFICATION.md](VERIFICATION.md).

## Baseline and preservation

- Pixzel initially contained only an unborn local Git repository; source/build/test modules were created here. The GitHub repository's existing `master` history was later fetched and retained as the parent of `dev`.
- The Arc implementation baseline is `419619a2ec7147e76d9b696af823d0ecdeb3e684`, exactly the specification basis.
- Original Arc `.idea/` files and `backend/render/arc-vulkan/src/main/kotlin/arc/vk/vk.kt` were preserved and excluded from delivery and the integration commit. User changes to their staging state were left intact.
- All attached specification documents and the actual pasted execution request were read before architectural decisions. Copies of the original specification remain under `docs/spec`.
- No applicable AGENTS.md was found. Toolchain: Oracle JDK21.0.10, Gradle9.6.1, Kotlin2.4.10, LWJGL3.4.2; existing compatible versions were retained.
- Selected Arc baseline compile succeeded offline before edits: 29 actionable tasks, 6 executed/23 up-to-date, 25seconds. Empty font/input scripts inherit real convention dependencies and were not misreported as baseline failures.
- Initial sandbox Gradle cache-lock restrictions required reviewed ordinary build access. Several account-quota approval-review interruptions prevented actions; those actions were resumed after the user's Continue instructions. They were not bypassed.

## Decisions and shared contracts

1. Separate Pixzel Gradle project with Arc's included `build-logic`, shared version catalog and typed project accessors. Every module is always included; Arc resolves from GitHub Packages at the pinned commit version. Core/common remain independent of Arc. Adapter imports only neutral Arc APIs. Application selects concrete OpenGL/GLFW/native fonts.
2. Retained public Elements and local component factories; direct API and typed DSL share runtime semantics. Explicit tracked bindings/keyed regions, no implicit whole-function recomposition. External samples compile separately.
3. Read-only nested Surface coordinates, X right/Y down, logical dp. Pointer divides by UI scale; framebuffer scale applies once at raster boundary. Logical parent controls inheritance/lifetime/routes; portals change geometric attachment only.
4. Public two-phase layout, immutable paint traversal, ordered independent draw commands and per-node paint caches. Surface does not allocate a framebuffer. Renderer batches adjacent compatible solids and flushes at material/clip/target boundaries.
5. Production FT/HB text uses measured positioned glyph data; deterministic independent font is an explicit fixture. Static overflow and timed reveal are replaceable independent strategies, with theme/scope/instance defaults and per-label running clocks.
6. Ordered capture/target/bubble plus default action. Consumed gesture repeats/releases remain blocked from lower gameplay layers even after the consuming listener closes. Physical polling remains separate. Focus scopes mask held gameplay inputs and restore focus.
7. Injected services are borrowed; sessions/tree/leases are owned. Detach and disposal differ. Cleanup aggregates errors. Generation-aware native cleanup cannot delete replacement-context IDs; owned image sources restore and borrowed handles require rebind.
8. Maven coordinates `pixzel.engine:<module>:<commit>`, with source JARs, a local artifact repository and a separate GitHub Packages publication target. Project dependencies and publication versions match. Optional native/platform support is not inferred from interfaces or source compilation.
9. Kotlin sources follow original Arc formatting: four-space indentation, eight-space continuations, explicit imports, separate statements, multiline KDoc and multiline blocks. Pixzel includes `.editorconfig` and a shared IntelliJ formatter profile. Import collisions use meaningful aliases; native binding functions use explicit imports. Logical function stages are separated by blank lines, with concise explanations for nontrivial algorithms.

## Workstreams and independent review

The lead owned shared contracts/build/layout/tree/input/integration. Three bounded workstreams owned state/runtime/reference/performance, text/font/assets/DSL, and Arc graphics/streaming/targets. Ownership was disjoint and returned before integration. Non-author source review produced concrete regressions, which were repaired and tested rather than accepted from summaries.

Important resolved findings include inherited wireframe/discard state, stale native ID deletion, tessellation output/cache budgets, callback/cleanup failures, minimized-frame closure resurrection, mutation before owner validation, stale text provider generations, IME shorter-preedit/caret mapping, themed label policies, slot accessible-label corruption, fixed-image remeasurement, context-menu cleanup and paired-key release leakage.

## Integrated evidence

The earlier composite-build timings below are historical. The later convention-plugin migration passed the independent checks and a full Maven-artifact build with real GPU/input fixtures. Arc's readability/publication changes passed Detekt and native regressions before entering `master`; [verification](VERIFICATION.md) records the current Maven checks.

- Final independent build/boundaries/state/animation/preview/local publications: PASS, 10 seconds, 112 Jupiter methods (common 109, reference 2, sample 1), zero failures/errors/skips. Default build does not configure Arc.
- Final full composite build, boundaries, executable checks, GPU/input fixtures, local publications and scale 1.25 showcase: PASS, 11 seconds. All 9 adapter methods pass. Native fixture entry points execute explicitly, separate from empty JUnit discovery in the demo.
- Final Arc standalone graphics regression/GLES compilation/native fonts: PASS, 13 seconds; native font tests 4/4. Total Jupiter count: 125; executable and GPU fixture assertions are additional.
- Native callback and showcase rerun after the final navigation/demo callback adjustment: PASS, 13 seconds.
- Actual GPU checks: exceptional scene state/target preservation, stencil clips/paths/holes, fractional borders, source crop/tint/undersized nine-slice, text/gradients/group/shadow/custom operations, 1,000-session cleanup, two unshared contexts with deliberate numeric ID collisions and no-current cleanup.
- Adjacent 1,002 compatible solids reduce to 2 draws, pixel-identical to separate submissions. Static resubmission uploads 0 bytes.
- Native showcase: 8 frames at scales 1/1.25/2 and 320×240; captured/visually inspected, lastMeasured=0, 77 retained nodes. Independent preview and native captures are preserved under `docs/visuals`.
- Release CPU benchmark: 300 warmup/500 sampled frames and 1,000 lifecycle cycles. Static p95=0.137 ms, live 100 updates/10,000 nodes=1.895 ms, text=0.087 ms, virtual=0.559 ms, lifecycle=0.278 ms. Both reference budgets met. Static measures=0, reveal shapes=0, 35 final rows/max 36, listeners/sessions=0 after every cycle. Historical misses/JFR evidence remain under `docs/performance`.
- Native five-clip/group/shadow/1,000-rectangle 1080p benchmark: 100 warmup/300 sampled frames, CPU submit p95=0.8475 ms, 17 draws, static uploads=0, tracked GPU storage estimate=199,301,700 bytes. GPU duration/native allocation count and driver total-memory measurement remain unknown.
- Final Arc source bundle: 37 tracked changes + 29 new files. All 66 source hashes and 34 package checksums pass; patch application to a separate baseline archive and unchanged Git index/HEAD/refs were verified. The lead independently rechecked all 66 current source hashes and ZIP digest.

## Delivered and remaining boundaries

README/build/run instructions, architecture decisions, tested public guide, capability/requirements matrix, performance/verification records, original specification, visual evidence, local artifacts and Arc migration/export bundle are delivered. No known mandatory work is left pending for the supported scope.

Unimplemented optional work: native OS IME/screen-reader bridges, Pixzel GLES/ANGLE/Vulkan shaders, native multitouch/gamepad adapters, mobile/Kotlin Native, world-space UI, rich/multiline editing, SVG/CSS, docking/tables and reference-resolution FIT/FILL/STRETCH. Single-line editing rejects styled editing spans explicitly. Linux/macOS native execution, live multi-monitor changes and manual OS input walkthroughs are unverified; controlled fixtures validate the relevant contracts. Physical GPU reset detection remains host-owned.
