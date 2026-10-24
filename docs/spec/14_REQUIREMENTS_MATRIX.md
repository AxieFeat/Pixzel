# Requirements matrix and final acceptance


Map the owner's request to technical contracts. During implementation add source/sample links and verification results per row. An interface's existence is not a completed user scenario.


| ID | Requirement | Documents | Verification |
| --- | --- | --- | --- |
| PZ-01 | Library named Pixzel | 01, 03 | Pixzel module/artifact/document naming |
| PZ-02 | UI in Arc games/applications | 03, 09, 13 | Real Arc/OpenGL showcase over a scene |
| PZ-03 | Non-Arc implementation | 03, 11, 13 | Core/common dependency checks; TestHost and software/recording backends |
| PZ-04 | Author's code style | 02, 05 | Interfaces/internal SimpleX, factories, KDoc, conventions |
| PZ-05 | Package style | 02, 03 | Domain-based pixzel.*, no com.* or core/impl namespace |
| PZ-06 | Modern Kotlin | 05 | DSL marker, typed units, extensions/state, readable samples |
| PZ-07 | Powerful DSL | 05, 13 | Showcase screens built through DSL |
| PZ-08 | Direct API and flexibility | 03, 10, 13 | Same elements without DSL; external component module |
| PZ-09 | Arbitrary appearance | 06, 10 | Three skins, paths/images/nine-slice, arbitrary content/painter |
| PZ-10 | Responsiveness | 04, 05 | Resize, density/uiScale/textScale, container breakpoints, preserved state |
| PZ-11 | Long labels show ellipsis | 07 | Cluster-safe truncation after padding/icons |
| PZ-12 | Hover ping-pong scrolling | 07, 13 | Fake-clock tests and interactive Button |
| PZ-13 | Fully replaceable behavior | 05, 07 | External overflow/reveal strategies, per-instance override/disable |
| PZ-14 | Surface/coordinate placement | 04 | Anchor/pivot/offset, local↔root conversion |
| PZ-15 | Top-left notification | 04, 10, 13 | Root position (24,24) in normative example |
| PZ-16 | Text centered in nested Surface | 04, 13 | Text root origin (84,48), independent of window center |
| PZ-17 | Authorized Arc changes | 09 | ARC-01…09, neutral APIs, migration |
| PZ-18 | Detailed Markdown specification/prompt | All files | README/index, master prompt, technical documents |
| PZ-19 | Correct input/focus/UI-game boundary | 08, 09 | Capture, modals, text commit, consumption, masked polling |
| PZ-20 | Performance/lifetime | 11 | No static relayout, budgets, repeated cleanup, benchmarks |
| PZ-21 | Accurate support matrix | 06, 09, 12 | No false Vulkan/GLES/IME/platform claims |
| PZ-22 | Compiling public extension API | 03, 10, 13 | External component/policy without internal access |
| PZ-23 | High-quality text | 07, 09 | Real font provider, Unicode/fallback, matching measure/draw |
| PZ-24 | Preserve the game scene | 06, 09, 11 | State/target restoration, no unsolicited clear |


## Key Given/When/Then scenarios


### A. Nested Surface


**Given:** 1280×720 dp root; 320×72 notification at offset (24,24), padding 12; 200×24 text with CENTER/CENTER.


**When:** layout runs.


**Then:** notification origin=(24,24); text local origin=(60,24); text root origin=(84,48). No extra framebuffer.


### B. Text reveal


**Given:** single-line label wider than Button's inner content; marker `...`, delay 600 ms, speed 32 dp/s.


**When:** pointer enters any Button region and the clock advances through delay, forward/backward motion.


**Then:** show ellipsis during delay, then the full run under clip from leading to trailing edge and back with pauses. Motion does not increase shaping count. Exit restores configured static behavior. Custom policies replace this without Button patches.


### C. Retina and scale


**Given:** 800×600 logical window, 1600×1200 framebuffer, UI scale 1.25.


**When:** window-logical pointer=(125,125).


**Then:** UI pointer=(100,100) dp; framebuffer position=(250,250) px; root=640×480 dp. The same Button draws and hits consistently.


### D. Modal and gameplay


**Given:** a world action bound to primary click and physical polling behind a dialog.


**When:** the user clicks modal UI.


**Then:** UI action executes; gameplay events/masked polling do not trigger the world action. Closing restores focus and leaves no capture.


### E. Rendering/transparency


**Given:** configured scene depth/cull/blend/viewport/target followed by translucent UI with nested clips.


**When:** Ui.frame completes or a custom painter throws.


**Then:** scoped state restores, scene target is not cleared and the next draw uses expected state. Mask/clip stacks remain balanced.


### F. Independent backend


**Given:** an application with no Arc artifacts.


**When:** the shared builder creates its menu on TestHost/RecordingBackend and basic software preview.


**Then:** compilation/execution succeeds, geometry/event contracts agree and limited preview capabilities are explicit.


### G. Layout and state


**Given:** a responsive container with TextField state and selection.


**When:** a breakpoint switches Row to Column and items reorder by stable key.


**Then:** preserve entered text, selection and valid focus without rebuilding the entire tree.


### H. Resources


**Given:** a borrowed application texture and owned UI glyph atlas.


**When:** menus mount/close 1,000 times, then Ui closes.


**Then:** borrowed texture survives; owned resources release according to GPU lifetime; listeners/tasks do not accumulate.


## Insufficient outcomes


- Expressive DSL without layout/input/resource implementation.
- Components that cannot be created outside DSL.
- Abstract interfaces still requiring an Arc singleton internally.
- Direct OpenGL/GLFW calls in Pixzel hiding missing Arc API.
- take(n) ellipsis without metrics/cluster boundaries.
- Rebuilding glyph meshes every marquee frame.
- A framebuffer per Surface/element.
- Setter-mirroring tests that verify neither visible behavior nor routing.
- Empty backend methods declaring support.
- Native IME/screen-reader/Vulkan/mobile claims without implementation/tests.


## Final review


Review every PZ ID, all mandatory technical requirements and capabilities. Any unmet mandatory item means “incomplete,” with its exact blocker/next step. List optional platform/features separately from missing required functionality.