# Pixzel

A retained Kotlin/JVM UI library for application and game loops, with independent implementations and a neutral Arc Engine adapter. Requires JDK 21. The application owns polling, scene drawing and presentation; Pixzel updates an ordinary retained element tree inside that loop.

| Module | Responsibility |
| --- | --- |
| `pixzel-core` | Independent public component, Surface, layout, graphics, text, input, state and resource contracts |
| `pixzel-common` | Retained runtime, typed DSL, layout, bindings/animations, standard components and text policies |
| `pixzel-test` | TestHost, FakeClock, RecordingBackend and limited software raster preview |
| `pixzel-samples` | External HealthBar, custom skins and text policies through public APIs |
| `pixzel-arc` | Neutral Arc adapters, always included and backed by Maven dependencies |
| `pixzel-demo` | Independent preview/benchmarks and Arc/OpenGL showcase/GPU/input checks |

## Setup and build

Arc is resolved from `https://maven.pkg.github.com/AxieFeat/Arc`, as described in Arc's README. GitHub Packages requires authentication even for public packages. Set `GITHUB_ACTOR` and `GITHUB_TOKEN` (with `read:packages`) in the build environment, or put `gpr.user` and `gpr.key` in your user-level `~/.gradle/gradle.properties`. Keep credentials outside the repository.

The shared [version catalog](gradle/libs.versions.toml) pins Kotlin, Arc, LWJGL and JUnit. All six modules are configured on every invocation; there is no checkout-path parameter or alternate demo build script.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.10'
.\gradlew.bat build verifyBoundaries :pixzel-common:stateChecks :pixzel-common:animationChecks
.\gradlew.bat :pixzel-demo:preview
```

`build` includes the adapter and its native-font tests, but does not open a window. `preview` writes `pixzel-demo/build/preview.png` using a deterministic test font and software backend. For checks that do not resolve Arc at all, run `:pixzel-common:build :pixzel-test:build :pixzel-samples:build`; the independent modules remain free of Arc dependencies.

## Arc showcase and real integration checks

The selected Arc version includes the neutral rendering, input and font APIs required by Pixzel. See [Arc migration and delivery](docs/ARC_MIGRATION.md) for the source history and archived patch bundle.

```powershell
.\gradlew.bat :pixzel-arc:build :pixzel-demo:graphicsChecks :pixzel-demo:inputChecks verifyBoundaries
.\gradlew.bat :pixzel-demo:run
# Hidden eight-frame run with a framebuffer capture:
.\gradlew.bat :pixzel-demo:run --args="--smoke --scale=1.25 --width=1100 --height=800"
```

The showcase draws UI over an application scene and includes flat/nine-slice/custom-shape buttons, Cyrillic, adjustable label reveal, text editing, scale/themes/reduced motion, an external animated HealthBar, modal/notifications and a 100,000-item VirtualList. Controls reflow without replacing their state. Use Tab/Shift+Tab, arrow keys and Enter/Space; Ctrl+Home toggles the inspector. Escape dismisses the top overlay before reaching gameplay. Captures go under `pixzel-demo/build/reports`.

`graphicsChecks` performs real OpenGL pixel/readback, exceptional state restoration, batching equivalence, context-generation and 1,000-session cleanup checks. `inputChecks` invokes installed real GLFW callback objects in a controlled fixture; it verifies Unicode commits, ordering, consumed/masked gameplay input and listener cleanup. This is automated native execution, not an OS keyboard/mouse automation claim.

## Benchmarks and local artifacts

```powershell
.\gradlew.bat :pixzel-demo:benchmark --args="--warmup=300 --frames=500 --lifecycle=1000 --output=build/benchmarks/pixzel-cpu.md"
.\gradlew.bat :pixzel-demo:graphicsChecks --args=--benchmark
.\gradlew.bat publishAllPublicationsToLocalArtifactsRepository
```

Library coordinates are `pixzel.engine:<module>:<commit>`, with source JARs. The publication convention follows Arc: `publishingVersion`, then the first seven characters of `GITHUB_SHA`, then the local commit hash. Project dependencies use the same version in POMs and Gradle metadata. `publishAllPublicationsToLocalArtifactsRepository` writes only to `build/repository`; `publishAllPublicationsToGitHubPackagesRepository` publishes to `AxieFeat/Pixzel` using the configured credentials. The adapter is included in both.

The [included build](build-logic/README.md) supplies `pixzel.base`, `pixzel.testing`, `pixzel.publishing`, `pixzel.detekt` and `pixzel.verification` conventions. Module scripts declare their plugins and dependencies through `libs` and `projects` accessors. `check` also verifies layer boundaries and multiline KDoc.

Measured reference desktop CPU p95: static HUD **0.137 ms**, 100 live updates in 10,000 nodes **1.895 ms**. These exclude GPU/present and are not universal guarantees. The [performance report](docs/PERFORMANCE.md) records hardware, JVM, allocations, percentiles and warmup sensitivity; [Arc graphics](docs/ARC_GRAPHICS.md) records native submission measurements.

## Contracts and boundaries

Ui borrows supplied host/backend/text services and owns its tree, subscriptions and render session. Close Ui before its graphics context; close application-owned services separately. AttachmentScope leases close on detach and reopen on attach. Tree/state/focus mutations belong to their owner thread; workers use `ui.post`.

A Surface establishes coordinates and allocates no framebuffer. Host logical coordinates and framebuffer pixels remain separate; UI scale is applied once to input and rendering. Binding is explicit (`value(bind { ... })`); direct String assignment removes the old text binding. Gameplay uses masked input after UI delivery; physical input is a separate opt-in view.

The runtime-tested production adapter is desktop OpenGL 4.1 on Windows. Arc GLES source compiles, but Pixzel's shader dialect has no GLES/ANGLE/Vulkan implementation. Native OS IME and screen-reader bridges, mobile/Kotlin Native and world-space UI are not supplied. TextField is a single-line, single-style editor; Text supports styled display spans. See the [capability matrix](docs/CAPABILITIES.md).

## Documentation

- [User guide and public examples](docs/USER_GUIDE.md)
- [Architecture, ownership and decisions](docs/ARCHITECTURE.md)
- [Arc renderer](docs/ARC_GRAPHICS.md), [Arc migration/delivery](docs/ARC_MIGRATION.md)
- [Requirement coverage and verification](docs/REQUIREMENTS_STATUS.md)
- [Implementation ledger](docs/IMPLEMENTATION_LEDGER.md)
- [Original mandatory specification](docs/spec/README.md)
- [Pinned fonts and licenses](assets/fonts/README.md)
