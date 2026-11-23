# Floyd Addons 5.7

## New features

- Item Search: browse SkyBlock items without REI using `/fa items`, an optional inventory sidebar, and Ctrl+F search. View item lore, crafting ingredients, pet upgrades, NPC purchases, and other acquisition sources, with clickable navigation between related items.
- Sack Tracker: shift-click sack items to track their counts on a movable HUD, with controls for removing tracked entries and support for gemstone grades.
- Mining Ability Notification: show a ready alert and play a configurable notification sound. Local MP3 files can be selected for the sound.
- Custom Sky: added Midnight Aurora and Cloud Nine. Midnight Aurora follows the menu's blue, violet, and silver palette; Cloud Nine adds animated volumetric clouds over a glass ocean.

## Improvements and fixes

- Cloud Nine quality modes reduce rendering cost using cached procedural noise and, in Balanced and Performance modes, reused sky frames. Horizon, water-boundary, and noise-filtering fixes improve stability while moving.
- Added Oringo font choices and a font-picker preview, improved symbol fallback and world text, and added compatibility handling for Skyblocker and MaLiLib text rendering.
- Companion now offers Chatterbox and optional OmniVoice speech engines, an Install Voice Runtime action, and an updated voice test. Local voice runtimes and model downloads are required separately.
- Improved experiment-table target selection during continuing sessions and required interaction with the identified furniture hitbox.
- Improved sack stock reading for the tracker and preserved calculator history between uses.

## Downloads

Choose the public JAR matching Minecraft **26.1**, **26.1.2**, or **26.2**. Fabric API and Fabric Language Kotlin are required. SHA-256 checksums are included.

This release contains the public build only. Private extensions and profile-installed JARs are excluded.

## Validation

All three Minecraft targets built successfully and passed 428 automated tests each. A fresh offline 26.1.2 client verified Item Search navigation and Cloud Nine rendering with state assertions and screenshots. Live multiplayer behavior and the optional voice-model downloads were not re-tested for this release.
