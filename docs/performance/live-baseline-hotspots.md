# Live baseline Java Flight Recorder hotspots

JDK21 profile recording, entire standalone process including setup, 300 warmup frames and 500 measured frames. Allocation weights are statistical estimates, not exact byte counts; CPU samples are sampled stacks, not instrumented durations. Exact per-frame allocations are in live-baseline-profile.md.

## Allocation sample sites

| Nearest Pixzel frame | Weighted MiB | Share |
| --- | ---: | ---: |
| pixzel/surface/Transform.<init>:29 | 2979.04 | 18.06% |
| pixzel/SimpleUi.nodes:76 | 2558.56 | 15.51% |
| pixzel/SimpleUi.rebuildGeometryTree:87 | 1755.85 | 10.64% |
| pixzel/SimpleUi.nodes:340 | 1755.77 | 10.64% |
| <no Pixzel frame> | 1097.91 | 6.65% |
| pixzel/SimpleUi.rebuildGeometryTree:83 | 1008.66 | 6.11% |
| pixzel/SimpleUi.nodes:337 | 758.37 | 4.60% |
| pixzel/surface/Insets.inset:9 | 748.95 | 4.54% |
| pixzel/surface/Transform.times:31 | 675.47 | 4.09% |
| pixzel/SimpleUi.rebuildGeometryTree:350 | 502.97 | 3.05% |
| pixzel/surface/Transform$Companion.translation:44 | 427.00 | 2.59% |
| pixzel/layout/LayoutsKt.overlayLayout$lambda$0:53 | 372.09 | 2.26% |
| pixzel/layout/LayoutsKt.overlayLayout$lambda$0:267 | 345.72 | 2.10% |
| pixzel/component/AbstractElement$measure$result$1.place:176 | 316.03 | 1.92% |
| pixzel/SimpleUi.rebuildGeometryTree:81 | 252.41 | 1.53% |

## Allocated classes

| Class | Weighted MiB | Share |
| --- | ---: | ---: |
| [Ljava/lang/Object; | 4532.66 | 27.47% |
| java/lang/Float | 1659.68 | 10.06% |
| java/util/LinkedHashMap$Entry | 1392.16 | 8.44% |
| java/util/ArrayList | 1388.82 | 8.42% |
| [Ljava/lang/Float; | 1356.35 | 8.22% |
| [Ljava/util/HashMap$Node; | 1203.11 | 7.29% |
| pixzel/surface/Rect | 1130.04 | 6.85% |
| pixzel/surface/Transform | 1102.46 | 6.68% |
| java/util/LinkedHashMap | 987.42 | 5.98% |
| pixzel/surface/Vec2 | 328.47 | 1.99% |

## Execution sample sites

| Nearest Pixzel frame | Samples | Share |
| --- | ---: | ---: |
| pixzel/SimpleUi.nodes:76 | 43 | 10.70% |
| pixzel/layout/LayoutsKt.overlayLayout$lambda$0:269 | 32 | 7.96% |
| pixzel/component/AbstractElement.add:228 | 31 | 7.71% |
| pixzel/layout/LayoutsKt.overlayLayout$lambda$0:53 | 29 | 7.21% |
| pixzel/SimpleUi$sorted$$inlined$compareBy$1.compare:101 | 26 | 6.47% |
| pixzel/surface/Transform.<init>:29 | 25 | 6.22% |
| pixzel/surface/Transform.<init>:28 | 19 | 4.73% |
| pixzel/SimpleUi.rebuildGeometryTree:350 | 18 | 4.48% |
| pixzel/layout/LayoutsKt.overlayLayout$lambda$0:59 | 15 | 3.73% |
| pixzel/SimpleUi.rebuildGeometryTree:87 | 15 | 3.73% |
| pixzel/component/AbstractElement.measure:238 | 14 | 3.48% |
| pixzel/SimpleUi.visibleAttached:343 | 13 | 3.23% |
| pixzel/layout/LayoutsKt.overlayLayout$lambda$0:60 | 11 | 2.74% |
| pixzel/component/AbstractElement$measure$result$1.place:181 | 10 | 2.49% |
| pixzel/SimpleUi.nodes:340 | 10 | 2.49% |
