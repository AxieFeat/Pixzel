# Text, fonts, ellipsis and hover reveal


## 1. Separate text pipeline


Do not measure text by creating VertexBuffer. Separate:


1. Unicode text, spans/style, locale/direction and available width.
2. Grapheme/word/line segmentation.
3. Font fallback and shaping into glyph runs.
4. Line breaking, baseline/alignment and overflow.
5. TextLayout: size, lines, glyph positions, cluster↔text mapping, hit/caret data.
6. Rasterization/atlas preparation and drawing without repeated layout.


Draw exactly the layout measured. TextService is replaceable through public API; geometry/input knows nothing about font-atlas formats.


FontFace identifies a face/resource; FontFamily specifies fallback. Style includes size/sp, weight, slant, letter spacing and line height. Do not invent ascent/descent/line-gap/advance metrics from bitmap bounding boxes.


Version 1.0 requires a real TTF/OTF path supporting at least Latin/Cyrillic, kerning, correct Unicode clusters and fallback. Legacy bitmap fonts remain an additional supported implementation. For bidi/complex scripts, choose a capable shaper, for example HarfBuzz/FreeType in a separate Arc module. Check licensing, bindings, native packaging and API before selection. Claim script coverage only from provider tests.


stbtt_GetCodepoint* alone is not complete shaping/bidi. Missing glyphs use replacement/fallback rather than disappearing. Native dependencies stay out of Pixzel core/common.


## 2. Unicode and layout


Handle surrogate pairs, combining marks and extended grapheme clusters. Caret movement, deletion, selection and truncation must not split emoji/combining sequences. UTF-16 internal indices are allowed, but public contracts state index units and provide mappings.


TextLayout accounts for kerning, ligatures, explicit newlines, tabs, trailing-whitespace policy, wrapping and maximum lines. AUTO/LTR/RTL and logical START/END differ from physical LEFT/RIGHT. The text provider supplies bidi visual order and caret mapping.


Spans do not require an Element per character. Inline icons may use text attachments with explicit metrics.


## 3. Static overflow policies


TextOverflowPolicy is externally extensible. Presets:


- CLIP: complete layout under a clip;
- ELLIPSIS: truncate at the visual end;
- ELLIPSIS_START and ELLIPSIS_MIDDLE;
- WRAP: wrap within constraints/maxLines;
- VISIBLE: overflow bounds subject to ancestor clips.


Prefer separate wrapping/maxLines properties; policy aliases must not create contradictory configuration. When wrapping reaches maxLines, put ellipsis on the final visible line.


Default marker: `…` (U+2026). Allow exactly `...`, a custom string/icon, or no marker. If the ellipsis glyph is absent, default to three periods. Measure the marker through the same font/fallback provider.


For full-text width W and available width V:


- W<=V+epsilon: do not truncate;
- otherwise choose a valid **cluster boundary** where shaped prefix+marker fits;
- reshape substrings when context changes; summing old advances may be wrong;
- if marker width exceeds V, apply explicit TOO_NARROW policy: clipped marker by default, optional empty/custom behavior;
- V<=0 must not produce negative sizes or division errors.


String.length-based measurement and character-by-character take(n) are invalid.


## 4. Hover reveal is separate behavior


Default Button label: one line, no wrapping, static ellipsis, **ping-pong reveal of full text** on hover. Focus activation is configurable. This is a default skin/label policy, not hardcoded Button logic.


TextRevealBehavior receives full layout, viewport, activation state, clock, reduced-motion preference and configuration. It returns offset, selected draw layout and whether another update is needed. It changes neither source text nor element size.


| Parameter | Default | Customization |
| --- | --- | --- |
| Trigger | Pointer hover | Focus, hover-or-focus, always, explicit State |
| Initial delay | 600 ms | Any nonnegative Duration |
| Speed | 32 dp/s | Speed or a separate duration mode |
| End pause | 700 ms | Independent duration |
| Start pause | 700 ms | Independent duration |
| Direction | Leading→trailing→leading | Physical direction, one-way, custom |
| Loop | While active | Once, repeat count, custom |
| Deactivation | Immediate static ellipsis | Finish segment, animate return, retain offset |
| Reduced motion | Static ellipsis | Tooltip/full-text affordance, explicit override |
| Insufficient overflow | No animation | Configurable epsilon/threshold |


Defaults are public design choices replaceable at theme, component-type and instance levels.


### State machine


| State | Display | Transition |
| --- | --- | --- |
| IDLE | Truncated text | Activation→WAIT |
| WAIT | Keep ellipsis | Delay expires→FORWARD |
| FORWARD | Full run moves toward trailing edge under clip | End reached→END_PAUSE |
| END_PAUSE | Full-text tail visible | Pause expires→BACKWARD |
| BACKWARD | Full run moves toward leading edge | Start reached→START_PAUSE |
| START_PAUSE | Initial fragment visible | Pause expires→FORWARD |
| Any active state | Full layout/viewport changes | Recalculate distance; reset/rebase safely by policy |


From FORWARD onward, remove the marker and move the original full run under clip. During initial delay keep ellipsis. Default deactivation returns to IDLE. Hover anywhere within Button, including icons/empty space, activates its label behavior. Decorative text children do not intercept Button events.


For LTR with normalized full-run coordinates:


```text
distance = max(0, W - V)
forwardDuration = distance / speed
offset: 0 → -distance → 0
```


RTL may start at -distance and move in the opposite physical direction. Visual text layout and policy determine direction. Do not animate when distance==0 or speed<=0; negative speed is invalid configuration, zero disables animation.


Large dt advances correctly through multiple phases without losing pauses or iterating millions of skipped periods. Paused/hidden behavior uses the UI clock policy, not wall-clock assumptions.


## 5. Required customization


For Button labels and standalone Text, independently configure:


- overflow policy and marker;
- reveal behavior, trigger and parameters;
- clip/viewport padding;
- full-text tooltip and delay;
- wrapping, max lines and horizontal/vertical alignment;
- font/fallback, direction and text scale;
- behavior on disable, focus loss, reduced motion and detach;
- custom algorithms, including disabling ellipsis/reveal entirely.


Multiline reveal is off by default; custom policies may provide paged/vertical reveal. Do not impose endless marquee on all labels.


## 6. Caching and invalidation


Cache keys include content/spans, font identity/version, size/scale, locale/direction, wrap/maxLines, width, spacing and overflow configuration. Resource generations protect atlas references.


Reveal updates only transform/offset/paint. Do not reshape, rebreak lines or rebuild GPU geometry each frame. Resize/font/text changes invalidate layout/distance. Hover/focus needs no new shaped run if static/full layouts are cached.


Bound caches with LRU/byte budgets; transient chat strings must expire. Worker shaping is allowed, but apply only current-generation results. GPU atlas updates stay on the render thread.


## 7. Tests and showcase


Required cases: empty/single cluster, exact fit, V narrower than marker, Cyrillic, mixed Latin/Cyrillic, surrogate emoji, combining mark, emoji ZWJ sequence, bidi for declared providers, kerning pair/ligature, newline/wrap/maxLines, missing glyph, font replacement and DPI/resize during motion.


FakeClock tests IDLE/WAIT/FORWARD/pauses/BACKWARD/deactivation. Assert actual offset, direction, distance and no measure/shape calls during animation-only frames.


The showcase supports mouse/keyboard activation of an overflowing Button and live changes to speed, delays, marker, direction, reduced motion, width and policy without changing Button implementation. An external reveal implementation must work.