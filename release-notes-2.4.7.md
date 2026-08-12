# FloydAddons 2.4.7

This compatibility release fixes a crash when opening Devonian's HUD editor across
Minecraft `26.1`, `26.1.2`, and `26.2`.

Changes in this release:

- Fixed the SkyBlock Pack Disabler item-model hook assuming every rendered item has an
  item-model identifier.
- Added safe pass-through behavior for synthetic cross-mod HUD previews, including
  Devonian's Barrier preview item.
- Prevented the resulting emergency crash path that appeared to freeze on
  `Saving World`.
- Added regression coverage for missing item-model identifiers while preserving the
  existing Hypixel SkyBlock replacement behavior.

Verified live with Devonian `1.28.9` and the SkyBlock Pack Disabler enabled.

Modrinth builds require both Fabric API and Fabric Language Kotlin.
