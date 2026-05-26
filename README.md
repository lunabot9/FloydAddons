# FloydAddons

FloydAddons rebuilt on Odin's Fabric module, config, event, and ClickGUI scaffolding.

The active runtime is a Fabric 1.21.11 client mod named `floydaddons`. Odin's
current 1.21 scaffold owns the app structure, UI, module registry, settings,
config persistence, event bus, render batching, and build. FloydAddons behavior
is ported into that scaffold as Odin modules.

## Source Layout

- `src/main/kotlin/floydaddons/not/dogshit/client`: active Odin-based scaffold and Floyd module surfaces.
- `src/main/java/floydaddons/not/dogshit/mixin`: active Mojang-mapped behavior mixins.
- `src/main/resources`: Fabric metadata and retained runtime assets under `assets/floydaddons`.
- `vendor/floydaddons-fabric`: vendored FloydAddons Fabric/Yarn implementation source from `/Users/twaldin/SkyblockQOLmod`.

See `PROVENANCE.md` for the exact Odin and Floyd baseline commits used by this
port.

## Licensing

The active metadata declares `BSD-3-Clause AND MIT`: Odin scaffold code is covered by `LICENSE`, and the vendored FloydAddons source declares MIT licensing in its Fabric metadata. See `THIRD_PARTY_NOTICES.md`.

## Current Port State

Floyd feature groups are registered as Odin modules using Floyd's GUI groupings:

- Render
- Hiders
- Player
- Camera
- Cosmetic
- QOL
- Misc

Active Floyd module surfaces:

- Render: `FloydRender`, `FloydXray`, `FloydAnimations`, `FloydHud`, `FloydMobEsp`
- Hiders: `FloydHiders`
- Player: `FloydNickHider`, `FloydPlayerSize`
- Camera: `FloydCamera`
- Cosmetic: `FloydSkin`, `FloydCape`, `FloydConeHat`
- Misc: `FloydDiscordPresence`, `FloydLocalControl`, `FloydCompatibility`

Animations, HUD, and Mob ESP live under the Render group as Odin modules, matching Floyd's old ClickGUI and v2 Render tab. The QOL group remains present as an empty Floyd tab surface.

Old Odin gameplay modules, categories, commands, skyblock/Hypixel helpers, stale `assets/odin`, and old Floyd GUI PNG resources are intentionally absent from active source and packaged jars. Floyd's old custom GUI screens are not compiled; equivalent controls live in Odin ClickGUI/settings surfaces.

Floyd's vendored Java/Yarn source is kept for provenance and parity audits, not compiled directly, because the active Odin app uses official Mojang mappings.

## Runtime Proof

The local-control bridge is enabled by default and writes `config/floydaddons/control-bridge.json` relative to the active Minecraft run directory. In this repo's `runClient` dev environment that file is `run/config/floydaddons/control-bridge.json`. Authenticated `/state` exposes runtime proof surfaces for:

- `scaffold`: mod id/version, Minecraft version, entrypoint, mixin config, resource namespace, retained Odin scaffold, and vendored Floyd source path.
- `modules`, `configs`, and `eventBus`: Odin module/config/event scaffolding state.
- `render`, `qol`, `cosmetics`, `playerFeatures`, and `misc`: Floyd feature state, hook counters, sidecar metadata, and bridge/RPC/compatibility status. `qol` is intentionally empty until a Floyd QoL feature exists outside the Render tab.
- `/replace-text`: Nick Hider/server-id replacement smoke checks without requiring screenshots.

Live authenticated Hypixel text acquisition from Hypixel itself is not yet verified in this workspace. Local/offline multiplayer scoreboard packet acquisition and replacement paths are verified; real Hypixel proof requires explicit approval before using Minecraft authentication/session state.

## Verification

```bash
./scripts/verify-floyd-in-floydaddons.sh
```

To summarize current completion evidence from proof artifacts:

```bash
python3 scripts/completion-status.py --json
```

Set `FLOYDADDONS_REQUIRE_COMPLETE=true` on `./scripts/verify-floyd-in-floydaddons.sh`
to make the gate fail unless runtime scaffold proof, non-auth live install
readiness, and live Hypixel proof are all valid. The completion summary reports
the prepared launcher state under `liveInstall`, so install readiness stays
separate from the remaining authenticated Hypixel proof.

To install the current runtime jar into a client for live proof, pass an
explicit Minecraft `mods` directory. The helper also installs the repo-pinned
Fabric loader profile, Fabric API, and Fabric Language Kotlin runtime jars
required by `fabric.mod.json`. It does not inspect launcher account or session
files:

```bash
./scripts/install-built-jar.sh /path/to/.minecraft/mods
```

Set `FLOYDADDONS_SKIP_RUNTIME_DEPS=true` only when those dependency jars are
already installed and you intentionally want the helper to copy FloydAddons
alone.
Set `FLOYDADDONS_SKIP_FABRIC_PROFILE=true` only when the matching Fabric loader
profile is already installed.

To check the non-auth live install readiness without contacting Minecraft
services, including that the installed FloydAddons jar matches the current
`build/libs/FloydAddons-0.1.0.jar` SHA-256 and that the installed runtime jars
are readable jar archives:

```bash
python3 scripts/live-install-status.py --json
```

When Minecraft is already running and connected to Hypixel with local-control enabled, the live acquisition blocker can be verified without reading Minecraft account files. Preconditions:

- Minecraft is already connected to Hypixel.
- `FloydLocalControl` is enabled and the active sidecar reports `enabled=true`. The live verifier auto-detects `config/floydaddons/control-bridge.json` for normal launches and `run/config/floydaddons/control-bridge.json` for this repo's `runClient` dev environment.
- Hiders -> `Server ID Hider` is enabled.
- The Nick Hider server-ID tracker has recorded a fresh live tab/scoreboard hit.

```bash
python3 scripts/verify-live-hypixel-acquisition.py
```

For a machine-readable proof artifact, use:

```bash
python3 scripts/verify-live-hypixel-acquisition.py --json
```

To check the live state readiness without sending the final `/replace-text`
probe:

```bash
python3 scripts/verify-live-hypixel-acquisition.py --preflight --json
```

To report the first missing live-proof precondition as JSON without sending
`/replace-text`, use:

```bash
python3 scripts/verify-live-hypixel-acquisition.py --diagnose --json
```

For a lightweight status artifact outside the full Gradle gate:

```bash
scripts/live-hypixel-status.sh
```

That writes `logs/live-hypixel-status.json`. A status file may contain
`ready=false`; it is diagnostic state, not completion proof.

By default, live proof requires the server-ID hit to be no older than 1200
world ticks. Use `--max-hit-age-ticks` only to widen or narrow that freshness
window for a specific manual run.
Use `--wait-seconds <seconds>` to retry the same live proof while Minecraft is
launching, joining Hypixel, or waiting for the first live server-ID hit. The
wait mode does not relax the proof requirements or seed synthetic scan text.

The standard verifier syntax-checks that helper, runs its offline Python
self-tests, and runs its `--help` path through `python3` without contacting
Minecraft.
It also runs the full Gradle test/build path, requires the active mixin config to
exactly match active mixin source files, rejects stale Odin/Floyd GUI surfaces in
source and jars, validates runtime and sources Fabric metadata, proves runtime
jar resources exactly match `src/main/resources`, and proves the sources jar
exactly mirrors active source and resource trees.
Set `FLOYDADDONS_RUN_RUNTIME_SMOKE=true` to have the gate also launch the
non-auth dev client and run `scripts/run-runtime-scaffold-smoke.sh`.
Set `FLOYDADDONS_RUN_LIVE_DIAGNOSE=true` to have the gate write
`logs/live-hypixel-diagnose.json` after the static/build checks. This records
readiness diagnostics and may contain `ready=false`; it is not a proof artifact.
Set `FLOYDADDONS_RUN_LIVE_PREFLIGHT=true` when Minecraft is already connected to
Hypixel to have the gate run the live readiness check without `/replace-text`;
the gate writes `logs/live-hypixel-preflight.json` only after success and JSON
validation.
Set `FLOYDADDONS_RUN_LIVE_HYPIXEL=true` only when Minecraft is already connected
to Hypixel with the live-proof preconditions below satisfied; the gate will then
also run `python3 scripts/verify-live-hypixel-acquisition.py --json` and write
`logs/live-hypixel-proof.json` only after success and JSON validation.
Set `FLOYDADDONS_LIVE_WAIT_SECONDS=<seconds>` with either live flag to have the
gate pass the helper's bounded wait through after the static/build checks pass.
Do not combine the live flags with `FLOYDADDONS_RUN_RUNTIME_SMOKE=true`; the
runtime smoke launches a non-auth dev client, while live proof requires an
already-connected Hypixel client.

When a dev client is already running, the non-Hypixel runtime scaffold proof can
be reproduced with:

```bash
python3 scripts/verify-runtime-scaffold.py --json
```

That verifier reads the same FloydAddons local-control sidecar, checks `/health`
and `/state`, and proves the active 1.21.11 FloydAddons runtime is mounted
through Odin's scaffold with Floyd's GUI groupings.

To launch a non-auth dev client, wait for the same proof, and shut the client
down automatically:

```bash
scripts/run-runtime-scaffold-smoke.sh
```

The wrapper waits for `net.minecraft.client.gui.screens.TitleScreen` before it
accepts the scaffold proof, writes logs to `logs/runtime-scaffold-smoke.log`,
and writes the proof artifact to `logs/runtime-scaffold-proof.json`.

See `LIVE_PROOF.md` for the final live-Hypixel proof runbook.

Focused parity/audit suites:

```bash
./gradlew test --tests floydaddons.not.dogshit.client.ScaffoldAuditTest
./gradlew test --tests floydaddons.not.dogshit.client.StartupParitySourceTest
./gradlew test --tests floydaddons.not.dogshit.client.LocalControlParitySourceTest
./gradlew test --tests floydaddons.not.dogshit.client.MixinParitySourceTest
./gradlew test --tests floydaddons.not.dogshit.client.CommandParitySourceTest
./gradlew test --tests floydaddons.not.dogshit.client.LegacyConfigImportParitySourceTest
./gradlew test --tests floydaddons.not.dogshit.client.LiveHypixelVerificationScriptTest
```

The migration ledger in `MIGRATION.md` records runtime smokes, parity decisions, and the remaining live-Hypixel verification blocker.
