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

### 1. Block Search (2026-06-10) — FIXED

Scene: perfarena-ores (32768 indexed diamond_ore, 10k render cap), config as user runs
it (Tracers ON — discovered mid-fix: the persisted config had `"Tracers": true`, so the
baseline numbers included ~10k tracers/frame; the A/B kept it on for parity).

Root causes (each measured before fixing):
1. Over-cap path re-sorted the whole 32k index EVERY FRAME (`sortedBy{distSqr}` —
   comparator Double-boxing) + 10k fresh AABB/BoxData per frame → Extract section
   5243 µs + 4.07 MB **per frame**.
2. `drawTracer` per block per frame: `tracerOrigin()` recomputed two Matrix4f
   inversions per call (frame-constant value!) + Vec3/Vector4f/LineData churn —
   ~1.93 MB/frame + 524 µs on its own after fix 1.
3. `renderLineBox` allocated a `floatArrayOf(24)` per box per frame (~120 MB/s);
   10k per-box BoxData records (~62 MB/s).

Fixes (design survived a 4-lens adversarial review; 6 blockers folded in):
- Selection cache keyed on a mutation-conditional `indexEpoch` (no-op removeChunk calls
  from chunk churn deliberately do NOT bump it) + 2-block eye-movement hysteresis (only
  when distance-capped). Rebuild = primitive tandem quickselect (median-of-three Hoare,
  Long distances, zero boxing) into FRESH AABB+center lists (a queued batch record may
  outlive a flush-skip frame — never mutated in place). 8 JVM unit tests.
- One `BoxBatchData` record per frame instead of 10k BoxData ('Both' = two records,
  fill alpha halved as a float — chroma parity); one `TracerFanData` per frame instead
  of 10k LineData — flush computes the origin once (`tracerOrigin` now frame-memoized
  by matrix identity) and re-aims each target with scratch math, allocation-free.
- `renderLineBox` corners hoisted to a reusable scratch (render-thread-only,
  non-reentrant — documented).
- Bonus correctness: dimension-switch ghost highlights fixed (level-identity check);
  bridge `/state` now reports batch queue counts.

| Metric (ON-delta vs OFF, 30s × 3, spread in parens) | BEFORE | AFTER |
|---|---|---|
| alloc MB/s | **+655.6** (14.2) | **+0.42** (0.04) — ~1500× less |
| frame p99 ms | **+2.23** (1.07) | **−0.84** (0.05) |
| frame p50 ms | −0.57 (0.20) | −0.27 (0.07) |
| fps | +1.8 | +4.6 (0.65) |
| Extract section /frame | 5243 µs / 4.07 MB | **1.3 µs / 104 B** |
| GC collections /30s (ON) | ~1–3 + pressure | 0–1 |

Moving case (review's thrash scenario): walking through chunk churn → 14 reselects in
763 frames (~2/s), 393 µs each, alloc 10.2 MB/s total. ON now measures FASTER than OFF
on every metric (the residual systematic offset exceeds the feature's remaining cost).

Known remaining floor (documented, not a regression): `RenderBatchManager.Last` CPU
~3.7 ms/frame at the 10k cap = 240k immediate-mode line-vertex emissions. Allocation is
now ~2.6 KB/frame there; killing the CPU needs retained geometry (camera-relative VBO)
— a candidate follow-up if p99 at the cap matters in practice.

Cross-platform gate: diff audited — no platform/dpr/guiScale/window/native coupling
(pure world-space math + collections; tracer math is matrix-derived, resolution-
independent). New scratches documented render-thread-only. guiScale/window-size matrix
run deferred to the end-of-mission batch (world-space feature, no screen coupling).

### 2. Inventory HUD (2026-06-10) — FIXED

Scene: perfarena-hud (27 main-inv items + 15-line sidebar). Baseline: +21.2 MB/s alloc
(spread 0.18); section `PostHud.InventoryHud` 786 µs + 197 KB **per frame**. Frame time
was never the issue (delta within noise) — this was a pure GC-pressure offender.

Root cause: per ITEM per FRAME — fresh `TrackingItemStackRenderState` + full
`updateForTopItem` model re-resolution, fresh `PoseStack`, fog-UBO save/set/restore,
lighting setup, projection set, `renderAllFeatures()` + **full `endBatch()` flush**, and
FBO rebind (27× each); plus one `HudTextRenderer.drawText` per stack count = 27 more
endBatch+rebind round-trips and a `listOf(Segment)`+`Matrix4f` per call.

Fix:
- Per-slot render-state cache keyed on (stack identity, count, damage) — the resolver
  runs only when a slot actually changes, not 27×/frame.
- `ItemStateRenderer` inline batch: one projection set + one fog swap per frame,
  lighting set per GROUP (flat/3D), one `renderAllFeatures`+`endBatch` per non-empty
  group (2 flushes max instead of 27); pooled entries, shared PoseStack (submit copies
  pose state — the standard entity-render pattern).
- `HudTextRenderer.drawTextDeferred`/`flushDeferred`: counts share ONE batch flush;
  scratch Matrix4f (drawInBatch bakes vertices eagerly — proven by NvgTextReplay's
  per-run matrices); cached count strings.

| Metric (ON-delta, 30s × 3, spread in parens) | BEFORE | AFTER |
|---|---|---|
| alloc MB/s | **+21.2** (0.18) | **+4.09** (0.19) — 5.2× less |
| frame p99 ms | −0.135 (0.299) | −0.059 (0.050) |
| section /frame | 786 µs / 197 KB | **197 µs / 40 KB** |

Live-verified: 27-item grid renders identically; mixed lighting groups (3D block items
beside flat items) correct; stack counts ("37"/"5") right-aligned correctly; scene
restored after the count check. Remaining ~4 MB/s = vanilla submit-node + per-glyph
internals + per-panel SDF/blur meshes — shared infra, revisit only if it tops the
re-baseline.

### 3. Custom Scoreboard (2026-06-10) — FIXED

Scene: perfarena-hud (15-line sidebar). Baseline: +16.9 MB/s vs the VANILLA sidebar
(spread 0.63); sections `PostHud.Scoreboard` 459 µs + 233 KB/frame plus
`HudLayer.elements` 82 µs + 150 KB/frame — the full layout (sort, per-glyph String +
NFKC normalization, width measurement) ran **twice per frame**, then 17 endBatch+rebind
round-trips for the text.

Fix:
- Layout cache invalidated by a packet-driven epoch (`ClientboundSetScore/ResetScore/
  SetObjective/SetDisplayObjective/SetPlayerTeam` via the existing onReceive hooks).
  Packets fire on the Netty thread before the main thread applies the data, so a bump
  opens a 150 ms rebuild window; 1 Hz fallback covers config drift (nick-hider edits).
- The brand line's letter-by-letter chroma is RECOLORED per frame from cached glyphs
  (geometry is color-independent) — animation identical, layout untouched.
- HUD-editor size callback serves the cached dimensions (was the second full layout).
- All text queued via `drawSegmentsDeferred` → ONE batch flush per frame instead of 17.

| Metric (ON-delta vs vanilla sidebar, 30s × 3) | BEFORE | AFTER |
|---|---|---|
| alloc MB/s | **+16.9** (0.63) | **−19.1** (0.18) — ON now allocates LESS than vanilla |
| section /frame | 459 µs / 233 KB (+82 µs / 150 KB editor callback) | **57 µs / 41 KB** |
| frame p50/p99 | noise | noise (never the issue) |

Live-verified: full 15-line panel + centered title + brand render identically from
cache; `ZZ_NewLine` added via /scoreboard appears in correct sort position < 0.6 s
(packet invalidation), disappears on reset; brand chroma animates across screenshots.

## Cross-platform audit notes

_(pending — per-feature gate d)_
