# V2 → v2.1 merge gap (authoritative audit, 2026-05-31)

Branch `feature/v2.1`. Audit compared `upstream-floyd/V2` (pkg `floydaddons.not.dogshit.client`) vs ours (`gg.floyd`), semantically (package rename ignored). 5/6 subsystems auto-audited; the player/cosmetic/misc subsystem agent failed structured output — **NickHider `selfNameChroma` + cosmetic deltas still need a manual re-check** (tracked below).

## Architecture decision (user)
Un-nesting uses: **each feature is its own top-level Module that OWNS its settings; the old mega-object becomes an unregistered backing object whose `shouldXxx()` helpers READ the new modules.** Mixins/callsites keep calling the mega-object facade → no mixin churn. We do NOT copy V2's `Bound*Setting` getter/setter glue.

## Done + verified
- ✅ **Global custom font** — `default.json` provider order fixed (removed `include/default` that shadowed the TTF). Verified in-client: title screen, in-world chat, holograms all render Inter. (`e630984`)

## Priority order (remaining)
1. [ ] **`Module.visibleInGui`** (low) — restore `@Transient open val visibleInGui = true`; blocker for un-nesting.
2. [ ] **Camera un-nest** → `Freecam` / `Freelook` / `F5 Customizer` (medium) — settings move onto modules; `FloydCamera` backing keeps runtime state + reads modules. Add `setF5CustomizerEnabled`.
3. [ ] **Hiders un-nest** → 15 modules (medium) — pure toggles; `FloydHiders` facade reads `<module>.enabled`. Watchdog/ModHider facade reads new modules (backing stays in FloydCompatibility).
4. [ ] **Compat un-nest** → `Spoof Client Brand` / `Custom Main Menu` / `Taskbar Icon` (visibleInGui=false) / `Update Checker` (medium).
5. [ ] **Scoreboard configurable settings** (user ask #2) — restore corner-radius + color settings + add internal padding so the border sits further from text. (FloydHud)
6. [ ] **ESP tracer view-bobbing lock** (user ask #1) — tracers wobble with view bobbing; lock origin to crosshair regardless of bob.
7. [ ] **Keybind sync** (medium) — `KeyMappingAccessor` + `KeyMappingCategoryAccessor` + `KeyBindingSyncMixin` + `KeybindSync.kt`; `KeybindSetting.bindKeyMapping/applyExternalKey`. Bidirectional with MC controls menu.
8. [ ] **GUI accent chroma** (low) — `guiAccentColor(offset)` + `clickGUIChroma` setting; rewire Panel bottom / Description border / title gradient.
9. [ ] **GuiMixin.render() HEAD reset** (low) — `FloydHud.resetVanillaScoreboardWouldRender()` each frame (method already exists). Resolve with `displayScoreboardSidebar()` objective-check question.
10. [ ] **FloydDayTrackerModule** (medium) — port `drawDayTrackerHud()`.
11. [ ] **FloydInventoryHudModule + per-HUD inventory theming** (low).
12. [ ] **Re-check player/cosmetic/misc** — NickHider selfNameChroma, Skin/Cape/ConeHat, DiscordPresence deltas (audit gap).

## Preserve (ours, no V2 equivalent)
FloydAutoTotem, FloydPlayerEsp, FloydMobEsp, FloydBlockSearch, Category.PVP, SearchableListSetting (+ scroll consumption), Color.chroma model, mc.font scoreboard rendering, FloydRender windowTitle, font JSON providers.

## Open questions / risks
- `displayScoreboardSidebar()`: V2 marks vanilla-scoreboard only when an objective exists; ours marks unconditionally when custom scoreboard on — investigate (may strand the vanilla signal).
- `FloydBrandSpoofMixin` dropped `remap=false` — verify brand spoof still fires on target MC, else it silently no-ops.
- Ported Java mixins must compile under our newer mixin AP (no explicit refmap).
- HUD default positions differ (V2 right-anchored large vs ours top-left small) — preserve saved positions when adding day-tracker/inventory.
- Camera config keys change when settings move off FloydCamera → existing saved camera settings reset (acceptable mid-dev).
