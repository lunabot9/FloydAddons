# Live Hypixel Proof Runbook

Use this only after Minecraft is already running and connected to Hypixel.
Do not combine these commands with `FLOYDADDONS_RUN_RUNTIME_SMOKE=true`; the
runtime smoke launches a non-auth dev client, while live proof requires the
already-connected Hypixel client.

## Preconditions

- The repo-pinned Fabric loader profile, current `FloydAddons-*.jar`, Fabric
  API, and Fabric Language Kotlin jars are installed in the live client. To
  build and install them without inspecting launcher account files:

```bash
./scripts/install-built-jar.sh /path/to/.minecraft/mods
```

To verify that profile and mod dependency install without touching
auth/session files:

```bash
python3 scripts/live-install-status.py --json
```

- Minecraft is already connected to Hypixel.
- FloydAddons Local Control is enabled.
- The active local-control sidecar reports `enabled=true`. The verifier
  auto-detects `config/floydaddons/control-bridge.json` for normal launches and
  `run/config/floydaddons/control-bridge.json` for this repo's `runClient` dev
  environment.
- Hiders -> `Server ID Hider` is enabled.
- The Nick Hider server-ID tracker has a fresh live tab or scoreboard hit.

## Preflight

This checks readiness without sending the final `/replace-text` probe:

```bash
FLOYDADDONS_RUN_LIVE_PREFLIGHT=true ./scripts/verify-floyd-in-odin.sh
```

On success, the gate writes:

```text
logs/live-hypixel-preflight.json
```

Failed preflight runs remove any previous preflight proof and do not leave an
empty or invalid JSON proof artifact behind.

To see the first missing precondition without writing a proof artifact or
calling `/replace-text`:

```bash
python3 scripts/verify-live-hypixel-acquisition.py --diagnose --json
```

To run the normal static/build gate first and then save the same diagnostic:

```bash
FLOYDADDONS_RUN_LIVE_DIAGNOSE=true ./scripts/verify-floyd-in-odin.sh
```

That writes `logs/live-hypixel-diagnose.json`. A diagnostic with `ready=false`
is expected before the live client is ready and is not a proof artifact.

For a faster status refresh that skips Gradle and still validates the JSON:

```bash
scripts/live-hypixel-status.sh
```

That writes `logs/live-hypixel-status.json`, which is also diagnostic state
rather than proof. The status JSON also includes non-auth runtime context for
whether the Minecraft launcher is running, whether a Minecraft game process is
running, and whether FloydAddons Local Control is listening on port `38765`.

When launching Minecraft and joining Hypixel at the same time as the proof, use
the helper directly with `--wait-seconds <seconds>` to retry until the exact same
preflight requirements are true:

```bash
python3 scripts/verify-live-hypixel-acquisition.py --preflight --json --wait-seconds 120
```

To keep the full static/build gate in the same run while waiting, pass the wait
through the repo verifier:

```bash
FLOYDADDONS_RUN_LIVE_PREFLIGHT=true FLOYDADDONS_LIVE_WAIT_SECONDS=120 ./scripts/verify-floyd-in-odin.sh
```

## Final Proof

This runs the full static/build gate and then verifies cached server-ID
replacement without synthetic `scanText`:

```bash
FLOYDADDONS_RUN_LIVE_HYPIXEL=true ./scripts/verify-floyd-in-odin.sh
```

The final proof accepts the same bounded wait pass-through:

```bash
FLOYDADDONS_RUN_LIVE_HYPIXEL=true FLOYDADDONS_LIVE_WAIT_SECONDS=120 ./scripts/verify-floyd-in-odin.sh
```

On success, the gate writes:

```text
logs/live-hypixel-proof.json
```

Failed final-proof runs remove any previous live proof and do not leave an empty
or invalid JSON proof artifact behind.

The live verifier reads only the FloydAddons local-control sidecar/token and
loopback HTTP state. It does not inspect Minecraft account/auth files.
