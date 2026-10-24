# Input, focus, interaction and accessibility


## 1. Independent event model


Accept host events for pointer move/down/up/cancel/enter/leave; wheel with units/phase; key down/repeat/up; text commit; composition update/commit/cancel; window focus and metrics changes.


Events include timestamp/sequence, host/window identity, modifiers and pointer ID/type where applicable. Distinguish semantic keys from scan/physical codes. Do not derive text from KeyCode: layouts, dead keys and IME need text events.


Store/deliver events in order; thread-pool scheduling does not determine UI order. Coordinates use a consistent host metrics snapshot. After resize, perform necessary layout before positional events.


Mouse/keyboard are mandatory in 1.0. Pointer ID/type contracts accommodate touch/stylus. Gamepad/native multitouch adapters are optional; include the basic navigation-command API immediately.


## 2. Hit testing


Traverse reverse visual paint order, accounting for layers, stacking contexts, transforms, clips, custom shapes, effective visibility, enabled state and pointer policy.


- AUTO: the node can be a target according to shape/behavior; dispatch determines consumption.
- PASS_THROUGH: the node itself is not a target; test children.
- NONE: exclude the node and subtree from positional hits.


Zero opacity does not implicitly disable interaction; use visibility/pointer policy. Decorative labels default to pass-through and do not block Button.


Recompute hover from the stable root pointer and current geometry, including layout/scroll without mouse movement. Text-only marquee transforms do not change Button's hover region.


## 3. Routing


Route through logical root→target capture, target, then target→root bubble. Handlers receive root coordinates and correct local coordinates for the current receiver. Portal events follow logical ownership.


Distinguish stopPropagation (stop routing), preventDefault (cancel default action) and consume (block gameplay/lower input layers). Define default-action ordering and removal-during-dispatch behavior. Snapshot the route at event start, skip disposed handlers and verify target lifetime afterward.


The UI router precedes gameplay bindings. Consumed events do not activate them. Arc's existing global polling also needs an InputContext/masked gameplay view; consumed events alone cannot hide a physically held key from polling. Update demos accordingly.


Unconsumed input may reach gameplay. A modal scrim may consume pointers outside its dialog; noninteractive HUD decoration need not block gameplay.


## 4. Click, capture and drag/drop


Default Button activation: enabled primary down inside→pressed/capture; up within the same activation region→click; outside release/cancel→no click. Capture alone does not imply click. Click policies are replaceable.


Capture is per pointer ID. Drag events continue outside bounds. Focus loss/detach/close release capture and send cancel; pressed state cannot remain stuck.


Drag/drop includes a dp threshold, typed payload, source/target lifecycle, preview overlay, acceptance/rejection and explicit completion/cancellation. Semantic actions can offer keyboard item movement. OS file drag/drop is not mandatory for 1.0.


Scroll goes to the nearest capable ancestor. At an edge, policy may pass the remainder upward. Preserve high-resolution wheel deltas rather than reducing them to Boolean.


## 5. Focus


Ui owns its focus manager: request/clear/move, Tab/Shift+Tab and directional navigation. Configure focusability, tabIndex/order, focus-visible, scopes and default focus.


Disabled/collapsed nodes cannot focus. Removing a focused node selects a valid replacement by policy. Focus cannot silently point to a recycled VirtualList cell for another item. VirtualList supports keyed materialization/scroll-to-focus.


Modals trap focus, remember prior focus and restore it on close if still valid. Escape dismisses the top dismissible popup/modal; handled events do not fall through to gameplay. Menus support arrow/Home/End navigation.


Enter activates Button. Space presses on down and clicks on up under explicit repeat rules. Default repeat must not generate uncontrolled click bursts. Custom behavior can replace these rules.


## 6. TextField and platform services


Required single-line TextField: caret, selection/select-all, word navigation, grapheme-safe Backspace/Delete, clipboard copy/cut/paste, validation, placeholder, disabled/read-only, horizontal caret scrolling and bounded undo/redo.


Text input is separate from shortcuts. Password mode masks display and disallows copy according to policy; it does not promise cryptographic protection of JVM String.


UiHost/platform services provide clipboard and cursor shapes, with result/error and explicit unsupported behavior. Do not substitute a global temporary clipboard variable when the host API exists.


Define IME contracts immediately: preedit range, composition selection, candidate-window caret rectangle, commit/cancel and no temporary preedit writes into committed state. Real OS IME adapters declare capability. GLFW alone, without a new native adapter, must not claim full composition. Ordinary Unicode commit is required in 1.0. Windows/macOS/Linux native IME bridges are separate platform work with an accurate matrix.


## 7. Semantics and accessibility


Public components expose role, label, value, enabled/checked/selected, actions, focus order and root bounds. Custom-drawn components can expose semantics without visible text.


OS screen-reader bridges are optional, but the semantics tree and accessibility-action tests remain required.


Required: keyboard-only showcase navigation, visible focus rings, reduced motion, independent text scale and no information accessible only through hover animation. Overflowing labels retain their full semantic value and tooltip/alternate view; visual ellipsis does not truncate accessible text.


Tooltips can open on focus as well as pointer hover. Theme configures delays. Tooltips do not capture focus automatically. Disabled-tooltip policy is independent of click policy.


## 8. Lifetime and acceptance


Remove subscriptions/listeners on detach/dispose according to scope policy. Window focus loss clears pressed/capture and completes/cancels composition. Reattaching a host does not duplicate callbacks.


Test overlap/transparent HUD, nested clips, portal coordinates, outside clicks, capture after removal, rapid same-frame press/release, repeat, modal focus restoration, tab order, nested scrolling, gameplay consumption plus masked polling, emoji selection, clipboard errors, window focus loss and VirtualList reuse.