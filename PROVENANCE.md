# Source Provenance

This repository is a FloydAddons-on-Odin port. The active source tree is the
combined implementation; the upstream inputs used for the port are:

| Input | Remote / path | Baseline |
| --- | --- | --- |
| Odin scaffold | `https://github.com/odtheking/Odin.git` `main` | `77b66713f74849bbcc05067484e6e85c01c96698` |
| FloydAddons upstream | `https://github.com/lunabot9/FloydAddons.git` `main` | `17c5ba3d4fa0185eb689a62f1f6c3de0d6a60b75` |
| FloydAddons fork source | `/Users/twaldin/SkyblockQOLmod` / `https://github.com/twaldin/SkyblockQOLmod.git` `main` | `17c5ba3d4fa0185eb689a62f1f6c3de0d6a60b75` |

The active Odin scaffold lives under `src/main/kotlin/com/odtheking/odin` and
`src/main/java/com/odtheking/mixin`, adapted for the `floydaddons` mod id and
Floyd feature surfaces.

The Floyd behavior source is vendored under `vendor/floydaddons-fabric` for
provenance and parity audits. Vendored Floyd GUI/editor screens are retained
only as reference material; they are not compiled into the active mod. Active
controls are represented through Odin's module, setting, config, event, and ClickGUI scaffolding.
