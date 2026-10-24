# Arc: verified sources and Pixzel style


## Review snapshot


Repository `AxieFeat/Arc`, branch `dev`, commit `419619a2ec7147e76d9b696af823d0ecdeb3e684`. The latest commit is `Move Scene api to new Arc-extra module`. The review covered the project tree and key API/common, OpenGL/GLES/Vulkan, GLFW, font, input, display, demo and annotation-processor classes. This is a targeted UI architecture review, not a claim of inspecting all 275 Kotlin files line by line.


No build was run. These are **values recorded in repository files**, not independently verified artifact availability: Java 21, Kotlin 2.4.10, LWJGL 3.4.2, Gradle wrapper 9.6.1.


## Patterns to follow


| Arc observation | Pixzel application |
| --- | --- |
| `arc-core` contracts; `arc-common` shared implementations | `pixzel-core` and `pixzel-common` |
| `arc.graphics`, `arc.shader`, `arc.font`, `arc.input.mouse` | Short `pixzel.graphics`, `pixzel.text`, `pixzel.input` |
| `Color` and `SimpleColor` share `arc.util` across modules | `Surface` and `SimpleSurface` share `pixzel.surface` |
| Concrete implementations are mostly `internal` | `internal SimpleElement` with a public contract/factory |
| `GlX`/`GlesX`/`VkX` and `arc.gl.*` | `ArcUiBackend` and `pixzel.arc.*` adapters |
| Reusable `AbstractGlfwWindow` base | Optional public `AbstractElement` base for external components |
| Meaningful `EmptyTexture`/`EmptyShaderInstance` objects | `EmptyPainter` only for valid empty semantics |
| Nested Factory/Provider; companion of/from/builder | Similar forms with explicit providers where needed |
| Small `assetExtras.kt`/`objectProviderExtras.kt` files | `surfaceExtras.kt`/`layoutExtras.kt`, not a miscellaneous Utils dump |
| English KDoc, require/check, named arguments | Preserve these in API and diagnostics |
| `Cleanable : AutoCloseable`, cleanup(), use | Independent Pixzel equivalent and an adapter bridge |


The style is short domain packages and explicit layers. Maven group `arc.engine` does not imply a `arc.engine.*` Kotlin package. Proposed Pixzel Gradle group: `pixzel.engine`; this is a design proposal, not an existing namespace.


Do not imitate incidental exceptions such as the repeated `extra` in `arc.extra.graphics.extra`. A few public concrete Arc classes do not invalidate its general internal-implementation approach.


## Factories and annotations


`Arc.single<T>()`/`Arc.factory<T>()` use a global ObjectProvider. `ArcObjectProvider.install()` installs it through reflection; bootstrap registers implementations. This is an Arc integration entry point, not a dependency for Pixzel core. Pass Pixzel factories/services to each Ui; two Ui instances can use different implementations simultaneously.


`@TypeFactory` is a processor-checked marker: a nested interface, `@ApiStatus.Internal`, creation methods, no properties. It does not automatically generate the factory implementation.


`@ImmutableType` is not a general immutable-state marker: its class validator requires a data class and `@JvmRecord`; the interface validator has JVM naming rules. Do not import Arc annotations into pixzel-core or apply them mechanically. Add an independent marker only if it provides useful validation. Sealed/value classes may be immutable without it.


## Verified APIs and UI implications


| Verified finding | Consequence |
| --- | --- |
| Application exposes `renderContext` | Use the actual name; README still has `renderSystem` examples |
| RenderContext has binding, viewport, depth/cull/blend but no scissor, state snapshot, stencil/mask or typed blend state | Add neutral Arc contracts; do not call GL directly from Pixzel |
| clear/blend methods accept raw numbers | Do not expose raw graphics enums through Pixzel; use typed boundaries |
| Drawer draws an entire VertexBuffer and has a matrix overload | Different clip/material batches need separate buffers or a range API |
| DrawBuffer is CPU storage; build() creates a GPU vertex buffer | Do not build per element/frame; implement streaming |
| NativeDrawBuffer allocates `bufferSize * 4` bytes and checks capacity | Calculate bytes from stride; the argument is not a guaranteed vertex count |
| NativeDrawBuffer has a shared companion temporary vector | Do not assume parallel instances have independent scratch |
| GlVertexBuffer.write maps/copies capacity using `GL_MAP_UNSYNCHRONIZED_BIT` | Audit ownership, active byte ranges and GPU synchronization |
| GlyphFont.prepare returns VertexBuffer; no text metrics/layout API | Separate measurement, shaping and drawing |
| SimpleGlyphFont iterates Char, finds glyphs linearly and advances by bitmap width | Full shaping/kerning/wrapping/bidi is absent; add a compatible new pipeline or replace it |
| Window has one handler and clipboard | Add lifecycle-safe subscriptions/multiplexing; reuse clipboard |
| AbstractGlfwWindow stores framebuffer dimensions in width/height; cursor callbacks use window coordinates | These spaces differ on HiDPI/Retina |
| GLFW input installs callbacks and dispatches bindings through a thread pool | UI needs ordered delivery and UI-thread mutations |
| GLFW input reduces action to `action == GLFW_PRESS` | Preserve PRESS/REPEAT/RELEASE semantics |
| WindowHandler lacks text/composition events | Add text events; KeyCode is not Unicode input |
| VkDrawer has TODO; many VkRenderContext methods are empty | README cannot establish production Vulkan support |
| GlfwGlWindow requests OpenGL 4.1 | The primary Pixzel implementation must fit that feature level |
| Display is a 3D virtual area with matrix/virtual mouse | Potential world-space adapter; not the definition of Surface |


Several build scripts, including arc-font-core, arc-font and arc-input-core, are empty despite source imports of Arc API types. This is a configuration observation, not a verified build-failure log. Establish the actual dependency graph and fix necessary declarations during implementation.


## Source references


All links below pin the inspected commit. Paths after `blob/<sha>/` identify actual source files.


- [S01: settings and module structure](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/settings.gradle.kts)
- [S02: API philosophy in README](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/README.md)
- [S03: Application](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/arc-core/src/main/kotlin/arc/Application.kt)
- [S04: RenderContext](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/arc-core/src/main/kotlin/arc/graphics/RenderContext.kt)
- [S05: Drawer](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/arc-core/src/main/kotlin/arc/graphics/Drawer.kt)
- [S06: NativeDrawBuffer](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/arc-common/src/main/kotlin/arc/graphics/NativeDrawBuffer.kt)
- [S07: GlVertexBuffer](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/backend/render/arc-opengl/src/main/kotlin/arc/gl/graphics/vertex/GlVertexBuffer.kt)
- [S08: GlyphFont](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/arc-extensions/arc-font/arc-font-core/src/main/kotlin/arc/font/GlyphFont.kt)
- [S09: SimpleGlyphFont](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/arc-extensions/arc-font/arc-font-simple/src/main/kotlin/arc/font/SimpleGlyphFont.kt)
- [S10: Window](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/arc-core/src/main/kotlin/arc/window/Window.kt)
- [S11: AbstractGlfwWindow](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/backend/window/arc-glfw/src/main/kotlin/arc/window/glfw/AbstractGlfwWindow.kt)
- [S12: GlfwInputEngine](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/arc-extensions/arc-input/arc-input-glfw/src/main/kotlin/arc/input/GlfwInputEngine.kt)
- [S13: GlfwKeyboardInput](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/arc-extensions/arc-input/arc-input-glfw/src/main/kotlin/arc/input/keyboard/GlfwKeyboardInput.kt)
- [S14: ArcObjectProvider](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/arc-common/src/main/kotlin/arc/ArcObjectProvider.kt)
- [S15: FactoryChecker](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/arc-annotation-processor/src/main/kotlin/arc/annotation/processor/factory/FactoryChecker.kt)
- [S16: ImmutableType](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/arc-annotations/src/main/kotlin/arc/annotations/ImmutableType.kt)
- [S17: VkDrawer](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/backend/render/arc-vulkan/src/main/kotlin/arc/vk/graphics/VkDrawer.kt)
- [S18: VkRenderContext](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/backend/render/arc-vulkan/src/main/kotlin/arc/vk/graphics/VkRenderContext.kt)
- [S19: GlFrameBuffer](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/backend/render/arc-opengl/src/main/kotlin/arc/gl/shader/GlFrameBuffer.kt)
- [S20: Display](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/arc-extensions/arc-display/arc-display-core/src/main/kotlin/arc/display/Display.kt)
- [S21: convention plugin](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/build-logic/src/main/kotlin/arc.base.gradle.kts)
- [S22: Detekt rules](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/config/detekt.yml)
- [S23: dependency catalog](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/gradle/libs.versions.toml)
- [S24: GlfwGlWindow, target 4.1](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/backend/render/arc-opengl/src/main/kotlin/arc/gl/window/GlfwGlWindow.kt)
- [S25: demo main/bootstrap](https://github.com/AxieFeat/Arc/blob/419619a2ec7147e76d9b696af823d0ecdeb3e684/arc-demo/src/main/kotlin/arc/demo/Main.kt)


See [09_ARC_EXTENSION_PLAN.md](09_ARC_EXTENSION_PLAN.md) for related decisions and Arc changes. README describes intent; implementation takes precedence when assessing readiness.