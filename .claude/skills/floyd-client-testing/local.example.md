# local.md template — machine-local values for floyd-client-testing

Copy to `local.md` (same directory, gitignored) and fill EVERY key. SKILL.md refers to
these as `local:<key>`. Do not leave `<replace-me>` placeholders behind. Commands are
recorded verbatim so agents run them, not reinvent them.

## Repo + jar

- `repoPath`: `<replace-me — absolute path to the FloydAddons repo clone>`
- `repoBranch`: `<optional — canonical live-testing branch of this clone, e.g. main>`
- `jarVersion`: `<derived — mod_version from gradle.properties; gradle.properties is AUTHORITATIVE, this is a convenience copy. Update on bump or just re-derive>`
- `minecraftVersion`: `<derived — minecraft_version from gradle.properties; same caveat>`
- `jarName`: `<derived — <archives_base_name>-<mod_version>.jar>`
- `jarRelPath`: `<derived — build/libs/<jarName>>`
- `installScript`: `<repoPath>/scripts/install-built-jar.sh`
- `deployEnvFlags`: `FLOYDADDONS_SKIP_BUILD=true FLOYDADDONS_SKIP_FABRIC_PROFILE=true FLOYDADDONS_SKIP_RUNTIME_DEPS=true` (already-provisioned instance)

## Canonical instance + launcher

- `instanceName`: `<replace-me — Prism instance name>`
- `instanceDir`: `<replace-me — absolute path to <Prism data dir>/instances/<instanceName>/minecraft>`
- `modsDir`: `<instanceDir>/mods`
- `screenshotsDir`: `<instanceDir>/screenshots` (informational — /screenshot responses return the absolute path)
- `prismBinary`: `<replace-me — per-OS, see SETUP.md table>`
- `launchCommand`: `<replace-me — full CLI launch line incl. instance + --profile account>`
- `guiQuitCommand`: `<replace-me — per-OS command that quits the Prism GUI (NOT java)>`
- `staleLaunchParentKillCommand`: `<replace-me — kills stale CLI-launch parent processes>`

## Bridge

- `bridgePort`: `<replace-me — Local Control Port setting of THIS instance; code default 38769>`
- `bridgeUrl`: `http://127.0.0.1:<bridgePort>`
- `tokenRelPath`: `config/floydaddons/control-bridge.json`
- `tokenPath`: `<instanceDir>/config/floydaddons/control-bridge.json` (read fresh every run; never hardcode the token)
- `portCheckCommand`: `lsof -nP -iTCP:<bridgePort> -sTCP:LISTEN -t` (or your OS equivalent — Linux without lsof: `ss -ltnp | grep :<bridgePort>`; Windows: `Get-NetTCPConnection -LocalPort <bridgePort> -State Listen | % OwningProcess`)
- `pidInstanceConfirmCommand`: `lsof -p <pid> | grep cwd` (or your OS equivalent — Linux: `readlink /proc/<pid>/cwd`; Windows: `Get-Process -Id <pid> | select Path`) — confirm the PID's cwd is YOUR instanceDir before killing
- `pidStartTimeCommand`: `ps -p <pid> -o lstart=` (macOS/Linux — lsof does NOT report start times; compare against the jar mtime, `stat -f %Sm <jar>` macOS / `stat -c %y <jar>` Linux, for the stale-client check)
- `bridgeWaitCommand`: `curl -s --retry 100 --retry-delay 3 --retry-all-errors --max-time 2 <bridgeUrl>/health -o /dev/null`

## Accounts

- `account`: `<your-account — the Minecraft account/profile to launch with; ALWAYS pass --profile>`
- `altAccount`: `<optional — alt account(s) that must NOT be launched for testing (may be the launcher default!)>`
- `devAuthAccount`: `<optional — local devauth account alias for FLOYDADDONS_DEVAUTH_ACCOUNT>`
- `devAuthEnvFlags`: `FLOYDADDONS_DEVAUTH=true FLOYDADDONS_DEVAUTH_ACCOUNT=<devAuthAccount>`

## Display

- `devicePixelRatio`: `<replace-me — screenshot px width / /state window.width; 2 on retina macs, usually 1 elsewhere>`

## Worlds

- `existingWorldName`: `<replace-me — name of a known-good singleplayer save for openWorld>`
- `perfArenaWorlds`: `<optional — saved perf stress worlds managed by scripts/perf-arenas.py, if provisioned>`

## Port map (optional but strongly recommended on shared/multi-agent machines)

List EVERY local bridge port and its owner. Mark ports you must never kill or assert
against. Pattern: each concurrent agent/instance gets its own port.

- `portMap<port>`: `<who owns it — instance name / loom dev client / other agent — and any trap notes>`

## Loom dev client (optional)

- `loomBridgePort`: `<port persisted in <repoPath>/run/config/floydaddons/floydaddons-config.json>`
- `loomRunDir`: `<repoPath>/run`
- `loomScreenshotsDir`: `<repoPath>/run/screenshots`

## Reference scripts (repo-relative; record absolute for convenience)

- `clickguiWorkedExampleScript`: `<repoPath>/scripts/verify-legacy-clickgui-runtime.py`
- `bridgeClientScript`: `<repoPath>/scripts/verify-live-hypixel-acquisition.py`
- `perfProtocolScript`: `<repoPath>/scripts/perf-protocol.py`
- `perfArenasScript`: `<repoPath>/scripts/perf-arenas.py`
- `perfBaselineScript`: `<repoPath>/scripts/perf-baseline.py`

## Measurement settings

- `optionsTxtMeasurementSettings`: `enableVsync:false, maxFps:260` (key is `maxFps`, not `framerateLimit`)

## Shared-machine notes (optional)

- `sharedMachineNotes`: `<who else runs java/Minecraft here; kill policy; anything an agent must know before touching processes>`

---

# Worked example (sanitized, macOS, user `alice`)

- `repoPath`: `/Users/alice/floyd-addons`
- `repoBranch`: `main` (canonical live-testing branch of this clone)
- `jarVersion`: derived from gradle.properties (e.g. `2.2.0` at time of writing)
- `minecraftVersion`: derived from gradle.properties (e.g. `1.21.11`)
- `jarName`: `FloydAddons-<mod_version>.jar`
- `jarRelPath`: `build/libs/FloydAddons-<mod_version>.jar`
- `installScript`: `/Users/alice/floyd-addons/scripts/install-built-jar.sh`
- `deployEnvFlags`: `FLOYDADDONS_SKIP_BUILD=true FLOYDADDONS_SKIP_FABRIC_PROFILE=true FLOYDADDONS_SKIP_RUNTIME_DEPS=true`
- `instanceName`: `floyd test`
- `instanceDir`: `/Users/alice/Library/Application Support/PrismLauncher/instances/floyd test/minecraft`
- `modsDir`: `/Users/alice/Library/Application Support/PrismLauncher/instances/floyd test/minecraft/mods`
- `screenshotsDir`: `/Users/alice/Library/Application Support/PrismLauncher/instances/floyd test/minecraft/screenshots`
- `prismBinary`: `/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher`
- `launchCommand`: `"/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher" --launch "floyd test" --profile <your-account> &`
- `guiQuitCommand`: `osascript -e 'tell application "Prism Launcher" to quit'`
- `staleLaunchParentKillCommand`: `pkill -f "prismlauncher --launch"`
- `bridgePort`: `38769` (code default; this machine runs a single instance)
- `bridgeUrl`: `http://127.0.0.1:38769`
- `tokenRelPath`: `config/floydaddons/control-bridge.json`
- `tokenPath`: `/Users/alice/Library/Application Support/PrismLauncher/instances/floyd test/minecraft/config/floydaddons/control-bridge.json`
- `portCheckCommand`: `lsof -nP -iTCP:38769 -sTCP:LISTEN -t`
- `pidInstanceConfirmCommand`: `lsof -p <pid> | grep cwd`
- `pidStartTimeCommand`: `ps -p <pid> -o lstart=`
- `bridgeWaitCommand`: `curl -s --retry 100 --retry-delay 3 --retry-all-errors --max-time 2 http://127.0.0.1:38769/health -o /dev/null`
- `account`: `<your-account>`
- `altAccount`: none
- `devAuthAccount`: none configured
- `devAuthEnvFlags`: n/a
- `devicePixelRatio`: `2` (retina)
- `existingWorldName`: `New World`
- `perfArenaWorlds`: not provisioned
- `portMap38769`: `floyd test` instance (canonical live-testing target)
- `portMap38765`: loom `runClient` dev client from the repo (only when started)
- `loomBridgePort`: `38765`
- `loomRunDir`: `/Users/alice/floyd-addons/run`
- `loomScreenshotsDir`: `/Users/alice/floyd-addons/run/screenshots`
- `clickguiWorkedExampleScript`: `/Users/alice/floyd-addons/scripts/verify-legacy-clickgui-runtime.py`
- `bridgeClientScript`: `/Users/alice/floyd-addons/scripts/verify-live-hypixel-acquisition.py`
- `perfProtocolScript`: `/Users/alice/floyd-addons/scripts/perf-protocol.py`
- `perfArenasScript`: `/Users/alice/floyd-addons/scripts/perf-arenas.py`
- `perfBaselineScript`: `/Users/alice/floyd-addons/scripts/perf-baseline.py`
- `optionsTxtMeasurementSettings`: `enableVsync:false, maxFps:260`
- `sharedMachineNotes`: single-user machine; still never `pkill`/`killall java` — kill only the PID verified to own your port
