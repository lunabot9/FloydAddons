# FloydAddons v2.1 — session handoff (2026-05-31)

Pick up the v2.1 merge + UX cleanup. This doc **references** other docs/files rather than duplicating them — read them.

## Read first (do not duplicate here)
- `MERGE_STATUS.md` — authoritative V2→v2.1 gap report (done vs pending, V2 features not yet ported, behavioral diffs, risks).
- `UX_CLEANUP_PLAN.md` — adversarial UX analysis output: prioritized work items + per-item file-level specs + open questions + risks (searchable-list redesign, HUD settings reorg, legacy-GUI extraction, parity).
- `~/.claude/CLAUDE.md` — user global prefs (no Claude attribution in commits/PRs; local git account only; no `as any` casts; TDD when possible; no over-engineering; direct/grill communication).
- Memory dir `~/.claude/projects/-Users-twaldin-new-floyd-addons/memory/` — esp. `floyd-verification-standard.md` (NEVER claim done until verified in a real client; I over-claimed early and broke trust), `floyd-prism-jar-traps.md`, `floyd-singleplayer-testing.md`, `MEMORY.md` index.

## Repo / git
- **Worktree: `/Users/twaldin/floyd-pvp-modules`** (branch `feature/v2.1`). NOTE: the session cwd is `/Users/twaldin/new-floyd-addons` (a DIFFERENT/older worktree) — agents inherit it; always use absolute paths under `floyd-pvp-modules` or `git -C /Users/twaldin/floyd-pvp-modules`.
- Remote `fork-floyd` = `github.com/twaldin/SkyblockQOLmod.git`. HEAD `ab6bc5b`, pushed. Other remotes: `upstream-floyd` (lunabot9/FloydAddons; branch `V2` is the merge source, pkg `floydaddons.not.dogshit.client`), `upstream-odin`.
- Our package root is `gg.floyd`; resource namespace `floydaddons`. Mappings: official Mojang, MC 1.21.11.
- Draft PR #23 → `lunabot9/FloydAddons`. Do NOT auto-merge upstream main. Commit/push incrementally; never add Claude attribution.

## Build / deploy / verify loop (operational)
- **Build:** `cd /Users/twaldin/floyd-pvp-modules && ./gradlew build` — read the literal `BUILD SUCCESSFUL`/`BUILD FAILED` line. GOTCHA: never `| tail`/`| echo` to judge gradle (masks exit). Jar: `build/libs/FloydAddons-2.1.0.jar`.
- **Deploy:** copy jar to `"/Users/twaldin/Library/Application Support/PrismLauncher/instances/floyd pvp/minecraft/mods/FloydAddons-2.1.0.jar"`.
- **Restart (required — running JVM won't reload jar resources):** `kill` the `floyd pvp` java pid (`ps aux | grep 'instances/floyd pvp' | grep -v grep`), then relaunch: `"/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher" --launch "floyd pvp"`. Do NOT touch other instances (e.g. "Floyd Baritone Smooth") or other ports.
- **Control bridge** (FloydLocalControl): `http://127.0.0.1:38769`, header `Authorization: Bearer hJ876CyAkshrtTzHRgIHwaFeV8L7NQYm_RuntyL_4WU`. Endpoints: `GET /state` (module/category registration, per-module state); `POST /screen` `{screen:"clickgui|legacy|hud|close"}`; `POST /action` `{action:"connect","address":"donutsmp.net"}` / `{action:"openWorld","world":"<name>"}` / camera/attack/etc.; `POST /chat`; `POST /screenshot` → saves `minecraft/screenshots/floyd-control.png`; `POST /mouse`.
- **Verify** per `floyd-verification-standard.md`: drive the real client (singleplayer creative for deterministic; donutsmp.net/Hypixel for players+scoreboard+icons), screenshot, crop FULL-RES with PIL (downscaled views are misleading), dump `/state`, assert EACH feature. Mixin/runtime errors → check `minecraft/logs/latest.log` + `crash-reports/`.
- **Mojmap method signatures** (for mixins): javap the named jar `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.11-loom.mappings.*/*.jar` (the `1.21.11/minecraft-merged.jar` is intermediary-named — less useful).

## Status
**Done + verified in-client + pushed:** global font (Inter via `assets/minecraft/font/default.json`, no `include/default`); un-nesting (Hiders→15, Camera→Freecam/Freelook/F5, Misc compat split — see `features/impl/hiders/FloydHiderModules.kt`, `camera/FloydCameraModules.kt`, `misc/FloydCompatModules.kt`; mega-objects are now plain backing facades); no-crash; 41 modules register.

**Committed + deployed, NOT yet user-confirmed in-client:** scoreboard padding/corner-radius (`FloydHud.kt` — see Task A note re: moving these to Custom Scoreboard), HUD-editor drag fix (`HudManager.kt`/`HUDElement.kt`), BlockSearch occlusion (`FloydBlockSearch.kt` + `XrayOcclusionMixin`), ESP overhead projection (`LevelRendererMixin.java` + `WorldToScreen.kt`).

**All 7 Floyd Compatibility features are implemented** (Spoof Client Brand, Custom Main Menu, Taskbar Icon, Update Checker, Discord Presence, Hide Watchdog, Mod Hider) — see `MERGE_STATUS.md`/the Misc+Hiders modules; not yet individually toggled-and-observed in-client (Discord needs an app ID; brand-spoof has a dropped `remap=false` to verify).

**Pending** (see `MERGE_STATUS.md` + `UX_CLEANUP_PLAN.md`): the two immediate tasks below, plus custom-font on/off toggle + BYO `.ttf`, keybind sync, GUI accent chroma, GuiMixin scoreboard frame-reset, day-tracker module, inventory-HUD standalone module + theming, full view-bob tracer lock (currently falls back to eye-based origin), NickHider `selfNameChroma`/cosmetic re-audit, config migration for renamed module keys.

## IMMEDIATE NEXT TASKS (new — not yet in the other docs)

### Task A — Player ESP overhead overlay rework (`features/impl/pvp/FloydPlayerEsp.kt`, `utils/render/WorldToScreen.kt`)
Current overhead = heart(❤)+HP on one row, then a row of 6 equipment item icons; projected via `WorldToScreen.project()` (matrices captured in `LevelRendererMixin`, projection auto-identified by `m33≈0`). Bug reported: overlay **gets smaller as you get CLOSER** (inverted). Implement:
1. **Scaling:** add `Scale` NumberSetting (base size, controls both modes) + `Scale with Distance` BooleanSetting + `Max Distance` NumberSetting (range). Static mode (off): constant size = `Scale`. Distance mode (on): LARGEST when close (= `Scale`), shrinks as the player gets farther, clamped at a min by `Max Distance`. (Fixes the current inverted behavior.) Confirm exact min/curve with user if unclear.
2. **Layout:** one line — heart + HP number + the 6 item icons all on a SINGLE row (not HP stacked above). (User: "make it 1l".)
3. **Panel:** give the overlay an always-on tinted background panel like the scoreboard/inventory HUD (reuse `FloydHud`'s `fillPanel`/`RoundRectPIPRenderer` path), with **border color = the player ESP color** (the existing `color` setting used by box/tracer), and configurable **corner radius + padding** + chroma/fade, matching the scoreboard/inv pattern.
- If overhead positioning is still wrong after a populated-server test, the knob is `WorldToScreen.capture()` matrix identification (`LevelRendererMixin` passes the 3 `renderLevel` Matrix4f params; projection = `m33≈0`, view = first remaining — swap if needed). `require=0` keeps it crash-safe.

### Task B — Legacy hub (`/fa`) ClickGUI removal + entrypoint routing + parity (`clickgui/LegacyFloydClickGUI.kt`, `commands/MainCommand.kt`)
Confirmed code map: `/fa` → `LegacyFloydClickGUI.openHub()` (fullscreen hub = the "old GUI", KEEP). Hub buttons (mouse handler ~line 959): `styleButton`→`Page.GUI_STYLE`, `hudButton`→`HudManager`, **`clickGuiButton`("ClickGUI")→`Page.CLICK_GUI`** (the redundant OLD dropdown page — REMOVE), `v2Button`→`mc.setScreen(ClickGUI)` (the NEW Odin GUI — KEEP).
1. **Remove** the `clickGuiButton` + `Page.CLICK_GUI` page entirely; relabel `v2Button` → "ClickGUI" so the hub still links to the Odin GUI.
2. **Fix the bug:** clicking module entrypoints in the hub (e.g. "Neck Hider", "Camera") currently routes into the old `Page.CLICK_GUI` instead of that module's own hub page/modal — make each entrypoint open its correct in-hub settings page.
3. **Parity:** the hub must reach 1-to-1 parity with the Odin `ClickGUI` (all modules/settings controllable in BOTH GUIs + via `/fa` commands — `MainCommand.kt` + `FloydLocalControl` action `connect`/screen map). Implement missing hub pages; if the full parity pass is too large, fix the routing bug + removal now and produce a parity-gap list (user OK'd a later "big pass" on this hand-rolled 6,999-line GUI).
- Watch: `HudManager.estimatedHudSize()` hardcodes HUD names (will break as HUDs move modules — see `UX_CLEANUP_PLAN.md` item 7); legacy GUI does NOT auto-discover modules (every split must be mirrored manually) — biggest parity-drift risk.

## How to run work (ultracode is ON)
Use the Workflow tool. Pattern that worked: a **sequential** pipeline of implementation agents in the main worktree (single writer), each building to green + committing, with "revert your uncommitted changes if you can't get green so the next chunk isn't poisoned." Then YOU deploy once + verify each in-client (don't trust agent self-reports for runtime/visual correctness — they can't run the client). Example scripts: `~/.claude/projects/-Users-twaldin-new-floyd-addons/fac7cdd0-*/workflows/scripts/` (`v21-feature-pipeline-*.js`, `floyd-ux-adversarial-analysis-*.js`).

## Open question still pending with user
ESP scaling exact curve/min (Task A.1) — proceed with the stated interpretation unless they correct it.
