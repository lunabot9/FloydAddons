# Floyd UX/UI cleanup plan

## 1. Executive summary

Floyd's configuration surface has drifted into three structural problems that compound each other:

1. **Settings live in the wrong modules.** `FloydHud` owns settings that conceptually belong elsewhere — scoreboard appearance (color, fade, corner radius, padding) belongs to the already-separate `FloydCustomScoreboard` module, and the inventory HUD scale has no home module at all (it is a buried private property). The "HUD" module simultaneously holds visual settings *and* the editor-launch logic, so a user toggling "HUD" cannot reason about what it controls. `HudManager.estimatedHudSize()` hardcodes HUD element names (lines 171–178), which breaks modularity the moment HUDs are split out.

2. **The Click GUI module is a junk drawer.** `ClickGUIModule` mixes ~15 unrelated settings: Odin GUI theming (`clickGUIColor`, `roundedPanelBottom`), chat notifications (read globally by `Module.kt`), nine legacy fullscreen-GUI styling settings (button/border/fade colors that only `LegacyFloydClickGUI` reads, lines ~6694–6699), keybinds, and an "Open HUD Editor" action button that belongs with the HUD feature. There is no `LegacyClickGUIModule`, so legacy styling is orphaned and undiscoverable.

3. **List-management is button soup with no parity story.** `FloydMobEsp`, `FloydXray`, and `FloydBlockSearch` all repeat a StringSetting-editor + Add/Remove/List/Clear-button pattern. MobEsp alone carries ~15 settings (~180px of an ~240px panel) for what is conceptually "pick which mobs to render." `SearchableListSetting` already exists and solves this (MobEsp line 120), but it only renders raw IDs (`minecraft:zombie`) with no icons, no friendly names, no per-entry color, and no inline add/remove — and the supporting MapSettings are `.hide()`'d, so command-only users have parity but GUI users do not. Search filters by raw ID substring, so searching "cobble" returns zero hits.

Underlying all of this is a **two-GUI parity tax**: every change must be mirrored across the Odin `ClickGUI`, the 6,999-line `LegacyFloydClickGUI`, the `HudManager` fullscreen editor, and the `/fa` command tree — and several settings currently exist in only one of those surfaces.

## 2. Prioritized work items

Ordered by user impact. The searchable-list redesign and HUD-settings reorg are the top asks.

- [ ] **1. Build `ExtendedSearchableListSetting` and migrate MobEsp/Xray/BlockSearch onto it.** Impact: **high** · Effort: **high** · *Why:* collapses ~15-setting button soup in three modules into one searchable multi-select list with icons, friendly names, inline color, and inline add/remove — the single largest discoverability and screen-real-estate win.
- [ ] **2. Reorganize HUD settings into per-feature modules.** Impact: **high** · Effort: **med** · *Why:* moves scoreboard appearance to `FloydCustomScoreboard`, creates `FloydInventoryHud`, and reduces `FloydHud` to an editor entry — so each module owns its own settings instead of one module owning everyone's.
- [ ] **3. Move "Open HUD Editor" to the HUD module.** Impact: **high** · Effort: **low** · *Why:* the button currently lives in `ClickGUIModule` (line 40) where no one editing HUD elements would look; it belongs in `FloydHud`.
- [ ] **4. Extract `LegacyClickGUIModule` so there are two clean GUIs.** Impact: **med** · Effort: **med** · *Why:* separates legacy fullscreen styling (9 settings) from Odin theming, making each GUI's settings self-contained and the legacy styling discoverable instead of orphaned under Odin.
- [ ] **5. Friendly-name + icon utility (`Identifiers.kt` + icon caches).** Impact: **med** · Effort: **med** · *Why:* prerequisite for item 1's "search by Cobblestone not minecraft:cobblestone" and icon rendering; shared by all three list modules.
- [ ] **6. Config migration for moved settings.** Impact: **med** · Effort: **med** · *Why:* without remapping old `hud`/`clickgui` keys, every existing user silently loses their scoreboard/inventory/legacy-GUI customizations on first load after the reorg.
- [ ] **7. Fix `HudManager.estimatedHudSize()` hardcoding.** Impact: **med** · Effort: **low** · *Why:* the hardcoded name switch (lines 171–178) breaks the moment HUDs move out of `FloydHud`; must become a registry/dispatch the new modules populate.
- [ ] **8. Restore command/GUI parity for moved + hidden settings.** Impact: **med** · Effort: **med** · *Why:* keep `/fa`, `/legacygui`, `/oldgui`, `/fa edithud` mapping correctly after the splits, and ensure nothing accessible via command becomes GUI-invisible (or vice-versa).
- [ ] **9. (Optional, defer) Deepen `SearchBar` to match setting names; add scripting commands (`/fa reset hud`, `/fa hud <name> <x> <y> <scale>`, etc.).** Impact: **low–med** · Effort: **med** · *Why:* nice discoverability and scripting wins but not part of the core directives; sequence after 1–8 land.
- [ ] **10. Standardize the chat "logger" across all modules.** Impact: **med** · Effort: **low–med** · *Why:* module output is inconsistent (see 2026-05-31 testing). The base `Module.toggle()` emits a standardized `§aenabled`/`§cdisabled` line, but (a) it is gated behind `ClickGUIModule.enableNotification`, and (b) several modules (Freelook, Freecam, …) bypass it with ad-hoc `modMessage("… enabled.")` calls that render plain white, and some messages carry the `FloydAddons »` prefix while others don't. Build one logger (extend `ChatUtils`/`ChatManager`) with consistent prefix + level formatting (success=green, error/off=red, info=white) and route ALL module status output through it; kill the per-module ad-hoc toggle strings so every module gets identical red/green formatting.
- [ ] **11. Block Search: radius = render distance, event-driven (no O(n³) scan).** Impact: **high** · Effort: **med-high** · *Why:* the 4–24 block `radius` slider + per-second cubic `for x/y/z` rescan (`FloydBlockSearch.rescan`) doesn't reach as far as you can see and can't scale to render distance without freezing. Replace with a per-chunk index maintained by `ClientChunkEvents.CHUNK_LOAD/UNLOAD` (scan each chunk once on load, skipping air-only sections) + a `LevelChunk.setBlockState` client mixin for O(1) live-edit updates; render iterates the index. "Radius = render distance" then falls out for free (the index only ever holds loaded chunks). Remove the `radius` setting. **(In progress 2026-05-31.)** Note: X-Ray (render/occlusion based) and Mob ESP (entity-list based) don't have this scan problem — scope is Block Search only.

## 3. Per-item implementation specs

Architecture principle held throughout: **modules own their own settings**; both GUIs (`ClickGUI`, `LegacyFloydClickGUI`) plus the `MainCommand` `/fa` tree must stay 1-to-1 after every change.

### Item 1 — `ExtendedSearchableListSetting` + module migrations

**New widget:** `src/main/kotlin/gg/floyd/clickgui/settings/impl/ExtendedSearchableListSetting.kt` (extends `SearchableListSetting`). Add constructor params over the base: `displayNameProvider: ((String) -> String)?`, `iconProvider: ((String) -> Image?)?`, `colorProvider: ((String) -> String?)?`, `onColorChange: ((String, String) -> Unit)?`, `onAltClick: ((String) -> Unit)?`, `showActionsRow: Boolean`. Reuse the base's existing expand/collapse animation, `TextInputHandler` search, scroll-wheel consumption, scissor clipping, and scrollbar tracking. Additions:
- Row render (after the existing option-text render): draw a 16px icon at row-left (gray placeholder if `iconProvider` returns null — always reserve the box so layout is stable), render the friendly display name instead of the raw ID, and draw a 6–8px color swatch right of the text when `colorProvider` returns non-null.
- Render a filled/unfilled checkbox instead of the current gray-vs-white text highlight, for a clearer multi-select affordance.
- `mouseClicked`: detect `button == 1` (right-click) on a row → `onColorChange`; detect Alt+left-click → `onAltClick` ("Add Looked-At" equivalent). **Verify in the codebase whether the mouse event exposes modifier state**; if not, read `GLFW_KEY_LEFT_ALT` from the keyboard input API.
- If `showActionsRow`, render a fixed bar *below* the scroll viewport (outside the scrollable area, so scroll-over-button cannot fire a click) with "Pick from World" and "Clear All".

**Friendly-name util:** `src/main/kotlin/gg/floyd/utils/Identifiers.kt` — `friendlyName(id)` (`minecraft:diamond_ore` → "Diamond Ore"), plus a precomputed searchable-token map so search matches both ID substring and friendly-name substring. Precompute per-module at init, not in the render loop.

**`FloydMobEsp.kt`:** delete the 15 list-management settings (`editorNameFilter`, `editorTypeFilter`, `editorColor`, `editorChroma`, the 8 Add/Remove/Color Name/Type action buttons, `listEditorFilters`, `clearEditorFilters`, `addLookedAtName`, `addLookedAtType`). Keep `nameFilters`/`typeFilters` MapSettings (persistence) and `filterColors`; keep them `.hide()`'d as backing storage. Add one `mobFilterList: ExtendedSearchableListSetting` whose `optionsProvider` unions nearby names/types + all entity-type IDs, `selectedProvider` returns `activeNameFilters() + activeTypeFilters()`, `onToggle` disambiguates name vs type by presence of `:` in the ID (or pass a typed discriminator), `onColorChange` writes `filterColors`, `onAltClick` does the add-looked-at. Update `matches()` (line ~259) to read the unified backing maps. Keep `StarMobs` as its own BooleanSetting — it is a flag, not a filter entry.

**`FloydXray.kt`:** delete `editorBlock` + Add/Remove/List/Clear actions. Keep `opaqueBlocks` MapSetting hidden as backing. Add `xrayBlockList: ExtendedSearchableListSetting` with `displayNameProvider = Identifiers::friendlyName`, a lazy block `iconProvider`, options = nearby + all block IDs. Keep `isOpaque()` / `rebuildChunks()` logic; call rebuild on toggle.

**`FloydBlockSearch.kt`:** delete `editorBlock` + Add/Remove/List/Clear actions. Keep `searchBlocks` MapSetting hidden. Add `blockSearchList: ExtendedSearchableListSetting` (same icon/display providers as Xray — factor the block icon cache into a shared lazy util). Call `forceRescan()` on toggle.

**Icons:** lazy-build caches on first GUI open. For blocks prefer loading PNGs from `/assets/minecraft/textures/block/{name}.png` (simpler than extracting `BlockAtlas` subregions for `NVGRenderer.image`). For mobs, render a gray placeholder unless a head/skin texture is readily available. Never block render on icon load.

**Parity:** if `FloydMobEsp`/`FloydXray`/`FloydBlockSearch` have `/fa ... add/remove/color/list/clear` handlers, preserve the public `addNameFilter()`/`addTypeFilter()`/`addOpaqueBlock()` etc. APIs so commands still route correctly. Mirror or deprecate the corresponding `LegacyFloydClickGUI` representations of these three modules.

### Item 2 — HUD settings reorg

**`FloydCustomScoreboard.kt`:** add the five settings moved from `FloydHud` — `scoreboardHudColor` (ColorSetting + chroma), `scoreboardHudFade` (Boolean), `scoreboardHudFadeColor` (ColorSetting), `scoreboardHudCornerRadius` (Number), `scoreboardHudPadding` (Number). Move the Scoreboard HUD element (keep it private) and the `drawScoreboardHud`/`drawScoreboardBox`/`circularBorderColors`/`scoreboardAccentColor`/`accentColor` rendering methods out of `FloydHud`. Add a `state()` exposing the scoreboard HUD's position/scale/enabled.

**`FloydInventoryHud.kt` (NEW)** in `.../features/impl/render/`: independently toggleable RENDER-category module. Move `inventoryHudScale`, the Inventory HUD element, and `drawInventoryHud()` from `FloydHud`. Add `state()`.

**`FloydHud.kt`:** delete all five scoreboard settings, `inventoryHudScale`, both HUD elements, and all their drawing methods. Resolve the shared `hudCornerRadius`: **recommended** — move it to `FloydRender` (which already owns global visual settings like window title / fullChatChroma) and have both HUD modules read it, avoiding a `FloydHud`-as-dependency. Simplify `state()` accordingly.

**`ModuleManager.kt`:** register `FloydInventoryHud` in `registerModules()` (line ~98). Both new modules auto-register their `HUDSetting`s to `hudSettingsCache` via the existing `Module.HUD()` delegation + `HUDSetting instanceof` check (line ~145), provided they are unconditionally registered.

**Parity:** verify package-based category detection (`Module.getCategoryFromPackage`, lines ~134–149) places both new modules under RENDER. Update `LegacyFloydClickGUI` everywhere it does `hudSetting(FloydHud, it)` to iterate all three HUD-owning modules. `/fa edithud` → `HudManager` (MainCommand lines ~28–30) is unchanged.

### Item 3 — Move "Open HUD Editor" to HUD module

In `FloydHud.kt`, add `ActionSetting("Open HUD Editor", desc = "Opens the HUD editor when clicked.") { mc.setScreen(HudManager) }` (import `gg.floyd.clickgui.HudManager`). Remove the action button from `ClickGUIModule` (line 40). This pairs naturally with item 2's reduction of `FloydHud` to an editor-entry module.

### Item 4 — Extract `LegacyClickGUIModule`

**`LegacyClickGUIModule.kt` (NEW)** in `.../features/impl/render/`: RENDER category, `@AlwaysActive`, name "Legacy Click GUI". Move the nine legacy styling settings from `ClickGUIModule` (`buttonTextColor`, `buttonTextFadeColor`, `buttonTextFade`, `buttonBorderColor`, `buttonBorderFadeColor`, `buttonBorderFade`, `guiBorderColor`, `guiBorderFadeColor`, `guiBorderFade`) plus `enableNotification`. `onKeybind() { toggle() }`, `onEnable() { mc.setScreen(LegacyFloydClickGUI.openHub()); super.onEnable(); toggle() }`. **Keybind decision pending user (see open questions).**

**`ClickGUIModule.kt`:** reduce to Odin-only — keep `clickGUIColor`, `roundedPanelBottom`, the `openGuiKey` (N) keybind, and the `panelSetting` MapSetting (panel positions). Remove the nine legacy settings, `enableNotification`, and the HUD-editor action. Simplify `onKeybind`/`onEnable` to only open `ClickGUI`. Strip legacy field mappings from `state()`.

**Reference updates:** `LegacyFloydClickGUI.kt` lines ~6694–6696 — repoint `ClickGUIModule.buttonTextColor.chroma` / `buttonBorderColor.chroma` / `guiBorderColor.chroma` to `LegacyClickGUIModule`. `Module.kt` — repoint the `enableNotification` reference (the global keybind-toast read) to `LegacyClickGUIModule`. `MainCommand.kt` `/legacygui` / `/oldgui` / `/fa` routes to `LegacyFloydClickGUI.openHub()` stay as-is (audit after).

### Item 5 — Friendly-name + icon util

Covered under item 1 (`Identifiers.kt` + shared lazy block icon cache + per-module mob icon cache). Built first because items 1's three migrations all depend on it.

### Item 6 — Config migration

`ModuleConfig.load()`: detect legacy keys. For the HUD reorg, when loading the old `hud` module, route `scoreboardHud*` keys → `customscoreboard` and `inventory*` keys → `inventoryhud`. For the GUI split, backfill `LegacyClickGUIModule` fields from old `ClickGUIModule` legacy keys (`buttonText*`, `buttonBorder*`, `guiBorder*`, `enableNotification`). Save immediately after migration so subsequent loads use new keys. Log each remap. The list-redesign (item 1) needs **no** migration — it keeps the same MapSettings, so saved configs load unchanged.

### Item 7 — `estimatedHudSize()` de-hardcode

`HudManager.kt`: replace the line 171–178 name switch with either a `Map<String, () -> Pair<Int,Int>>` that `FloydCustomScoreboard` and `FloydInventoryHud` register into, or — cleaner — put `estimatedWidth/Height` on the HUD element itself and query it dynamically. Fall back to a default (e.g. 120×40) for unknown HUDs.

### Item 8 — Parity sweep

After items 2–4 land, audit all four surfaces: Odin `ClickGUI` (auto-discovers via `hudSettingsCache` / module registration), `LegacyFloydClickGUI` (hand-rolled; must be manually updated for split modules), `HudManager`, and `MainCommand`. Confirm no setting is reachable in commands but `.hide()`'d in both GUIs without an intentional wrapper, and no setting moved out of one GUI without being added to the other.

### Item 9 — (Defer) search depth + scripting commands

`SearchBar.kt` / `Panel.kt`: extend filtering to match `setting.name` in addition to `module.name`. `MainCommand.kt`: add `/fa reset hud`, `/fa hud <name> <x> <y> <scale>`, `/fa keybinds reset`, optional `/fa color <setting> <hex>`. Check the `/fa` namespace for collisions before adding. Not part of the core directives — sequence last.

## 4. Open questions to confirm with the user

These are blocking ambiguities. Each has a recommended answer.

1. **What exactly are the "two clean GUIs," and is removal of either intended?** The codebase has *three* UI surfaces: Odin `ClickGUI`, the 6,999-line fullscreen `LegacyFloydClickGUI`, and the separate `HudManager` editor. Your directive (2) says "two clean GUIs (fullscreen old + Odin)." Does "legacy clickgui" (the styling settings being extracted) refer to the styling-config *module*, while "fullscreen modal old gui" refers to the `LegacyFloydClickGUI` *screen* — i.e. one is the settings, one is the renderer, and both stay? Or do you intend to eventually delete one renderer? **Recommended:** keep both renderers for now; the extraction is purely settings-ownership (move legacy styling into `LegacyClickGUIModule`), with no renderer removed this pass. Confirm before I touch the 6,999-line file beyond the three chroma references.

2. **Where does the RIGHT_SHIFT keybind go after the split?** Today `ClickGUIModule.key = RIGHT_SHIFT` and `openGuiKey = N` both reach the Odin GUI. After extracting `LegacyClickGUIModule`, should RIGHT_SHIFT bind to the legacy module (opens the old fullscreen GUI), bind to Odin, or be removed? **Recommended:** RIGHT_SHIFT → `LegacyClickGUIModule` (opens legacy), N stays → Odin. This gives each GUI a distinct key and preserves muscle memory for legacy users. Confirm.

3. **Does `enableNotification` belong in the legacy module?** It is read globally by `Module.kt` for *all* keybind-toggle toasts, so logically it is UI-wide, not legacy-specific. **Recommended:** move it to `LegacyClickGUIModule` only if you accept the "legacy owns it" framing; otherwise create a neutral home. I lean toward leaving it logically global — confirm whether you want a small "UI Settings" module instead.

4. **HUD module after the reorg — toggle semantics?** If `FloydHud` becomes editor-only, does toggling it enter/exit drag mode, or does only the new "Open HUD Editor" action button open `HudManager`? **Recommended:** the action button opens the editor; the module toggle does nothing user-visible (or is removed), so behavior is unambiguous. Confirm.

5. **`hudCornerRadius` home.** Both new HUD modules need it. **Recommended:** move it to `FloydRender` (global visual settings) rather than leaving it in `FloydHud` and creating a cross-module dependency. Confirm.

6. **Color editing UX for the searchable list — right-click modal vs inline row?** A right-click color-picker modal may fight the `Panel` scissor/input handling. **Recommended:** start with an inline expand-row color editor (no new modal layer); add a true modal only if a modal system already exists. Confirm acceptable.

7. **Do MobEsp/Xray/BlockSearch have `/fa` command handlers today?** The redesign must preserve them. **Recommended:** I audit `MainCommand.kt` for these before deleting any module setting, and keep the underlying add/remove APIs public. Confirm you want command parity preserved exactly (not slimmed).

## 5. Adversarial risks

- **Config-migration silent data loss (highest).** Moving scoreboard/inventory settings out of `hud` and legacy styling out of `clickgui` orphans existing user keys. Without the item-6 remap, every upgrading user loses customizations on first load. Migration must run *before* any module reads its settings, and must save immediately to re-persist under new keys. A user who downgrades after upgrading will have keys the old build can't read.
- **Two-GUI parity drift.** `LegacyFloydClickGUI` is hand-rolled (6,999 lines) and does *not* auto-discover modules the way the Odin `ClickGUI` does via `hudSettingsCache`. Every module split (HUD into three, ClickGUI into two) must be manually mirrored there, or settings silently vanish from the legacy GUI. This is the most error-prone surface.
- **Legacy color settings may be dead code.** Lines ~6694–6699 only *detect* chroma state; the actual color-rendering hookup is unconfirmed. If `buttonTextColor`/`buttonBorderColor`/`guiBorderColor` are never read for rendering, extracting them into `LegacyClickGUIModule` preserves dead code. Verify rendering actually consumes them before treating the extraction as functional.
- **Icon loading frame-stutter.** Eagerly building ~1000 block + entity icons stutters the game. Must be lazy on first GUI open with graceful gray-box fallback; never block render. Coordinate-system mismatch between `BlockAtlas`/entity textures and `NVGRenderer.image` makes atlas-subregion extraction nontrivial — loading PNGs from `/assets` is the safer path.
- **Search performance over full registries.** Substring-matching ~1000 blocks/entities against both ID and friendly name on every keystroke can lag. Precompute the ID→friendly-name/token map at module init (not in the render loop); debounce input if needed.
- **Modifier-key detection.** Alt/right-click for "Add Looked-At" and color-edit depends on the mouse event exposing modifier state. If it doesn't, the feature must read `GLFW_KEY_LEFT_ALT` separately or fall back to a different affordance — do not assume the API.
- **Action-row vs scroll click-stealing.** The "Pick from World" / "Clear All" bar must sit *outside* the scroll viewport; otherwise scrolling over it fires button clicks. `Panel.handleScroll` delegates to children first, so placement matters.
- **`estimatedHudSize()` hardcoding becomes a runtime gap.** The moment HUDs leave `FloydHud`, the line 171–178 name switch returns wrong/default sizes for the moved HUDs until item 7's registry lands — sequence item 7 with item 2, not after.
- **Hidden-but-accessible settings still violate parity.** Keeping `nameFilters`/`typeFilters`/`opaqueBlocks`/`searchBlocks` `.hide()`'d as backing storage is fine *only if* the `ExtendedSearchableListSetting` wrapper renders them; if the wrapper fails to render, the data is reachable via command but invisible in GUI. Unmask/wrap in the same commit.
- **Cross-module dependency creep.** If `hudCornerRadius` stays in `FloydHud`, the two new HUD modules import `FloydHud`, coupling supposedly-independent modules. The `FloydRender` relocation avoids this.
- **State-API consumers break.** Anything reading `FloydHud.state()` for inventory/scoreboard details (telemetry/monitoring) must be repointed to query `FloydHud`, `FloydCustomScoreboard`, and `FloydInventoryHud` independently after the split.
- **Command namespace collisions.** Any new scripting commands (`/fa color`, `/fa hud`, `/fa nick`) may collide with existing aliases or user scripts — audit `MainCommand.kt` first; accept both old and new forms for a version if muscle memory is at stake.