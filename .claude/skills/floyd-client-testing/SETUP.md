# SETUP — bootstrap floyd-client-testing on a NEW machine

Goal: a working Prism instance running the FloydAddons jar with a reachable bridge,
and a filled-in `local.md` so `SKILL.md` never has to guess machine values.

## 1. Prerequisites

| Requirement | Notes |
|---|---|
| Prism Launcher | installed with at least one Minecraft account added and selected/selectable as a profile (see "Accounts" in section 2) |
| Build JDK | a JDK matching the repo's Gradle toolchain (`./gradlew build` will tell you loudly if wrong) — this covers the build only |
| Instance Java | separate from the build JDK: MC 1.21.x needs a Java 21+ runtime; let Prism auto-download it (Settings → Java) or set it per instance in Edit Instance → Settings → Java |
| Display | a logged-in desktop session is required (X11/Wayland on Linux) — the bridge screenshots the real framebuffer. Headless boxes need Xvfb/`xvfb-run`; the resulting `devicePixelRatio` is 1 and window focus-throttling caveats apply to `/perf` |
| This repo cloned | record its absolute path as `repoPath` in local.md |
| python3 + curl | used by the loop for token reads and `/state` asserts |
| POSIX shell | all scripts and loop commands are bash/POSIX (`install-built-jar.sh` is bash; `FOO=bar script.sh` env-prefixes don't run in cmd/PowerShell). **Windows: run the entire SETUP/SKILL pipeline from Git Bash (or WSL)** — `%APPDATA%` paths become `/c/Users/<u>/AppData/Roaming/...` there, and use `python` (py launcher) if `python3` is absent |

Version truth lives in `gradle.properties`: `minecraft_version`, `loader_version`,
`fabric_api_version`, `fabric_kotlin_version`, `mod_version`, `archives_base_name`.
Read them; never hardcode versions anywhere.

## 2. Per-OS notes (Prism)

| | macOS | Linux | Windows |
|---|---|---|---|
| Prism data dir | `~/Library/Application Support/PrismLauncher` | `~/.local/share/PrismLauncher` (Flatpak: `~/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher`) | `%APPDATA%\PrismLauncher` |
| Instance game dir | `<data>/instances/<name>/minecraft` | same pattern | same pattern |
| CLI binary | `/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher` | `prismlauncher` on PATH, or `flatpak run org.prismlauncher.PrismLauncher` | `C:\Program Files\Prism Launcher\prismlauncher.exe` |
| Launch command shape | `"<binary>" --launch "<instance>" --profile <account> &` | same | same (no `&`; use Start-Process) |
| Quit the GUI | `osascript -e 'tell application "Prism Launcher" to quit'` | `pkill -x prismlauncher` (graceful SIGTERM; verify your java client PID survives) | close the window / `taskkill /IM prismlauncher.exe` (untested workflow) |
| Kill stale launch parents | `pkill -f "prismlauncher --launch"` | same | Task Manager / taskkill by command line |
| Port check | `lsof -nP -iTCP:<port> -sTCP:LISTEN -t` | same (no lsof: `ss -ltnp \| grep :<port>`) | `Get-NetTCPConnection -LocalPort <port> -State Listen \| % OwningProcess` (or `netstat -ano \| findstr :<port>`) |
| PID confirm (cwd = your instance) | `lsof -p <pid> \| grep cwd` | same (or `readlink /proc/<pid>/cwd`) | `Get-Process -Id <pid> \| select Path` |
| Typical devicePixelRatio | 2 (retina) | 1 (HiDPI displays: 2) | 1 |

All OSes: older/MultiMC-imported instances use `.minecraft` instead of `minecraft` —
`ls` the instance dir and use whichever exists.

Definition used everywhere below: **`<gameDir>` = `<data>/instances/<instanceName>/minecraft`**
(or `.minecraft`, per the footnote above). `instanceDir` in local.md records exactly this
path — it already ENDS in `/minecraft`; never append another `minecraft/`.

Flatpak (Linux): launch = `flatpak run org.prismlauncher.PrismLauncher --launch "<instance>" --profile <account> &`;
quit the GUI with `flatpak kill org.prismlauncher.PrismLauncher` (`pkill -x prismlauncher`
does not reliably match the bwrap-sandboxed process tree). Verify the java client PID
survives the GUI quit — under the sandbox it may not; if it dies, prefer native/AppImage
Prism for this workflow.

Why GUI-quit matters: a running Prism GUI (or a stale `prismlauncher --launch` parent)
intercepts CLI launches via QLocalSocket and may drop/mangle them. The GUI process is
always safe to quit. **Never touch java processes you did not verify.**

Account trap: if the launcher's default profile is a different account than you intend,
the CLI launches the wrong identity (wrong server cookies). Always pass `--profile`.

### Accounts

- Adding a Microsoft account to Prism is GUI-only interactive OAuth (Settings →
  Accounts → Add Microsoft → device-code login in a browser). **This is the one manual
  human step in this setup** — it cannot be done headless.
- List existing profiles: `jq -r '.accounts[].profile.name' "<PrismData>/accounts.json"`.
- `--profile` takes the account's **in-game player name** (`profile.name`), NOT the
  email.
- No-Microsoft-account fallback for pure local verification: the Loom dev client with
  an offline/devauth account (see SKILL.md "Alternative loop").

## 3. Create + provision the instance

1. In Prism: Add Instance → name it e.g. `floyd test` → Minecraft version =
   `minecraft_version` from gradle.properties. Then Edit Instance → Version →
   Install Loader → **Fabric** → pick `loader_version` — Prism manages the loader
   itself. (At time of writing those are `1.21.11` / `0.18.5` — READ gradle.properties,
   don't copy this doc.) Cloning an existing working instance also inherits these
   settings.
2. Provision mods with the install script (it builds, installs the FloydAddons jar,
   and downloads matching fabric-api + fabric-language-kotlin into the mods dir):

   ```bash
   # Prism instance: skip the fabric-installer profile step (Prism supplies the loader)
   FLOYDADDONS_SKIP_FABRIC_PROFILE=true \
     <repo>/scripts/install-built-jar.sh "<gameDir>/mods"
   ```

   - The single argument MUST end in `/mods` (or set `FLOYDADDONS_MODS_DIR`).
   - `<gameDir>` (section 2) already ends in `/minecraft` — passing
     `<gameDir>/minecraft/mods` silently `mkdir -p`'s a wrong directory: the jar
     "installs" fine, the client loads no mod, and the bridge never answers.
   - Full mode (NO skip flags) additionally runs the Fabric installer and writes a
     `versions/fabric-loader-<loader>-<mc>` profile into the parent dir — that is for
     vanilla-launcher-style directories, not Prism.
   - Later redeploys on a provisioned instance: `FLOYDADDONS_SKIP_BUILD=true`
     `FLOYDADDONS_SKIP_FABRIC_PROFILE=true` `FLOYDADDONS_SKIP_RUNTIME_DEPS=true`
     reduce it to a pure jar copy (build separately and read BUILD SUCCESSFUL).
   - The script echoes the absolute path of every file it installs.

## 4. Bridge port + token (how it actually works)

- The bridge is the "Local Control" module (enabled by default). It binds **loopback
  only** on the port from the in-GUI `Port` NumberSetting, persisted per instance in
  `<gameDir>/config/floydaddons/floydaddons-config.json` (`"Local Control"` →
  `settings.Port`). Code default: **38769** — every fresh instance starts there.
- `<gameDir>/config/floydaddons/control-bridge.json` is rewritten on every bridge
  start: `{enabled, port, token}`. Only `enabled` and `token` are READ from it (the
  GUI Port setting is authoritative for the bind; the json's `port` field is
  overwritten). The token is reused across restarts unless the file is missing,
  unparseable, or blank — then a new random token is generated. **Read it fresh every
  run; never hardcode tokens.** `{"enabled": false}` in the file disables the bridge.
- `GET /` and `GET /health` need no auth and return the port, the absolute
  `settingsPath` (token-file location), and the endpoint list — use `/health` to
  confirm which instance owns a port. Auth elsewhere: `Authorization: Bearer <token>`,
  `X-FloydAddons-Token`, or `?token=`.

Fresh-instance port sequencing (IMPORTANT on shared machines):

- The config json does not exist until first launch, so every fresh instance
  unavoidably binds **38769** on its first boot. BEFORE first launch, confirm 38769 is
  unbound (Port check command, section 2 table) — two clients racing one port means the
  loser boots with a silently dead bridge (section 5).
- To move off the default: first launch → quit the client → edit `"Local Control"` →
  `settings.Port` in `<gameDir>/config/floydaddons/floydaddons-config.json` (or change
  it in the in-game ClickGUI) → relaunch → re-verify ownership via `/health`
  `settingsPath`.

## 5. Multi-agent port-collision pattern (IMPORTANT)

If more than one agent/instance/dev-client can run on this machine:

- Each concurrent instance needs its OWN bridge port (change the Local Control `Port`
  setting per instance — mechanics at the end of section 4; the repo's Loom `run/` dir
  keeps its own persisted port too).
- Document the FULL local port map in `local.md` — which port belongs to which
  instance/agent, and which ports are off-limits.
- Never kill or assert against a port you don't own. A port answering with the wrong
  `scaffold.version` is someone else's client/branch.
- Never `pkill`/`killall java`. Kill only the single PID verified to own your port.
- Two clients racing one port = the loser boots with a silently dead bridge.

## 6. Create local.md

```bash
cd <repo>/.claude/skills/floyd-client-testing
cp local.example.md local.md   # then fill EVERY key with this machine's real values
```

`local.md` is gitignored (machine-private). Keep it current: if you move the instance,
change the port, or add a new local instance/agent, update local.md in the same change.

## 7. Validation checklist (do all of these before first real use)

1. Launch the instance via the CLI command you recorded in local.md; confirm a java
   PID appears owning your port via the `portCheckCommand` you recorded (per-OS
   commands in the section 2 table — macOS/Linux `lsof -nP -iTCP:<port> -sTCP:LISTEN -t`,
   Windows `Get-NetTCPConnection -LocalPort <port> -State Listen`).
2. `curl -s http://127.0.0.1:<port>/health` → 200, and the returned `settingsPath`
   matches your instance dir (proves port ownership).
3. Confirm the launched IDENTITY: `grep 'Setting user' <gameDir>/logs/latest.log`
   (the player-name line) must equal `local:account`, NOT `local:altAccount` — the
   account trap from section 2; a screenshot of the pause/tab screen also shows the
   name.
4. Read the token from `control-bridge.json`; `GET /state` with it; assert
   `scaffold.version == mod_version` and `scaffold.minecraftVersion ==
   minecraft_version` from gradle.properties.
5. `POST /screenshot {"fileName":"setup-check.png"}`; the response contains the
   absolute path — Read that PNG and confirm it shows the client.
6. Compute `devicePixelRatio` = screenshot pixel width ÷ `/state` `window.width`;
   record it in local.md (drives all `/mouse` gui-coordinate math).
7. Create the canonical save (a fresh machine has none): while DISCONNECTED,
   `POST /action {"action":"createFreshWorld","world":"floyd-test-world","flat":true,"gamemode":"creative","cheats":true}`,
   poll `/state` until `connected && server.singleplayer`, then record the name as
   `existingWorldName` in local.md.
8. Re-read `SKILL.md` top to bottom once with local.md filled in — every
   `local:<key>` reference must resolve.
