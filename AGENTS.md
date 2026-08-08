# Repository expectations

- For user-visible gameplay, rendering, HUD, GUI, font, or integration changes, use
  `$floyd-client-testing`.
- Never launch the root multi-version `runClient`; use exactly one configured
  version-specific client.
- Do not claim a user-visible change complete without a fresh `/state` assertion and
  screenshot from the changed build.
- Treat Modrinth/Prism profile deployment as a separate action that requires explicit
  user authorization.
- Every Modrinth version must declare Fabric API (`P7dR8mSH`) and Fabric Language
  Kotlin (`Ha28R6CL`) as required dependencies before publication is considered complete.
- Never kill broad Java or Minecraft processes; stop only a PID proven to own the
  configured bridge and instance.
