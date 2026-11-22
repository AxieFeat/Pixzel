# Cristalix video reference in Pixzel

The standalone Kotlin/Arc application implements the interface in `Base Profile 2026.10.25 - 04.57.28.02.mp4` (71.27 seconds, 2560 × 1440). All controls, text, clipping, focus, filters and input are retained Pixzel elements. The bundled original illustrations and fonts come from the installed local Cristalix client; the application needs no client installation at runtime.

See [the composition and customization guide](CRISTALIX_COMPOSITION.md) for the data-driven screen builders and extension examples.

## Run

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.10'
.\gradlew.bat :pixzel-demo:cristalix
# Match the recording resolution:
.\gradlew.bat :pixzel-demo:cristalix --args="--width=2560 --height=1440"
```

The default window requests 2560 × 1440. Hidden verification runs use a borderless window so Windows does not trim a client pixel. The reference composition uses six columns, a translucent pinned toolbar and a single scrolling container for the banner and catalogue. Smaller windows reflow the cards and toolbar. The default scale follows the window height; `--scale=1` overrides it. Alt+F4 closes the standalone window.

## Interactions

- Search combines with category, language and favorites. Empty search and empty favorites have separate messages.
- Search restores the original animation from `85c7efd`: position springs at frequency 13, appearance at frequency 12, a 24-unit rise for reappearing tiles and a 24 ms stagger per tile (capped at index 18). Removed tiles fade out; clearing search reveals them in sequence. The banner's presence also animates again. Wheel scrolling uses the shared displacement after the animated content positions. Reduced motion snaps to the final state.
- Categories: all games, hubs, testing, arcade and tabletop. Language codes use a scrollable dropdown and preserve Russian navigation labels, as in the recording.
- Hover immediately darkens a card and opens its description, with the reference `corner.png` ornament reflected and tinted at all four corners. The centered favorite star is visible only during hover or keyboard focus, including saved favorites. Click the star to toggle a persistent favorite. Right-click a card to open its reviews.
- Both original promotional banners rotate with a horizontal transition and two timer-driven filling progress strips. `--reduced-motion` freezes decorative motion.
- Reviews opens the catalogue of rated modes. Clicking a mode opens its reviews, with all/positive/negative filters, sorting, scrolling, useful/not useful votes, reactions, report feedback and expandable comments.
- Rate a mode opens the reference form with positive/negative choices, a 500-character multiline editor, selection, clipboard shortcuts, undo/redo, emoji shortcuts, cancellation and local publication. New reviews appear in the current session and in My reviews.
- Escape closes the form before the reviews page, then dropdowns. Dialog focus is scoped and restored. Tab, Shift+Tab, arrows, Enter and Space operate the controls.

Favorites are stored in `pixzel-demo/build/cristalix/favorites.txt`. Counts, language eligibility and review entries are local fixture data based on the recording. The game action remains a local launch preview: this application does not connect to Cristalix servers, send reviews or submit reports to the service. User avatars are cropped from the supplied recording.

## Verification

```powershell
.\gradlew.bat :pixzel-demo:test :pixzel-demo:detekt verifyBoundaries verifyCodeStyle
.\gradlew.bat :pixzel-demo:cristalix --args="--smoke --verify"
```

The native 1050-frame fixture exercises pointer/keyboard transport, first-frame hover/tooltips, dropdowns, search, saved-star visibility, review navigation/publication, equal banner/card scroll displacement, responsive layout and reduced motion. It writes 12 captures under `pixzel-demo/build/reports/cristalix` and checks for zero Pixzel diagnostics. JUnit tests cover model/persistence, text alignment, panel coverage and multiline editing (Unicode graphemes, newlines, length limits, undo/redo and clipboard). The common runtime also tests that zero-opacity subtrees keep updating without submitting invisible GPU layers, and reappear correctly.

For individual captures, use `--smoke --scenario=<name>`. Scenarios include `menu`, `hover`, `scroll`, `dropdown`, `favorites`, `arcade`, `tabletop`, `hubs`, `testing`, `reviews`, `game-reviews` and `review-compose`. Add `--width`, `--height`, `--scale`, `--frames` or `--reduced-motion` as needed. `--profile --frames=300` measures completed GPU frames after 60 warmup frames and writes renderer memory, uploads and timing to `build/reports/cristalix/profile-<scenario>.txt`.

See [the correction and measurement report](CRISTALIX_FIXES.md) for pixel overlays, performance results and reproduction commands.

Artwork provenance is recorded in [SOURCES.md](../pixzel-demo/src/main/resources/cristalix/SOURCES.md). The reference implementation is in `pixzel-demo/src/main/kotlin/pixzel/demo/cristalix/`. The common Pixzel painter skips fully transparent subtrees, and identical opacity assignments no longer invalidate paint. Arc library sources and the renderer's defaults for other applications are unchanged.
