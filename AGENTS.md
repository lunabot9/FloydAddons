# Repository expectations

- This checkout and its GitHub remote are for public builds only. Keep all local-only
  extension source and private JARs outside this checkout, in local storage with no
  GitHub remote. Never publish the Ginger Snaps installed JAR or upload it as a release.
- Before any push, run `python scripts/verify-public-distribution.py --ref HEAD`.
  Keep `core.hooksPath=.githooks` enabled; do not bypass the pre-push check.
- Before uploading release artifacts, run
  `python scripts/verify-public-distribution.py --artifacts <each-public-jar>`.
  Use only the verified public build outputs, never a profile's installed mod.

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
