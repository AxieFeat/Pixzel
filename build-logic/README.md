# Build conventions

This included build follows Arc's build structure: precompiled Kotlin convention plugins, a shared version catalog, typed project accessors and small module scripts. It imports `../gradle/libs.versions.toml` rather than maintaining a second catalog.

| Convention | Responsibility |
| --- | --- |
| `pixzel.base` | Kotlin/JVM, the Java toolchain and JUnit Platform execution |
| `pixzel.testing` | Shared JUnit API, engine and launcher dependencies |
| `pixzel.publishing` | Library variants, source JARs, consistent commit versions and Maven repositories |
| `pixzel.detekt` | Arc's Detekt configuration, optional module overrides, baselines and reports |
| `pixzel.verification` | Root checks for independent layers, the neutral adapter and multiline KDoc |

Root project metadata lives in `gradle.properties`. Keep `javaVersion` synchronized with this build's `gradle.properties`, as in Arc. Root settings also use Arc's Foojay toolchain resolver. The root applies the base convention to all projects; library modules opt into publication and the demo applies `application`.

Arc dependencies always come from GitHub Packages. Configure `GITHUB_ACTOR` and `GITHUB_TOKEN`, or user-level `gpr.user` and `gpr.key`, before resolving them. No local Arc checkout is required. `lwjgl.kt` carries Arc's helper for attaching the supported native classifiers to the concrete demo's dependencies.

Publication uses `publishingVersion`, then the first seven characters of `GITHUB_SHA`, then the local Git hash, falling back to the project version outside a Git checkout. The selected value is assigned to the project and publication so intermodule dependencies resolve to the same version. `githubRepository` can override `AxieFeat/Pixzel`. `skipPublishing=true` skips publication setup while retaining library and test conventions; `skipDetekt=true` skips Detekt configuration.

`publishAllPublicationsToLocalArtifactsRepository` writes to the root `build/repository` directory. Remote publication is a separate `publishAllPublicationsToGitHubPackagesRepository` task and needs package-write credentials.

The initial module baselines record existing complexity, naming and other Detekt findings when adopting Arc's rules. New findings fail `check`; the baseline is never regenerated automatically. Review changes before deliberately running `detektBaseline`. HTML/XML reports are written under each module's `build/reports/detekt`.

Use multiline KDoc, including short descriptions. `verifyCodeStyle` enforces this for live Kotlin sources and build scripts; archived delivery bytes are excluded.
