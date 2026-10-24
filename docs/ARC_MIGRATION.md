# Arc changes and separate delivery

The Arc work starts from commit `419619a2ec7147e76d9b696af823d0ecdeb3e684`. Pixzel remains a separate Gradle build and resolves Arc from GitHub Packages, following Arc's README. All modules are always included; there is no composite-build parameter. The version catalog pins the integrated Arc release from [Arc's `master` branch](https://github.com/AxieFeat/Arc/tree/master). [Pixzel's `dev` branch](https://github.com/AxieFeat/Pixzel/tree/dev) preserves the original repository history.

The bundle below is a historical snapshot of `e5d04a8`, before the later readability and publication fixes. It remains available for reconstructing that exact source version. Current development uses the Maven artifacts and does not require applying the bundle.

The Arc changes provide neutral scoped graphics/state/targets/samplers/context generations, safe active-byte streaming and cleanup, window/input event contracts, and the native FreeType/HarfBuzz font provider. Arc runtime contracts contain no Pixzel component/UI types. The original untracked `.idea/` files and `backend/render/arc-vulkan/src/main/kotlin/arc/vk/vk.kt` are excluded and remain untouched.

The [final verified package and hashes](delivery/README.md) reconstruct all 66 exported files; its final standalone Arc regression/GLES/native-font command passed in 17 seconds.

## Generate and review the bundle

After all Arc source changes are complete, run PowerShell 7.2 or later from the Pixzel workspace:

```powershell
./tools/Export-ArcPatch.ps1 -ArcPath 'E:\Projects\arc-engine'
```

The exporter creates a unique directory under `docs/delivery/` and prints the ZIP, SHA-256, manifest, verification report and file counts. It never deletes an existing directory or overwrites an earlier export. The source checkout is read only. File classification uses the fixed baseline allowlists, so staged and committed additions are supported. Unexpected tracked/new source paths fail until explicitly reviewed into the allowlist; original IDE/Vulkan files and build/cache/Git directories are excluded. Add `-ValidateOnly` to check this inventory and Git metadata without creating an archive.

Each package contains:

- `arc-tracked.patch`: full-index binary Git diff against the exact baseline, including tracked configuration/source changes.
- `new-files/`: allowlisted new contracts, OpenGL/GLES implementations/tests/docs and the native font module, fixture, licenses and provenance.
- `line-ending-overlays/`: only tracked files whose verified Git canonical content differs from their raw working-tree CRLF bytes. These optional overlays reproduce the exact source bytes after applying the canonical patch.
- `manifest.json`: baseline/source HEAD, patch SHA-256 and per-file raw/canonical SHA-256, size and kind.
- `verification.json`: baseline-archive hash, successful patch applicability/application and every final overlay hash.
- `SHA256SUMS.txt` and `README.txt`: package integrity and application notes. The ZIP also has an adjacent `.sha256` file.

Validation uses `git archive` of the exact baseline in a separate temporary directory. Archive and validation apply use process-local LF settings, without changing Git configuration; each baseline source blob is checked against its Git object ID. Repository discovery is explicitly stopped there, then `git apply --check` and `git apply` run without an index. Every tracked canonical byte hash is checked before line-ending overlays, and every final exported file is checked against its original working-tree SHA-256. The exporter rechecks source hashes and Arc index/HEAD/refs to detect changes during export. Validation files remain alongside the package for inspection; no actual Arc checkout files or Git metadata are changed.

## Apply in a separate checkout

The exporter's temporary `validation/` baseline archives remain available for inspection and are ignored by Git; they are not part of the source delivery.

Review the patch, manifest, licenses and ZIP hash first. Use an existing separate checkout of the exact baseline. The following commands are an application recipe; the exporter itself does not execute them against a checkout.

```powershell
$arcCheckout = 'E:\Projects\arc-review'
$package = 'C:\path\to\extracted\package'
git -C $arcCheckout rev-parse HEAD
git -C $arcCheckout apply --check (Join-Path $package 'arc-tracked.patch')
git -C $arcCheckout apply (Join-Path $package 'arc-tracked.patch')

foreach ($folder in @('new-files', 'line-ending-overlays')) {
    $sourceRoot = Join-Path $package $folder
    if (-not (Test-Path -LiteralPath $sourceRoot)) { continue }
    foreach ($file in Get-ChildItem -LiteralPath $sourceRoot -File -Recurse) {
        $relative = [IO.Path]::GetRelativePath($sourceRoot, $file.FullName)
        $destination = [IO.Path]::GetFullPath((Join-Path $arcCheckout $relative))
        if (-not $destination.StartsWith([IO.Path]::GetFullPath($arcCheckout) + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'A package path escaped the review checkout.'
        }
        [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination))
        Copy-Item -LiteralPath $file.FullName -Destination $destination
    }
}
```

The reported HEAD must equal the baseline above. A checkout with existing edits needs its own conflict review. The package contains no staged changes or Git metadata and performs no commit automatically.

## Build and compatibility notes

Use JDK 21; the verified local installation is `C:\Program Files\Java\jdk-21.0.10`. Set `JAVA_HOME` for the shell before invoking the wrapper if the system Java shim selects another runtime. The verified toolchain is Gradle 9.6.1, Kotlin 2.4.10 and LWJGL 3.4.2.

From the Arc review checkout:

```powershell
./gradlew.bat :arc-opengl:graphicsRegression :arc-opengles:compileKotlin :arc-extensions:arc-font:arc-font-native:test --offline --no-daemon --console=plain
```

For normal Pixzel builds, configure GitHub Packages credentials and use the catalog's published Arc version:

```powershell
./gradlew.bat :pixzel-demo:graphicsChecks --args=--benchmark verifyBoundaries --no-daemon --console=plain
```

To verify a separately modified Arc source checkout, publish its library modules to a Maven repository and update the catalog to that version. Offline builds require the selected dependencies in the local Gradle cache. Native graphics checks require a desktop OpenGL 4.1 core context. The bundle verifies source reconstruction independently of those environment requirements.

New RenderContext operations have explicit unsupported defaults. OpenGL/GLES implement the supported native subset and report instance texture limits. `beginScopedPass(textureUnits = setOf(0))` snapshots once, restores in LIFO order and preserves exceptional exits; custom operations declare additional texture units. Owned render-target attachment views remain borrowed. `FrameBuffer.render` no longer clears its destination; callers requiring a clear must request one. Texture bind no longer forces nearest filtering; use a sampler. Uploads honor active initialized bytes rather than backing capacity, and old resource cleanup cannot delete colliding IDs in a new context.

Hosts install capabilities after making a context current and clear capabilities when it is no longer current. Generation changes trigger Pixzel session reopening; bounded owned RGBA image sources restore lazily, while borrowed images require an explicit replacement/rebind. Physical GPU-reset detection/notification remains a host responsibility. GLES source compilation does not establish a working Pixzel GLES shader path; the current production adapter uses desktop GLSL 4.10. Vulkan remains unsupported.

See [graphics behavior and limits](ARC_GRAPHICS.md), [the recorded 1080p benchmark](ARC_GRAPHICS_BENCHMARK.md), and the delivered Arc `docs/graphics-passes.md` for state, ownership, migration and verification details.
