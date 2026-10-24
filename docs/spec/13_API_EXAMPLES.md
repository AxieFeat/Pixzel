# Target API and usage examples


Every fragment below describes the **proposed Pixzel API**, absent from current Arc. New Arc adapter method names are also targets. During implementation, reconcile signatures, add imports/assets and turn all fragments into compiling samples.


Common entry point in pixzel-common:


```kotlin
fun createInterface(
    host: UiHost,
    backend: UiBackend,
    text: TextService
): Ui {
    return ui(
        host = host,
        backend = backend,
        text = text,
        settings = UiSettings.of(
            uiScale = 1f,
            textScale = 1f,
            isReducedMotion = false
        )
    )
}
```


ui is the common implementation's public facade. Core exposes Ui.Factory and `Ui.of(factory = ..., host = ..., backend = ..., text = ...)` for lower-level replacement. Neither path accesses global Arc.


## 1. Top-left notification with local centering


```kotlin
ui.content {
    surface(id = "notification") {
        layout {
            width = 320.dp
            height = 72.dp
            padding = Insets.all(12.dp)
        }
        placement {
            anchor = Anchor.TOP_LEFT
            pivot = Pivot.TOP_LEFT
            offset(24.dp, 24.dp)
        }
        appearance {
            background = solid(0x18212BFF.rgba)
            corners = 12.dp
        }


        text("Connection established") {
            layout {
                width = 200.dp
                height = 24.dp
            }
            placement {
                anchor = Anchor.CENTER
                pivot = Pivot.CENTER
            }
            typography {
                size = 16.sp
            }
            horizontalAlignment = TextAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
        }
    }
}
```


Root is the application's UI space. surface creates a local region. Text centers in the notification's content box, not the root. Under document 04's normative viewport, Text origin is (84,48). Production toasts constrain size to available viewport; fixed sizes here make the coordinate example deterministic.


## 2. Overflowing label and controlled reveal


```kotlin
button(id = "server") {
    layout {
        width = 260.dp
        height = 48.dp
        padding = Insets.horizontal(16.dp)
    }


    label("A very long and descriptive game server name") {
        overflow = TextOverflow.ELLIPSIS
        ellipsisMarker = "..."
        reveal = hoverReveal {
            trigger = RevealTrigger.HOVER_OR_FOCUS
            delay = 600.milliseconds
            speed = 32.dpPerSecond
            pauseAtEnd = 700.milliseconds
            pauseAtStart = 700.milliseconds
            mode = RevealMode.PING_PONG
            onDeactivate = RevealExit.RESET
            reducedMotion = RevealReducedMotion.STATIC
        }
    }


    tooltip("A very long and descriptive game server name")
    onClick { connectToServer() }
}
```


label is a Text-slot convenience. Derive available width from inner width after padding/other slots. Replace behavior without subclassing Button:


```kotlin
label("Custom label") {
    overflow = MyOverflowPolicy()
    reveal = MyRevealBehavior()
}
```


Disable movement entirely:


```kotlin
label("No scrolling") {
    overflow = TextOverflow.WRAP
    maxLines = 2
    reveal = TextReveal.NONE
}
```


MyOverflowPolicy implements public TextOverflowPolicy; MyRevealBehavior implements public TextRevealBehavior. Neither interface is sealed.


## 3. Arbitrary Button content


```kotlin
button(id = "launch") {
    layout {
        width = 280.dp
        height = 64.dp
    }
    appearance {
        skin = GameButtonSkin(
            normal = assets.buttonNormal,
            hovered = assets.buttonHover,
            pressed = assets.buttonPressed
        )
    }


    content {
        row {
            layout {
                width = fillAvailable
                height = fillAvailable
                spacing = 10.dp
                alignment = Alignment.CENTER
            }
            image(assets.playIcon) {
                layout {
                    width = 24.dp
                    height = 24.dp
                }
            }
            column {
                text("Play")
                text("EU • 18 ms") {
                    typography { size = 12.sp }
                }
            }
        }
    }


    onClick { launchGame() }
}
```


assets and application actions are application dependencies, not global Pixzel services. Skins may use nine-slice, paths or custom Painter. Button behavior/semantic activation remain intact. Custom content replaces its standard label slot.


## 4. Reactive state


```kotlin
val online = stateOf(12)
val isConnected = stateOf(false)


ui.content {
    column {
        layout {
            spacing = 12.dp
        }


        text {
            value(bind { "Players online: "+online.value })
        }


        button {
            label {
                value(bind {
                    if (isConnected.value) "Disconnect" else "Connect"
                })
            }
            onClick {
                transaction {
                    isConnected.value = !isConnected.value
                    online.value += if (isConnected.value) 1 else -1
                }
            }
        }
    }
}
```


value(String) and value(Binding<String>) are builder overloads. Kotlin cannot implicitly convert String to PropertyValue; one property cannot accept unrelated types through imagined implicit conversion. Direct Text.value remains String; bindValue installs bindings explicitly.


`text(online.value.toString())` is a snapshot. bind tracks dependencies. transaction batches notifications/invalidation. The entire content lambda is not rebuilt.


## 5. Layout and container breakpoints


```kotlin
surface(id = "settings") {
    layout {
        width = fillAvailable
        height = fillAvailable
        padding = Insets.all(24.dp)
    }


    adaptiveLayout {
        default {
            policy = rowLayout(spacing = 20.dp)
        }
        whenWidthAtMost(720.dp) {
            policy = columnLayout(spacing = 12.dp)
        }
    }


    surface(id = "navigation") {
        layout {
            width = 220.dp
            minWidth = 160.dp
            height = wrapContent
        }
        text("Settings")
    }


    surface(id = "content") {
        layout {
            width = fillAvailable
            height = wrapContent
        }
        text("Application settings")
    }
}
```


Breakpoints change the same container's policy while preserving children. Use the containing Surface size under document 04. In Row, measure fixed navigation first; content's fillAvailable takes the remainder. In Column, width fillAvailable fills the cross axis. Multiple main-axis fill children share remaining space with default weight 1 or explicit weights. Constraints bound every value.


## 6. Direct API without DSL


```kotlin
val notification = SurfaceElement.of(components = ui.components)
notification.layout.width = 320.dp
notification.layout.height = 72.dp
notification.layout.padding = Insets.all(12.dp)
notification.placement = Placement.of(
    anchor = Anchor.TOP_LEFT,
    pivot = Pivot.TOP_LEFT,
    offset = Offset.of(24.dp, 24.dp)
)


val title = Text.of(components = ui.components)
title.value = "Saved"
title.placement = Placement.center()
notification.add(title)
ui.root.add(notification)


val button = Button.of(components = ui.components)
val buttonLabel = button.setLabel("Continue")
buttonLabel.overflow = TextOverflow.ELLIPSIS
buttonLabel.reveal = hoverReveal()
button.onClick { continueGame() }
notification.add(button)
```


Surface is the geometry contract; SurfaceElement is the owning container component. Following Arc style, its interface and internal SimpleSurfaceElement share pixzel.surface across modules. Ui-local components is an explicit factory provider; no Arc singleton is needed.


The Button shares this anchored container and may overlap other children without placement. Production layouts specify anchors/flow. This demonstrates public object APIs, not a finished arrangement.


## 7. External painter and hit shape


```kotlin
button {
    layout {
        width = 120.dp
        height = 120.dp
    }
    appearance {
        skin = RadialButtonSkin(
            painter = MyRadialPainter(),
            focusPainter = MyFocusRingPainter()
        )
    }
    hitShape = CircleHitShape
    content {
        image(assets.actionIcon) {
            layout {
                width = 40.dp
                height = 40.dp
            }
            placement {
                anchor = Anchor.CENTER
                pivot = Pivot.CENTER
            }
        }
    }
    onClick { useAbility() }
}
```


Implement RadialButtonSkin, MyRadialPainter and MyFocusRingPainter in an external sample using public interfaces. Renderer must not know their concrete types. Hit results differ inside/outside the circle despite its shared bounding box.


State-driven custom drawing needs owned observation/invalidatePaint APIs. Reading State in an arbitrary Painter is not automatically reactive unless paint scopes track dependencies. Choose/document one mechanism.


## 8. Shared Arc/non-Arc builder


```kotlin
fun buildMenu(ui: Ui) {
    ui.content {
        surface {
            layout {
                width = fillAvailable
                height = fillAvailable
            }
            button {
                placement {
                    anchor = Anchor.CENTER
                    pivot = Pivot.CENTER
                }
                label("Start")
            }
        }
    }
}
```


Integration chooses host/backend/text without changing the tree:


```kotlin
val testHost = TestHost.of(width = 1280, height = 720)
val recording = RecordingBackend.of()
val testUi = createInterface(
    host = testHost,
    backend = recording,
    text = FixedFontTextService.of()
)
buildMenu(testUi)
testUi.frame(16.milliseconds)
```


FixedFontTextService is a deterministic test provider, not a production font. Real non-Arc bitmap preview uses a compatible raster glyph provider and the same public draw contract.


## 9. Arc game-loop integration


After Arc bootstrap and application.init():


```kotlin
val ui = createInterface(
    host = ArcUiHost.of(
        window = application.window,
        input = inputEngine
    ),
    backend = ArcUiBackend.of(application.renderContext),
    text = arcTextService
)
buildMenu(ui)


try {
    while (!application.window.shouldClose()) {
        application.renderContext.beginFrame()
        try {
            renderWorld()
            ui.frame(frameDelta())
        } finally {
            application.renderContext.endFrame()
        }
    }
} finally {
    ui.close()
}
```


frameDelta returns Duration. arcTextService is created after the new Arc font pipeline and contains real font resources. Final demos include complete construction and assets. Ui.frame also provides target/FrameInfo overloads for offscreen use; window target defaults to the current host/backend.


This is target code after ARC-02…07. Current Arc lacks ordered input and scoped UI passes. Ui.frame does not repeat pollEvents/clear/swapBuffers. Close Ui before application/context.


Gameplay bindings use context-aware/masked input. Direct physical polling is not automatically consumed by modal UI.


## 10. Contracts to establish in code


| API | Contract |
| --- | --- |
| `ui(host, backend, text, settings, ...)` | Common facade, explicit services, local factories |
| `Ui.of(factory, host, backend, text, ...)` | Core entry for replaceable implementations |
| `X.of(components, ...)` | Direct Arc-independent component factory |
| `layout.width: SizeSpec` | Typed Dp/fill/wrap/percentage variants |
| Dp | Implements fixed-size variant/interface, no implicit numeric conversion |
| `TextScope.value(String/Binding<String>)` | Overloads without Any or ambiguous setters |
| `Text.value: String` / bindValue | Direct mutation or explicit binding |
| Surface | Read-only computed geometry |
| Element.layout/placement | Controlled configuration mutation with invalidation |
| `reveal: TextRevealBehavior` | Externally replaceable strategy |
| `overflow: TextOverflowPolicy` | Externally replaceable strategy |
| Duration | One time type for delays/frame delta |


Initial ADRs may refine names. Update all samples afterward rather than leaving incompatible API variants.