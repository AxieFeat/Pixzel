# Benchmark protocol

Record OS/JDK/CPU/GPU/driver, viewport/window/framebuffer sizes, DPI, flags, warmup, measurement duration and vsync. Report p50/p95/p99 update/layout/paint preparation separately from GPU submission/present, allocations/frame, draw calls, active upload bytes and native/GPU high-water marks.

Required scenes: 1,000 visible/10,000 total static HUD nodes; 300 labels with50 reveal animations;100 updates/frame among10,000 nodes;100,000-item VirtualList with about30 visible;five nested clips/effects;1,000 mount/close/resize cycles. Budgets2ms static/4ms live p95 are engineering targets, never invented measurements.

An early Arc scoped-guard microbenchmark (100 samples, OpenGL 4.1, NVIDIA 617.42, RTX 4060 Ti) measured full texture-unit snapshots at p50=1.9869 ms, p95=2.4646 ms, p99=2.9451 ms; declared texture unit 0 at p50=0.0951 ms, p95=0.1865 ms, p99=0.2493 ms. This historical guard-only result excludes context creation/present and is distinct from the completed scene measurements. Final integrated CPU evidence is in [PERFORMANCE.md](PERFORMANCE.md); native clip/effect submission and tracked GPU storage are in [ARC_GRAPHICS_BENCHMARK.md](ARC_GRAPHICS_BENCHMARK.md). Arc `graphicsRegression` reproduces the state-guard checks.
