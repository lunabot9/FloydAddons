# Floyd-in-Odin Completion Audit

This file maps the current objective to the evidence that proves it in this
worktree. It is not a substitute for the verifier; it records what the verifier
and runtime smokes are expected to prove.

## Objective

Build FloydAddons as the final mod, using the current `odtheking/Odin` 1.21
Fabric scaffold for module registration, settings, config, event handling,
ClickGUI/components, resources, and build output. Floyd's game behavior remains
the implementation source of truth, with old Floyd GUI/editor screens retained
only as vendored reference material.

## Evidence Matrix

| Requirement | Authoritative evidence | Status |
| --- | --- | --- |
| Use Odin 1.21 scaffold, not legacy 1.8.9 client | `gradle.properties` targets Minecraft `1.21.11`; `PROVENANCE.md` pins `odtheking/Odin` at `77b66713f74849bbcc05067484e6e85c01c96698`; `ScaffoldAuditTest.active build metadata targets FloydAddons on Odin 1_21_11 scaffold` pins metadata. | Verified by `./scripts/verify-floyd-in-floydaddons.sh`. |
| Final mod is FloydAddons/floydaddons | `gradle.properties`, `fabric.mod.json`, `FloydAddonsMod`, runtime and sources jar metadata audits in `scripts/verify-floyd-in-floydaddons.sh`. | Verified by `./scripts/verify-floyd-in-floydaddons.sh`. |
| Active modules are Floyd behavior mounted through Odin's module system | `ModuleManager.kt` registers ClickGUI plus Floyd modules; `ScaffoldAuditTest.active module registry is Floyd feature modules plus Odin ClickGUI` pins exact order and membership; runtime `/state.modules` reports `moduleCount=16`. | Verified by tests and runtime smoke. |
| Module groupings match Floyd GUI groups | `Category.kt` defines `Render`, `Hiders`, `Player`, `Camera`, `Cosmetic`, `QOL`, `Misc`; `ScaffoldAuditTest.active categories are Floyd gui groups only` and `active module categories follow Floyd GUI grouping` compare active modules to vendored Floyd GUI source. | Verified by tests and runtime smoke. |
| Floyd old GUI/editor screens are not active runtime surfaces | `VendoredFloydCoverageSourceTest` classifies old GUI/editor files as retired; `ScaffoldAuditTest.old Floyd GUI controls are represented by Odin settings or explicit retired styling` pins control coverage; jar/source audits reject old GUI packages and PNG resources. | Verified by `./scripts/verify-floyd-in-floydaddons.sh`. |
| Vendored Floyd behavior and resources are covered | `vendor/floydaddons-fabric` is present; `VendoredFloydCoverageSourceTest` requires every vendored source/resource to be classified, requires packaged Floyd resources to match vendored bytes, and requires active behavior files to be referenced outside its own ledger. | Verified by `./scripts/verify-floyd-in-floydaddons.sh`. |
| Old Odin gameplay modules, old categories, stale helpers, and stale `assets/odin` are absent | `scripts/verify-floyd-in-floydaddons.sh` rejects old Odin source tokens, stale directories, and jar entries; `ScaffoldAuditTest` pins the verifier tokens. | Verified by `./scripts/verify-floyd-in-floydaddons.sh`. |
| Active mixin config matches active mixin source | `scripts/verify-floyd-in-floydaddons.sh` compares `floydaddons.mixins.json` against `src/main/java/floydaddons/not/dogshit/mixin` in both directions. | Verified by `./scripts/verify-floyd-in-floydaddons.sh`. |
| Runtime scaffold proof is available without screenshots | `FloydLocalControl` exposes `/health`, `/state`, and `/replace-text`; README documents proof surfaces; `LocalControlParitySourceTest` pins the bridge shape. | Verified by tests and runtime smoke. |
| Runtime title-screen smoke proves the scaffold loads | `MIGRATION.md` records the 2026-05-19 smoke: `FLOYDADDONS_DEVAUTH=false ./gradlew runClient --quiet`, `/health ok=true`, `/state ok=true`, Floyd categories, `moduleCount=16`, empty `QOL`, Local Control runtime flags, and scaffold provenance. `scripts/run-runtime-scaffold-smoke.sh` now reproduces the same non-auth title-screen proof end to end through local-control and exits only after port `38765` is closed. | Verified by `scripts/run-runtime-scaffold-smoke.sh`. |
| Legal/provenance notices are packaged | `LICENSE`, `THIRD_PARTY_NOTICES.md`, and `PROVENANCE.md` exist; `build.gradle.kts` packages them under `META-INF`; `scripts/verify-floyd-in-floydaddons.sh` byte-compares packaged copies in runtime and sources jars. | Verified by `./scripts/verify-floyd-in-floydaddons.sh`. |
| Sources jar mirrors active source/resources | `scripts/verify-floyd-in-floydaddons.sh` compares the sources jar against `src/main/kotlin`, `src/main/java`, and `src/main/resources`. | Verified by `./scripts/verify-floyd-in-floydaddons.sh`. |
| Live Hypixel server-ID acquisition from Hypixel itself | `scripts/verify-live-hypixel-acquisition.py --json` is the required proof command once Minecraft is already connected to Hypixel with Local Control and Server ID Hider enabled. The helper auto-detects normal-launcher `config/floydaddons/control-bridge.json` and dev-run `run/config/floydaddons/control-bridge.json` sidecars unless `--config` is supplied. | Not yet verified; this remains the completion blocker. |

## Current Gate

The current repo gate is:

```bash
./scripts/verify-floyd-in-floydaddons.sh
```

The current proof-artifact status can be summarized with:

```bash
python3 scripts/completion-status.py --json
```

Set `FLOYDADDONS_REQUIRE_COMPLETE=true` on the repo gate to run
`python3 scripts/completion-status.py --require-complete --json` as the final
step. This intentionally fails unless runtime scaffold proof, prepared live
launcher install readiness, and live Hypixel proof are all valid. The completion
report includes a separate `liveInstall` section, so install readiness is
tracked independently from the required live Hypixel proof.
The live proof portion is revalidated from the JSON artifact: diagnostic and
preflight artifacts do not count, the scan source must be one of the live
tab/scoreboard sources, the hit age must be fresh and internally consistent with
world time, and the cached `/replace-text` probe must have produced Floyd's
replacement without retaining the original cached server ID.
When a live proof exists, the completion report exposes the same audited fields:
server identity, live scan source, scan hit count, last hit tick, world time,
hit age, cached probe ID, and replacement result.
Top-level `remaining` includes both failed component names and their concrete
subreasons, so install failures expose the exact missing/stale/invalid profile
or jar check that blocked completion.

It runs Gradle tests/build, offline live-verifier tests, mixin source coverage,
stale source and jar audits, metadata audits, legal/provenance packaging checks,
runtime resource checks, sources jar checks, and helper script syntax checks.
`scripts/install-built-jar.sh` is the explicit-path helper for installing the
repo-pinned Fabric loader profile and placing the runtime jar plus repo-pinned
Fabric API and Fabric Language Kotlin dependency jars into a live client's
`mods` directory without inspecting launcher account/session files, and
`scripts/test-install-built-jar.py` covers target validation, Floyd-only jar
replacement behavior, offline Fabric-profile invocation, and offline
dependency-copy coverage. With
`python3 scripts/live-install-status.py --json`, the same non-auth live install
readiness can be checked without contacting Minecraft services or reading
auth/session files, including a SHA-256 match between the installed FloydAddons
jar and the current build artifact plus readable-jar validation for the runtime
dependency jars. With
`FLOYDADDONS_RUN_RUNTIME_SMOKE=true`, the same gate also runs the non-auth
title-screen smoke wrapper. With `FLOYDADDONS_RUN_LIVE_PREFLIGHT=true`, the gate
also runs the live Hypixel readiness check and writes
`logs/live-hypixel-preflight.json` without sending `/replace-text`. With
`FLOYDADDONS_RUN_LIVE_HYPIXEL=true`, the gate also runs the live Hypixel
verifier, which still requires an already-connected Hypixel client and the
live-proof preconditions from `README.md`. The non-auth runtime smoke flag is
mutually exclusive with both live flags.
Live proof artifacts are written through a temporary file and moved into place
only on success after non-empty JSON validation, so failed live runs do not
leave empty or invalid JSON proof files behind.
The live verifier also supports `--wait-seconds <seconds>` for direct manual
runs while Minecraft is launching or joining Hypixel; this retries the same
proof and does not relax any state or replacement requirements.
`--diagnose --json` reports the first missing live-proof precondition without
calling `/replace-text` or writing a proof artifact.
The repo gate exposes that as `FLOYDADDONS_RUN_LIVE_DIAGNOSE=true`, writing
`logs/live-hypixel-diagnose.json`; this may record `ready=false` and is not
completion proof.
`scripts/live-hypixel-status.sh` provides the same validated diagnostic JSON as
`logs/live-hypixel-status.json` without running Gradle; this status artifact is
also not completion proof. It includes non-auth runtime context showing whether
the Minecraft launcher is running, whether a Minecraft game process is running,
and whether Local Control is listening on port `38765`.
The helper prints the same augmented JSON that it writes, so command output and
the saved artifact are directly comparable.
`scripts/test-live-hypixel-status.py` covers that
helper's not-ready JSON and custom status-file handling.
The repo gate passes that through as `FLOYDADDONS_LIVE_WAIT_SECONDS=<seconds>`
when either live proof flag is enabled.

When a dev client is already running, the runtime scaffold smoke can be
reproduced with:

```bash
python3 scripts/verify-runtime-scaffold.py --json
```

To run the non-auth dev-client smoke end to end:

```bash
scripts/run-runtime-scaffold-smoke.sh
```

The wrapper requires the Minecraft title screen before accepting the scaffold
proof and writes `logs/runtime-scaffold-proof.json`.

## Remaining Completion Blocker

Live authenticated Hypixel text acquisition has not been run in this worktree.
Run it only after Minecraft is already connected to Hypixel and the live proof
preconditions in `README.md` are satisfied:

```bash
FLOYDADDONS_RUN_LIVE_HYPIXEL=true ./scripts/verify-floyd-in-floydaddons.sh
```

That helper is intentionally limited to the FloydAddons local-control sidecar and
loopback HTTP state; it does not inspect Minecraft account/auth files. A
successful gate run writes `logs/live-hypixel-proof.json`. Use
`python3 scripts/verify-live-hypixel-acquisition.py --preflight --json` to check
live readiness without sending the final `/replace-text` probe.

The final live proof procedure is also summarized in `LIVE_PROOF.md`.
