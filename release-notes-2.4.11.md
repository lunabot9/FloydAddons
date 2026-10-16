# FloydAddons 2.4.11

This release makes player models user-facing: the bundled models are now seeded into
`config/floydaddons/models/`, and any OBJ or GLB you drop there can be selected and rendered,
for Minecraft `26.1`, `26.1.2`, and `26.2`.

Changes in this release:

- **`config/floydaddons/models/` is now a real model folder.** On first use the bundled models
  (`low_poly_tung.obj`, `tung_tung_sahur.glb`, `jenny_dressed.json`), their textures, and their
  credit files are copied there, so the defaults can be inspected, replaced, or copied as a
  starting point instead of only living inside the jar.
- **Add your own model.** Put an OBJ or GLB in that folder, set the new `Model File` setting on
  the Player Model module, and your player renders it. `Open Model Folder`, `List Model Files`,
  and `Reload Model Files` actions are included.
- **Textures and scale are automatic.** `<name>.png` next to the model is used as its texture
  (a flat texture is used when there is none), and models are fitted to player height so any
  export unit scale works. OBJ player parts are driven by the usual group names (`body`,
  `left_arm`, `right_arm`, `left_leg`, `right_leg`, `baseitem`) — a plain single-object export
  still renders as one rigid body — and GLB run/hit animations work as they do for the bundled
  model.
- **Fixed model texture seeding.** The bundled textures live under
  `textures/entity/player_model`, so they are now copied next to the seeded models and a file
  model is no longer left with the flat fallback texture.
- Model files apply to your own player only and are never published to the shared cosmetics
  directory: other clients cannot resolve a file that exists on one disk, so publishing it would
  render the wrong model for them. OBJ and GLB are the formats the renderer parses; no new
  dependency was added.

Downloads:

- `FloydAddons-2.4.11-26.1.jar` for Minecraft `26.1`
- `FloydAddons-2.4.11-26.1.2.jar` for Minecraft `26.1.2`
- `FloydAddons-2.4.11-26.2.jar` for Minecraft `26.2`
- `SHA256SUMS-v2.4.11.txt` for artifact verification

Modrinth builds require both Fabric API and Fabric Language Kotlin.
