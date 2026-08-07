# local.md template — machine-local values for floyd-client-testing

Copy this file to `local.md` in the same directory and fill every key. Keep
`local.md` ignored because it contains machine-specific paths and account details.

## Repo and mode

- `repoPath`: `<replace-me — absolute repo path>`
- `repoBranch`: `<replace-me — branch used for live testing>`
- `clientMode`: `<replace-me — loom or packaged>`
- `minecraftTarget`: `<replace-me — one supported target such as 26.1.2>`
- `artifactPattern`: `versions/<minecraftTarget>/build/libs/<archives_base_name>-<mod_version>-<minecraftTarget>.jar`

Derive `mod_version`, `archives_base_name`, and the default Minecraft version from
`gradle.properties` every run. Do not copy version numbers into this file.

## Canonical client

- `instanceName`: `<replace-me — descriptive client/instance name>`
- `instanceDir`: `<replace-me — absolute game/run directory>`
- `modsDir`: `<instanceDir>/mods` (packaged mode only)
- `screenshotsDir`: `<instanceDir>/screenshots`
- `launchCommand`: `<replace-me — full packaged launch or one version-specific :<target>:runClient command>`
- `account`: `<replace-me — packaged profile/account, or offline Loom account>`
- `altAccount`: `<optional — accounts that must not be launched>`

## Bridge and process checks

- `bridgePort`: `<replace-me — dedicated port for this client>`
- `bridgeUrl`: `http://127.0.0.1:<bridgePort>`
- `tokenPath`: `<instanceDir>/config/floydaddons/control-bridge.json`
- `portCheckCommand`: `<replace-me — reliable OS-specific listener/PID command>`
- `pidInstanceConfirmCommand`: `<replace-me — confirm this exact instance; /health settings path is mandatory>`
- `pidStartTimeCommand`: `<replace-me — process start-time command>`
- `bridgeWaitCommand`: `curl --retry 100 --retry-delay 3 --retry-all-errors --max-time 2 <bridgeUrl>/health`

## World and display

- `devicePixelRatio`: `<replace-me — screenshot width divided by /state window.width>`
- `existingWorldName`: `<replace-me — known-good singleplayer save>`
- `perfArenaWorlds`: `<optional — provisioned performance worlds>`

## Port map

List every local Floyd bridge that might run concurrently, including profiles that
this test client must never stop or assert against.

- `portMap<port>`: `<owner, instance, and any off-limits notes>`

## Packaged-mode commands (omit or mark not applicable for Loom)

- `installScript`: `<repoPath>/scripts/install-built-jar.sh`
- `deployEnvFlags`: `FLOYDADDONS_SKIP_BUILD=true FLOYDADDONS_SKIP_FABRIC_PROFILE=true FLOYDADDONS_SKIP_RUNTIME_DEPS=true`
- `prismBinary`: `<absolute launcher path>`
- `guiQuitCommand`: `<quit only the launcher GUI, never Java>`
- `staleLaunchParentKillCommand`: `<stop only a verified stale launch parent>`

## Reference scripts

- `clickguiWorkedExampleScript`: `<repoPath>/scripts/verify-legacy-clickgui-runtime.py`
- `bridgeClientScript`: `<repoPath>/scripts/verify-live-hypixel-acquisition.py`
- `perfProtocolScript`: `<repoPath>/scripts/perf-protocol.py`
- `perfArenasScript`: `<repoPath>/scripts/perf-arenas.py`
- `perfBaselineScript`: `<repoPath>/scripts/perf-baseline.py`
- `optionsTxtMeasurementSettings`: `enableVsync:false, maxFps:260`
- `sharedMachineNotes`: `<process safety and concurrent-client notes>`
