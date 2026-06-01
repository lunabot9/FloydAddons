# FloydAddons v2.1 — session handoff (updated 2026-05-31, session 3)

Worktree **`/Users/twaldin/floyd-pvp-modules`** (branch `feature/v2.1`, pkg `gg.floyd`, MC 1.21.11, Mojmap). Session cwd is the DIFFERENT old worktree `/Users/twaldin/new-floyd-addons` — IGNORE it; always use absolute paths under `floyd-pvp-modules`.

## Build / deploy / verify loop
- **Build:** `cd /Users/twaldin/floyd-pvp-modules && ./gradlew build` — read the literal `BUILD SUCCESSFUL`/`FAILED`. Jar: `build/libs/FloydAddons-2.1.0.jar`.
- **Deploy:** copy jar to `"/Users/twaldin/Library/Application Support/PrismLauncher/instances/floyd pvp/minecraft/mods/FloydAddons-2.1.0.jar"`.
- **Restart:** `kill -9` the `floyd pvp` java pid, confirm dead (busy-poll `kill -0`, NO `sleep`), then `"/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher" --launch "floyd pvp"` as a simple background command. Don't touch other instances.
- **Bridge** (FloydLocalControl): `http://127.0.0.1:38769`, header `Authorization: Bearer hJ876CyAkshrtTzHRgIHwaFeV8L7NQYm_RuntyL_4WU`. `GET /state`; `POST /screen {screen:"clickgui|legacy|hud|close"}`; `POST /action {action:"connect","address":...}` or `{action:"openWorld","world":"<saveFolder>"}`; `POST /look {pitch,yaw}`; `POST /screenshot` → `minecraft/screenshots/floyd-control.png`. **NEW debug:** `GET /iconcheck` (block-icon coverage), `GET /entities` (nearby entities + tab-list signals for the NPC-filter heuristic).
- **Verify:** drive the real client, screenshot, crop FULL-RES with PIL, dump `/state`, assert each feature.

## HARNESS GOTCHAS (heed)
- **Foreground `sleep` is BLOCKED** — never use it; busy-poll with `kill -0` / curl-retry loops.
- `prismlauncher --launch` collides if the old java pid is alive — KILL + confirm dead first, then launch; verify the NEW jar loaded (process start time > jar mtime).
- Don't re-issue `/action connect` if already connected (donutsmp refuses rapid reconnect).
- ALWAYS pin every workflow/Agent to `/Users/twaldin/floyd-pvp-modules`.
- Workflow `agent({schema})` sometimes fails to emit StructuredOutput — fall back to reading the code yourself.

## ANTI-REGRESSION RULES (these exact bugs happened)
- NEVER call NVGRenderer/RenderSystem/GL/nvgCreate from static-init, module init, `ModuleConfig.load`/`Setting.read`, or a Setting value-setter → `No GLCapabilities` boot crash. Defer GL to render time.
- NEVER add a per-frame reset of a HUD render signal in `Gui.render` — races the HUD pass (custom-scoreboard regression).
- Block Search match index/render are capped (MAX_INDEXED 100k / MAX_RENDERED 10k nearest).
- **Custom GUI RenderPipeline samplers:** the GLSL `uniform sampler2D` name must EXACTLY equal the `.withSampler("X")` string (no `+Sampler` suffix for code-built GUI pipelines). GUI convention = `Sampler0`. Mismatch → "shader program does not use sampler X" warning + the sampler reads black.

## DONE + verified this session (session 3; newest first, all build green on feature/v2.1)
- **Real per-panel blur (HEADLINE — verified in-client).** Replaced the darken-tint frosted fallback with a genuine framebuffer blur. `CustomRenderPipelines.PIPELINE_PANEL_BLUR` (GUI round-rect + `Sampler0`) + `PanelBlurPIPRenderer` mirror RoundRectPIPRenderer's command-encoder/Std140 UBO pattern but bind the main render target's color texture and box/gaussian blur it inside the rounded mask, writing to the PIP's own offscreen texture (sample main / write elsewhere = no feedback loop). `HudPanel.fillPanel` submits it under the translucent fill+border. Strength 0..20 → radius 0..8 (×0.4, sampled step 2). `FloydPanelStyle.panelBlurIsBox()` selects kernel. Shaders `core/panel_blur.vsh/.fsh` + `pipeline/panel_blur.json`. Per-frame UBO reset in RenderUtils. **Verified:** panel backdrop blurs the real world (green over foliage, blue over sky — correct X+Y mapping), rounded, tinted, no GL errors. Commits 71e2d58 + f2e7751 (sampler fix).
- **Block-icon coverage → 0 missing (verified via `/iconcheck`: 0 of 1163).** `BlockIconCache`: keep texture category prefix so `item/` textures (doors, signs, hanging signs) load; `tryResolve` wraps each resolver step (builtin/entity item JSON nests an object under `model` → ClassCastException was aborting before the fallback); `resolveSpecialTexture` fallback (beds/banners → dye wool, chests/pots/skulls → entity texture); only the picked candidate whose png actually EXISTS is used. Air variants excluded from BlockSearch/Xray lists via `BlockIconCache.invisibleBlockIds`. New `debugResolvedPath` + `/iconcheck` bridge endpoint. Commits 3e3e3a0, f638ea1, 29c4885.
- **Community links (verified rendering).** Legacy hub bottom: "Join the Floyd Addons Community" + clickable `github`→github.com/lunabot9/FloydAddons and `.gg/FLOYD`→discord.gg/FLOYD (Desktop.browse, click bounds, hover, lifted up so both lines clear the panel bottom). Same line below the Odin ClickGUI search bar (`drawCommunity` + `hitCommunityLink`). Commit 1b54a24.
- **Misc cross-platform (committed; needs user visual confirm with Discord app running).** Discord: native `discord-rpc` ships no macOS-arm64 dylib → `UnsatisfiedLinkError`; added pure-Java `DiscordIpcClient` (little-endian discord-ipc-N AF_UNIX, Gson-escaped activity JSON) on its own daemon thread; `FloydDiscordPresence` tries native then IPC. Dock icon: GLFW icons are a no-op on macOS → set via `java.awt.Taskbar.setIconImage` on a daemon thread, guarded. Win/Linux keep native dll + GLFW icon. Commit 063f033.
- **Player ESP fake-NPC filter (build green; empirical donutsmp validation pending).** Shared `RealPlayerFilter` (entity name must be a valid username in the tab list — mirrors MobEsp's proven heuristic), behind "Real Players Only" toggle (default on). `/entities` debug endpoint dumps tab-list signals. Commit 66d21f2.
- **Font module + shared cosmetics dir:** already complete (verified) — FloydFont mirrors the cosmetic picker; `CosmeticImages` shares one `images/` dir seeded with default cape + cone, migrates legacy dirs.

## CAPSTONE perf audit — DONE (run `wf_28c399aa-e19`, full output saved). Applied the high-value SAFE fixes (commit 649f0af):
- **Config-migration retargets** (fixes the silently-failing original-Java config.json import): General→Window, Camera→Freecam/Freelook/F5 Customizer, HUD→Inventory HUD/Custom Scoreboard. ("Hiders" still exists — those routes were already correct.)
- **FloydMobEsp.chromaColor → ChromaCache** (same 4000ms cycle; per-entity loop now memoized).
- **Panel blur early-exit** (skip <0.5px blur / <~45x45px panels).
- NOTE: the audit's "textureIsReadyToBlit caching" for the blur was REJECTED — it would freeze the backdrop to a stale frame when a panel is static but the world moves (the blur output depends on the live framebuffer, unlike round_rect). The blur MUST re-render per frame.

## REMAINING (deferred follow-ups — none block the primary work, all build-green-verified safe to do)
1. **Separable two-pass blur** (the real blur perf win, audit `audit:blur-cost`): convert `panel_blur.fsh`'s O(r²) 2D kernel to two O(r) passes (horizontal→intermediate target→vertical). Needs a 2nd pipeline + intermediate TextureTarget + visual re-verify. Medium risk. Current single-pass works fine for a few small panels.
2. **Double-toggle collapse** (6 modules' inner "Enabled" → module toggle; audit `audit:config-toggles` has per-module blueprints): Discord/LocalControl/Cape/ConeHat are SAFE+behavior-preserving (Cape/ConeHat also need config-migration lines 209/211 changed from set(...,"Enabled") to setModuleEnabled). Xray is safe but touches toggleXray(). **NickHider was flagged behaviorPreserving:false** — its inner flag has distinct semantics; needs care.
3. **Legacy-GUI micro-opts** (audit `audit:sweep`, all cold-ish GUI-open paths, all SAFE): cache panelWidth/Height per render; cache row-label lambda results; precompute slider-spec lookup maps; hoist USERNAME_PATTERN regex; cache page-title/scroll-indicator strings. Low individual impact; LegacyFloydClickGUI is ~7000 lines so touch carefully.
4. **Other safe micro-opts:** FloydPlayerEsp hoist overhead border color out of the player loop; FloydMobEsp WeakHashMap entity-type-id cache (medium); entitiesPayload + parseHexColor regex-per-call hoist (cold paths).
5. **Legacy hub central-label routing:** CODE VERIFIED CORRECT (openLabel maps each label to its own page). Just needs an in-client click-test to fully close.
6. **Mob filter empirical validation:** connect to donutsmp, `GET /entities`, confirm command-hologram NPCs show `inTabByName:false`/`validNamePattern:false`; refine the username pattern if numeric-name NPCs slip through.
7. **Misc visual confirm:** Discord presence (needs the Discord app running on the user's Mac) + dock icon — confirm visually; the code is in + safe.

## Config note
For blur testing I temporarily moved Inventory HUD to (80,80) scale 2.0; RESTORED to the user's (2723,14, scale 4.8). Panel Blur left ON (strength 17, Gaussian) per the user's saved config.
