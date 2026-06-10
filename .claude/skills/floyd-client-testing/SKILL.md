---
name: floyd-client-testing
description: Build, deploy, launch, drive, and visually verify the FloydAddons Fabric mod in a real Minecraft client via the FloydLocalControl HTTP bridge — Prism instance management, account selection, singleplayer worlds, screenshots, game-state assertions. Use whenever a FloydAddons change needs live-client verification (the project standard - NEVER claim a feature done without exercising it in a real client).
---

# FloydAddons live-client testing loop

Verification standard (non-negotiable, from hard-won history): **a feature is not done
until it has been exercised in a live client via the bridge, with a screenshot and a
`/state` assertion proving the NEW jar is what's running.** Build-green is not done.

## Canonical targets

| What | Value |
|---|---|
| Repo (main, MC 1.21.11) | `/Users/twaldin/new-floyd-addons` |
| Jar | `build/libs/FloydAddons-2.2.0.jar` |
| Instance | `/Users/twaldin/Library/Application Support/PrismLauncher/instances/floyd pvp/minecraft` |
| Bridge | `http://127.0.0.1:38769` |
| Account | `timhotty` (pass `--profile timhotty` — launcher default may be the bazaar alt `rabbi1337`!) |
| Token | read fresh each run from `<instance>/config/floydaddons/control-bridge.json` (regenerated on bridge start; never hardcode) |

Port map (do not guess): `floyd pvp`=38769 · Loom `runClient` from repo=38765 ·
`FloydAgent Harness`=38766 · `floyd new`=38767. **38765 is a booby trap** — a stale dev
client from another branch/agent may hold it; never kill it, never assert against it.
Other agents run Prism MC clients concurrently: **NEVER `pkill`/`killall java`** — only
kill the one PID you verified owns your port (`lsof -nP -iTCP:38769 -sTCP:LISTEN -t`,
confirm instance via `lsof -p <pid> | grep cwd`).

## The loop (one iteration)

```bash
REPO=/Users/twaldin/new-floyd-addons
INST="/Users/twaldin/Library/Application Support/PrismLauncher/instances/floyd pvp/minecraft"
BRIDGE=http://127.0.0.1:38769

# 1 BUILD — read the literal BUILD SUCCESSFUL/FAILED
cd "$REPO" && ./gradlew build

# 2 DEPLOY (instance already provisioned — skip profile/deps reinstall)
FLOYDADDONS_SKIP_BUILD=true FLOYDADDONS_SKIP_FABRIC_PROFILE=true FLOYDADDONS_SKIP_RUNTIME_DEPS=true \
  "$REPO/scripts/install-built-jar.sh" "$INST/mods"

# 3 KILL only the verified old PID (busy-poll death; foreground sleep is BLOCKED in this harness)
OLD=$(lsof -nP -iTCP:38769 -sTCP:LISTEN -t 2>/dev/null || true)
[ -n "$OLD" ] && kill -9 "$OLD"; while [ -n "$OLD" ] && kill -0 "$OLD" 2>/dev/null; do :; done

# 4 LAUNCH (collides if old PID alive — hence step 3). TWO REAL TRAPS:
#  (a) a running Prism GUI (or stale `prismlauncher --launch` parent) intercepts the CLI
#      via QLocalSocket and may drop/mangle the launch — `osascript -e 'tell application
#      "Prism Launcher" to quit'` the idle GUI and `pkill -f "prismlauncher --launch"`
#      stale parents FIRST (the GUI process is safe to quit; never touch java).
#  (b) overlapping clients race the bridge port bind: the loser boots with a DEAD bridge
#      and hangs silently. One client at a time, always.
"/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher" --launch "floyd pvp" --profile timhotty &

# 5 WAIT for bridge — curl's retry engine paces without foreground sleep (~5 min budget)
curl -s --retry 100 --retry-delay 3 --retry-all-errors --max-time 2 "$BRIDGE/health" -o /dev/null && echo up

# 6 PROVE the new jar: scaffold version+mcVersion match, process newer than jar
TOKEN=$(python3 -c "import json;print(json.load(open('$INST/config/floydaddons/control-bridge.json'))['token'])")
curl -s -H "Authorization: Bearer $TOKEN" "$BRIDGE/state" | python3 -c "
import sys,json; s=json.load(sys.stdin)
assert s['scaffold']['version']=="2.2.0" and s['scaffold']['minecraftVersion']=='1.21.11', s['scaffold']"
# also: lsof PID start time must be NEWER than the jar mtime (stale-client trap)

# 7 WORLD: existing save
curl -s -X POST -H "Authorization: Bearer $TOKEN" -d '{"action":"openWorld","world":"New World"}' "$BRIDGE/action"
until curl -s -H "Authorization: Bearer $TOKEN" "$BRIDGE/state" | python3 -c "
import sys,json;s=json.load(sys.stdin);exit(0 if s.get('connected') and s['server']['singleplayer'] else 1)"; do :; done

# 8 SCREENSHOT → "$INST/screenshots/<name>.png" (FULL framebuffer res = retina 2x)
curl -s -X POST -H "Authorization: Bearer $TOKEN" -d '{"fileName":"check.png"}' "$BRIDGE/screenshot"
```

Then Read the PNG (crop with PIL at full res first if checking detail) and assert
`/state` fields. Judge by screenshot + state, not by logs.

## Bridge API (auth: `Authorization: Bearer <token>`; loopback only)

- `GET /health` (no auth), `GET /state` — connected, screen, fps, scaffold{version,minecraftVersion}, window{width,height,scaledWidth (guiScale = width/scaledWidth)}, server{singleplayer,multiplayer,address}, player{x,y,z,yaw,pitch,health}, world{dimension,time}, hotbar, nearbyEntities, modules
- `POST /chat {message}` (leading `/` = command; **`§` in chat on servers = kick**)
- `POST /look {yaw,pitch | deltaYaw,deltaPitch}` · `POST /hotbar {slot}`
- `POST /key {key:forward|back|left|right|jump|sneak|sprint|attack|use|tab, pressed|durationMs}`
- `POST /screen {screen:"clickgui"|"legacy"|"hud"|"close"}`
- `POST /mouse {event:move|click|down|up|drag|scroll, x,y,...}` — **default coord space = raw screenshot px** (auto-converted); `coordinateSpace:"gui"` for gui-scaled coords (= screenshot px ÷ devicePixelRatio; ÷2 on retina)
- `POST /type {text, clear?, submit?}` · `POST /screenshot {fileName}`
- `POST /action {action:...}`: `attack/use/swing/jump/sneak/sprint {durationMs}`, `connect {address}` (don't re-issue while connected), `openWorld {world}` (existing saves only), `setSetting {module,setting,value}` (Bool/Number), `setModuleEnabled {module,enabled}`, `camera {type}`, `fullscreen {enabled}`, `closeScreen`, `reloadConfig`, `reloadResources`
- `GET /entities` (tab-list/NPC signals) · `GET /iconcheck` (block-icon coverage)

Fresh worlds: `POST /action {"action":"createFreshWorld","world":"name","flat":true,"gamemode":"creative|survival","cheats":true,"seed":N}`
(added + live-verified 2026-06-09; requires being DISCONNECTED first, errors
`world_already_exists` on reuse — use unique names or `openWorld` to revisit).
`/state.player.gameMode` reports CREATIVE/SURVIVAL/etc. Creative-mode block breaks drop
no items (judge by block state, not inventory). `screen` names in production jars are
intermediary (`net.minecraft.class_442` = TitleScreen), not mojmap.

## GUI driving (ClickGUI/legacy over the bridge)

Open with `/screen`, screenshot, locate elements by their **white text** (chroma-cycled
accents defeat frame-diffs), send `/mouse` clicks in raw screenshot px. Left-click =
toggle, right-click = expand module settings. Re-screenshot to confirm. A 1400-line
worked example of driving every widget type: `scripts/verify-legacy-clickgui-runtime.py`.
Reusable `BridgeClient` + token auto-detect: `scripts/verify-live-hypixel-acquisition.py`.

## Alternative loop: Loom dev client

`cd $REPO && ./gradlew runClient` — offline dev account (or
`FLOYDADDONS_DEVAUTH=true FLOYDADDONS_DEVAUTH_ACCOUNT=main`), bridge on **38765** from
`$REPO/run/`, screenshots in `$REPO/run/screenshots/`. Only use when 38765 is free —
check who holds it first; a stale client on 38765 reporting the wrong
`scaffold.version` means someone else's branch is running, not yours.

## Failure modes that have actually happened

1. Claimed done off a stale client — scaffold-version assert is mandatory every run.
2. `prismlauncher --launch` while old PID alive → silent collision; kill+confirm first.
3. Wrong account launched (rabbi1337 default) → wrong server identity/cookies.
4. Foreground `sleep` → harness block; busy-poll only.
5. NVG/GL calls from static-init or config-load → `No GLCapabilities` boot crash; defer GL to render time.
6. Mixin `@At` target moved → with `defaultRequire: 1` it fails loudly at launch; read the crash, don't bisect blind.
7. `./gradlew test` forks its own JVM — safe to run; never broad-kill java to "clean up".
