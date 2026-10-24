# Lifecycle, performance and verification


## Lifecycle and ownership


Ui: CREATED→ATTACHED→ACTIVE↔SUSPENDED→CLOSED. Closing is final/idempotent; subsequent frame calls fail clearly.


Application-supplied host/backend/text services are borrowed by default. Session allocations/UI-created resources are owned. Other ownership policies are explicit. Closing Ui does not automatically close shared external backends/font services. Close Ui before destroying the graphics context; callers close owned external services afterward while the context remains alive.


Element: create/configure→attach→layout/input/paint→detach/reparent→dispose. Detach differs from disposal and may preserve reusable subtrees. Detached nodes neither draw nor receive input; scoped subscriptions/tasks pause or close under declared policy. Disposal releases bindings/listeners/owned resources.


Context loss invalidates GPU generations and initiates restoration. Do not run native cleanup after context destruction.


## Frame pipeline


Pixzel does not call beginFrame/endFrame/pollEvents/swapBuffers. Inside the application frame:


1. Apply queued commands, ready resources and metrics snapshot.
2. Resolve environment/theme/state and required layout.
3. Process ordered input. If a handler changes geometry, lay out before the next positional event.
4. Update animation clock and resulting invalidations.
5. Reconcile hover/capture/focus with geometry and bound cycles.
6. Prepare/cache paint data; upload changed bytes.
7. Submit to the target in a scoped UI pass.
8. Complete deferred disposal and statistics.


Alternative schedulers must preserve current-geometry hit testing, immutable geometry during draw traversal and event ordering. Limit layout↔hover↔style loops and report dependency chains.


## Resources


Distinguish JVM managed memory, CPU native memory, GPU resources and borrowed handles. Track owner/generation/cleanup.


- Budget/evict image and font caches.
- Style/hover creates no per-element shader/buffer.
- Persistent buffers grow under defined policies; reuse scratch.
- Delete GPU resources after their final in-flight use.
- Ignore disposed/stale async results and release temporary bytes.
- Painter/upload exceptions leave no open pass/lease.
- Shared themes hold no per-instance animation.
- Effect-free Surfaces allocate no targets.


## Invalidation


Stable static UI performs no tree rebuild, measure/arrange/shaping or GPU-resource recreation. Submission may repeat each game frame.


Marquee changes paint offset. Color changes do not measure. One counter update does not rebuild a list. Offscreen subtrees may skip paint; visibility policy governs clock/animation continuation.


Do not promise zero allocation under all conditions. Separate library steady-state allocations from backend/driver allocation. Measure value-class boxing in generics.


## Benchmark protocol


Record reference hardware, OS/JDK/GPU/driver, resolution/DPI, warmup/measurement interval, flags and vsync. Separate CPU update/layout/paint preparation from GPU submit/present. Report p50/p95/p99, bytes/frame and native/GPU high-water marks, not just FPS.


| Scene | Load | Measure |
| --- | --- | --- |
| Static HUD | 1,000 visible simple elements, 10,000 total nodes | CPU/allocations, no repeated layout, draw calls |
| Text menu | 300 labels, 50 reveal animations | Shape count/cache, animation-only cost |
| Live data | 100 updates/frame in 10,000 nodes | Local invalidation, p95 |
| VirtualList | 100,000 items, about 30 visible rows | Materialized count, latency, memory |
| Clips/effects | Five nested clips, group opacity/shadows | Flushes, target pool, CPU/GPU |
| Lifecycle | 1,000 mount/close/resize cycles | No accumulated listeners/resources/native memory |


Initial reference-desktop targets at 1080p: Static HUD CPU work excluding present p95≤2 ms; live scene≤4 ms. These are engineering budgets, **not measurements or universal guarantees**. After the prototype, establish a realistic ADR baseline; profile/explain deviations.


VirtualList materialization is bounded by visible+overscan+documented cache. Model/height caches may grow with item count; materialized nodes must not reach 100,000.


## Test infrastructure


TestHost/RecordingBackend/FakeClock run without Arc, native libraries or windows. Recording tests assert observable order, clipping, geometry and input results rather than internal-tree hashes.


Provide a minimal independent software rect/image/text/clip raster preview with declared limits. Use pinned fonts/providers for goldens, not arbitrary system substitution.


Compile external component/policy samples in a separate module. Dependency checks forbid arc/LWJGL/JOML/AWT in core/common and concrete renderer/window imports in pixzel-arc.


## Meaningful checks


| Level | Scenarios |
| --- | --- |
| Unit | Constraints, anchors, units, bindings, reveal phases |
| Property | Invertible-transform round trips, clip intersections, finite sizes |
| Layout | Padding/min/max/unbounded, weights/grid, resize/DPI, portals |
| Input | Same-frame down/up, repeat, capture/cancel, modal focus, consumption |
| Text | Graphemes/caret/ellipsis, fallback/kerning, no reveal reshaping |
| Resources | Borrowed ownership, cleanup, eviction, stale async results |
| Backend | Painter order, exact clips, alpha/groups, state/target restoration |
| Integration | Arc/OpenGL scene→UI→next scene draw |
| Visual | Three skins, paths/masks/gradients/text, scale 1/1.25/2 |
| Lifecycle | Mount/detach/reparent/dispose/context destruction |
| Public API | DSL/direct samples and external components without internal access |


Do not mirror private setters merely to inflate coverage.


## Inspector and diagnostics


Provide an inspector overlay that can be turned off. It shows selected element/path, border/content bounds, anchor/pivot, clips, constraints, measured size, focus/capture, state and invalidation reasons.


Statistics: total/materialized/visible nodes, measured nodes/frame, text-cache hits/misses, draw calls/batches, vertices/active uploaded bytes, texture switches, transient targets and owned resources. Disabled inspection has minimal overhead.


Errors identify element path/parameter. Unsupported operations are not ignored. Suppress repeated per-frame diagnostic spam.


Separate automated, manual, static and environment-blocked checks in delivery. Compilation is not visual QA; a Vulkan interface is not a working implementation.


By default, SUSPENDED freezes the UI clock. Resume does not feed accumulated wall-clock dt into animations. An independent real-time notification timer is an explicit policy. Use one consistent Duration/time-source model.