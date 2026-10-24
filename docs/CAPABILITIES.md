# Capabilities and verification boundaries

This describes the implementation in this checkout, not a cross-platform support promise. API availability, compilation, automated behavior, GPU pixel checks and manual visual inspection are different evidence. See [the user guide](USER_GUIDE.md), [Arc graphics details](ARC_GRAPHICS.md), [implementation ledger](IMPLEMENTATION_LEDGER.md) and [performance measurements](PERFORMANCE.md).

## Platform and artifact matrix

| Area | Implemented / verified | Boundary |
| --- | --- | --- |
| Independent runtime | Kotlin/JVM, JDK 21; core/common/reference tests without Arc or native libraries | Kotlin Multiplatform, Android, iOS and Kotlin/Native are not implemented targets |
| Native application | Windows 11 desktop, Arc OpenGL 4.1; actual GLFW mouse/keyboard and GPU tests | Linux/macOS runtime execution is unverified; matching packaged native classifiers alone do not establish support |
| Arc adapter dependency | Neutral Arc core/window/input/font/render contracts | pixzel-arc does not import concrete OpenGL/GLES/GLFW/LWJGL implementations; the application chooses them |
| Arc GLES | Companion Arc implementation compiles | Pixzel GLSL 4.10 renderer has no production GLES/ANGLE shader/runtime path |
| Vulkan | No Pixzel renderer | An interface or existing Arc backend is not a working Pixzel Vulkan implementation |
| Input | Ordered pointer/mouse, fractional wheel, keyboard/repeat, Unicode commit, masked gameplay polling | Touch/stylus IDs/types are contracts; native multitouch/gamepad adapters are not supplied |
| Navigation | Tab, directional focus, explicit navigation commands, component keyboard behavior | Applications supply gamepad-to-command mapping |
| IME | Preedit/selection/replacement/commit/cancel and candidate-caret contracts exercised with TestHost | ArcUiHost advertises imeComposition=false and imeCaret=false; no native Windows/macOS/Linux composition bridge |
| Accessibility | Semantics snapshots/actions, focus scopes/rings, reduced motion, independent text scale | No OS screen-reader bridge, native accessibility tree or assistive-technology certification |
| Clipboard/cursor | Arc neutral window services and explicit Result failures; TestHost fixtures | Availability still belongs to the selected host/window implementation |
| Assets | Injected asynchronous byte source/decoder, immutable decoded RGBA, Ui-thread uploader, state/retry/generation/ownership | No built-in filesystem/network loader or production PNG/JPEG codec is selected by common |
| Packaging | Source Gradle modules and Arc composite development build | No published stable release or binary compatibility guarantee is asserted |

The recorded GPU environment is Windows 11, Oracle JDK 21.0.10, Kotlin 2.4.10, Gradle 9.6.1, NVIDIA GeForce RTX 4060 Ti / driver 617.42, with an OpenGL 4.1 context. Those are test conditions, not minimum hardware requirements or results for other drivers.

## Renderer matrix

| Operation | RecordingBackend | SoftwareBackend | ArcUiBackend |
| --- | --- | --- | --- |
| Ordered commands, geometry/input assertions | Records and validates command structure | Executes reference pixels | Executes native GPU draws |
| Rect/rounded/ellipse shapes and transformed clips | Records | Pixel-center contains tests | Tessellation, owned stencil, configurable supersampling |
| Solid / linear / radial paint | Records | Supported | Supported |
| Images / sampling / source cropping / nine-slice | Records | SoftwareImage reference pixels | Native textures with independent samplers |
| Image brush | Records | Unsupported | Source crop, affine mapping, sampling |
| Positioned text | Records layout data | Requires a matching SoftwareGlyphProvider | Exact ArcTextService glyph IDs/sizes/positions through FreeType masks |
| Group opacity | Records | Offscreen reference composition | Pooled owned targets, group alpha applied once |
| Paths / stroke joins and caps | Records | Paths unsupported; limited closed rectangle/ellipse/rounded strokes | Nonzero/even-odd paths, holes/intersections, miter/bevel/round joins and butt/square/round caps |
| Shadows | Records | Unsupported | Pooled separable blur approximation |
| Custom operations | Records declared commands | Unsupported | Explicit operation registry, capability negotiation, scoped state/leases |
| Antialiasing | Not a rasterizer | None; deterministic pixel-center preview | Default 2× supersampling; selectable 1..4 |
| Scene-state preservation | No native state | No native state | Native scoped pass restores application's target/pipeline/bindings, including exceptional paths |

Software preview deliberately uses JVM BufferedImage/ImageIO behind pixzel-test. Its default pinned 5×7 ASCII provider is compatible with FixedFontTextService; unknown glyphs use a documented replacement. It is not a system-font substitute, a production text renderer, or evidence of native script coverage. Its glyph LRU is capped at 256 entries and 8 MiB by default and clears when its final session closes.

Arc merges adjacent compatible solid fills/strokes into ordered retained batches with per-vertex colors/transforms. Clip/group changes, gradients, images, text, shadows and custom operations flush them. Each resulting mesh, clip, text tile or composite issues a draw. Generic native metrics become unknown when unmeasurable custom rendering runs. No zero-draw-call, universal zero-allocation or universal 2 ms claim is made.

Arc owns one composition target per Ui session, not per Surface. Groups/effects allocate reusable additional targets. Explicit limits include 64 gradient stops, 255 stencil clip levels, 8 composition groups, a 256 MiB individual composition target and 512 MiB aggregate target pool by default. Text tiles use a 32 MiB/256-layout LRU. GPU meshes/batches share a 32 MiB/512-entry LRU; CPU flattened geometry has a separate 32 MiB/512-entry LRU. Owned RGBA image restoration retains a configurable 32 MiB source per image by default. Tessellation is bounded by 8192 points, 4096 non-horizontal edges, 4096 scan levels, 4,194,304 work units and 262,144 output vertices. Device texture limits are checked; see ARC_GRAPHICS.md for the precise contracts.

Context-generation tests deliberately create two unshared contexts with colliding native object IDs. Old sessions/passes reject submission/restoration; stale cleanup does not delete replacement objects. Owned RGBA images recreate from retained bytes; borrowed images require an explicit replacement/rebind. Clearing capabilities yields generation 0 and cleanup performs no native calls. Physical GPU-reset detection is the application's responsibility; automatic reset recovery notification is not implemented.

## Text matrix

| Feature | Native Arc text | Deterministic FixedFontTextService |
| --- | --- | --- |
| Real scalable font path | FreeType + HarfBuzz, pinned real TTF fixtures | Synthetic monospace fixture only |
| Measured/drawn identity | Native glyph IDs, bearings, advances and baselines shared with renderer | Deterministic positioned fixture data |
| Script evidence | Latin, Cyrillic and Arabic fixtures; mixed-direction Arabic/numbers/Cyrillic | No production script/shaping claim |
| Kerning / ligatures | Tested AV kerning and ffi shaping | Unsupported |
| Grapheme indices | UTF-16 extended grapheme boundaries, combining/ZWJ/region-pair segmentation | Same boundary model |
| Fallback | Ordered registered families; whole missing clusters become U+FFFD while source mapping remains | Deterministic fixture replacement behavior |
| Bidi | Java Bidi paragraph ordering plus HarfBuzz directional/script runs | Simplified direction positioning for tests, not full bidi |
| Styles | Real registered regular/bold faces, nearest available weight/slant; no synthetic italic/bold | Single style; nonempty spans rejected |
| Styled Text spans | Immutable full-style ranges, grapheme validation, mixed sizes/faces/direction, preserved overflow mappings | Unsupported explicitly |
| Wrapping / overflow / caret | Width, max-lines, newlines/tabs, trailing-whitespace policy, measured ellipsis and grapheme-safe caret | Deterministic equivalent algorithms for independent tests |
| Raster | Grayscale masks at independent physical density | Pinned software fixture masks through the support renderer |

Arabic evidence does not establish all complex-script/language coverage. Indic scripts, vertical layout, color emoji, variable-font axes, OpenType feature controls and font-file hot replacement are not validated features. Emoji/combining clusters remain intact for caret/truncation even when the supplied fonts lack a visible glyph and replacement is drawn. Ligature caret subdivisions use measured cluster coverage, not native GDEF caret data.

Font providers and faces are explicitly owned and thread-confined. Native `load(id, bytes, faceIndex=0, version=0)` rejects live duplicate IDs; when bytes change under a reused ID in a replacement provider, supply a new nonnegative version and update FontFamily references. Each ArcTextService receives a distinct provider identity by default. Supplying an explicit shared providerId asserts resource compatibility and makes that assertion the caller's responsibility.

Default label behavior is configurable through Theme.buttonText, inherited StyleTokens.buttonText and explicit instance settings. Reveal configuration is shared, runtime clocks are per label. Marquee changes draw offset without repeated full layout/raster upload; reduced motion displays static overflow. A different ellipsis policy may reshape its candidate substring. Full source text is retained in semantics; applications can add focus-triggered tooltips/alternate detail views.

The bundled Noto Sans Regular/Bold and Noto Sans Arabic fixtures have pinned source commit and SHA-256 records in [assets/fonts](../assets/fonts/README.md), with OFL 1.1 notices. LWJGL, HarfBuzz and FreeType notices ship in the native font module. Native packaging/licensing details are in that module's README.

## Components, lifecycle and extension

The required standard families have direct construction plus DSL composition: Surface/Box/Stack and layout policies, Text/Image/Icon, Button/IconButton with arbitrary slots, Checkbox/Toggle/Radio, Slider/Progress, single-line TextField, ScrollView/ScrollBar, vertical VirtualList, Tabs/Dropdown/Menu, Popup/ContextMenu/Tooltip/Modal, and NotificationHost. Some foundation names are aliases or composition helpers, not distinct runtime classes. Rich/multiline editors, trees/data grids/docking and OS file drag/drop remain optional and are not implemented as standard components.

VirtualList supports stable keys, variable row heights, viewport/overscan materialization, keyed model state and a bounded focused-row pin. Its model/height caches can scale with the item count; live Element count is bounded by visible rows, overscan and that pin. It is vertical-only. ScrollView supports vertical, horizontal and both-axis content. Tabs retain inactive pages in collapsed state; menus/dialogs use focus scopes and topmost dismissal.

Typed DSL scopes restrict standard Row/Column weight and Grid-cell helpers; direct public LayoutConfig retains parent data for custom policies. Theme/scoped tokens, explicit instance properties, public state, layout, Painter/skin, hit shape, overflow/reveal, host/backend/text and custom-operation contracts are extension boundaries. The separate HealthBar sample demonstrates that extension without internal casts/imports.

Detach differs from disposal: attachment leases close and can reopen; detached components do not paint or receive input. Owner resources close in reverse order with aggregated errors. Ui borrows injected services and owns its session/tree-created resources. Async generation tests cover supersession, close races, retry/errors, reentrant upload and owned/borrowed results. Reduced-motion, transaction recovery, painter failure, capture cancellation, modal restoration, inherited-disabled activation and host polling masking have automated regressions.

Unsupported platform integrations above are explicit capability boundaries. Optional items are not silently counted as mandatory implementation work. Automated GPU checks establish tested pixels/state/lifetime on the recorded machine; screenshots and any manual review should be reported separately from unit/integration passes.
