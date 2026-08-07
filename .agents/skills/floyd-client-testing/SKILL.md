---
name: floyd-client-testing
description: Build, launch, drive, and visually verify FloydAddons in a real Minecraft client through the FloydLocalControl HTTP bridge. Use for FloydAddons gameplay, GUI, HUD, rendering, font, input, configuration, or integration changes that need live-client proof. Support packaged Prism/Modrinth clients and version-specific Loom dev clients; take all machine-specific paths, ports, accounts, launch commands, and client mode from local.md.
---

# FloydAddons live-client testing

## Load the machine configuration first

1. Read `.agents/skills/floyd-client-testing/local.md` completely.
2. If it is missing or contains `<replace-me`, stop and follow `SETUP.md`.
3. Resolve every `local:<key>` from that file. Never guess a path, port, account,
   instance, client mode, or launch command.
4. Read [references/windows.md](references/windows.md) when running on Windows.

## Enforce the proof standard

Do not call a user-visible change complete until all of these are true:

- the relevant build/tests report literal success;
- `/health` identifies the expected instance from its absolute `settings` path;
- authenticated `/state` reports the current `mod_version` and target Minecraft version;
- `/state` asserts the feature-specific condition;
- a fresh bridge screenshot visibly proves the result.

Treat build output, installed-jar hashes, CI, releases, `/state`, and screenshots as
separate evidence. Never substitute one for another.

## Select exactly one client mode

Use `local:clientMode`.

### Loom mode

1. Confirm `local:bridgePort` is free or already owned by `local:instanceDir`.
2. Run only `local:launchCommand`, which must be a single version-specific task such
   as `:26.1.2:runClient`. Never run the root multi-version `runClient` task.
3. Do not copy a FloydAddons jar into the Loom run directory. Loom loads the mod from
   its development classpath and an installed jar can create a duplicate mod.
4. Treat the successful version-specific build/run task plus runtime scaffold values
   as freshness proof. The packaged-jar mtime rule does not apply.

### Packaged mode

1. Derive `mod_version` from `gradle.properties` and the target artifact from the
   actual version-specific output under `versions/<target>/build/libs`. This
   Stonecutter repo suffixes runtime jars with the Minecraft target.
2. Build before deployment and read the literal success/failure result.
3. Deploy only to `local:modsDir`; remove or disable stale FloydAddons jars without
   touching `config/floydaddons` unless the user explicitly requested config work.
4. Hash-check the built artifact against the installed jar.
5. Launch with `local:launchCommand`, including the configured account/profile.
6. Confirm the client process began after the installed jar was written.

## Protect ports and processes

- Inspect `local:portMap*` before touching a port.
- Use unauthenticated `/health` to prove ownership from the returned settings path.
- If another instance owns the port, leave it running and use the configured port for
  this client. Never assert against it or kill it.
- Never broadly terminate Java, Minecraft, Gradle, or launcher processes. Stop only a
  PID proven to own the configured bridge and expected instance.
- Keep one client per bridge port. A second client may open normally while its bridge
  silently fails to bind.

## Run one verification iteration

1. Derive the current mod version and the selected Minecraft target from repo state.
2. Build/test the affected target. For release work, build all supported targets.
3. Apply the mode-specific deploy rules above.
4. Announce that the test client is launching. On Windows it may open behind Codex;
   use the visible-launch guidance in the Windows reference when the user wants to
   watch.
5. Wait for `local:bridgeUrl/health`, then verify its settings path.
6. Read the token fresh from `local:tokenPath`; never cache or print it.
7. Assert `/state.scaffold.version` equals `mod_version` and
   `/state.scaffold.minecraftVersion` equals the selected target.
8. Open `local:existingWorldName` and poll until `connected` and
   `server.singleplayer` are true.
9. Prepare the exact UI/game state, assert the changed behavior, capture a uniquely
   named screenshot, and inspect the returned absolute PNG path.
10. Disconnect through the bridge, poll until `connected=false`, and stop only the
    verified test-client process.

Module state is nested under `modules.categories[].modules[]`; do not search only the
root `modules` object. Screenshot after every preparatory chat/GUI action because some
command failures are visible only in game even when the HTTP request returns success.

## Drive the bridge

Read [references/bridge-api.md](references/bridge-api.md) for endpoints, coordinate
spaces, GUI-driving details, and known runtime traps. Read
[references/performance.md](references/performance.md) only for performance work.

`scaffold.version` comes from the loaded Fabric metadata. The generated
`Branding.VERSION` comes from `gradle.properties`, so a version bump has one source of
truth in the current build.
