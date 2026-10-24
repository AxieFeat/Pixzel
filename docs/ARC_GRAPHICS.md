# Arc graphics integration

`ArcUiBackend.of(context, arcTextService, supersampling = 2)` consumes Pixzel draw commands through neutral Arc APIs. The application selects Arc's implementations, creates a current context and owns its loop. Neither the backend nor Ui polls, presents, begins/ends the application frame or clears an application target.

The backend opens a scoped Arc pass over the application's actual target. It renders into one reusable, owned RGBA8/depth-stencil UI target, then composites onto that original target. Surface instances allocate no targets. Isolated groups and shadows use reusable targets; stencil clips belong exclusively to UI attachments. The application stencil is neither cleared nor borrowed.

Save/Restore retain affine transforms and clip depth. Clips increment/decrement the owned stencil and use the same flattened geometry as drawing. Quadratic/cubic paths flatten to the smaller of 0.01 logical dp and 0.2 physical supersample pixels. The pure core `PathGeometry` also supplies hit testing without Arc or AWT. Scanline trapezoids produce non-overlapping triangles for even-odd/nonzero fills, holes and intersections. Default two-times supersampling antialiases shape, path and clip edges. `RoundedCorners` supports independently fitted corner radii. Strokes support miter/bevel/round joins, a validated miter limit and butt/square/round caps. Overlapping stroke segments are unioned before alpha blending.

Solid, linear and radial brushes interpolate encoded sRGB. Input colors/textures use straight alpha and the shader converts once to premultiplied output. Composition targets already contain premultiplied colors. `ImageBrush` supports source cropping, nearest/linear sampling and an invertible affine mapping from source-image coordinates into local drawing coordinates. Core nine-slice painting emits ordinary ordered image commands.

`ArcImageHandle.borrowed(texture, size)` never deletes the application's texture. `ArcImageHandle.rgba(context, width, height, rgba)` owns its texture and copies a bounded restoration source (32 MiB per image by default, configurable through `sourceBudgetBytes`). `size` is the source pixel extent; optional `intrinsicSize` is the logical preferred size and defaults to `size`. Sampling uses separate Arc sampler objects, so nearest/linear selection does not mutate an application texture's parameters. CPU RGBA rows start at the top; render-target views flip vertically when composited. Image handles remain caller-owned and must outlive commands which reference them.

Native text uses the measured glyph IDs, font size, positions and baselines from `ArcTextService`. Glyph masks are prepared into cached 1024-pixel text tiles. Cached text/geometry survive frames; reveal offsets change the transform rather than reshaping, rerasterizing or uploading vertices. Session text textures use a 32 MiB/256-layout LRU. Geometry, image and solid-batch VBOs share a 32 MiB/512-entry LRU; flattened CPU geometry arrays have a separate 32 MiB/512-entry LRU. Both byte limits follow the optional meshBudgetBytes factory argument.

`ArcRenderSession.statistics` exposes framework draw calls, active bytes uploaded during submission, allocated target count/bytes, geometry entries, GPU mesh bytes, flattened CPU geometry bytes and cached text bytes. `RenderMetricsProvider` exposes independent nullable metrics to the inspector. Adjacent solid fills/strokes merge into ordered batches with per-vertex colors and captured affine transforms, including across Save/Concat/Restore when the clip is unchanged. Clip/group changes, gradients, images, text, shadows and custom operations flush the batch. The retained batch VBO is reused on later static frames. Each resulting mesh, clip, tile or composite issues one draw; `batches` counts those actual draw submissions. Custom native drawing cannot be measured by this executor; generic metrics become unknown (`null`) when a custom command runs. The Arc statistics still report the framework's own activity. A zero-size frame reports zero submission work while retaining its cache.

## Custom operations

A `CustomCommand` declares `operationId` and `requiredCapabilities`. Pass an immutable `ArcCustomRenderers` registry to the backend to enable `Capability.CUSTOM`; unregistered IDs and missing capabilities fail with the command index. A handler receives borrowed context/frame/transform, `emit` for normal ordered commands, and a session-owned `ResourceScope`. Every native texture unit it may change must appear in its `textureUnits` declaration. A nested Arc pass restores native state when the handler returns or throws; balanced save/group commands and a recursion limit are checked.

`emit` retains the current clip and transform. Custom native materials must themselves respect those values and the active UI target. Resource contents are not rolled back. Allocate cached resources into the provided scope; Arc GPU wrappers have generation-safe cleanup. Custom cleanup for raw native objects must perform an equivalent generation check. Handlers and injected services remain borrowed.

## Context lifetime and budgets

Arc's live `RenderContext.resourceGeneration` changes when the host installs capabilities for a different native context and becomes zero after capabilities are cleared. `ArcUiBackend.resourceGeneration` forwards it. A stale session rejects submission before drawing, and cleanup of Arc handles from an old/missing context releases CPU state without deleting numeric IDs in the replacement context. An open pass whose context changes rejects close and discards its old snapshot. Resources and passes remain bound to their creating thread.

`SimpleUi` reopens its render session at the next frame when backend generation changes, retaining tree/state/commands. Owned RGBA image handles lazily recreate from their retained bytes. Borrowed images fail explicitly until the application calls `rebind(replacementTexture, context = context)` after rebuilding its resource. Logical intrinsic metadata remains independent of replacement pixel size. A host must call `GL.createCapabilities()` after making a new context current and `GL.setCapabilities(null)` when no context is current. Arc's normal application close clears its capabilities. Detection/notification of a physical GPU reset is still a host responsibility; no automatic robustness/reset callback is claimed.

Limits are explicit: 64 gradient stops, 8192 flattened/outline points, 4096 non-horizontal edges and 4096 scan levels per shape, 4,194,304 edge/level work units, 262,144 output vertices, 255 clip levels, eight nested composition groups, a 256 MiB individual UI color/stencil target and a configurable 512 MiB aggregate target pool. Solid batches split at 65,532 vertices. Dense intersecting paths fail before unbounded output allocation. Resize discards cached group/effect targets before budgeting their replacements. Arc also checks the device's maximum texture dimension. Budget/unsupported brush/shape/command failures include diagnostics instead of silently omitting drawing. Select supersampling 1 when target budgets require it. OpenGL GLSL 4.10 is the verified shader dialect; the Arc GLES implementation compiles, but this adapter has no GLES/ANGLE/Vulkan shader implementation.

## Verification

On Windows 11, Oracle JDK 21.0.10, Gradle 9.6.1, Kotlin 2.4.10, NVIDIA GeForce RTX 4060 Ti, driver 617.42, OpenGL 4.1.0:

```powershell
.\gradlew.bat :pixzel-demo:graphicsChecks :pixzel-common:test --tests pixzel.PathGeometryTest verifyBoundaries --offline --no-daemon --console=plain
```

The hidden desktop check verifies rendered Cyrillic, even-odd holes, transformed and independently rounded clips, linear/radial gradients, nearest image orientation/brush mapping, stroke caps/joins, custom operation dispatch/error restoration, overlapping group opacity, shadows, application target/viewport/depth/raster restoration (including inherited line mode and rasterizer discard), the next application draw and zero uploaded bytes on the second static frame. It writes `pixzel-demo/build/reports/arc-graphics.png` for visual inspection. The acceptance scene uses 23 draws, four targets (14,745,600 bytes) and 62,524 text texture bytes; its second frame uploads zero bytes. One run closed 1000 GPU sessions in 2467 ms, checked native program/VBO/FBO deletion on every cycle and custom resource leases and verified a target-budget failure preserved application state. These are rendering/lifetime checks, not the 1000-element UI performance benchmark.

The same fixture creates two fresh, unshared native contexts and deliberately allocates colliding texture, buffer, VAO, program, sampler, framebuffer and renderbuffer names. It verifies stale submission/pass rejection, survival of replacement objects after old cleanup, owned image pixel restoration, explicit borrowed rebind, and cleanup with no current context/capabilities. `PathGeometryTest` checks winding/even-odd holes, self-intersection, curves, independent corners and hit testing through a rotated path clip.

Arc's companion `:arc-opengl:graphicsRegression` verifies native GPU upload readback, empty/repeated draw-buffer finalization, grow/reuse/close, exceptional state restoration including program/texture/sampler/VAO/buffer bindings, empty scissor, stencil-mask pixels, target resize/cleanup and legacy framebuffer composition. GLES source compilation is checked separately; compilation does not establish native execution support.

## Instance capability metadata

`BackendCapabilities.limits` contains nullable, validated values. Arc reports the current device maximum texture dimension, clip depth 255, gradient-stop/edge/vertex budgets, RGBA8 and RGBA8_DEPTH24_STENCIL8 target formats, and GRAYSCALE_GLYPH_MASK text rendering. Unknown metadata stays null in recorders/other implementations. Arc's neutral `RenderCapabilities.maxTextureDimension` is queried once per context generation.

`RasterState()` normalizes polygon fill, rasterizer discard, logic/sample coverage/mask enable state and user clip-distance enables before UI rendering and final composition. Scoped restoration preserves the application's values, including exceptional custom operations. GLES supports its native subset and rejects unsupported non-fill/logic/user-clip requests explicitly.

The GPU fixture also compares 1002 transformed/multicolor/translucent solid commands batched into two draws against 1003 separate submissions, requiring byte-identical framebuffer pixels and zero static uploads. A separate 512-byte cache test forces bounded GPU mesh/CPU geometry eviction. The dense 181-edge star regression proves intersection/output budgets fail descriptively; ordinary intersecting fills remain supported.
## 1080p clip/effect renderer baseline

Run the optional native benchmark with:

```powershell
.\gradlew.bat :pixzel-demo:graphicsChecks --args=--benchmark verifyBoundaries --offline --no-daemon --console=plain
```

The recorded run uses a 1920x1080 offscreen application target, scale 1, supersampling 2, vsync 0, 100 warmup frames and 300 measured retained frames on the hardware above. Five nested rounded clips surround an isolated opacity group, a blurred/spread shadow and 1000 transformed multicolor rectangles. Timing covers CPU `RenderSession.submit`; application clear, poll, present and final `glFinish` are outside the interval. Submission includes driver backpressure.

| Observation | p50 | p95 | p99 |
|---|---:|---:|---:|
| CPU submit, ms | 0.5171 | 0.8475 | 1.072 |
| JVM allocation, bytes/frame | 236024 | 237856 | 237856 |

Each measured frame submits 17 draws/batches and 10,647 vertices, switches textures four times and uploads zero bytes. Four pooled targets reserve 199,065,600 bytes; VBOs reserve 236,100 bytes; the tracked GPU storage estimate reaches 199,301,700 bytes. Flattened CPU arrays use 36,840 bytes. These estimates exclude driver padding, shader storage, borrowed scene resources and native font memory. Native allocation counts, total driver GPU memory and GPU timer-query duration are unavailable. The [full recorded run](ARC_GRAPHICS_BENCHMARK.md) preserves flags and observations. This renderer result is separate from the independent UI/layout benchmark and does not establish an entire application's frame latency.

The final fractional-DPI fixture renders at scale 1.25 and asserts a one-physical-pixel border, proportionally reduced nine-slice borders in an undersized destination, cropped sprite tint/alpha applied once, and inside/outside coverage through five nested rounded clips. It writes `pixzel-demo/build/reports/arc-graphics-scale125.png`. These pixel assertions run with ordinary `graphicsChecks`; the optional `--benchmark` flag is only needed for timing output.