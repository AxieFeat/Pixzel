# Components and extensibility


## Required 1.0 set


| Group | Components | Behavior |
| --- | --- | --- |
| Foundation | Surface, Box/Stack, Row, Column, Flow, Grid, Spacer, Separator | Layout, slots, clipping, transforms |
| Display | Text, Image, Icon | Typography, overflow, fitting, sizing |
| Actions | Button, IconButton | Pointer/keyboard, disabled state, arbitrary content |
| Selection | Checkbox, Radio, Toggle | Checked/indeterminate/group semantics |
| Values | Slider, Progress | Min/max/step, keyboard, determinate/indeterminate |
| Input | Single-line TextField | Caret, selection, clipboard, validation, undo/redo |
| Scrolling | ScrollView, ScrollBar | Nested scrolling, clipping, scrollIntoView |
| Data | VirtualList | Stable keys, variable heights, viewport materialization |
| Navigation | Tabs, Dropdown/Menu | Navigation/focus, selection |
| Overlays | Popup, ContextMenu, Tooltip, Modal/Dialog | Portals, dismissal, layers, focus |
| Messages | Notification/Toast host | Anchor, queue/stack, timeout, own Surface |


Container aliases may share runtime types when semantics match. TreeView, data tables, docking, rich-text/multiline editors, full SVG loaders and native widgets are optional.


## Component composition


Separate model/state, Element lifecycle/Surface, layout policy, slots, interaction behavior, skins/painters and semantics.


Button must not inherit a giant flag-heavy concrete Widget. Replace skins without resetting focus/handlers/model; replace behavior independently. Public bases are conveniences, not requirements to inherit internal SimpleElement.


Every required component has direct API without DSL, typed properties/events, applicable disabled/visibility/focus semantics and documented lifetime.


## Content slots


Button: leading/content-or-label/trailing/background/foreground. Dialog: header/body/actions. Notification: icon/title/body/actions/close. Dropdown: field/item/empty content.


Slots are not Text-only. Custom content replaces the standard label rather than drawing on top. Compute label width after padding/leading/trailing and use that width for ellipsis/reveal.


External components do not require a giant renderer switch over concrete element types.


## Notifications as Surfaces


NotificationHost is a convenience over ordinary Surfaces. It controls root layer/anchor, spacing, visible limit, queue and lifetime. Each notification has its own Surface for child placement/centering.


Consumers may instead place an ordinary Surface top-left; coordinates work identically.


Timeout is an optional Duration, with configurable hover/focus pause. Closing animation retains resources until final paint, but closing nodes no longer activate. Queue overflow is explicit. Policy chooses wrapping/maxWidth or ellipsis/reveal. Toasts never exceed the viewport.


## VirtualList


100,000 logical items do not imply 100,000 materialized Elements. Viewport/overscan determine the working set; stable keys are mandatory.


Variable heights use caches/incremental measurement. Refining heights above the viewport preserves anchor item/offset. Model/cache size may depend on item count; runtime nodes stay bounded.


Selected/editing/expanded state is keyed separately from recycled nodes. Rebind handlers/bindings so stale items receive no actions. Focus uses explicit pin/materialize/transfer policy. Fast scrolling causes neither negative ranges nor full-tree rebuilds.


## External acceptance component


Implement HealthBar or RadialActionButton in a separate sample module:


1. Public model with configurable value/slots.
2. Custom layout or composition of standard policies.
3. Path/gradient/image painter.
4. Custom hit shape where interactive.
5. Applicable semantics and keyboard/focus actions.
6. DSL extension and direct factory API.
7. Animation, owned subscriptions and cleanup.
8. RecordingBackend operation without Arc.


Do not copy internal SimpleElement, access internal members or patch core for the sample.


## Behavioral details


Selection groups define single/multiple/none and removal of selected items. Slider defines rounding/clamp/step and min==max. Radio groups define navigation.


Read-only TextField allows selection/copy; disabled does not. Validation permits intermediate editing states. Indeterminate Progress honors reduced motion.


A popup must not close on the same outside-click that opened it. Modal outside dismissal is opt-in. Tooltips steal neither focus nor capture.


Default themes are usable immediately. Showcase light, dark and game skins with identical semantics.