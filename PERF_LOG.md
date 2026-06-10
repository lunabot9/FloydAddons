# FloydAddons Performance Log

Feature-by-feature performance rewrite with real measurements. Every change cites a
before-number and ships with an after-number; deltas smaller than run-to-run variance
are not findings.

## Measurement environment (fixed for all runs)

| What | Value |
|---|---|
| Machine | macOS (Darwin 23.3.0), Apple Silicon, dev display dpr=1 |
| Instance | PrismLauncher "floyd pvp", profile timhotty, bridge 38769 |
| Java | 21.0.7 (Prism java-runtime-delta) — `com.sun.management.ThreadMXBean#getThreadAllocatedBytes` available |
| MC | 1.21.11 Fabric |
| Coexisting perf mods | sodium-fabric-0.8.12, lithium-0.21.4, entityculling-1.10.2, ImmediatelyFast-1.14.2, ferritecore-8.2.0, krypton-0.2.10 |
| Renderer | Sodium pipeline — the XraySodium* mixins are ACTIVE; vanilla-pipeline Xray mixins are the dormant ones here |
| Window focus | measurements taken with client window in ONE consistent focus state per A/B pair (unfocused throttles to ~9fps) |

## Protocol

Warm up 10s → sample 30s feature-OFF → `setModuleEnabled` ON → sample 30s feature-ON,
×3 repeats; report p50/p95/p99 frame-time deltas + render-thread alloc rate with
run-to-run variance. Arenas are saved named worlds (same scene every run).

## Baseline table

_(pending — Phase 0)_

## Per-feature log

_(pending)_

## Cross-platform audit notes

_(pending — per-feature gate d)_
