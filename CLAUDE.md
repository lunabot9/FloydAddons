# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FloydAddons is a client-side Minecraft Fabric mod supporting **Minecraft 1.21.10** and **1.21.11**. It provides nick hiding, skin swapping, inventory HUD, ClickGUI, and stealth features. Written in Java 21.

## Branches & Instances

| Branch | Minecraft Version | Prism Launcher Instance | Mods Path |
|--------|-------------------|-------------------------|-----------|
| `main` | 1.21.10 | `1.21.10(2)` | `~/.local/share/PrismLauncher/instances/1.21.10(2)/minecraft/mods/` |
| `1.21.11` | 1.21.11 | `taun 1.21.11` | `~/.local/share/PrismLauncher/instances/taun 1.21.11/minecraft/mods/` |

**Workflow:** Develop features on `main` (1.21.10), then merge `main` into `1.21.11` and fix any API incompatibilities. Each branch has its own CI release workflow.

### Remotes

| Remote | URL | Purpose |
|--------|-----|---------|
| `origin` | `github.com/lunabot9/FloydAddons` | Upstream (lunabot9) |
| `fork` | `github.com/twaldin/FloydAddons` | Personal fork (twaldin) |

Push to `fork`, create PRs to `origin/main`.

### 1.21.11 API Differences

When porting from 1.21.10 to 1.21.11, these MC API changes require code fixes:

- `RenderLayer.getEntityCutoutNoCull()` → `RenderLayers.entityCutoutNoCull()` (CapeFeatureRenderer, CapeManager)
- LINES vertex format requires per-vertex `.lineWidth(float)` call (MobEspRenderer, StalkRenderer)
- `RenderPhase`/`MultiPhaseParameters` → `RenderSetup`/`RenderSetup.Builder` (StalkRenderer)
- `TextRenderer.prepare()` descriptor changed from `FFIZI` to `FFIZZI` (NickHiderTextRendererMixin)
- `NativeImageBackedTexture.setFilter()` removed (CapeManager GIF textures)
- `InventoryScreen.drawEntity()` Quaternionf overload removed; use `getAndUpdateRenderState()` + `context.addEntity()` directly (ClickGuiScreen)

## Build Commands

```bash
# Build the mod (output JAR in app/build/libs/)
./gradlew clean build

# Run a dev Minecraft client
./gradlew runClient
```

Tests are disabled in this project. There is no linter configured.

## Deploying

**Always deploy after building.** Each branch has a deploy target:

```bash
# On main branch (1.21.10):
./gradlew build && ./deploy.sh

# On 1.21.11 branch:
./gradlew build && ./deploy.sh
```

The `deploy.sh` script detects which branch is checked out and copies to the correct instance:
- `main` → `~/.local/share/PrismLauncher/instances/1.21.10(2)/minecraft/mods/`
- `1.21.11` → `~/.local/share/PrismLauncher/instances/taun 1.21.11/minecraft/mods/`

## Architecture

### Entry Point & Lifecycle

`FloydAddonsClient` (`ClientModInitializer`) is the mod entry point. It registers a keybinding (N key), initializes configs on startup, registers HUD rendering callbacks, and saves configs on shutdown.

### Configuration System

`FloydAddonsConfig` is the central config manager using GSON serialization with static accessors. Config files live in `.minecraft/config/floydaddons/`:
- `config.json` — all mod settings (nick hider, skin, render)
- `name-mappings.json` — per-player nick replacements

Subsystem configs (`NickHiderConfig`, `SkinConfig`, `RenderConfig`) read/write through `FloydAddonsConfig`.

### Mixin Architecture

Mixins are registered in `floydaddons.mixins.json` with compatibility level Java 21 and `defaultRequire: 1`.

- **`NickHiderTextRendererMixin`** — Intercepts all `TextRenderer` rendering methods (`prepare`, `drawWithOutline`, `getWidth`) to perform name replacement across tab list, nametags, chat, scoreboard, action bar, and boss bars.
- **`PlayerEntityRendererMixin`** — Injects into `getTexture()` for skin swapping.
- **`BrandSpoofMixin`** — Reports "vanilla" as client brand instead of "fabric".
- **`FabricLoaderImplMixin`** — Hides the mod from Fabric loader queries (`getAllMods`, `getModContainer`, `isModLoaded`).

### GUI System

Screens extend Minecraft's `Screen` class in a stacked navigation pattern:
- `FloydAddonsScreen` (main hub) → `NickHiderScreen`, `SkinScreen`, `RenderScreen` → `MoveInventoryScreen`

Screens use chroma gradient borders, scale animations, and draggable panels.

### Nick Hider

Three modes: `OFF`, `CONFIG_ONLY` (replace only mapped players), `CONFIG_AND_DEFAULT` (replace all players with mappings overriding default). Uses case-insensitive matching with style preservation. Player list is cached every 1 second.

### Skin System

Custom PNG skins loaded from `config/floydaddons/skins/`. `SkinManager` handles texture loading via `NativeImage` and `TextureManager`. Supports applying skins to self and/or others independently.

### Inventory HUD

`InventoryHudRenderer` draws a movable 9x3 inventory grid overlay with chroma rainbow border. Position and scale persist through `RenderConfig`.

## Troubleshooting

Check game logs for mixin errors or runtime issues:
```bash
# 1.21.10 instance logs
cat ~/.local/share/PrismLauncher/instances/1.21.10\(2\)/minecraft/logs/latest.log
grep -i 'floydaddons\|mixin.*fail' ~/.local/share/PrismLauncher/instances/1.21.10\(2\)/minecraft/logs/latest.log

# 1.21.11 instance logs
cat ~/.local/share/PrismLauncher/instances/taun\ 1.21.11/minecraft/logs/latest.log
grep -i 'floydaddons\|mixin.*fail' ~/.local/share/PrismLauncher/instances/taun\ 1.21.11/minecraft/logs/latest.log
```

## Decompilation Tools

Two Java decompilers are available at `~/tools/` for analyzing other mods' JARs:

- **Vineflower** (`~/tools/vineflower.jar`) — Preferred for Minecraft mods. Produces output matching Fabric/yarn mapping style.
  ```bash
  java -jar ~/tools/vineflower.jar input.jar output_dir/
  ```
- **CFR** (`~/tools/cfr.jar`) — General-purpose fallback, good with obfuscated code.
  ```bash
  java -jar ~/tools/cfr.jar input.jar --outputdir output_dir/
  ```

Use these when asked to port or reference features from other mod JARs.

## Build System Notes

- Uses Fabric Loom (`fabric-loom-remap` v1.14.10) — Gradle configuration cache is **disabled** due to Loom incompatibility.
- Version properties managed in `gradle.properties`.
- Multi-project Gradle setup: root `settings.gradle.kts` includes the `app` subproject.
- JDK 21 auto-downloaded via Gradle toolchains.
- Mod ID: `floydaddons`. The mod is hidden from ModMenu (marked as library).
