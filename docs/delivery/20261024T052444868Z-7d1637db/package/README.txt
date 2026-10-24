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
