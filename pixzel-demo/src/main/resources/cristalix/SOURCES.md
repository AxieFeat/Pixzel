# Cristalix reference assets

The UI reference is the user-provided recording `Base Profile 2026.10.25 - 04.57.28.02.mp4`, 2560 × 1440, duration 71.27 seconds. Controls and text are drawn and operated by Pixzel, rather than being flattened screenshots.

## Original client artwork

`reference/` contains original transparent mode illustrations, the Nightmares and Arasaka banner backgrounds, the reviews backdrop and favorite-star icons. These were found in the user's installed client under `.cristalix/updates/Minigames/cache/textures`. `reference/manifest.json` records the original cache object identifiers and image dimensions. WebP cache objects were decoded to actual PNG bytes for Java ImageIO. `tools/cristalix/import_assets.py` can refresh those images from the same installed cache.

`avatar-0.png` through `avatar-7.png` are isolated avatar crops from the supplied video at 11.5 seconds. `thumb-up.png` and `thumb-down.png` are isolated rating-icon crops at 21.5 seconds, with the surrounding background removed.

`reference/corner.png` is the user's original 32×32 ornament supplied at the project root. Its bytes are preserved; runtime tint and reflection produce the four tooltip corners.

`MinecraftSeven.otf`, `MinecraftFive.otf` and `MinecraftTen.ttf` were extracted from the corresponding `.crifont` archives in the installed client's `libraries/mcassets.jar`. The original font bytes are unchanged.

These assets remain the property of their respective owners. This local interface study does not grant a redistribution license or claim affiliation with Cristalix.

## Earlier reference assets

`Monocraft.ttf` and `OFL.txt` remain from the earlier demo. Monocraft is by IdreesInc, under SIL Open Font License 1.1, revision `e498bf70aeb25b4bdcff1e44d878fb2cb4f7c2a9`. It is retained for the original font-alignment tests.

`hub.png` is the earlier AI-generated decorative world backdrop. It is almost completely darkened by the navigator overlay; the video reference itself has a nearly black underlying game view. The former hand-drawn substitute mode icons have been removed from the runtime.
