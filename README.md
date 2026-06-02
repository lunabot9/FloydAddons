# Floyd Addons

[.gg/floyd](https://discord.gg/floyd)

A Fabric **1.21.11** client mod: a module suite with a ClickGUI, HUD, ESP, and
PvP utilities. Mod id `floydaddons`, package root `gg.floyd`.

The module registry, settings, config persistence, event bus, render batching,
and ClickGUI are built on a Fabric scaffold originally derived from
[Odin](https://github.com/odtheking/Odin) (see `THIRD_PARTY_NOTICES.md` for
attribution). Behavior runs against official Mojang mappings.

## Source layout

- `src/main/kotlin/gg/floyd` — mod entry, module registry, settings, ClickGUI, features.
- `src/main/java/gg/floyd/mixin` — Mojang-mapped behavior mixins and accessors.
- `src/main/resources` — Fabric metadata (`fabric.mod.json`, `floydaddons.mixins.json`) and assets under `assets/floydaddons`.

## Module categories

`Render`, `Hiders`, `Player`, `Camera`, `Cosmetic`, `PvP`, `QOL`, `Misc`.

- **Render** — `FloydRender`, `FloydXray`, `FloydAnimations`, `FloydHud`, `FloydMobEsp`, `FloydBlockSearch`
- **Hiders** — `FloydHiders`
- **Player** — `FloydNickHider`, `FloydPlayerSize`
- **Camera** — `FloydCamera`
- **Cosmetic** — `FloydSkin`, `FloydCape`, `FloydConeHat`
- **PvP** — `FloydAutoTotem`, `FloydPlayerEsp`
- **Misc** — `FloydDiscordPresence`, `FloydLocalControl`, `FloydCompatibility`

## Build

```bash
./gradlew build        # produces build/libs/FloydAddons-0.1.0.jar
./gradlew test         # run the Kotlin test suites
./gradlew runClient    # launch a dev client (DevAuth)
```

The built jar requires Fabric Loader, Fabric API, and Fabric Language Kotlin
(see `fabric.mod.json` for pinned versions).

## Licensing

Declared `BSD-3-Clause AND MIT`. See `LICENSE` and `THIRD_PARTY_NOTICES.md` for
upstream attribution (Odin scaffold and bundled libraries).
