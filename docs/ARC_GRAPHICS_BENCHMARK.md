# Arc native clip/effect submission benchmark

GPU: NVIDIA GeForce RTX 4060 Ti/PCIe/SSE2; OpenGL: 4.1.0 NVIDIA 617.42; vendor: NVIDIA Corporation.
JVM: Java HotSpot(TM) 64-Bit Server VM 21.0.10; OS: Windows 11 amd64; processors: 16.
Hidden GLFW OpenGL 4.1 core context; vsync 0; offscreen application target/logical/framebuffer 1920 x 1080; scale 1; supersampling 2; RGBA8/depth24stencil8; configured pool 512 MiB/mesh 32 MiB.

Scene: five nested rounded stencil clips, isolated opacity group 0.72, two-pass blurred/spread shadow, 1000 transformed multicolor rectangles. Warmup 100 frames; measured 300 retained frames. CPU timing surrounds RenderSession.submit only, excluding application clear, event polling, swap/present, report construction and final glFinish. Driver backpressure during submit is included. No timer-query GPU duration is claimed.

| CPU submit | p50 | p95 | p99 |
|---|---:|---:|---:|
| milliseconds | 0.5171 | 0.8475 | 1.072 |
| JVM bytes allocated | 236024 | 237856 | 237856 |

Last frame: ArcRenderStatistics(drawCalls=17, uploadedBytes=0, ownedTargets=4, geometryEntries=9, textBytes=0, ownedTargetBytes=199065600, meshBytes=236100, cpuGeometryBytes=36840). Independent metrics: RenderMetrics(drawCalls=17, batches=17, vertices=10647, uploadedBytes=0, textureSwitches=4, transientTargets=4, ownedResources=16).
Tracked high-water target bytes 199065600; mesh bytes 236100; text bytes 0; total GPU storage estimate 199301700; CPU geometry-array bytes 36840; owned logical resources 16.
Storage estimates count requested attachment/VBO/texture bytes and exclude driver padding, shaders, borrowed application scene/resources and native font memory. Native allocation counts and driver/device total GPU memory are unavailable. Static upload bytes were 0 throughout all 300 measured frames. This is a renderer submission benchmark; layout/recomposition/input performance belongs to the independent UI benchmarks.
