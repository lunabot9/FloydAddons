# FloydAddons 5.3

A fully independent Floyd settings screen, customizable themes, animated procedural skies,
and cleaner HUD rendering for Minecraft 26.1, 26.1.2, and 26.2.

## Menus and themes

- Floyd's primary UI now runs as its own screen. Its branding, theme, sounds, and HUD behavior are isolated from the actual OneConfig menu.
- Added a bottom-left Theme page with six presets: Golden Amber, Nebula Violet, Glacier Blue, Emerald Grove, Rose Quartz, and Midnight Silver, plus custom color controls.
- Added Edit HUD above Theme; closing the HUD editor returns to Floyd's settings screen.
- Polished rounded sidebar hover states, sliding category/preset highlights, preset outlines, outer borders, and the full-height divider.
- Improved search, slower smooth scrolling, descriptive action labels, and inline previous/next arrows for file selections.
- Retained opening/closing fades without Floyd menu music or opening/closing sounds. Fixed the opening key being typed into focused text fields and unwanted HUD-editor previews from other mods.

## HUD and notifications

- Added Notifier under Render: animated bottom-right toggle notifications with duration, visible-card limit, menu visibility, and centered plus/minus badges.
- Added global and per-panel border switches. New panel styles default to Box blur at strength 20.
- Improved frosted backgrounds with lighter tint, dense separable blur to remove sampling-grid artifacts, and a smoother, lighter exterior fade that stays outside panel edges.
- Custom fonts are off by default for new installs. Renamed Scoreboard Larp to Scoreboard Name while migrating saved values.

## Custom skies

- Added six animated 360-degree procedural skies under Render: Golden Aurora, Nebula, Emerald Aurora, Crimson Eclipse, Sapphire Galaxy, and Rose Clouds.
- Skies are generated in shaders, rather than animated photographs. Golden Aurora uses a starfield without the menu's water, and Rose Clouds has sparser cloud banks and clearer gaps.
- Added Performance, Balanced, and High rendering quality. Balanced renders expensive gas/cloud layers at reduced resolution while keeping stars sharp; lighter skies remain native resolution.
- Fixed sky/terrain flickering and preserved world depth and renderer state.

## Other fixes

- Corrected detached-camera/freecam frustum positioning so terrain culling follows the camera.
- Config loading stays quiet instead of generating toggle notifications.

## Downloads and requirements

Choose the jar matching your Minecraft version:

- `FloydAddons-5.3-26.1.jar`
- `FloydAddons-5.3-26.1.2.jar`
- `FloydAddons-5.3-26.2.jar`
- `SHA256SUMS-v5.3.txt` contains SHA-256 checksums for all three jars.

Requires Java 25, Fabric Loader 0.19.5 or newer, matching Fabric API, and Fabric Language Kotlin 1.14.1+kotlin.2.4.20 or newer. Settings libraries are bundled. Existing Floyd settings are preserved.

All three targets were built and tested locally. UI, HUD, notifier, and sky changes were also checked in the isolated Minecraft 26.1.2 client; this is separate from the three-version build verification.
