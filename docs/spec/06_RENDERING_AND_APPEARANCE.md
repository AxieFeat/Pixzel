# Rendering and arbitrary appearance


## 1. Independent drawing pipeline


pixzel-common produces an ordered drawing description in Pixzel terms. UiBackend executes it through Arc or another technology. Element and ButtonSkin never call GL/Vulkan/Arc directly.


Minimum public canvas/draw contract:


- save/restore transforms and clips;
- fill/stroke shapes/paths, rounded rectangles, lines/polylines;
- images, atlas regions and nine-slice;
- text layouts/glyph runs through a compatible text renderer;
- linear/radial gradient brushes;
- opacity/compositing groups;
- custom render operations with explicit capabilities;
- scoped render passes over specified targets.


Streams/packed buffers may replace one object per command; the contract does not require an allocation-heavy collection. CanvasScope balances stacks through lexical scopes/try-finally. Resource handles are opaque to core and carry ownership/generation.


## 2. Appearance through composition


Painter draws a layer; ComponentSkin<T> defines state-dependent appearance and content slots. Skins do not replace Button, selection or focus semantics.


Elements expose background/content/foreground slots and decorations. Consumers can remove every default visual layer; replace shape, hit shape, padding or text renderer; draw images/icons/borders/glow/gradients/decorative edges; compose children; use atlases/nine-slice without stretching corners; and add custom materials/backend operations through extension contracts.


Arbitrary appearance must not be a closed list of background presets. A custom GPU shader is not automatically portable: it declares capabilities and fallback.


## 3. Required 1.0 visuals


| Primitive | Requirement |
| --- | --- |
| Rect/rounded rect/circle/ellipse | Fill, stroke, antialiasing, independent corner radii |
| Path | Move/line/quadratic/cubic/close, fill rule, stroke joins/caps |
| Brushes | Solid, linear/radial gradient, image brush |
| Image | Source rect, tint, opacity, crop/contain/cover/stretch, sampling |
| Nine-slice | Independent source borders/target insets; defined undersized-target behavior |
| Text | Baseline, clip, color, font fallback, scale |
| Border/outline | Width, position relative to boundary, focus ring |
| Shadow | Configurable blur/spread/offset and quality/cost |
| Transform | Translation/scale/rotation and origin |
| Clip | Nested rectangles and exact rounded/path masks |
| Group opacity | Alpha applied to the composed subtree, not independently to children |


Paths may use cached CPU flattening/tessellation with controlled accuracy. Independent contract tests cover holes/self-intersections. Generic triangle rendering provides a fallback when native paths are unavailable. Preserve winding/fill rules.


Backdrop blur, HDR tone mapping, arbitrary blend modes and complex SVG filters are optional effects/capabilities. A full SVG/CSS/browser engine is outside 1.0.


## 4. Themes and style resolution


Theme holds immutable reusable palette/typography/spacing/shape/duration tokens and default component skins. Shared themes never hold runtime state or clocks.


States include hover, pressed, focused, focus-visible, disabled, selected, checked, indeterminate, invalid and dragging; they can overlap.


Resolution order:


1. Library defaults.
2. Theme defaults for the component type.
3. Inherited scope tokens/typography.
4. Explicit state rules in declaration order.
5. Per-instance configuration/binding.


Instance state-specific overrides are final-level rules. Avoid implicit CSS specificity arithmetic. Layout, typography and paint properties trigger their respective invalidations.


Custom skins receive state/resolved tokens. Typed StyleKey<T> extensions are allowed, but standard properties must not become a string-keyed Map<String, Any> with runtime casts.


Theme changes update dependent nodes only. Color animation does not measure; focus highlighting does not move neighbors.


## 5. Painter order and batching


Drawing follows Surface stacking order. Never reorder transparent elements merely to group textures. Batch only adjacent compatible commands without changing order.


Batch keys account for material/pipeline, texture/sampler, clip/mask, target, blend and transform strategy. Different clips require a flush or supported per-command clipping, not unrestricted combined drawing.


OpenGL 4.1 can use streamed vertices, uniforms and atlas batching. SSBO/compute/bindless are not prerequisites. Indexed primitives/instancing are optional optimizations with correct fallbacks.


Clip after Surface transforms. Convert axis-aligned scissors to framebuffer coordinates using the correct Y origin and conservative rounding. A rotated shape's AABB is not exact clipping.


## 6. Render passes and application state


Draw inside an existing application frame, usually after 3D rendering and before presentation. Never clear another owner's framebuffer/depth/stencil without an explicitly assigned area/target.


Restore every affected target, viewport/scissor, depth/cull/write mask, blend state, shader/pipeline, texture/sampler and changed binding after the UI pass, including custom-painter exceptions. A documented state-cache/ownership strategy is allowed; do not call glGet for every draw.


Arc integration uses Arc render-pass/state contracts, not RenderContext-to-GlRenderContext downcasts.


Render-to-texture serves effects/caching/world-space UI. Ordinary Surfaces draw to the same target. Group opacity for overlapping children requires composition; multiplying every child's alpha is not equivalent.


## 7. Capabilities and fallbacks


BackendCapabilities reflects actual instance support: rectangular clips, shape masks, paths, text mode, targets, group opacity, shadows, materials, target formats, maximum texture size, etc.


For each operation choose an exact native implementation, an exact generic implementation such as CPU tessellation/offscreen masks, explicitly permitted approximation, or a diagnostic failure.


Never silently ignore shape clipping/group opacity. Production Arc/OpenGL implements all basic 1.0 visuals, including exact clips through new Arc APIs or generic masks. Test preview may declare a smaller set and tests shared supported scenarios.


## 8. Images and assets


Image handles separate logical/intrinsic size from GPU textures. Support loading/ready/failed, placeholders and explicit retry. Async decoding does not mutate UI directly. Fixed placeholder dimensions can prevent layout jumps.


Distinguish UI-owned and externally borrowed textures. Closing Ui does not delete borrowed Arc textures. Atlas eviction cannot leave stale UVs; include resource versions/generations in caches. Padding/extrusion prevents linear-filter bleeding.


Use `0xRRGGBBAA.rgba` consistently; rgb adds alpha 255. Define the color model and linear/sRGB conversion policy. Apply premultiplied alpha consistently at render boundaries; convert straight-alpha input exactly once. Text and gradients follow the same contract.


## 9. Visual acceptance


Show at least three substantially different skins for one Button: flat, game-style nine-slice and a custom shape with arbitrary content/hit shape. Input/focus semantics remain identical.


Golden/visual checks cover transparent overlap, group alpha, nested rounded clipping, path holes, thin borders at scale 1.25, alpha gradients, undersized nine-slice, Cyrillic text, sprite tint, UI over 3D and state restoration.