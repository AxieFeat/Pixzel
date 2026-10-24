#Requires -Version 7.2
using namespace System.Collections.Generic
using namespace System.Diagnostics
using namespace System.IO
using namespace System.IO.Compression
using namespace System.Security.Cryptography
using namespace System.Text

<#
.SYNOPSIS
Exports reviewed Arc changes relative to the fixed baseline.

.DESCRIPTION
Classifies files by the baseline allowlists, independently of Git staging or commit
state. The original user IDE/Vulkan files and build outputs are always excluded.
ValidateOnly checks the source inventory and Git integrity without creating files.

.EXAMPLE
./tools/Export-ArcPatch.ps1 -ArcPath 'E:\Projects\arc-engine' -ValidateOnly
#>
[CmdletBinding()]
param(
    [string]$ArcPath = 'E:\Projects\arc-engine',
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '../docs/delivery'),
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'
$baseline = '419619a2ec7147e76d9b696af823d0ecdeb3e684'
$utf8 = [UTF8Encoding]::new($false)
$workspace = [Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$arcRoot = [Path]::GetFullPath($ArcPath)
$outputRoot = [Path]::GetFullPath($OutputDirectory)
$workspacePrefix = $workspace + [Path]::DirectorySeparatorChar

if (-not $outputRoot.StartsWith($workspacePrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The export directory must stay inside this Pixzel workspace.'
}

function Invoke-Git {
    param(
        [string]$Directory,
        [string[]]$Arguments,
        [switch]$OutsideRepository,
        [switch]$AllowFailure
    )

    $start = [ProcessStartInfo]::new('git')
    $start.WorkingDirectory = $Directory
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.WindowStyle = [ProcessWindowStyle]::Hidden
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.Environment['GIT_OPTIONAL_LOCKS'] = '0'

    if ($OutsideRepository) {
        $start.Environment['GIT_CEILING_DIRECTORIES'] = [Path]::GetDirectoryName($Directory)

        foreach ($variable in @('GIT_DIR', 'GIT_WORK_TREE', 'GIT_INDEX_FILE')) {
            [void]$start.Environment.Remove($variable)
        }
    }

    foreach ($argument in $Arguments) {
        $start.ArgumentList.Add($argument)
    }

    $process = [Process]::new()
    $process.StartInfo = $start
    $memory = [MemoryStream]::new()

    try {
        [void]$process.Start()
        $copy = $process.StandardOutput.BaseStream.CopyToAsync($memory)
        $errors = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $copy.GetAwaiter().GetResult()
        $errorText = $errors.GetAwaiter().GetResult()

        if ($process.ExitCode -ne 0 -and -not $AllowFailure) {
            throw "git $($Arguments -join ' ') failed: $errorText"
        }

        $bytes = $memory.ToArray()

        [pscustomobject]@{
            Bytes = $bytes
            Text = $utf8.GetString($bytes)
            ExitCode = $process.ExitCode
        }
    } finally {
        $memory.Dispose()
        $process.Dispose()
    }
}

function Get-Sha256 {
    param(
        [byte[]]$Bytes
    )

    $algorithm = [SHA256]::Create()

    try {
        [Convert]::ToHexString($algorithm.ComputeHash($Bytes)).ToLowerInvariant()
    } finally {
        $algorithm.Dispose()
    }
}

function Get-GitBlobId {
    param(
        [byte[]]$Bytes
    )

    $prefix = $utf8.GetBytes("blob $($Bytes.Length)`0")
    $algorithm = [SHA1]::Create()

    try {
        [void]$algorithm.TransformBlock($prefix, 0, $prefix.Length, $prefix, 0)
        [void]$algorithm.TransformFinalBlock($Bytes, 0, $Bytes.Length)
        [Convert]::ToHexString($algorithm.Hash).ToLowerInvariant()
    } finally {
        $algorithm.Dispose()
    }
}

function Join-SafePath {
    param(
        [string]$Root,
        [string]$Relative
    )

    if ([Path]::IsPathRooted($Relative) -or ($Relative -split '[/\\]') -contains '..') {
        throw "Unsafe relative path: $Relative"
    }

    $full = [Path]::GetFullPath((Join-Path $Root $Relative))
    $rootPrefix = $Root + [Path]::DirectorySeparatorChar

    if (-not $full.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path escapes its root: $Relative"
    }

    $full
}

function Write-Bytes {
    param(
        [string]$Path,
        [byte[]]$Bytes
    )

    [void][Directory]::CreateDirectory([Path]::GetDirectoryName($Path))
    [File]::WriteAllBytes($Path, $Bytes)
}

function Test-ExcludedPath {
    param(
        [string]$Relative
    )

    $segments = $Relative -split '[/\\]'
    $excludedDirectory = @(
        $segments | Where-Object {
            $_ -in @('.git', '.idea', '.gradle', '.kotlin', 'build', 'out')
        }
    ).Count -gt 0

    $originalVulkanFile = 'backend/render/arc-vulkan/src/main/kotlin/arc/vk/vk.kt'
    $excludedDirectory -or $Relative -eq $originalVulkanFile
}

function Assert-DeliverablePath {
    param(
        [string]$Relative
    )

    if (Test-ExcludedPath $Relative) {
        throw "Excluded path in export: $Relative"
    }
}

function Get-GitInventory {
    param(
        [string]$Directory
    )

    $changed = Invoke-Git $Directory @(
        'diff', '--no-renames', '--name-only', '-z', $baseline, '--'
    )
    $untracked = Invoke-Git $Directory @(
        'ls-files', '--others', '--exclude-standard', '-z'
    )

    [pscustomobject]@{
        ChangedText = $changed.Text
        UntrackedText = $untracked.Text
        ChangedPaths = @(
            $changed.Text.Split([char]0, [StringSplitOptions]::RemoveEmptyEntries)
        )
        UntrackedPaths = @(
            $untracked.Text.Split([char]0, [StringSplitOptions]::RemoveEmptyEntries)
        )
    }
}

function Get-GitIntegritySnapshot {
    param(
        [string]$Directory
    )

    $head = (Invoke-Git $Directory @('rev-parse', 'HEAD')).Text.Trim()
    $gitDirectory = (Invoke-Git $Directory @('rev-parse', '--absolute-git-dir')).Text.Trim()
    $indexPath = Join-Path $gitDirectory 'index'
    $indexHash = $null

    if ([File]::Exists($indexPath)) {
        $indexHash = (Get-FileHash -LiteralPath $indexPath -Algorithm SHA256).Hash
    }

    [pscustomobject]@{
        Head = $head
        IndexHash = $indexHash
        Refs = (Invoke-Git $Directory @(
            'for-each-ref', '--format=%(refname) %(objectname)'
        )).Text
    }
}

function Assert-GitUnchanged {
    param(
        [string]$Directory,
        [object]$InitialSnapshot,
        [object]$InitialInventory
    )

    $currentSnapshot = Get-GitIntegritySnapshot $Directory
    $currentInventory = Get-GitInventory $Directory
    $metadataChanged = $InitialSnapshot.Head -ne $currentSnapshot.Head -or
        $InitialSnapshot.IndexHash -ne $currentSnapshot.IndexHash -or
        $InitialSnapshot.Refs -ne $currentSnapshot.Refs
    $inventoryChanged = $InitialInventory.ChangedText -ne $currentInventory.ChangedText -or
        $InitialInventory.UntrackedText -ne $currentInventory.UntrackedText

    if ($metadataChanged -or $inventoryChanged) {
        throw 'Arc index/HEAD/refs or changed paths changed during validation; wait for a source freeze and retry.'
    }
}

function Resolve-ExportScope {
    param(
        [string[]]$ChangedPaths,
        [string[]]$UntrackedPaths,
        [string[]]$BaselineTrackedAllowlist,
        [string[]]$BaselineNewAllowlist
    )

    $reviewedChanges = @(
        $ChangedPaths | Where-Object {
            -not (Test-ExcludedPath $_)
        }
    )
    $reviewedUntracked = @(
        $UntrackedPaths | Where-Object {
            -not (Test-ExcludedPath $_)
        }
    )
    $unexpectedChanges = @(
        $reviewedChanges | Where-Object {
            $_ -notin $BaselineTrackedAllowlist -and $_ -notin $BaselineNewAllowlist
        }
    )
    $unexpectedNew = @(
        $reviewedUntracked | Where-Object {
            $_ -notin $BaselineNewAllowlist
        }
    )

    if ($unexpectedChanges.Count) {
        throw "Unreviewed changes require an explicit allowlist update: $($unexpectedChanges -join ', ')"
    }

    if ($unexpectedNew.Count) {
        throw "Unreviewed new files require an explicit allowlist update: $($unexpectedNew -join ', ')"
    }

    [pscustomobject]@{
        Tracked = @(
            $reviewedChanges | Where-Object {
                $_ -in $BaselineTrackedAllowlist
            }
        )
        New = @(
            @($reviewedChanges + $reviewedUntracked) |
                Where-Object {
                    $_ -in $BaselineNewAllowlist
                } |
                Sort-Object -Unique
        )
        ExcludedChangedCount = $ChangedPaths.Count - $reviewedChanges.Count
        ExcludedUntrackedCount = $UntrackedPaths.Count - $reviewedUntracked.Count
    }
}

# These paths existed in the fixed baseline and belong in the tracked patch.
# Baseline additions below remain overlays even after staging or committing them.
$trackedAllowlist = @(
    'arc-common/src/main/kotlin/arc/graphics/NativeDrawBuffer.kt'
    'arc-core/src/main/kotlin/arc/graphics/DrawBuffer.kt'
    'arc-core/src/main/kotlin/arc/graphics/RenderContext.kt'
    'arc-core/src/main/kotlin/arc/graphics/vertex/VertexBuffer.kt'
    'arc-core/src/main/kotlin/arc/shader/FrameBuffer.kt'
    'arc-core/src/main/kotlin/arc/shader/ShaderInstance.kt'
    'arc-core/src/main/kotlin/arc/shader/UniformBuffer.kt'
    'arc-core/src/main/kotlin/arc/texture/TextureLike.kt'
    'arc-core/src/main/kotlin/arc/window/Window.kt'
    'arc-extensions/arc-input/arc-input-core/src/main/kotlin/arc/input/InputEngine.kt'
    'arc-extensions/arc-input/arc-input-glfw/src/main/kotlin/arc/input/GlfwInputEngine.kt'
    'arc-extensions/arc-input/arc-input-glfw/src/main/kotlin/arc/input/keyboard/GlfwKeyboardInput.kt'
    'arc-extensions/arc-input/arc-input-glfw/src/main/kotlin/arc/input/mouse/GlfwMouseInput.kt'
    'backend/render/arc-opengl/build.gradle.kts'
    'backend/render/arc-opengl/src/main/kotlin/arc/gl/GlApplication.kt'
    'backend/render/arc-opengl/src/main/kotlin/arc/gl/OpenGL.kt'
    'backend/render/arc-opengl/src/main/kotlin/arc/gl/graphics/GlRenderContext.kt'
    'backend/render/arc-opengl/src/main/kotlin/arc/gl/graphics/GlVertexUploader.kt'
    'backend/render/arc-opengl/src/main/kotlin/arc/gl/graphics/vertex/GlVertexArrayBuffer.kt'
    'backend/render/arc-opengl/src/main/kotlin/arc/gl/graphics/vertex/GlVertexBuffer.kt'
    'backend/render/arc-opengl/src/main/kotlin/arc/gl/shader/GlFrameBuffer.kt'
    'backend/render/arc-opengl/src/main/kotlin/arc/gl/shader/GlShaderInstance.kt'
    'backend/render/arc-opengl/src/main/kotlin/arc/gl/shader/GlUniformBuffer.kt'
    'backend/render/arc-opengl/src/main/kotlin/arc/gl/texture/GlTexture.kt'
    'backend/render/arc-opengl/src/main/kotlin/arc/gl/window/GlfwGlWindow.kt'
    'backend/render/arc-opengles/src/main/kotlin/arc/gles/GlesApplication.kt'
    'backend/render/arc-opengles/src/main/kotlin/arc/gles/graphics/GlesRenderContext.kt'
    'backend/render/arc-opengles/src/main/kotlin/arc/gles/graphics/GlesVertexUploader.kt'
    'backend/render/arc-opengles/src/main/kotlin/arc/gles/graphics/vertex/GlesVertexArrayBuffer.kt'
    'backend/render/arc-opengles/src/main/kotlin/arc/gles/graphics/vertex/GlesVertexBuffer.kt'
    'backend/render/arc-opengles/src/main/kotlin/arc/gles/shader/GlesFrameBuffer.kt'
    'backend/render/arc-opengles/src/main/kotlin/arc/gles/shader/GlesShaderInstance.kt'
    'backend/render/arc-opengles/src/main/kotlin/arc/gles/shader/GlesUniformBuffer.kt'
    'backend/render/arc-opengles/src/main/kotlin/arc/gles/texture/GlesTexture.kt'
    'backend/window/arc-glfw/src/main/kotlin/arc/window/glfw/AbstractGlfwWindow.kt'
    'gradle/libs.versions.toml'
    'settings.gradle.kts'
)
$newAllowlist = @(
    'arc-core/src/main/kotlin/arc/graphics/ContextResource.kt'
    'arc-core/src/main/kotlin/arc/graphics/RenderState.kt'
    'arc-core/src/main/kotlin/arc/graphics/RenderTarget.kt'
    'arc-core/src/main/kotlin/arc/graphics/TextureSampler.kt'
    'arc-core/src/main/kotlin/arc/window/WindowEvents.kt'
    'arc-extensions/arc-font/arc-font-core/src/main/kotlin/arc/font/FontProvider.kt'
    'arc-extensions/arc-font/arc-font-native/README.md'
    'arc-extensions/arc-font/arc-font-native/build.gradle.kts'
    'arc-extensions/arc-font/arc-font-native/src/main/kotlin/arc/font/NativeFontProvider.kt'
    'arc-extensions/arc-font/arc-font-native/src/main/resources/META-INF/licenses/FreeType-FTL.txt'
    'arc-extensions/arc-font/arc-font-native/src/main/resources/META-INF/licenses/HarfBuzz-COPYING.txt'
    'arc-extensions/arc-font/arc-font-native/src/main/resources/META-INF/licenses/LWJGL-LICENSE.txt'
    'arc-extensions/arc-font/arc-font-native/src/test/kotlin/arc/font/NativeFontProviderTest.kt'
    'arc-extensions/arc-font/arc-font-native/src/test/resources/fonts/NotoSans-Regular.ttf'
    'arc-extensions/arc-font/arc-font-native/src/test/resources/fonts/OFL.txt'
    'arc-extensions/arc-font/arc-font-native/src/test/resources/fonts/SOURCE.md'
    'arc-extensions/arc-input/arc-input-core/src/main/kotlin/arc/input/InputContext.kt'
    'backend/render/arc-opengl/src/main/kotlin/arc/gl/graphics/GlContextResource.kt'
    'backend/render/arc-opengl/src/main/kotlin/arc/gl/graphics/GlRenderState.kt'
    'backend/render/arc-opengl/src/main/kotlin/arc/gl/graphics/GlRenderTarget.kt'
    'backend/render/arc-opengl/src/main/kotlin/arc/gl/graphics/GlRgbaTexture.kt'
    'backend/render/arc-opengl/src/main/kotlin/arc/gl/graphics/GlTextureSampler.kt'
    'backend/render/arc-opengl/src/test/kotlin/arc/gl/graphics/GraphicsRegression.kt'
    'backend/render/arc-opengles/src/main/kotlin/arc/gles/graphics/GlesContextResource.kt'
    'backend/render/arc-opengles/src/main/kotlin/arc/gles/graphics/GlesRenderState.kt'
    'backend/render/arc-opengles/src/main/kotlin/arc/gles/graphics/GlesRenderTarget.kt'
    'backend/render/arc-opengles/src/main/kotlin/arc/gles/graphics/GlesRgbaTexture.kt'
    'backend/render/arc-opengles/src/main/kotlin/arc/gles/graphics/GlesTextureSampler.kt'
    'docs/graphics-passes.md'
)

$resolvedBaseline = (
    Invoke-Git $arcRoot @('rev-parse', '--verify', ($baseline + '^{commit}'))
).Text.Trim()

if ($resolvedBaseline -ne $baseline) {
    throw 'Unexpected baseline commit.'
}

$objectFormat = (Invoke-Git $arcRoot @('rev-parse', '--show-object-format')).Text.Trim()

if ($objectFormat -ne 'sha1') {
    throw 'This exporter currently validates SHA-1 Git object repositories.'
}

$initialSnapshot = Get-GitIntegritySnapshot $arcRoot
$initialInventory = Get-GitInventory $arcRoot
$allAllowedPaths = @($trackedAllowlist + $newAllowlist)
$baselinePaths = Invoke-Git $arcRoot (
    @('ls-tree', '-r', '--name-only', '-z', $baseline, '--') + $allAllowedPaths
)
$baselineOwnedPaths = [HashSet[string]]::new([StringComparer]::Ordinal)

foreach ($relative in $baselinePaths.Text.Split([char]0, [StringSplitOptions]::RemoveEmptyEntries)) {
    [void]$baselineOwnedPaths.Add($relative)
}

foreach ($relative in $trackedAllowlist) {
    if (-not $baselineOwnedPaths.Contains($relative)) {
        throw "A tracked allowlist path is missing from the baseline: $relative"
    }
}

foreach ($relative in $newAllowlist) {
    if ($baselineOwnedPaths.Contains($relative)) {
        throw "A new-file allowlist path already exists in the baseline: $relative"
    }
}

$scopeArguments = @{
    ChangedPaths = $initialInventory.ChangedPaths
    UntrackedPaths = $initialInventory.UntrackedPaths
    BaselineTrackedAllowlist = $trackedAllowlist
    BaselineNewAllowlist = $newAllowlist
}
$scope = Resolve-ExportScope @scopeArguments
$tracked = $scope.Tracked
$newFiles = $scope.New
$ownedPaths = @($tracked + $newFiles | Sort-Object -Unique)

if (-not $ownedPaths.Count) {
    throw 'There are no reviewed Arc changes to export.'
}

foreach ($relative in $ownedPaths) {
    Assert-DeliverablePath $relative
    $source = Join-SafePath $arcRoot $relative

    if (-not [File]::Exists($source)) {
        throw "This no-delete export does not support deleted files: $relative"
    }

    if ((Get-Item -LiteralPath $source).Attributes -band [FileAttributes]::ReparsePoint) {
        throw "A source file is a reparse point: $relative"
    }
}

if ($tracked.Count) {
    $deleted = Invoke-Git $arcRoot (
        @('diff', '--diff-filter=D', '--name-only', '-z', $baseline, '--') + $tracked
    )

    if ($deleted.Text.Length) {
        throw 'This no-delete export does not support baseline file deletions, including index-only deletions.'
    }
}

if ($ValidateOnly) {
    Assert-GitUnchanged $arcRoot $initialSnapshot $initialInventory

    [pscustomobject]@{
        Mode = 'ValidateOnly'
        Baseline = $baseline
        SourceHead = $initialSnapshot.Head
        BaselineTrackedFiles = $tracked.Count
        BaselineNewFiles = $newFiles.Count
        TotalFiles = $ownedPaths.Count
        ExcludedChangedPaths = $scope.ExcludedChangedCount
        ExcludedUntrackedPaths = $scope.ExcludedUntrackedCount
        IndexHeadRefsUnchanged = $true
        OutputCreated = $false
    }

    return
}

$timestamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
$uniqueSuffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)
$runId = "$timestamp-$uniqueSuffix"
$runRoot = Join-Path $outputRoot $runId
$package = Join-Path $runRoot 'package'
$validation = Join-Path $runRoot 'validation'
$baselineRoot = Join-Path $validation 'baseline'
$newFilesRoot = Join-Path $package 'new-files'
$lineEndingsRoot = Join-Path $package 'line-ending-overlays'

[void][Directory]::CreateDirectory($package)
[void][Directory]::CreateDirectory($validation)

$patchBytes = [byte[]]::new(0)

if ($tracked.Count) {
    $patch = Invoke-Git $arcRoot (
        @(
            'diff',
            '--binary',
            '--full-index',
            '--no-ext-diff',
            '--no-color',
            '--no-renames',
            $baseline,
            '--'
        ) + $tracked
    )
    $patchBytes = $patch.Bytes
}

Write-Bytes (Join-Path $package 'arc-tracked.patch') $patchBytes
$records = [List[object]]::new()

foreach ($relative in $ownedPaths) {
    $source = Join-SafePath $arcRoot $relative
    $raw = [File]::ReadAllBytes($source)
    $canonical = $raw
    $isNew = $relative -in $newFiles
    $overlay = $false

    if ($isNew) {
        Write-Bytes (Join-SafePath $newFilesRoot $relative) $raw
    } else {
        $expectedBlob = (
            Invoke-Git $arcRoot @('hash-object', "--path=$relative", '--', $source)
        ).Text.Trim()

        if ((Get-GitBlobId $raw) -ne $expectedBlob) {
            $normalized = [MemoryStream]::new()

            try {
                for ($index = 0; $index -lt $raw.Length; $index++) {
                    $carriageReturn = $raw[$index] -eq 13
                    $nextIsLineFeed = $index + 1 -lt $raw.Length -and $raw[$index + 1] -eq 10

                    if ($carriageReturn -and $nextIsLineFeed) {
                        continue
                    }

                    $normalized.WriteByte($raw[$index])
                }

                $canonical = $normalized.ToArray()
            } finally {
                $normalized.Dispose()
            }

            if ((Get-GitBlobId $canonical) -ne $expectedBlob) {
                throw "Unsupported clean filter for $relative; only verified CRLF normalization is allowed."
            }

            $overlay = $true
            Write-Bytes (Join-SafePath $lineEndingsRoot $relative) $raw
        }
    }

    $kind = if ($isNew) {
        'new'
    } else {
        'tracked'
    }

    $records.Add([ordered]@{
        path = $relative
        kind = $kind
        bytes = $raw.Length
        sha256 = Get-Sha256 $raw
        canonicalSha256 = Get-Sha256 $canonical
        lineEndingOverlay = $overlay
    })
}

$baselineArchive = Join-Path $validation 'baseline.zip'
$archive = Invoke-Git $arcRoot @(
    '-c', 'core.autocrlf=false',
    '-c', 'core.eol=lf',
    'archive', '--format=zip', $baseline
)

Write-Bytes $baselineArchive $archive.Bytes
[ZipFile]::ExtractToDirectory($baselineArchive, $baselineRoot)

foreach ($relative in $tracked) {
    $baselineObject = $baseline + ':' + $relative
    $expectedBaselineBlob = (Invoke-Git $arcRoot @('rev-parse', $baselineObject)).Text.Trim()
    $baselineFile = Join-SafePath $baselineRoot $relative
    $actualBaselineBlob = Get-GitBlobId ([File]::ReadAllBytes($baselineFile))

    if ($actualBaselineBlob -ne $expectedBaselineBlob) {
        throw "Baseline archive content differs from the recorded Git blob: $relative"
    }
}

$patchPath = Join-Path $package 'arc-tracked.patch'
$probe = Invoke-Git $baselineRoot @(
    'rev-parse', '--is-inside-work-tree'
) -OutsideRepository -AllowFailure

if ($probe.ExitCode -eq 0) {
    throw 'Validation must take place outside Git repository discovery.'
}

$applyArguments = @(
    '-c', 'core.autocrlf=false',
    '-c', 'core.eol=lf',
    'apply', '--allow-empty', '--binary'
)

[void](Invoke-Git $baselineRoot ($applyArguments + @('--check', $patchPath)) -OutsideRepository)
[void](Invoke-Git $baselineRoot ($applyArguments + @($patchPath)) -OutsideRepository)

foreach ($record in $records) {
    $target = Join-SafePath $baselineRoot $record.path

    if ($record.kind -eq 'tracked') {
        $canonicalHash = Get-Sha256 ([File]::ReadAllBytes($target))

        if ($canonicalHash -ne $record.canonicalSha256) {
            throw "Patched canonical bytes differ: $($record.path)"
        }

        if ($record.lineEndingOverlay) {
            $overlayPath = Join-SafePath $lineEndingsRoot $record.path
            Write-Bytes $target ([File]::ReadAllBytes($overlayPath))
        }
    } else {
        $newFile = Join-SafePath $newFilesRoot $record.path
        Write-Bytes $target ([File]::ReadAllBytes($newFile))
    }

    $finalHash = Get-Sha256 ([File]::ReadAllBytes($target))
    $sourcePath = Join-SafePath $arcRoot $record.path
    $sourceHash = Get-Sha256 ([File]::ReadAllBytes($sourcePath))

    if ($finalHash -ne $record.sha256) {
        throw "Final overlay hash differs: $($record.path)"
    }

    if ($sourceHash -ne $record.sha256) {
        throw "Arc changed during export: $($record.path)"
    }
}

Assert-GitUnchanged $arcRoot $initialSnapshot $initialInventory

$manifest = [ordered]@{
    formatVersion = 1
    baseline = $baseline
    sourceHead = $initialSnapshot.Head
    generatedUtc = [DateTime]::UtcNow.ToString('o')
    patchSha256 = Get-Sha256 $patchBytes
    trackedFiles = $tracked.Count
    newFiles = $newFiles.Count
    excludedChangedPaths = $scope.ExcludedChangedCount
    excludedUntrackedPaths = $scope.ExcludedUntrackedCount
    exclusions = @(
        '.idea/**'
        'backend/render/arc-vulkan/src/main/kotlin/arc/vk/vk.kt'
        '**/build/**'
        '**/.gradle/**'
        '**/.kotlin/**'
        '**/out/**'
        '**/.git/**'
    )
    files = $records.ToArray()
}

$verification = [ordered]@{
    baselineArchiveSha256 = (Get-FileHash -LiteralPath $baselineArchive -Algorithm SHA256).Hash.ToLowerInvariant()
    patchCheck = 'passed'
    patchAppliedOutsideGit = 'passed'
    canonicalTrackedHashes = 'passed'
    exactWorkingTreeOverlayHashes = 'passed'
    sourceStableDuringExport = 'passed'
    verifiedFileCount = $records.Count
    originalArcCheckoutModified = $false
    gitMetadataModified = $false
    indexHeadRefsUnchanged = $true
    validationDirectory = $baselineRoot
}

$lineFeed = [string][char]10
$manifestJson = ($manifest | ConvertTo-Json -Depth 8) + $lineFeed
$verificationJson = ($verification | ConvertTo-Json -Depth 8) + $lineFeed

[File]::WriteAllText((Join-Path $package 'manifest.json'), $manifestJson, $utf8)
[File]::WriteAllText((Join-Path $package 'verification.json'), $verificationJson, $utf8)

$readme = @'
Arc source changes from baseline 419619a2ec7147e76d9b696af823d0ecdeb3e684.

Review arc-tracked.patch and manifest.json. Verify SHA256SUMS.txt before applying.
In a separate checkout of the exact baseline, run git apply --check then git apply
with the absolute arc-tracked.patch path. Copy new-files/ into the checkout root.
Optional line-ending-overlays/ restores the exact exported working-tree CRLF bytes;
the canonical patch content was verified before these byte-only overlays were copied.

This package contains no .git metadata, .idea settings, the user's vk/vk.kt or build
outputs. Native font test fixtures and their licenses/provenance are included.
New-file classification is relative to the baseline, regardless of staging/commit state.
The exporter applied this patch to git-archive baseline files outside any Git index,
then checked SHA-256 for every final exported file against the original source.
No actual Arc source/index/refs were changed. Validation artifacts remain alongside
this package; no directories were deleted. See Pixzel docs/ARC_MIGRATION.md for builds.
'@

[File]::WriteAllText((Join-Path $package 'README.txt'), $readme + $lineFeed, $utf8)

$sums = [List[string]]::new()
$packageFiles = Get-ChildItem -LiteralPath $package -Recurse -File | Sort-Object FullName

foreach ($file in $packageFiles) {
    $relative = [Path]::GetRelativePath($package, $file.FullName).Replace([Path]::DirectorySeparatorChar, [char]'/')
    Assert-DeliverablePath $relative
    $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    $sums.Add($hash + '  ' + $relative)
}

[File]::WriteAllText((Join-Path $package 'SHA256SUMS.txt'), ($sums -join $lineFeed) + $lineFeed, $utf8)

$zip = Join-Path $runRoot 'arc-419619a2-changes.zip'
[ZipFile]::CreateFromDirectory($package, $zip, [CompressionLevel]::Optimal, $false)

$zipHash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
$zipChecksum = $zipHash + '  ' + [Path]::GetFileName($zip) + $lineFeed
[File]::WriteAllText($zip + '.sha256', $zipChecksum, $utf8)

[pscustomobject]@{
    Zip = $zip
    Sha256 = $zipHash
    Manifest = Join-Path $package 'manifest.json'
    Verification = Join-Path $package 'verification.json'
    Tracked = $tracked.Count
    New = $newFiles.Count
    Verified = $records.Count
}
