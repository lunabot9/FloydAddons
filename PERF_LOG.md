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
run-to-run variance (`scripts/perf-protocol.py`). Arenas are saved named worlds
(`scripts/perf-arenas.py`): perfarena-ores (64³ ore cube, 32768 diamond_ore — past the
Block Search 10k render cap), perfarena-ents (50 named armor stands + 50 NoAI husks),
perfarena-hud (15-line sidebar + full inventory). Headline numbers from NO-sections
windows; one sections=1 window per feature for attribution only. `maxFps:260`,
`enableVsync:false` during measurement (~110 fps in-world ⇒ p50 ≈ 9.1 ms).

Known measurement limits (by design, recorded not hidden):
- Xray's dominant cost is OFF the render thread (Sodium section-compile workers +
  forced-TRANSLUCENT overdraw) — judge by whole-frame deltas + `xrayIsOpaqueCalls`
  counter (observed 3.3–4.4M calls per 30s ON window in the ore arena), not sections.
- Player ESP world pass is ~idle in perfarena-ents: summoned entities aren't real
  players (RealPlayerFilter is tab-list based). Real load needs a live server.
- Section p50/p95/p99 are log-bucket upper bounds (≤ ~9% high); max/totals exact.
  Sections are inclusive: never sum PostHud.total with PostHud.*, or ClickGUI.nvgPip
  with ClickGUI.textReplay.

## Harness gotchas hit (all fixed in 19b3380)

- `disconnectWithSavingScreen()` without `level.disconnect` first → client deadlocks
  on "Saving world" (one wedged client killed). Bridge `disconnect` action now uses
  the exact pause-menu flow `disconnectFromWorld(DEFAULT_QUIT_MESSAGE)`.
- Brigadier word-args reject `:` — `/fa blocksearch minecraft:diamond_ore` fails only
  in chat while bridge /chat returns ok. Bare ids normalize via `Identifier.tryParse`.
- `setModuleEnabled` is lowercase-keyed (`"x-ray"`, not `"X-Ray"`).
- Adversarial review (50 agents) found 23 real harness defects pre-baseline; the
  worst: lazy SectionAcc arrays sized maxFrames injected MBs of probe allocation into
  the measured window → replaced with ~1KB log histograms.

## Baseline table (2026-06-10, commit 19b3380, 30s windows × 3 repeats)

Sorted by measured severity. "finding" = delta exceeds run-to-run spread (3 repeats).
Full raw JSON per run in `perf-results/`.

| Feature | Arena | Δp50 ms (spread) | Δp99 ms (spread) | Δalloc MB/s (spread) | finding | dominant section (ON, per frame) |
|---|---|---|---|---|---|---|
| **Block Search** | ores | -0.572 (0.199) | **+2.232 (1.067)** | **+655.6 (14.2)** | YES | Extract 5243µs / 4.07MB; flush 3670µs / 1.76MB |
| **Inventory HUD** | hud | -0.072 (0.049) | -0.135 (0.299) | **+21.2 (0.18)** | YES | PostHud.InventoryHud 786µs / 197KB |
| **ClickGUI (open)** | hud | -0.015 (0.165) | -0.374 (0.396) | **+20.0 (0.47)** | YES | nvgPip 786µs / 168KB; textReplay 131µs / 126KB |
| **Custom Scoreboard** | hud | +0.020 (0.125) | -0.084 (0.330) | **+16.9 (0.63)** | YES | PostHud.Scoreboard 459µs / 233KB (+ HudLayer 82µs / 150KB = the double-layout) |
| **Mob ESP** | ents | -0.032 (0.158) | -0.015 (0.051) | **+6.8 (3.06)** | YES | flush 29µs / 7.8KB |
| Day Tracker | hud | -0.063 (0.396) | -1.573 (9.906) | +1.5 (3.63) | no | PostHud.DayTracker 115µs / 8KB |
| Player ESP | ents | -0.037 (0.079) | -0.009 (0.047) | +0.0 (3.96) | no | (arena limit: no real players) |
| X-Ray | ores | -0.015 (0.118) | -0.136 (0.456) | -0.0 (0.05) | no | steady-state cost is worker-thread (3.3–4.4M isOpaque calls/window) + GPU; whole-frame unaffected on this rig |
| HUD | hud | -0.001 (0.163) | +0.103 (0.193) | -0.1 (0.30) | no | PostHud.total 98µs / 0B |

Interpretation:
- **Block Search is the worst offender by ~30×**: its Extract section alone is 5.2ms +
  4.07MB *per frame* (re-sorting the whole 32k index every frame past the 10k render
  cap), plus 3.7ms + 1.76MB in the wire-box flush. p99 confirms real hitching (+2.2ms).
- The next four are pure allocation-rate offenders (16–21 MB/s each) with frame time
  inside variance on this machine — they matter for GC-hitch pressure, not p50.
- Negligible (recorded, skipped per protocol): Day Tracker, HUD, X-Ray steady-state,
  Player ESP (in this arena — needs a live-server measurement before any verdict).

Backlog order: Block Search → Inventory HUD → ClickGUI → Custom Scoreboard → Mob ESP.

## Per-feature log

_(pending)_

## Cross-platform audit notes

_(pending — per-feature gate d)_
