# Pixzel user guide

Pixzel is a retained Kotlin/JVM UI library. Build its tree once, keep component handles, and change model state or properties when something happens. The application owns the window, graphics context and frame loop. Pixzel neither polls native events nor starts, clears or presents the application's frame.

The current build uses JDK 21, Kotlin 2.4.10 and Gradle 9.6.1. See [capabilities and limits](CAPABILITIES.md) before choosing a renderer or text provider.

## Build and modules

Run independent checks without Arc:

```powershell
.\gradlew.bat build verifyBoundaries :pixzel-common:stateChecks :pixzel-common:animationChecks --offline
.\gradlew.bat :pixzel-demo:run --offline
```

`--offline` assumes dependencies are already cached; omit it for the initial dependency resolution. The independent demo uses an explicitly limited software preview and deterministic test font. Its PNG is a test artifact, not evidence of production typography.

For joint development, pass the separate Arc checkout explicitly:

```powershell
.\gradlew.bat -ParcPath=E:/Projects/arc-engine :pixzel-arc:test :pixzel-demo:graphicsChecks :pixzel-demo:inputChecks --offline
.\gradlew.bat -ParcPath=E:/Projects/arc-engine :pixzel-demo:run --args="--smoke" --offline
```

In this checkout, an application depends on `project(":pixzel-common")`; add `pixzel-arc` and selected Arc implementations for the native application. `pixzel-core` contains contracts and value types; `pixzel-common` implements them. `pixzel-test` is a separate support artifact. `pixzel-samples` demonstrates external extensions using public APIs. No published Maven release is assumed by these instructions.

## A first frame

The independent snippets use this import prelude. The `directCounter`, `dslPanel` and `loadImage` functions below are also compiled and exercised in [GuideExamplesTest](../pixzel-common/src/test/kotlin/pixzel/dsl/GuideExamplesTest.kt).

```kotlin
import pixzel.component.Button
import pixzel.component.ContextMenu
import pixzel.component.Element
import pixzel.component.Image
import pixzel.component.MenuItem
import pixzel.component.Text
import pixzel.component.TextField
import pixzel.dsl.content
import pixzel.graphics.RecordingBackend
import pixzel.layout.FixedTrack
import pixzel.layout.unit.dp
import pixzel.layout.unit.fillAvailable
import pixzel.layout.WeightedTrack
import pixzel.platform.TestHost
import pixzel.resource.bindAsset
import pixzel.resource.imageAsset
import pixzel.resource.ImageAssetSizing
import pixzel.resource.ImageDecoder
import pixzel.resource.ImageUploader
import pixzel.state.bind
import pixzel.state.stateOf
import pixzel.state.stateRuntime
import pixzel.style.ButtonTextDefaults
import pixzel.style.StyleScope
import pixzel.style.StyleTokens
import pixzel.surface.Size
import pixzel.text.FixedFontTextService
import pixzel.text.FontFace
import pixzel.text.FontFamily
import pixzel.text.hoverReveal
import pixzel.text.RevealTrigger
import pixzel.text.TextOverflow
import pixzel.text.TextReveal
import pixzel.text.TextStyle
import pixzel.ui
import pixzel.Ui
import java.util.concurrent.Executor
import kotlin.time.Duration.Companion.milliseconds
```

```kotlin
fun firstFrame() {
    val host = TestHost.of(400, 260)
    val backend = RecordingBackend.of()
    val ui = ui(host, backend, FixedFontTextService.of())
    try {
        dslPanel(ui)
        ui.frame(16.milliseconds)
    } finally {
        ui.close()
        host.close()
    }
}
```

`ui.frame(delta)` takes a finite, nonnegative elapsed duration. It applies posted work, resolves layout, drains ordered host events, advances visible attached elements, prepares commands and submits the owned render session. Call it inside the application frame, after the application has obtained its events and before its presentation. `TestHost` queues events; it does not open a native window.

For real Arc text, initialize Arc's selected graphics/window/input implementations in the application, create `arc.font.NativeFontProvider.of()`, and keep it alive until Ui closes. This construction function accepts the application's already-current context and font bytes:

```kotlin
import arc.font.FontProvider
import arc.graphics.RenderContext
import arc.window.Window
import pixzel.arc.graphics.ArcUiBackend
import pixzel.arc.platform.ArcUiHost
import pixzel.arc.text.ArcFontRegistration
import pixzel.arc.text.ArcTextService
import pixzel.text.CachingTextService
import pixzel.ui
import pixzel.Ui

fun nativeUi(
    window: Window,
    context: RenderContext,
    fonts: FontProvider,
    regular: ByteArray,
    bold: ByteArray,
    arabic: ByteArray
): Ui {
    val registrations = listOf(
        ArcFontRegistration(fonts.load("regular", regular), "ui"),
        ArcFontRegistration(fonts.load("bold", bold), "ui", weight = 700),
        ArcFontRegistration(fonts.load("arabic", arabic))
    )
    val text = ArcTextService.from(registrations)
    return ui(
        ArcUiHost.of(window),
        ArcUiBackend.of(context, text),
        CachingTextService.of(text)
    )
}
```

Select fallback explicitly when displaying Arabic: `TextStyle(family = FontFamily(listOf(FontFace("regular"), FontFace("arabic"))))`. Font families select actual registered weight/slant variants; missing italic is not synthesized. Initial native face versions default to 0. When replacing data under an existing ID in a replacement provider, supply a new version and update corresponding FontFace references. New text services have unique default provider identities. The full application bootstrap and loop are in [ArcDemo](../pixzel-demo/src/arc/kotlin/pixzel/demo/ArcDemo.kt).

## Direct construction and state

```kotlin
fun directCounter(ui: Ui): Button {
    val runtime = stateRuntime()
    val count = stateOf(0, runtime)
    val button = Button.of(ui.components, "counter")
    button.layout.width = 240.dp
    button.setLabel("").bindValue(bind(runtime) { "Players: ${count.value}" })
    button.resources.own(button.onClick { count.value++ })
    ui.root.add(button)
    return button
}
```

`ui.components` is a per-Ui factory. Creating a component does not attach it; `parent.add(child)` transfers tree lifetime to that parent. IDs are unique among named siblings. Remove with `parent.remove(child)` to dispose it, or `remove(child, dispose = false)` to retain a detached subtree for later attachment.

`stateOf` creates structurally compared state. `bind` tracks library states read by its expression and refreshes after dependencies change. Expressions must be pure. `transaction(runtime) { ... }` batches writes into one notification wave; exceptions do not roll writes back. Replace collection values with new snapshots rather than mutating an ordinary collection in place.

`Text.bindValue` owns its binding and observation. Direct assignment to `Text.value` removes that binding. An ordinary `text(model.value.toString())` captures a snapshot. Register subscriptions with their intended owner's `resources`, or close them explicitly. Repeated button handlers accumulate through `onClick`; `replaceOnClick` explicitly replaces the registered activation handlers.

## DSL and typed layout scopes

```kotlin
fun dslPanel(ui: Ui): TextField {
    val runtime = stateRuntime()
    val count = stateOf(0, runtime)
    lateinit var field: TextField
    ui.content {
        column("panel", spacing = 8.dp) {
            layout {
                width = 320.dp
                height = 220.dp
            }
            text {
                value(bind(runtime) { "Players: ${count.value}" })
                layout {
                    height = 28.dp
                }
            }
            button("add") {
                layout {
                    width = 240.dp
                    height = 44.dp
                }
                label("Add another player to the server") {
                    overflow = TextOverflow.ELLIPSIS
                    reveal = hoverReveal {
                        trigger = RevealTrigger.HOVER_OR_FOCUS
                        delay = 600.milliseconds
                    }
                }
                onClick { count.value++ }
            }
            field = textField(id = "name") {
                placeholder = "Player name"
                layout.width = 240.dp
            }
        }
    }
    return field
}
```

`ui.content` replaces and disposes the previous root content. Builders do not rerun every frame. `surface`, `row`, `column`, `button` and `text` return real retained handles. The corresponding receivers are `SurfaceScope`, `RowScope`, `ColumnScope`, `ButtonScope` and `TextScope` under `@PixzelDsl`.

Common `layout {}` exposes sizing, minima/maxima, margins, padding and aspect ratio. Parent data belongs to the enclosing layout scope:

```kotlin
fun weightedPanel(ui: Ui) {
    ui.content {
        row(spacing = 8.dp) {
            layout {
                width = 400.dp
                height = 40.dp
            }
            text("One share") {
                layout {
                    width = fillAvailable
                    height = 28.dp
                }
            }.weighted(1f)
            weight(
                text("Three shares") {
                    layout {
                        width = fillAvailable
                        height = 28.dp
                    }
                },
                3f
            )
        }
        grid(listOf(FixedTrack(100.dp), WeightedTrack()), spacing = 8.dp) {
            layout {
                width = 400.dp
                height = 80.dp
            }
            text("Name").inCell(row = 0, column = 0)
            text("Value").inCell(row = 0, column = 1)
        }
    }
}
```

Weight helpers exist in Row/Column scopes; cell helpers exist in Grid scopes. They reject elements owned by another parent. Direct APIs retain `element.layout.weight`, `gridRow` and `gridColumn` for custom layout policies. Row/Column `spacing` and cross-axis `alignment` are builder arguments. `layout.spacing` is metadata for policies that consume it, such as button slots.

Use `dp` for logical geometry, `sp` for text size, `percent` for a finite parent reference, and `fillAvailable`/`wrapContent` for sizing policies. A weighted or percentage layout without a finite reference is a configuration error. Minima can force overflow; choose clipping explicitly.

`Placement` combines an anchor on the reference box and a pivot on the child. For example, `placement = Placement.center()` centers a child in an overlay parent. Each Element owns one read-only computed Surface, with local/parent/root transforms; root-coordinate methods require attachment and committed geometry. `paintTransform` changes drawing and hit testing while retaining measured size.

## Text and button labels

All text indices are UTF-16 code-unit offsets at extended grapheme boundaries. Source clusters, caret/hit data and positioned glyph runs live in `TextLayout`; a renderer draws those exact runs. The native path uses real font metrics and shaping. Styled `Text.spans` are immutable snapshots whose ranges must cover complete graphemes; later spans replace earlier styles over overlapping ranges. `TextField` is a single-style, single-line editor and rejects nonempty styled spans explicitly.

Set `Text.wrap`, `maxLines`, `direction`, `locale`, `horizontalAlignment` and `verticalAlignment` separately. Static policies are `CLIP`, `VISIBLE`, `ELLIPSIS`, `ELLIPSIS_START`, `ELLIPSIS_MIDDLE` and `WRAP`. Truncation reshapes valid substrings through the same provider. The marker defaults to `…`, falling back to `...` if needed; `ellipsisMarker` can be a custom string or empty. The public lower-level `TextOverflowContext` also exposes clipped-marker versus empty behavior when the viewport is too narrow.

Standard button labels use one-line ellipsis and hover reveal: 600 ms delay, 32 dp/s, 700 ms endpoint pauses, ping-pong movement. Focus triggering is opt-in, as shown above. Reduced motion uses the static layout. Source/semantics remain complete; add a full-text tooltip or another visible detail view where needed.

Defaults can be supplied at theme, inherited component scope, or explicit label level:

```kotlin
fun configureLabels(ui: Ui, group: Element, label: Text) {
    ui.theme = ui.theme.copy(
        buttonText = ButtonTextDefaults(
            overflow = TextOverflow.ELLIPSIS_MIDDLE,
            reveal = hoverReveal { trigger = RevealTrigger.HOVER_OR_FOCUS }
        )
    )
    group.styleScope = StyleScope(
        StyleTokens(
            buttonText = ButtonTextDefaults(reveal = TextReveal.NONE)
        )
    )
    label.overflow = TextOverflow.ELLIPSIS_START // explicit instance setting wins
}
```

Null default fields inherit. `TextReveal.NONE` explicitly disables motion. A shared reveal strategy creates a separate runtime for every label. Policy-only changes reuse the full measured layout; a different truncation policy may still need provider work to shape its new displayed substring.

## Images and asynchronous assets

An Image borrows its `ImageHandle`. Pixel `size` is used for source cropping; logical `intrinsicSize` is used for preferred layout. Image fits include contain, cover, stretch and crop. `Canvas.nineSlice` emits ordinary image commands; `ImageBrush` adds explicit cropping and mapping.

```kotlin
fun loadImage(
    ui: Ui,
    executor: Executor,
    source: () -> ByteArray,
    decoder: ImageDecoder,
    uploader: ImageUploader
): Image {
    val asset = imageAsset(ui, source, decoder, uploader, executor, placeholderSize = Size(96f, 64f))
    val image = Image.of(ui.components, "portrait")
    ui.root.add(image)
    image.bindAsset(asset, ImageAssetSizing.PLACEHOLDER)
    return image
}
```

The application injects source retrieval, a decoder, a borrowed Executor, and a Ui-thread uploader. Common does not choose a filesystem, network stack or image codec. `DecodedImage` owns copied straight-alpha RGBA8 bytes and optional logical intrinsic metadata. Uploads run only after `Ui.post` delivery. `Loading`, `Ready`, `Failed` and `Closed` are observable through `asset.status`.

`retry()` supersedes old generations. Stale worker completions never upload; stale owned upload results are closed. The Ui resource scope owns the asset. Uploaded handles are owned by default; choose `Ownership.BORROWED` explicitly for externally managed handles. The executor, decoder, source and uploader remain borrowed. The decoded-result budget defaults to 64 MiB; decoders must also bound their own work and temporary allocations.

`PLACEHOLDER` retains the fixed placeholder dimensions; `INTRINSIC` adopts decoded logical dimensions when ready; `PRESERVE_LAYOUT` leaves existing sizing untouched. Observe failures to offer a retry action. Close an asset before using an externally owned handle elsewhere if its ownership policy requires it.

For Arc, the uploader is an ordinary adapter:

```kotlin
import arc.graphics.RenderContext
import pixzel.arc.graphics.ArcImageHandle
import pixzel.resource.ImageUploader

fun arcImageUploader(context: RenderContext): ImageUploader = ImageUploader { decoded ->
    ArcImageHandle.rgba(
        context,
        decoded.width,
        decoded.height,
        decoded.rgba,
        intrinsicSize = decoded.intrinsicSize
    )
}
```

## Input, focus and navigation

The host delivers ordered pointer, wheel, key and text events. Unicode input is separate from physical key codes. Input routing uses logical capture/target/bubble; default behavior follows unless prevented. `consume()` blocks gameplay delivery, `preventDefault()` blocks default behavior, and `stopPropagation()` stops further routing. Pointer capture is per pointer ID and is cancelled on focus loss, detachment and disposal.

`PointerPolicy.PASS_THROUGH` allows child hits while excluding the decoration itself; `NONE` excludes the subtree. Zero opacity remains interactive. Custom hit shapes and transformed clips participate in picking. Wheel deltas retain fractions and declare LINE, DP or PAGE units.

Use `ui.focus.request(element)`, Tab/Shift+Tab, `ui.focus.move(direction)`, or `ui.navigate(NavigationCommand.NEXT/ACTIVATE/...)`. Navigation commands let an application add gamepad input without inventing physical keyboard events. The Arc application must use the masked gameplay polling view after `ui.frame`; consuming an event does not change physical key state.

TextField supports selection, grapheme deletion, whitespace-delimited word movement, clipboard, bounded undo/redo, placeholder, validator, read-only and password display. Validators decide which intermediate strings to accept. Host clipboard/cursor methods return `Result`; inspect failure rather than assuming OS support. Composition/preedit and candidate-caret contracts are tested through TestHost; Arc's GLFW host advertises no native IME composition.

Menus, context menus and dialogs own focus scopes and restore prior valid focus. For a typed context menu:

```kotlin
fun installContextMenu(ui: Ui, owner: Element): ContextMenu<String> =
    ContextMenu.of(ui, owner, listOf(MenuItem("copy", "Copy item", "copy"))) { item ->
        println(item.value)
    }
```

Right-button invocation uses root logical pointer coordinates. `openFor()` provides explicit keyboard invocation; custom shortcuts can call it. The controller belongs to its owner and closes an open menu on detach. `Popup`, `Menu`, `dropdown`, `Tabs`, `Modal` and `NotificationHost` have direct APIs; DSL extensions compose the same implementations. `Button.setLeading`, `setContent`, and `setTrailing` replace owned slots without removing keyboard activation. Dialogs expose header/body/actions; structured notifications expose icon/title/body/actions/close slots.

`ui.semanticsTree()` returns roles, full labels/values, effective enabled state, focus, actions and root bounds. `performSemanticsAction` checks target lifetime and enabled state. This is an independent semantics tree, not an OS screen-reader bridge.

## Animation, lifetime and extension

`animateFloat`, `animateOpacity`, `animateColor` and `animateTransform` create explicitly advanced handles; `tween` and `keyframes` are reusable specs. Retargeting supports current value, original start or queue. Color interpolation uses premultiplied stored-sRGB channels; transform interpolation uses decomposed shortest rotation, scale/shear and translation. `AFFINE_TRANSFORM` explicitly interpolates matrix coefficients instead.

`element.opacityTransition()`, `transformTransition()` and `colorTransition(initial) { ... }` create reusable owner-scoped controllers advanced by the Ui clock. Call `transitionTo` on events rather than every frame. Detachment/hidden visibility pauses them; reduced motion selects the latest target; disposal cancels callbacks. Paint-property transitions avoid measurement unless the callback explicitly changes layout.

All tree, state, focus and animation mutations belong to their creating Ui thread. Worker code uses `ui.post { ... }`, which is FIFO at frame boundaries and rejects work after closure. `attachments.bind { openLease() }` opens a subscription/task lease while attached, closes it on detach, and reopens it on attachment. Use `resources.own` for lifetime through disposal instead. Ui suspension freezes its clock; the resume frame does not consume accumulated wall time.

Close Ui before destroying the graphics context. Ui closes its render session and owned tree/assets; injected host, backend, text services and font providers remain borrowed. Close those services afterward according to the application's ownership policy. Context replacement invalidates sessions; owned Arc RGBA images restore from retained bytes, while borrowed textures require explicit `rebind`.

External modules can implement Element directly or extend public `AbstractElement`; supply public `LayoutPolicy`, Painter, `ComponentSkin`, HitShape, overflow/reveal strategies, host/text/backend contracts, and DSL extensions using `ContentScope.insert`. No internal node or renderer switch is needed. [HealthBar](../pixzel-samples/src/main/kotlin/sample/HealthBar.kt) demonstrates a separate module with model observation, gradient drawing, animation, attachment cleanup and both direct/DSL construction. Custom native drawing uses an explicitly registered `ArcCustomRenderer`, capability checks and a session-owned resource scope; see [Arc graphics integration](ARC_GRAPHICS.md).

Enable `UiInspector.of(ui)` only when needed; it exposes selected geometry, constraints, invalidation, focus/capture and nullable renderer statistics. Default error handling throws; `UiSettings.errorPolicy` can select continue with bounded diagnostics. Unsupported capabilities and malformed draw streams fail explicitly. Performance measurements and their environment are recorded separately in [PERFORMANCE.md](PERFORMANCE.md) and [the benchmark protocol](BENCHMARK_PROTOCOL.md).
