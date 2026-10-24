# Pixzel independent CPU benchmark

Measured at 2026-10-23T19:39:57.524814500+10:00[Asia/Vladivostok].

- OS: Windows 11 10.0 amd64
- JVM: Java HotSpot(TM) 64-Bit Server VM 21.0.10
- CPU: AMD Ryzen 7 7800X3D 8-Core Processor; 16 logical processors
- CPU power plan (read-only OS query): Power Scheme GUID: 8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c (High performance); actual clocks/thermal throttling were not sampled
- Garbage collectors: G1 Young Generation, G1 Concurrent GC, G1 Old Generation; collection pauses are included in the observed frame timings
- JVM flags: -XX:StartFlightRecording=filename=pixzel-demo/build/benchmarks/live-baseline.jfr,settings=profile,dumponexit=true
- Heap maximum: 8489271296 bytes
- Framebuffer/logical viewport: 1920x1080, DPI1, uiScale1, textScale1; deterministic delta16ms
- Warmup: 300 frames per steady scene, min(300,50) lifecycle cycles; samples: 500 steady frames, 1 lifecycle cycles
- Allocation source: ThreadMXBean current-thread allocated bytes
- Submission: bounded CPU command counter, no rasterization, GPU, driver, vsync or presentation; operations are draw commands, not GPU draw calls

| Scene | Samples | CPU p50 ms | p95 ms | p99 ms | Mean allocated B/sample | Allocation p95 B | Mean measured nodes | Shape calls | Retained nodes | Materialized rows | Commands / draw operations |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Live data: 100 writes/frame / 10,000 retained | 500 | 9.419 | 11.046 | 13.174 | 21241891.616 | 21241904.000 | 101.000 | 50000 | 10000 | n/a | 4303 / 1000 |

- Live data: 100 writes/frame / 10,000 retained: 100 structurally different counter writes in one transaction each frame.

Reference engineering budgets (not universal guarantees): static p95<=2ms not sampled; live p95<=4ms exceeded for this CPU-only run.

Native/GPU memory high-water marks, GPU submit/present times, actual draw calls and visual acceptance were not measured. Setup/destruction are excluded from steady scenes and included in lifecycle. CPU thread allocation includes the library, deterministic fixture and benchmark callbacks; JVM/GC scheduling affects tail latency.
