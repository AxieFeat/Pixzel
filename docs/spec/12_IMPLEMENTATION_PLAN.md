# Implementation plan and readiness criteria


## Delivery principle


Each stage leaves a working vertical scenario. The first prototype is not completed Pixzel. Final 1.0 includes every mandatory requirement in the technical documents; Arc complexity does not justify reducing scope to two buttons.


Establish the current dev baseline first. If it differs from the reviewed commit, update the API map without reverting useful owner changes.


## Stage 0. Review, baseline and ADRs


- Compare Arc code/style/dependencies with document 02.
- Run the minimum Gradle tasks and record existing failures/environment limits.
- Decide module graph, explicit services, Surface/trees, retained DSL/state, text provider and render-pass/input-context contracts.
- Validate native font provider/packaging before designing dependent APIs.
- Create project structure and initial public API samples.


**Exit:** reproducible environment, coherent API and an initial small module build. Full Vulkan is not required.


## Stage 1. Independent core and test pass


Implement Ui lifecycle, Element/Surface ownership, units/constraints, basic root/anchor container, rect/text protocol, TestHost, FakeClock and RecordingBackend.


**Scenario:** create a Surface/text without Arc; record and test measurement, coordinates and draw order.


**Done:** no forbidden core/common dependencies; public backend replacement works; close releases owned resources.


## Stage 2. DSL, state and incremental layout


Implement scopes, direct factories, Row/Column/Stack, intrinsic sizing, bindings/transactions, keyed dynamic scopes and invalidation.


**Scenario:** a counter updates one label; keyed reordering preserves focus/state.


**Done:** static frames perform no measure/shape/rebuild; DSL/direct APIs agree; no impossible Kotlin implicit conversions.


## Stage 3. First real Arc render/input/text path


Implement minimal ARC-01…07 and ArcUiHost/ArcUiBackend/ArcTextService. Draw UI over a simple 3D/triangle scene, with a real font and basic input events.


**Scenario:** clickable Cyrillic-labeled Button on Arc/OpenGL 4.1, text entry and correct resize/DPI.


**Done:** no concrete renderer/window imports in Pixzel; intact scene after UI; consumed actions do not reach gameplay; text commit is separate from key events.


## Stage 4. Full Surface and responsiveness


Complete constraints, Grid/Flow, padding/margins/weights, transforms, breakpoints, safe area, clipping, portals and popup placement.


**Scenario:** top-left notification with locally centered text, nested scrolling and state-preserving Row→Column changes at scales 1.25/2.


**Done:** document 04 coordinates, exact shape clips, matching visual/hit geometry and zero/minimized viewports.


## Stage 5. Text and configurable reveal


Implement shaping/layout caches, fallback, wrap/maxLines and extensible overflow/reveal. Configure default Button labels.


**Scenario:** long label→ellipsis→WAIT→forward/pause/backward, with live showcase parameters.


**Done:** grapheme-safe truncation, supported-provider RTL, text/resize changes mid-reveal, reduced motion, no motion-only reshaping and external policies without Text/Button patches.


## Stage 6. Appearance and standard components


Implement all of document 10; shapes/paths/gradients/images/nine-slice/shadows/group opacity; themes/tokens/skins; ARC-08 and capabilities.


**Scenario:** identical UI in three skins, including modal/menu/toasts/TextField/virtualized list.


**Done:** arbitrary content/painters, keyboard use, selection/clipboard/undo, popup/modal focus, drag/drop and bounded materialization for 100,000 items.


## Stage 7. Independence and public extension


Add real minimal non-Arc software preview and external sample components/policies. A shared builder constructs the Arc and TestHost trees.


**Done:** working independent basic operations, declared capabilities and compiling public extensions without internal access or a core fork.


## Stage 8. Lifecycle, performance, documentation and release


Profile, benchmark and run stress/golden/contract checks. Complete inspector, cache/ownership review, API compatibility and packaging.


**Done:** README launch instructions, Arc patches/migration, pinned assets/fonts/licenses, accurate backend matrix, test report and benchmark protocol.


## Mandatory scope


| Feature | 1.0 condition |
| --- | --- |
| Arc/OpenGL 4.1 integration | Required |
| Independent core/common and non-Arc reference | Required |
| Surface/DSL/responsiveness/state/custom components | Fully required |
| Text, TTF/OTF Latin/Cyrillic, clusters, ellipsis/reveal | Required |
| Capability-aware shaping/bidi/fallback contracts | Required; script coverage established by provider tests |
| Keyboard/mouse/text commit/clipboard | Required |
| IME composition contract and semantics tree | Required |
| Native IME/screen-reader bridges | Optional platform stage; disclose absence |
| Basic shapes/path clips/shadows/group opacity | Required in Arc/OpenGL |
| GLES/ANGLE | Architecture/capabilities required; production support after tests |
| Vulkan | Does not block 1.0; no fake support |
| World-space UI/Display | Optional adapter |
| KMP/Android/iOS | Optional delivery |
| Full rich-text editor/SVG/browser styling | Outside mandatory 1.0 |


## Per-stage definition of done


- No required TODO()/silent no-op operations.
- New behavior has minimal meaningful verification.
- Public samples stay current and compile against the implementation.
- Packages follow Arc style.
- Document ownership, threading, units and unsupported behavior.
- Preserve user changes; Arc additions remain neutral and Pixzel-independent.


## Final delivery


1. Pixzel sources, builds and publication metadata.
2. Arc changes as a coherent sequence of commits/patches.
3. API reference, quick start, extension/Surface/text-reveal guides.
4. Showcase with scale/theme/reduced-motion modes.
5. Headless and minimal software examples.
6. Checks, benchmark scripts/results and capability matrix.
7. Specific platform/function/condition limitations, not vague “possible bugs.”


GitHub commits/PRs require the authority granted by the user. This specification authorizes development/local changes; it is not an instruction to publish releases, change access or message third parties.