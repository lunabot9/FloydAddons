---
name: floyd-client-testing
description: Build, deploy, launch, drive, and visually verify FloydAddons in a real Minecraft client via the FloydLocalControl HTTP bridge — Prism instance management, account selection, singleplayer worlds, screenshots, game-state assertions. Use whenever a FloydAddons change needs live-client verification (the project standard — NEVER claim a feature done without exercising it in a real client). All machine-specific config (paths, ports, accounts, launcher commands) comes from local.md in this skill directory.
---

# FloydAddons live-client testing loop

## Machine-local config — read FIRST

1. Read `local.md` in this skill directory (`.claude/skills/floyd-client-testing/local.md`) before anything else.
2. If `local.md` does not exist OR still contains the string `<replace-me`: **STOP**. Follow `SETUP.md` (same directory) to provision this machine and create/complete it (`grep -q '<replace-me' local.md` is the one-line gate). `SETUP.md` and `local.example.md` are tracked in git — if they are missing, the clone is broken: `git pull` first.
3. **NEVER guess machine values** — paths, ports, instance names, account names, launcher commands. A guessed port or account means asserting against (or killing) someone else's client.
4. Every `local:<key>` reference below resolves to a key in `local.md`.

## Verification standard (non-negotiable)

A feature is **not done** until it has been exercised in a live client via the bridge,
with a screenshot AND a `/state` assertion proving the NEW jar is what's running.
Build-green is not done. The scaffold-version assert is mandatory **every run**.

## Canonical targets

| What | Where |
|---|---|
| Repo | `local:repoPath` |
| Jar | derive from `gradle.properties`: `build/libs/<archives_base_name>-<mod_version>.jar` — never hardcode the version |
| Instance dir | `local:instanceDir` (mods in `local:modsDir`) |
| Bridge | `local:bridgeUrl` (port `local:bridgePort`) |
| Account | `local:account` — pass the launcher profile flag; the launcher default may be a different alt (`local:altAccount`), which = wrong server identity/cookies |
| Token | read fresh each run from `local:tokenPath` (= `<instance>/config/floydaddons/control-bridge.json`); the file is rewritten on every bridge start and the token rotates if the file is deleted/blank — never hardcode |

Port discipline (project-generic facts):

- The bridge port is the in-GUI "Local Control" module `Port` setting, persisted per
  instance in `<instance>/config/floydaddons/floydaddons-config.json` (code default
  38769 for fresh instances). The `port` field in `control-bridge.json` is overwritten
  from the GUI setting on every start — only `enabled` and `token` are read from it.
- `GET /` and `GET /health` are unauthenticated and return the port, the absolute
  `settingsPath` (token-file location), and the endpoint list — use `/health` to
  discover/confirm which instance owns a port. Bridge binds loopback only.
- Consult `local:portMap*` before touching ANY port. If a port reports the wrong
  `scaffold.version`, that is someone else's client/branch — do not kill it, do not
  assert against it.
- Shared machine rule: other agents may run Minecraft clients concurrently. **NEVER
  `pkill`/`killall java`.** Only kill the one PID verified to own YOUR port
  (`local:portCheckCommand`), after confirming its instance dir
  (`local:pidInstanceConfirmCommand`).
- One client per bridge port at a time: overlapping clients race the port bind and the
  loser boots with a DEAD bridge and hangs silently.

## The loop (one iteration)

```bash
REPO=<local:repoPath>; INST=<local:instanceDir>; BRIDGE=<local:bridgeUrl>

# 0 DERIVE jar identity from gradle.properties (canonical source; never hardcode)
MOD_VERSION=$(sed -n 's/^mod_version=//p' "$REPO/gradle.properties")
MC_VERSION=$(sed -n 's/^minecraft_version=//p' "$REPO/gradle.properties")
BASE=$(sed -n 's/^archives_base_name=//p' "$REPO/gradle.properties")
JAR="$REPO/build/libs/${BASE}-${MOD_VERSION}.jar"

# 1 BUILD — read the literal BUILD SUCCESSFUL/FAILED
cd "$REPO" && ./gradlew build

# 2 DEPLOY (already-provisioned instance — skip profile/deps reinstall)
FLOYDADDONS_SKIP_BUILD=true FLOYDADDONS_SKIP_FABRIC_PROFILE=true FLOYDADDONS_SKIP_RUNTIME_DEPS=true \
  "$REPO/scripts/install-built-jar.sh" "$INST/mods"   # target MUST end in /mods

# 3 KILL only the verified old PID on YOUR port (busy-poll death; foreground sleep is
#   BLOCKED in the agent harness). Port-check + instance-confirm commands: local.md.
OLD=$(<local:portCheckCommand> 2>/dev/null || true)
[ -n "$OLD" ] && kill -9 "$OLD"; while [ -n "$OLD" ] && kill -0 "$OLD" 2>/dev/null; do :; done

# 4 LAUNCH (collides if old PID alive — hence step 3). TWO REAL TRAPS:
#  (a) a running launcher GUI (or a stale CLI-launch parent process) intercepts the CLI
#      launch (Prism uses a QLocalSocket) and may drop/mangle it — quit the idle GUI
#      with local:guiQuitCommand and kill stale launch parents with
#      local:staleLaunchParentKillCommand FIRST (the GUI process is safe to quit;
#      never touch java).
#  (b) overlapping clients race the bridge port bind: the loser boots with a DEAD
#      bridge and hangs silently. One client at a time, always.
<local:launchCommand>   # includes the instance name and --profile <local:account>

# 5 WAIT for bridge — curl's retry engine paces without foreground sleep (~5 min budget)
curl -s --retry 100 --retry-delay 3 --retry-all-errors --max-time 2 "$BRIDGE/health" -o /dev/null && echo up

# 6 PROVE the new jar: scaffold matches gradle.properties AND process is newer than jar
TOKEN=$(python3 -c "import json;print(json.load(open('$INST/config/floydaddons/control-bridge.json'))['token'])")
curl -s -H "Authorization: Bearer $TOKEN" "$BRIDGE/state" | \
  MV="$MOD_VERSION" MC="$MC_VERSION" python3 -c "
import sys,json,os; s=json.load(sys.stdin)
assert s['scaffold']['version']==os.environ['MV'] and s['scaffold']['minecraftVersion']==os.environ['MC'], s['scaffold']"
# also: the client PID start time must be NEWER than the jar mtime (stale-client trap).
#   lsof does NOT report start times — use local:pidStartTimeCommand:
#   ps -p <pid> -o lstart= (macOS/Linux), vs jar mtime stat -f %Sm (macOS) / stat -c %y (Linux)

# 7 WORLD: existing save (local:existingWorldName); poll for the ACTUAL condition
curl -s -X POST -H "Authorization: Bearer $TOKEN" -d '{"action":"openWorld","world":"<local:existingWorldName>"}' "$BRIDGE/action"
until curl -s -H "Authorization: Bearer $TOKEN" "$BRIDGE/state" | python3 -c "
import sys,json;s=json.load(sys.stdin);exit(0 if s.get('connected') and s['server']['singleplayer'] else 1)"; do :; done

# 8 SCREENSHOT — response JSON contains the ABSOLUTE saved path; full framebuffer
#   resolution (= window px x local:devicePixelRatio)
curl -s -X POST -H "Authorization: Bearer $TOKEN" -d '{"fileName":"check.png"}' "$BRIDGE/screenshot"
```

Then Read the PNG (crop with PIL at full resolution first when checking detail) and
assert `/state` fields. **Judge by screenshot + state, not by logs.** Screenshot after
every prepare command — some failures (e.g. command parse errors) are only visible in
chat while the API returns ok.

`scaffold.version` is read at runtime from the loaded jar's Fabric mod metadata —
deliberately, as the stale-client detector. Note: `FloydAddonsMod.MOD_VERSION` is a
duplicated hardcode used as fallback; bump it alongside `gradle.properties`.

## Bridge API (loopback only)

Auth, three equivalent forms: `Authorization: Bearer <token>` header,
`X-FloydAddons-Token: <token>` header, or `?token=<token>` query param.
`GET /` and `GET /health` need no auth. Request bodies capped at 8192 bytes.

- `GET /state` — connected, screen, fps, scaffold{version,minecraftVersion}, window{width,height,scaledWidth (guiScale = width/scaledWidth)}, server{singleplayer,multiplayer,address}, player{x,y,z,yaw,pitch,health,gameMode}, world{dimension,time}, hotbar, nearbyEntities, modules
- `POST /chat {message}` — leading `/` runs a command; **`§` in chat on servers = kick**
- `POST /look {yaw,pitch | deltaYaw,deltaPitch}` · `POST /hotbar {slot}`
- `POST /key {key:forward|back|left|right|jump|sneak|sprint|attack|use|tab, pressed|durationMs}`
- `POST /screen {screen:"clickgui"|"legacy"|"hud"|"pause"|"options"|"close"}` (`pause`/`options` open the vanilla in-game Game Menu / Options — the only way to drive in-game menu screens, since ESC is not a `/key` keybind)
- `POST /mouse {event:move|click|down|up|drag|scroll, x,y,...}` — **default coord space = raw screenshot px** (auto-converted); pass `coordinateSpace:"gui"` for gui-scaled coords (= screenshot px ÷ `local:devicePixelRatio`)
- `POST /type {text, clear?, submit?}` · `POST /replace-text {...}`
- `POST /screenshot {fileName}` — must end `.png`, no path separators/`..`; saved to `<gameDir>/screenshots/<fileName>`; response includes the absolute `path` (never compute it yourself); 5 s timeout → `screenshot_timeout`
- `POST /action {action:...}`: `attack/use/swing/jump/sneak/sprint {durationMs}`, `connect {address}` (don't re-issue while connected — disconnect first and confirm via state polling), `disconnect`, `openWorld {world}` (existing saves only), `setSetting {module,setting,value}` (Bool/Number), `setModuleEnabled {module,enabled}`, `camera {type}`, `fullscreen {enabled}`, `closeScreen`, `reloadConfig`, `reloadResources`
- `GET /entities` (tab-list/NPC signals) · `GET /iconcheck` (block-icon coverage) · `GET /fontdebug` · `GET /perf` (see below)

Fresh worlds: `POST /action {"action":"createFreshWorld","world":"name","flat":true,"gamemode":"creative|survival","cheats":true,"seed":N}`
(live-verified 2026-06-09; requires being DISCONNECTED first; errors
`world_already_exists` on name reuse — use unique names, or `openWorld` to revisit).
`/state.player.gameMode` reports CREATIVE/SURVIVAL/etc. **Creative-mode block breaks
drop NO items — judge by block state, not inventory.** `screen` names in production
jars are intermediary mappings (`net.minecraft.class_442` = TitleScreen), not mojmap.

## GUI driving (ClickGUI/legacy over the bridge)

Open with `/screen`, screenshot, locate elements by their **white text** (chroma-cycled
accents defeat frame-diffs), send `/mouse` clicks in raw screenshot px, re-screenshot to
confirm. Left-click = toggle, right-click = expand module settings. Worked example of
driving every widget type (~1400 lines): `scripts/verify-legacy-clickgui-runtime.py`.
Reusable `BridgeClient` + token auto-detect: `scripts/verify-live-hypixel-acquisition.py`
(paths relative to `local:repoPath`).

## Alternative loop: Loom dev client

`cd <local:repoPath> && ./gradlew runClient` — offline dev account, or
`FLOYDADDONS_DEVAUTH=true FLOYDADDONS_DEVAUTH_ACCOUNT=<local:devAuthAccount>`. Its
bridge serves from `local:loomRunDir` on `local:loomBridgePort` (the run dir persists
its own Port setting in `run/config/floydaddons/floydaddons-config.json`); screenshots
in `local:loomScreenshotsDir`. Only use when that port is free — check who holds it
first; a client there reporting the wrong `scaffold.version` means someone else's
branch is running, not yours. Never kill it, never assert against it.

## Perf measurement (GET /perf)

`GET /perf?seconds=N` (1-120) samples N s of frame times at the Minecraft.runTick
boundary: `{frames, fps, frameMs{p50,p95,p99,max,min,avg},
alloc{renderThreadBytesPerSecond, bytesPerFrame}, gc, counters}`. Add `&sections=1` for
per-feature INCLUSIVE sections (EventBus listeners + PostHud/ClickGUI/BlockSearch
wraps; **parents nest children — never sum PostHud.total with PostHud.\*, or
ClickGUI.nvgPip with ClickGUI.textReplay**). Section p50/p95/p99 are log-bucket upper
bounds (up to ~9% high); max/totals are exact.

Blocking call (up to seconds+15); one window at a time (`perf_busy`). **Do not hit
other client-thread endpoints (e.g. /state) during a window** — they steal
render-thread time and perturb the numbers. Headline A/B numbers must come from
NO-sections windows (probes add overhead). Keep the client window focus state constant
across A/B (an unfocused window throttles). `options.txt` for measurement:
`enableVsync:false`, `maxFps:260` (the key is `maxFps`, NOT `framerateLimit`).

Tooling (relative to `local:repoPath`): `scripts/perf-protocol.py` (A/B toggle
protocol, 3 repeats, delta vs spread), `scripts/perf-arenas.py` (saved stress worlds —
names in `local:perfArenaWorlds`), `scripts/perf-baseline.py` (full table).

## Failure modes that have actually happened

1. Claimed done off a stale client — the scaffold-version assert (vs gradle.properties)
   is mandatory every run; also check client PID start time newer than jar mtime.
2. CLI launch while the old PID was still alive → silent collision. Kill the verified
   PID, busy-poll until dead, THEN launch.
3. Wrong account launched (launcher default was the alt `local:altAccount`) → wrong
   server identity/cookies. Always pass the profile flag with `local:account`
   (baked into `local:launchCommand`).
4. Foreground `sleep` → agent-harness block. Busy-poll with until-loops; pace long
   waits with curl's retry engine against `/health` (see loop step 5 /
   `local:bridgeWaitCommand`).
5. NVG/GL calls from static-init or config-load → `No GLCapabilities` boot crash.
   Defer all GL work to render time.
6. Mixin `@At` target moved → with `defaultRequire: 1` it fails LOUDLY at launch.
   Read the crash, don't bisect blind.
7. `./gradlew test` forks its own JVM — safe to run; never broad-kill java to
   "clean up" (shared machine — other agents' clients).
8. `disconnectWithSavingScreen()` alone (without `level.disconnect` first)
   **deadlocks the client on the "Saving world" screen** — use
   `mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE)` (the exact pause-menu
   quit flow). The bridge's `{"action":"disconnect"}` does this; poll `/state` until
   `connected=false`.
9. Brigadier word-args **reject ":"** — `/fa blocksearch minecraft:diamond_ore` fails
   with "Expected whitespace to end one argument" and the failure is only visible in
   chat (the bridge `/chat` still returns ok). Use bare ids (`diamond_ore`, `husk`);
   they normalize via `Identifier.tryParse`. Screenshot after every prepare command to
   catch this class of bug.
10. `setModuleEnabled`/`setSetting` look up `ModuleManager.modules` keyed by
    `name.lowercase()` — `"X-Ray"` → `module_not_found`; send `"x-ray"`.
11. **Time Changer module overrides client time** — clock-dial/daylight visual tests
    are invalid while it's enabled (`setModuleEnabled "time changer" false` first).
    `/state` `world.time` reflects the override, while `/time query daytime` in chat
    shows server truth.
12. ClickGUI driving: right-click on a panel HEADER collapses the whole panel;
    right-click on a module ROW expands its settings. Module-settings expansion is
    runtime-only (relaunch resets); panel positions persist. The header hitbox extends
    over the first ~module-row.
13. Long measurement sessions (~1h) make HUD-arena tail percentiles noisy (GC debt/thermal; p95
    spreads 5-10 ms in BOTH A and B windows) — clean per-feature A/Bs right after a
    fresh relaunch are the canonical numbers; treat marathon re-baselines only as
    ranking confirmation.
14. `/state` render.batch "queued" counts read 0 between frames (callClient lands
    after the per-frame clear) — assert on `render.batch.lastFlushed` instead
    (published at flush time).
