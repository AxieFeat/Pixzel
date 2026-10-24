# Required Arc extension plan


Extending Arc is an authorized, necessary part of Pixzel implementation. Add general engine facilities, not Node/Button/Surface/Theme types in arc-core. Verified source references are in [02_ARC_ANALYSIS_AND_STYLE.md](02_ARC_ANALYSIS_AND_STYLE.md).


Check the current dev branch before changes. The following is a design plan, not existing API documentation.


## ARC-01. Build and dependencies


Scope: settings, build-logic and participating module build files.


The reviewed Arc targets Java 21; obtain Kotlin/KSP/LWJGL/Gradle versions from current files. Some font/input-core build scripts are empty despite Arc-type imports. Verify the real dependency graph, build baseline and required api/implementation declarations. Distinguish network/credential failures, existing Arc failures and regressions.


Do not upgrade everything unnecessarily. If versions are unavailable/incompatible, make the smallest justified fix, preserve the target and document migration.


**Done:** reproducible commands, declared dependencies, compatible versions and accurate build results. An empty build file alone is not a verified failure log.


## ARC-02. Typed render state and scoped passes


Scope: arc-core's arc.graphics.RenderContext, neutral state/pass types, GlRenderContext and GlesRenderContext.


Add typed blend factors/equations, depth state/write, culling, write masks, viewport/scissor and targets. New APIs take no numeric GL constants. Existing methods may remain deprecated bridges.


A scoped pass/state guard restores changed target, viewport/scissor, shader/pipeline, textures/samplers, bindings and depth/cull/blend/write masks, including exceptions. State caching is allowed; no glGet per UI command. Every Arc setter updates tracking.


Define an explicit invalidate/synchronize boundary for raw graphics calls. Ordinary Arc operations must restore reliably; an inconsistent cache cannot provide a correct snapshot.


**Done:** scene→UI→next scene draw preserves behavior. Pixzel neither downcasts RenderContext nor calls GL directly.


## ARC-03. Scissor and exact masks


Scope: neutral arc-core graphics contracts and supported render backends.


Add explicit scissor enable/disable, origin and render-target pixel units. An empty area draws nothing. Pixzel intersects logical clips; the adapter converts to backend pixels.


Add stencil/mask APIs or a generic target-based alpha-mask pipeline for rounded/rotated/path clips. A rectangular AABB is not an exact mask. Do not clear application stencil without ownership; allocate bits/targets or a separate alpha mask.


**Done:** nested scrolling/masks, empty intersections, rotation and DPI pass visual checks; input shares clip geometry. Unimplemented backends declare no support.


## ARC-04. Window metrics and DPI


Scope: arc.window, AbstractGlfwWindow and other window implementations as applicable.


Separate logical window size, framebuffer pixels, X/Y content scale, focus/minimized state and a consistent versioned metrics snapshot.


GLFW currently stores framebuffer dimensions in Window.width/height while cursor events use window units. Do not silently redefine existing properties. Add unambiguous APIs, update viewport/camera callers and document compatibility/deprecation.


Add independent cancellable subscriptions. Keep Window.handler as a compatibility listener; UI must not replace the game's handler. Distinguish logical resize, framebuffer resize and content-scale events.


**Done:** Retina/scales 1/1.25/2, monitor changes and zero framebuffer work; pointer/render coordinates agree; subscriptions clean up.


## ARC-05. Ordered events and input contexts


Scope: arc-input-core, arc-input-glfw and shared window event registration.


Add a raw stream: pointer/wheel, key PRESS/REPEAT/RELEASE, modifiers, Unicode commit, focus/cancel, sequence/time and window identity. Deliver original order on the UI/render thread. Legacy thread-pool binding handlers are not UI transport.


One owner/multiplexer installs callbacks without erasing other listeners. Retain/free callback objects by lifetime. Preserve or explicitly migrate existing async binding mode.


Add context priorities, consumption and a masked gameplay polling view. Consumed UI input activates no gameplay bindings, and masked polling does not expose UI-blocked keys. Physical state remains a separate opt-in API.


Do not reconstruct text commit from KeyCode. GLFW char callbacks provide Unicode commit, not full OS IME composition. Composition/candidate rectangles are separate platform capabilities.


**Done:** same-frame down/up, correct repeat, Cyrillic TextField, UI clicks that do not break world blocks, and hooks/cleanup without duplicate callbacks or damaged WindowHandler.


## ARC-06. Font metrics, shaping and glyph pipeline


Scope: arc-font-core and implementations; new arc-font-common/arc-font-native modules if needed, following extension structure.


Provide FontFace/Family, metrics, glyph IDs/advances/offsets, shaped runs, cluster maps, rasterized glyphs and atlas handles. Use arc.font, arc.font.layout and arc.font.shaping, not arc.font.core.


Preserve GlyphFont as a bitmap compatibility API or deprecated bridge. GlyphFont.prepare creates VertexBuffer and is not a full measurement/layout service.


Pixzel 1.0 requires a real TTF/OTF provider. Confirm HarfBuzz/FreeType or alternatives through an ADR after checking bindings, licenses, packaging and quality. Codepoint rasterization without shaping is not complete Unicode layout.


Declare legacy limitations. If using SimpleGlyphFont, check empty patterns, atlas overflow, maximum texture size, fallback and linear lookup; do not invent baseline from bounding boxes. The new path consumes positioned glyphs and batches them.


**Done:** consistent measure/draw; Cyrillic/kerning/fallback/cluster tests; bounded caches; no per-frame reveal meshes; native cleanup.


## ARC-07. Streaming buffers and safe uploads


Scope: DrawBuffer/VertexBuffer, NativeDrawBuffer/GlVertexBuffer and, if needed, a new streaming API.


Separate capacity, initialized/active byte range and vertex count. Old bufferSize is not guaranteed vertex capacity: NativeDrawBuffer allocates bufferSize*4 bytes.


Reuse GPU buffers safely through orphaning, rings/fences or another correct mechanism. Never blindly overwrite UNSYNCHRONIZED memory still read by the GPU. Upload active bytes rather than arbitrary capacity.


Check empty/build/clear, growth, overflow, double cleanup and CPU native ownership. GlVertexBuffer retains the original ByteBuffer reference; the new path cannot depend on freed memory. NativeDrawBuffer's shared companion temporary vector is not thread-safe worker scratch.


Draw-range/indexed paths are desirable. Several reusable batch buffers can temporarily substitute; per-element VertexBuffer allocation cannot.


**Done:** resize/text stress without corruption, bounded native allocation, correct synchronization and active-byte/upload-count statistics.


## ARC-08. Render targets and compositing


Scope: FrameBuffer and implementations.


GlFrameBuffer.render currently selects the default target and clears color/depth. It is not a general overlay-compositing blit.


Add target binding/color-attachment views and blit/composite without unsolicited clear, restoring the previous target. On resize, actual attachments and textureWidth/textureHeight must agree. Shared fullscreen geometry has explicit owner/context lifetime.


Group opacity, shadows and masks use a budgeted transient-target pool. Do not allocate a framebuffer per Surface. Adapters never delete borrowed targets/textures.


**Done:** effects preserve the background; nested targets restore; resize reports current dimensions; repeated cleanup is safe.


## ARC-09. Capabilities and backend status


Declare actual implemented operations and verify them when opening a session/probing.


| Backend | 1.0 condition |
| --- | --- |
| Arc/OpenGL 4.1 | Required working integration with all basic appearance features |
| Headless non-Arc | Required contract/reference path |
| Arc/GLES/ANGLE | Compatible contracts and accurate status; production claims require tests |
| Arc/Vulkan | Unavailable/experimental until VkDrawer/TODO and empty state methods are implemented |
| World-space Display | Optional adapter |


Completing Vulkan is outside mandatory Pixzel 1.0 scope. No-op methods cannot simulate readiness.


## Order and migration


The first interactive demo needs ARC-01 and minimal ARC-02…07. Complete 1.0 also requires mask/compositing portions of ARC-03/08 and the ARC-09 matrix.


Deliver each patch as problem→neutral contract→implementations→regression checks→migration. Arc must not depend on Pixzel. Unrelated refactors and wholesale upgrades are outside scope.