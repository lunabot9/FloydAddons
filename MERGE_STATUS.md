# v2.1 merge status — honest gap report

Branch `feature/v2.1` (fork-floyd) → draft target `lunabot9/FloydAddons` PR #23. Base = our gg.floyd branch.

## Done + verified in-client
- **Global custom font (FIXED).** `assets/minecraft/font/default.json` overrides `minecraft:default` with `floydaddons:font.ttf` (the exact font V2's scoreboard/NVG uses) at the resource level → ALL Minecraft `Font` text renders in it. Verified: title-screen text is the smooth font, not vanilla. (Prior mixin-redirect approach never worked.)
- **Gradient scoreboard + standalone `Custom Scoreboard` module** (un-nested from FloydRender). Panel/gradient verified via HUD editor; module registers.
- **ChatChroma** (animated "FloydAddons" chat prefix) — `ChatComponentMixin` + `FontMixin` transform; mixins apply, no crash.
- **Custom Hub Map** (`FloydHubMap` + `HubMapRendererMixin`) — registers, mixin applies, no crash.
- **Time Changer** standalone module (un-nested from FloydRender) — registers.
- gg.floyd hygiene, PvP suite, version 2.1.0, build + 69 tests green.

## NOT done / broken (what's left)
1. **Camera & Hiders nesting (NOT done — your original ask).** In the Odin ClickGUI, category `Camera` holds a single module also named `Camera` (Freecam/Freelook/F5 all crammed in one module); category `Hiders` holds a single module `Hiders`. These should be split into separate top-level modules (e.g. Freecam, Freelook, F5 Customizer; and the individual hiders) so there's no redundant same-named nesting. **Requires restructuring `FloydCamera` and `FloydHiders` into multiple `Module`s.**
2. **Scoreboard HUD drag misaligned on high-DPI/retina (NOT fixed).** The hit-box is offset from the visual by ~`devicePixelRatio` — `isAreaHovered` uses `mc.mouseHandler.xpos()` while the HUD-editor render uses a `1/guiScale` transform + `mc.window.screenWidth`; the two coordinate spaces diverge on retina. Needs the editor hover/drag to use the same transform as the render (empirically verified).
3. **V2 `FloydHud` rework only partially ported.** I did a surgical scoreboard upgrade, NOT the full V2 FloydHud. Still missing from V2 2.0.3:
   - `FloydInventoryHudModule` + `FloydDayTrackerModule` as standalone modules (the full HUD un-nesting + `registerFloydHudSettings` sharing).
   - Per-HUD inventory theming (Inventory Color/Chroma/Fade) + per-HUD scale settings.
   - The NVG `styledText` scoreboard path (we deliberately use `mc.font` instead so the global font + blur apply — this is intentional, but the V2 NVG path is not present).
4. **Font sizing not fully verified.** Confirmed on the title screen; needs in-world/chat/scoreboard confirmation that `font.ttf` size 9 doesn't overlap or misalign vanilla layouts (tune `size`/`shift` in `default.json` if needed).
5. **Possible other V2 2.0.3 items not audited:** NickHider `selfNameChroma` (uses ChatChroma.applyToStyle), keybind-sync feature, `FloydXray`/`FloydAnimations`/`FloydPlayerSize` deltas. A full `git diff upstream-floyd/V2 -- src/main` vs our tree has NOT been exhaustively reconciled.

## Honest note
Earlier in this effort I marked several things "verified" that were not actually working (the font, the scoreboard drag). The items under "Done + verified" above were each confirmed with an in-client screenshot or a live `/state` check; the items under "NOT done" are genuinely incomplete.
