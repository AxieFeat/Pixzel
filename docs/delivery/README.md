# Verified Arc source delivery

Final package: [arc-419619a2-changes.zip](20261024T052444868Z-7d1637db/arc-419619a2-changes.zip).

SHA-256: `db5923ec0dde2bd20b16d8152af25b59a4514ba66dd5fb52969e943a14d6a597`.

The package reconstructs 37 baseline-tracked changes and 29 additions from Arc baseline `419619a2ec7147e76d9b696af823d0ecdeb3e684`. It was regenerated after formatting from committed source [`e5d04a8`](https://github.com/AxieFeat/Arc/commit/e5d04a8de4eec543e67eff7778d37f88ae29d824) on [`codex/pixzel-integration`](https://github.com/AxieFeat/Arc/tree/codex/pixzel-integration).

All 66 final source hashes and 34 package checksums were verified against an independently archived baseline after patch application and the allowlisted file overlays. Native font fixtures, licenses and provenance are included. User IDE/Vulkan files, Git metadata and build outputs are excluded.

Review the [manifest](20261024T052444868Z-7d1637db/package/manifest.json), [verification result](20261024T052444868Z-7d1637db/package/verification.json), [tracked patch](20261024T052444868Z-7d1637db/package/arc-tracked.patch) and [application workflow](../ARC_MIGRATION.md).

The final standalone Arc graphics regression, GLES compilation and native font tests passed in 17 seconds. The integrated Pixzel build, graphics/input checks and showcase passed in 26 seconds. Arc index, HEAD and refs remained unchanged during export. Delivery bytes are protected from Git line-ending conversion by `.gitattributes`.

Earlier export directories remain local and ignored. Only this final package is versioned. Regeneration creates a new directory and requires reviewing its new digest.
