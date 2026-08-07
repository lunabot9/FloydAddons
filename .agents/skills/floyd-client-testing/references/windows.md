# Windows live-client notes

Use PowerShell end to end. Do not enumerate paths in one shell and pass them to a
different shell for moves or deletion.

## Ports and ownership

- Prefer `netstat -ano -p tcp` when `Get-NetTCPConnection` is denied by a managed
  session. Read the listening PID from the final column.
- Use `/health` and its absolute `settings` path as the required instance-ownership
  proof. `Get-CimInstance` may also be denied.
- Use `$ownerPid`, not `$PID`; `$PID` is a reserved automatic PowerShell variable.
- Use `Get-Process -Id <pid>` for process name, path, and start time when permitted.
- Never stop a port owner whose `/health` path points to another Modrinth/Prism/Loom
  instance.

## Launch visibility

A client launched from Codex's managed terminal can open behind the Codex window, so
the user may not see it even though the bridge can drive and screenshot it. Announce
the launch. If the user explicitly wants to watch, launch through a visible Windows
process/window and bring that client forward; otherwise keep helper processes hidden.

Run only a version-specific Gradle task such as:

```powershell
.\gradlew.bat :26.1.2:runClient
```

Never invoke the root multi-version `runClient` task.

## Shutdown

Disconnect through the bridge first and poll for `connected=false`. Sending Ctrl+C to
a running Gradle batch command can prompt `Terminate batch job (Y/N)?`; answer `Y` and
then verify both the process and bridge are gone. Do not use broad `taskkill` patterns
against Java, Minecraft, or Gradle.

## Packaged deployment

Use separate, literal-path operations to enumerate stale FloydAddons jars, remove only
those exact files, copy the selected jar, and hash-check it. Snapshot the hash of
`config\floydaddons\floydaddons-config.json` before and after; routine jar replacement
must leave it unchanged.
