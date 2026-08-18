# FloydAddons 2.4.9

This release fixes new SkyBlock player heads rendering as null/empty items (in
the inventory, in hand, and on the ground) whenever the pack disabler is active,
for Minecraft `26.1`, `26.1.2`, and `26.2`.

Changes in this release:

- **Fixed null SkyBlock player head items.** New SkyBlock heads no longer need a
  hand-maintained registry entry to render. Head skin textures referenced by the
  live pack's `minecraft:head` item definitions are now auto-discovered and
  preserved, so a freshly released head resolves and displays its intended
  texture instead of showing a null/empty item.
- **Fixed NBT-profile heads as well.** Heads whose look is carried in the item's
  own profile NBT now fall back to the vanilla player-head model when their pack
  texture is unavailable, so their skin still renders instead of going blank.
- Added unit/regression coverage for the render and pack-disabler paths; the
  build matrix is green across 26.1, 26.1.2, and 26.2.

Downloads:

- `FloydAddons-2.4.9-26.1.jar` for Minecraft `26.1`
- `FloydAddons-2.4.9-26.1.2.jar` for Minecraft `26.1.2`
- `FloydAddons-2.4.9-26.2.jar` for Minecraft `26.2`
- `SHA256SUMS-v2.4.9.txt` for artifact verification

Modrinth builds require both Fabric API and Fabric Language Kotlin.
