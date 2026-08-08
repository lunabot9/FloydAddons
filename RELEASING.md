# FloydAddons release checklist

1. Bump `mod_version` in `gradle.properties` and write `release-notes-<version>.md`.
2. Build and test Minecraft `26.1`, `26.1.2`, and `26.2`.
3. Perform the configured live-client `/state` assertions and capture a fresh screenshot.
4. Push the intended source, tests, version bump, and notes to `main`.
5. Publish the GitHub release with the three version-specific runtime jars.
6. Publish one Modrinth version for each supported Minecraft version.
7. On every Modrinth version, declare both of these dependencies as **required**:
   - Fabric API — project ID `P7dR8mSH`
   - Fabric Language Kotlin — project ID `Ha28R6CL`
8. Verify the GitHub and Modrinth metadata, then download and hash-check every remote jar
   against the corresponding local build.
