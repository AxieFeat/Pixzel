# Measured CPU performance

The final independent run met the reference CPU budgets on the machine below: static HUD
p95 **0.137 ms** (target 2 ms), and 100 live updates across a 10,000-node tree p95 **1.895 ms**
(target 4 ms). These are measured CPU results for a bounded command-counting backend, without
rasterization, native graphics, presentation, or GPU work. They are not universal hardware guarantees.

## Final run

Measured on **2026-10-24 at 05:40:11 +10:00**, in a fresh process after ContextMenu, themed button
label defaults, image invalidation, and paired input-release integration:

| Condition | Recorded value |
| --- | --- |
| OS | Windows 11, version 10.0, amd64 |
| CPU | AMD Ryzen 7 7800X3D 8-Core Processor, 16 logical processors |
| Power plan | High performance, GUID `8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c` |
| JVM | Java HotSpot 64-Bit Server VM, JDK 21.0.10 |
| JVM arguments | None |
| Maximum heap | 8,489,271,296 bytes |
| Collectors | G1 Young Generation, G1 Concurrent GC, G1 Old Generation |
| Logical viewport/framebuffer | 1920 × 1080; density, UI scale and text scale all 1 |
| Clock input | Deterministic 16 ms per frame |
| Warmup | 300 frames per steady scene; 50 complete lifecycle cycles |
| Sampling | 500 frames per steady scene; 1,000 complete lifecycle cycles |
| Timing | `System.nanoTime`, nearest-rank p50/p95/p99; observed GC pauses included |
| Allocation | Supported `ThreadMXBean` current-thread allocated bytes around each measured sample |

Actual CPU clocks, temperatures, thermal throttling, and other-process activity were not sampled.
Allocation includes the library, deterministic fixtures, and benchmark callbacks on the calling
thread. It does not measure allocations made by JVM or native worker threads.

| Scene | Samples | p50 ms | p95 ms | p99 ms | Mean allocated B/sample | Allocation p95 B |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Static HUD: 1,000 visible children / 10,000 retained nodes | 500 | 0.119 | 0.137 | 0.161 | 384.528 | 384 |
| Live counters: 100 writes/frame / 10,000 retained nodes | 500 | 1.620 | 1.895 | 2.595 | 1,539,140.832 | 1,539,144 |
| Text menu: 300 labels / 50 active reveal policies | 500 | 0.069 | 0.087 | 0.116 | 78,897.616 | 82,152 |
| VirtualList: 100,000 logical items / about 30 visible rows | 500 | 0.319 | 0.559 | 0.702 | 764,059.600 | 772,928 |
| Lifecycle: resize/create/build/frame/close | 1,000 | 0.168 | 0.278 | 0.364 | 247,476.752 | 249,432 |

The static tree performed zero steady-state measurements. Live frames measured 101 nodes on
average and shaped 100 changed counter labels per frame. The text reveal scene performed zero
measurements and zero shaping calls during its 500 measured frames. VirtualList made deterministic
97-item jumps, retained 35 materialized rows at the final sample (72 total runtime nodes), and
asserted at most 36 materialized rows. Every lifecycle cycle alternated 1080p/720p, then asserted
zero host listeners and zero rendering sessions after closing the UI.
The lifecycle harness resizes the reused host before creating each fresh UI; active-UI resize
behavior is exercised separately by integration tests.

The final submission contained 4,003 commands / 1,000 draw operations for the static HUD, 4,303 /
1,000 for live counters, 2,103 / 300 for text, and 357 / 35 for VirtualList. These are command counts,
not GPU draw calls. Setup/destruction are excluded from steady-scene timings and included in the
lifecycle timings. The lifecycle result does not report a per-cycle layout measurement count.

The full generated output is preserved at
[`pixzel-cpu-release.md`](performance/pixzel-cpu-release.md), copied outside build output so a clean build
does not remove the measurement evidence.
The harness is [`Benchmarks.kt`](../pixzel-demo/src/main/kotlin/pixzel/demo/Benchmarks.kt).

## Earlier measurements and warmup sensitivity

Prior outputs are retained, including misses. The earlier baseline and intermediate implementations
are different source states; these rows do not isolate a single optimization or warmup alone.

| Live-data run | Warmup / measured frames | p50 ms | p95 ms | p99 ms | Mean allocated B/frame |
| --- | --- | ---: | ---: | ---: | ---: |
| Initial baseline, no JFR | 60 / 300 | 9.548 | 12.692 | 16.401 | 20,116,072.080 |
| Baseline, JFR profile enabled | 300 / 500 | 9.419 | 11.046 | 13.174 | 21,241,891.616 |
| Intermediate optimized, no JFR | 60 / 300 | 2.294 | 4.905 | 6.692 | 2,586,991.600 |
| Intermediate optimized, JFR profile enabled | 300 / 500 | 2.010 | 2.936 | 3.028 | 2,605,108.992 |
| Earlier integrated source, no JFR (00:51:11) | 300 / 500 | 1.552 | 2.243 | 2.372 | 1,539,036.832 |
| Release integrated source, no JFR (05:40:11) | 300 / 500 | 1.620 | 1.895 | 2.595 | 1,539,140.832 |

The intermediate 60-frame-warmup run exceeded the 4 ms live-data p95 target; the longer warmed
profiled run met it. Different profiling flags and runtime compilation/GC state also influence those
two runs, so the difference cannot be attributed solely to warmup. The final protocol uses 300 warmup
frames and reports the complete scene set. A single run does not establish run-to-run confidence
intervals or a worst-case latency bound.

Baseline Java Flight Recorder allocation samples frequently pointed to repeated node enumeration,
geometry-tree rebuilding, and boxed transform validation. The baseline's nearest `Transform`
constructor frame accounted for 18.06% of sampled allocation weight; three `SimpleUi.nodes` sites
accounted for 30.75% together. Those are statistical sample weights for the **entire process**, which
includes setup and warmup; they are not exact steady-frame byte shares. Exact per-frame allocation
numbers in the tables come from `ThreadMXBean`.

Historical evidence:

- [Initial all-scene baseline](performance/pixzel-cpu.md).
- [Intermediate static/live run](performance/pixzel-cpu-optimized-static-live.md).
- [Baseline JFR measurement](performance/live-baseline-profile.md) and
  [sample-site analysis](performance/live-baseline-hotspots.md).
- [Intermediate optimized JFR measurement](performance/live-optimized-profile.md).
- [Earlier integrated all-scene measurement](performance/pixzel-cpu-final.md).
- [Release all-scene measurement](performance/pixzel-cpu-release.md).

Some historical filtered-report headers predate a metadata correction and mention unselected
lifecycle cycles; their actual table rows identify the executed scenarios. These older outputs
remain unchanged. The final report records selected scenes and sample counts explicitly.

## Reproduction

Build and verify the independent modules using cached dependencies:

```powershell
.\gradlew.bat build verifyBoundaries :pixzel-common:stateChecks :pixzel-common:animationChecks --offline --console=plain
```

The final invocation used the exact JDK executable and a fresh process, without JFR:

```powershell
$taskStdlib = (Get-ChildItem -LiteralPath 'C:\Users\kiril\.gradle\caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-stdlib\2.4.10' -Recurse -Filter 'kotlin-stdlib-2.4.10.jar').FullName
$taskClasspath = @('pixzel-core\build\classes\kotlin\main','pixzel-common\build\classes\kotlin\main','pixzel-test\build\classes\kotlin\main','pixzel-samples\build\classes\kotlin\main','pixzel-demo\build\classes\kotlin\main',$taskStdlib) -join ';'
& 'C:\Program Files\Java\jdk-21.0.10\bin\java.exe' -cp $taskClasspath pixzel.demo.BenchmarksKt --warmup=300 --frames=500 --lifecycle=1000 --output=pixzel-demo/build/benchmarks/pixzel-cpu-release.md
```

The portable Gradle task also launches a benchmark JVM; retain its report's actual JVM flags when
comparing results:

```powershell
.\gradlew.bat :pixzel-demo:benchmark '--args=--warmup=300 --frames=500 --lifecycle=1000 --output=build/benchmarks/pixzel-cpu-new.md' --offline --console=plain
```

`--scene=static,live` restricts a diagnostic run. Other scene names are `text`, `virtual`, `lifecycle`,
and `all`. Use a new output filename to preserve previous evidence. The default shorter warmup is
60 frames; the final report above explicitly overrides it.

Native/GPU high-water memory, GPU submission/presentation times, actual draw calls, driver behavior,
antialiasing quality, and visual acceptance are outside this CPU benchmark. Independent build/test
validation immediately before the release run completed successfully: 108 common tests, 2 reference
backend tests, 1 external sample test, module boundaries, and both executable state/animation checks.
All six ContextMenu scenarios passed, including consumed key releases, safe-area placement, and
removing temporary listeners on Escape, focus loss, detach and disposal. No failures, errors, or
skipped tests were recorded. The headless preview was generated and visually inspected in the
earlier integrated build; it is separate from the CPU benchmark.
