# Surface, coordinates, layout and responsiveness


## 1. Surface definition


A Surface is a positioned area with a local coordinate system. It combines size, placement, content box, transforms and clipping. It is neither a native window nor a framebuffer. A button, notification or container can provide a Surface for its children.


Each Surface has:


- a logical owner, geometric parent and stable identifier;
- requested sizing/placement and a computed LayoutResult;
- border box, padding/content box and local bounds;
- local-to-parent, local-to-root and inverse transforms;
- effective clip/visibility and paint order;
- toParent/toRoot/fromRoot/mapRect operations;
- separate layout and paint transforms.


Configure layout through element/placement APIs. Computed geometry is read-only: arbitrary `surface.width = ...` after layout is not allowed. This prevents conflicts between DSL, animation and measurement.


Local UI coordinates use X right, Y down, origin at the border box's top-left. The renderer converts to backend coordinates. Coordinates are Float logical units; time is monotonic.


## 2. Anchor, pivot and offset


Normalized anchor and pivot are independent. Anchor selects a point in the **chosen parent box**; pivot selects a point in the child's border box. The default parent reference is its content box after padding and root safe-area handling.


For axis-aligned placement:


```text
childX = parentContentLeft + anchorX * parentContentWidth
         - pivotX * childWidth + offsetX
childY = parentContentTop + anchorY * parentContentHeight
         - pivotY * childHeight + offsetY
```


Anchor neither scales nor sizes the child; constraints determine size. TOP_LEFT=(0,0), CENTER=(0.5,0.5), BOTTOM_RIGHT=(1,1). Convenience aliases may set anchor/pivot together; granular APIs keep them separate.


Offsets are signed regardless of anchor: positive X moves right, positive Y down. Use `offset(24.dp, 24.dp)` from top-left; `offset((-24).dp, (-24).dp)` for inward bottom-right placement. An inset convenience API needs explicitly documented positive-inward semantics.


Apply root safe area once. Children explicitly choose `referenceBox = CONTENT/BORDER/SAFE_AREA`; do not add ancestor insets repeatedly. Ordinary containers default SAFE_AREA to CONTENT.


### Normative notification example


Root: 1280×720 dp, zero safe insets, uiScale=1. Notification: 320×72 dp, top-left placement, offset (24,24), padding 12 dp:


- notification border origin in root: (24,24);
- notification-local content box: (12,12,296,48);
- measured text: 200×24 dp;
- CENTER anchor/pivot text origin in notification: (60,24);
- text origin in root: **(84,48)**.


Root resize preserves the 24 dp inset. With top-right anchor/pivot and offset (-24,24), X becomes `rootContentRight - 24 - width`. Text remains centered within the notification.


## 3. Units and DPI


Distinguish:


| Quantity | Example | Purpose |
| --- | --- | --- |
| Window logical size | 800×600 window units | OS cursor/window coordinates |
| Framebuffer size | 1600×1200 px | Raster target |
| UI scale u | 1.25 | User interface scale |
| Text scale t | 1.2 | Additional font scale |


For logical window width Lw and framebuffer width Fw:


- `rootWidthDp = Lw / u`;
- `framebufferPixelsPerDpX = (Fw / Lw) * u`;
- `pointerDpX = pointerWindowX / u`;
- resolve Sp to dp through t, then to px through pixels-per-dp.


Use the corresponding Y scale. Do not apply DPI to the cursor twice. Suspend rendering for minimized/zero framebuffers; avoid division by zero. Host metrics are a consistent generation-tagged snapshot.


dp denotes logical UI units, px explicit physical pixels and sp text size. Dp supports signed finite offsets; sizing/padding validate non-negativity where required. Provide meaningful unit operators and typed size expressions without implicit dp/px/sp mixing. Units are not untyped Float aliases. Value classes plus layout value types are allowed. `50.percent` means 50% of the reference box's finite available size.


Pixel snapping is a policy. Retain fractional layout and round consistently at raster boundaries; shared edges must round identically. Visual and hit boundaries must not visibly diverge. Texture scaling supports nearest/linear selection.


## 4. Measure and arrange


Use a clear two-phase contract:


1. `measure(constraints, environment)` computes desired size and baseline.
2. `arrange(allocatedBounds)` places children and stores Surface results.
3. Paint consumes layout results without changing size.


Constraints have nonnegative finite minima and finite or unbounded maxima; min<=max. Reject NaN. Infinite values never reach final paint bounds.


Per-axis sizing supports fixed(dp), wrapContent, fillAvailable, fractions/percentages of finite reference size, min/max/clamp, aspect ratio subordinate to constraints, and Row/Column weights.


In Row/Column, measure fixed/wrap children first, then distribute finite remaining space among weighted/fill children. On the main axis, fillAvailable means a share of remaining space with default weight 1, not the entire parent size for each child. On the cross axis it fills available size within constraints. With no remaining space, respect minima and explicit overflow/shrink policy; sizes never become negative.


Measure content-driven children before final arrangement, and text against its actual available width. “Width from text, text from width” requires a defined intrinsic contract, not unbounded remeasurement loops.


fillAvailable/percentages on an unbounded axis without a finite reference are diagnosable configuration errors. An explicit WRAP_CONTENT fallback is allowed; silent fallback is not. Test Scroll and wrap-sized parents particularly. Min/max override preferred size and aspect ratio; document ratio violations caused by conflicts.


Margin is outside the border box; padding is inside. Row/Column account for spacing, margins, weights, baselines and child order. Anchored Surfaces allow overlap and have no flow. Avoid cyclic sizing from anchored children: require finite constraints or an explicit intrinsic policy.


## 5. Layout policies


Required: overlay/anchor layout, Row, Column, Stack, wrapping flow, basic Grid with fixed/weighted/content tracks, Scroll and VirtualList. Do not implement every container as one flag-heavy class.


Custom LayoutPolicy receives public child measurables/placeables and constraints, not internal nodes. Parent data carries weight/grid-cell/placement information; DSL scopes restrict it to suitable parents.


Diagnose measurement cycles, duplicate arrangement and placing a child twice, with hierarchy paths. Depth/pass limits prevent infinite recursion in custom policies.


## 6. Responsiveness


Provide:


- viewport changes without recreating Ui;
- breakpoints based on a selected Surface, not only the window;
- min/max width/height, aspect-ratio and input-mode rules;
- percentages, weights and min/max constraints;
- scalable text and safe insets;
- Row-to-Column reflow preserving state/identity;
- an optional reference-resolution FIT/FILL/STRETCH policy with explicit letterboxing and inverse pointer transform.


Reference resolution is off by default. When enabled, it adds a consistent transform; it neither replaces dp nor distorts input.


Resolve rules deterministically: default → ordered matching rules → instance override. Rules use the containing Surface's previous committed size; changing a rule schedules a bounded layout pass. Do not allow a size-dependent rule to oscillate indefinitely because it changes its own predicate. Diagnose boundary oscillation and use a stable fallback; prefer container-width rules to self-size rules.


## 7. Transforms and hit testing


Paint transforms do not change neighboring measured sizes. Layout transforms affect measurement only in explicitly supported modes. Apply translation/scale/rotation consistently to render, clip and hit testing.


Map root pointer coordinates through the inverse Surface matrix. Singular matrices, such as scale zero, exclude the subtree from positional input and produce debug diagnostics. Rectangular bounds do not replace HitShape.


For rotation, mapRect returns the enclosing AABB; precise hits use the local shape. Scroll offset transforms descendants without moving the container viewport.


## 8. Clipping, overlays and portals


Clip policy is explicit: NONE/BOUNDS/CONTENT/SHAPE. Paint/input share geometry. Intersect nested rectangular clips; an empty intersection excludes drawing and hits. Rotated/rounded clips need mask/stencil/path support or a documented exact fallback.


Root layer order: CONTENT → OVERLAY → MODAL → TOOLTIP → DEBUG. Within a layer, use stacking context, zIndex and stable insertion order. A child's huge zIndex cannot escape its parent's context; use a portal.


Popup placement chooses a preferred anchor edge/alignment, then flip/shift/clamp within the viewport. Moving/removing the anchor invalidates the popup. Constrain menu size to the viewport and scroll content.


Portals attach geometry/paint to an overlay while preserving logical ownership, styles and lifetime. Original ancestor clips do not follow the portal; target-overlay clips apply. Events follow the logical route with correct root-to-local transforms for each receiver. Logical semantics determine modal scope, focus and disabled state, not the incidental render parent.


## 9. Verification


Table-test all nine anchors/pivots, nesting of 3+ levels, the (84,48) example, padding/safe area, resize 320×240→3840×2160, scale 1/1.25/2, textScale, zero viewport, unbounded constraints, transformed clipping, negative offsets, RTL alignment, portal routing and breakpoint state preservation. No effect-free Surface allocates a framebuffer.