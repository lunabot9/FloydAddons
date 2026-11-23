# Compose compatibility candidate

Build a candidate for a specific Minecraft target:

```powershell
python scripts/test-ui-isolation.py
.\gradlew.bat :26.2:isolatedUiJar
```

The output is `versions/26.2/build/libs/FloydAddons-<version>-26.2-ui-isolated.jar`.
Use it as a replacement for the regular Floyd Addons JAR, never alongside it.
This task does not deploy into a launcher profile or publish anything.

Floyd uses OneConfig's Compose bundle. Nebula and Odin also use Compose classes,
and Fabric selecting a different runtime does not make incompatible UI APIs
compatible. This candidate relocates Floyd's `androidx`, `org.jetbrains.compose`,
and `org.polyfrost.oneconfig` classes beneath `gg.floyd.internal.uilibs` after
compilation/remapping. It also rewrites Floyd's references, nested Fabric IDs and
dependency declarations, entrypoints, mixin resources and service registrations.
Shared non-UI dependencies remain byte-for-byte unchanged. The transformer drops
invalidated signatures and compile-time Kotlin module mapping indexes only from
the modified archives. Third-party license and source metadata are retained.

The task creates a separate candidate artifact; the standard build and Loom dev
classpath are unchanged. The JVM regression reproduces a linkage error with two
incompatible APIs and verifies that the same callers succeed after isolation.
It also checks nested metadata, service registrations, idempotency and native
preservation. This does not replace the live client proof required by AGENTS.md.

Before treating the candidate as release-ready, test the packaged artifact with
Nebula on 26.2, open both overlays/settings screens, and capture fresh state and
screenshot evidence. Repeat with Odin enabled. Skiko JNI classes and binaries
retain their upstream names: this addresses Compose/OneConfig class collisions,
but does not promise compatibility between arbitrary native Skiko versions.
