# Pixzel independent CPU benchmark

Measured at 2026-10-24T00:51:11.173902300+10:00[Asia/Vladivostok].

- OS: Windows 11 10.0 amd64
- JVM: Java HotSpot(TM) 64-Bit Server VM 21.0.10
- CPU: AMD Ryzen 7 7800X3D 8-Core Processor; 16 logical processors
- CPU power plan (read-only OS query): Power Scheme GUID: 8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c (High performance); actual clocks/thermal throttling were not sampled
- Garbage collectors: G1 Young Generation, G1 Concurrent GC, G1 Old Generation; collection pauses are included in the observed frame timings
- JVM flags: none
- Heap maximum: 8489271296 bytes
- Framebuffer/logical viewport: 1920x1080, DPI1, uiScale1, textScale1; deterministic delta16ms
- Warmup: 300 frames per sampled steady scene; lifecycle 50 cycles
- Measured samples: Static HUD: 1,000 visible / 10,000 retained=500, Live data: 100 writes/frame / 10,000 retained=500, Text menu: 300 labels / 50 active reveals=500, VirtualList: 100,000 items / about 30 visible rows=500, Lifecycle: mount/frame/resize/close=1000
- Allocation source: ThreadMXBean current-thread allocated bytes
- Submission: bounded CPU command counter, no rasterization, GPU, driver, vsync or presentation; operations are draw commands, not GPU draw calls

| Scene | Samples | CPU p50 ms | p95 ms | p99 ms | Mean allocated B/sample | Allocation p95 B | Mean measured nodes | Shape calls | Retained nodes | Materialized rows | Commands / draw operations |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Static HUD: 1,000 visible / 10,000 retained | 500 | 0.122 | 0.200 | 0.237 | 368.528 | 368.000 | 0.000 | 0 | 10000 | n/a | 4003 / 1000 |
| Live data: 100 writes/frame / 10,000 retained | 500 | 1.552 | 2.243 | 2.372 | 1539036.832 | 1539040.000 | 101.000 | 50000 | 10000 | n/a | 4303 / 1000 |
| Text menu: 300 labels / 50 active reveals | 500 | 0.107 | 0.196 | 0.298 | 78881.616 | 82136.000 | 0.000 | 0 | 301 | n/a | 2103 / 300 |
| VirtualList: 100,000 items / about 30 visible rows | 500 | 0.426 | 0.625 | 0.837 | 753531.888 | 758840.000 | 109.000 | 17500 | 72 | 35 | 357 / 35 |
| Lifecycle: mount/frame/resize/close | 1000 | 0.212 | 0.339 | 0.425 | 247487.184 | 249504.000 | 0.000 | 10000 | 0 | n/a | 44 / 7 |

- Static HUD: 1,000 visible / 10,000 retained: 10,000 nodes; steady-state measuredNodes=0; no model writes.
- Live data: 100 writes/frame / 10,000 retained: 100 structurally different counter writes in one transaction each frame.
- Text menu: 300 labels / 50 active reveals: 300 retained labels; 50 ALWAYS reveal policies; shape count reported for measured interval.
- VirtualList: 100,000 items / about 30 visible rows: 30-row viewport, 2 overscan rows per side, 97-item deterministic jumps; materialized<=36.
- Lifecycle: mount/frame/resize/close: 1000 complete create/build/frame/close cycles; alternating1080p/720p; listeners=0 and sessions=0 after every cycle.

Reference engineering budgets (not universal guarantees): static p95<=2ms met; live p95<=4ms met for this CPU-only run.

Native/GPU memory high-water marks, GPU submit/present times, actual draw calls and visual acceptance were not measured. Setup/destruction are excluded from steady scenes and included in lifecycle. CPU thread allocation includes the library, deterministic fixture and benchmark callbacks; JVM/GC scheduling affects tail latency.
