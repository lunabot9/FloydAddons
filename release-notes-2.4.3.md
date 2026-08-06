FloydAddons 2.4.3

This release packages the current `main` branch state for Minecraft `26.1`, `26.1.2`, and `26.2`.

Changes in this release:
- Added the module browser flow to the legacy ClickGUI routing and pointed the `/fa` and Local Control GUI entrypoints at the active ClickGUI.
- Hardened loadout activation so it only fires when a player is available and covered the new behavior with focused tests.
- Refreshed SkyBlock Pack Disabler fallbacks so missing-item placeholders resolve to vanilla items instead of paper or nulls, including Terminator, Superboom TNT, honey accessories, Architect's First Draft, Lumberjack accessories, and Torrhus accessories.
- Updated the fallback asset bundle and pack-disabler tests to cover the newer live-pack and fallback cases.

Included artifacts:
- `FloydAddons-2.4.3-26.1.jar`
- `FloydAddons-2.4.3-26.1.2.jar`
- `FloydAddons-2.4.3-26.2.jar`
