# FloydAddons release checklist

Releases are **tag-driven and manual**. Never bump a version, commit a release, or push a tag
without an explicit go-ahead from the owner — always ask **"should I make a release?"** first.

## Every commit gets downloadable jars

The build workflow runs for every PR, every push to `main`, and every tag. Each Minecraft version
in the matrix (`26.1`, `26.1.2`, `26.2`) uploads its runtime jar as a workflow artifact
(`FloydAddons-26.1`, `FloydAddons-26.1.2`, `FloydAddons-26.2`), so a fix can be downloaded from the
Actions run without cutting a release. Merging to `main` does **not** bump a version and does
**not** publish a release.

## Cutting a release (only when asked)

1. Ask first: **"should I make a release?"** — this is the owner's call, never automatic.
2. Bump `mod_version` in `gradle.properties` and write `release-notes-<version>.md`.
3. Push that to `main` and let CI go green for `26.1`, `26.1.2`, and `26.2`.
4. Perform the configured live-client `/state` assertions and capture a fresh screenshot.
5. Tag the exact commit: `git tag v<version> && git push origin v<version>`.
6. CI publishes the GitHub release for that tag with the three runtime jars and
   `SHA256SUMS-v<version>.txt` attached. A tag that does not match `mod_version` fails the job on
   purpose.
7. Publish one Modrinth version for each supported Minecraft version (separate action — needs
   explicit authorization).
8. On every Modrinth version, declare both of these dependencies as **required**:
   - Fabric API — project ID `P7dR8mSH`
   - Fabric Language Kotlin — `Ha28R6CL`
9. Verify the GitHub and Modrinth metadata, then download and hash-check every remote jar against
   the corresponding local build.

## Release asset contract

- `FloydAddons-<version>-26.1.jar`
- `FloydAddons-<version>-26.1.2.jar`
- `FloydAddons-<version>-26.2.jar`
- `SHA256SUMS-v<version>.txt` — `sha256sum` output for the three jars, `-sources` jars excluded.

## Repairing a release that shipped without jars

Tag-driven publishing means an old release can be repaired without rewriting history:

```bash
git worktree add --detach /tmp/rel-<version> v<version>
cd /tmp/rel-<version> && ./gradlew :26.1:build :26.1.2:build :26.2:build
cd versions && sha256sum */build/libs/FloydAddons-<version>-*.jar | grep -v sources > /tmp/SHA256SUMS-v<version>.txt
gh release upload v<version> <jars> SHA256SUMS-v<version>.txt --clobber --repo lunabot9/FloydAddons
gh release download v<version> --repo lunabot9/FloydAddons --pattern '*' -D /tmp/verify   # hash-check
```
