# FloydAddons 2.4.6

This release adds new SkyBlock render helpers, restores the independent Floyd GUI flow,
and fixes panel/text controls across Minecraft `26.1`, `26.1.2`, and `26.2`.

Changes in this release:

- Added a standalone Sparkling Critter ESP with local loaded-entity detection, optional
  tracers and hitboxes, chat coordinates, and migration from the previous Mob ESP toggle.
- Added a movable, resizable tree-fell HUD notification for WOODPECKER, PETALFALL, and
  TIMBER messages.
- Added a customizable `Scoreboard Larp` footer to Custom Scoreboard and ensured layout
  caches refresh immediately when the text changes.
- Restored the Floyd GUI hub for the Floyd/legacy commands and keybind while keeping the
  panel-based ClickGUI available independently.
- Fixed editable string settings so their text, caret, selection, and click focus use the
  rendered field bounds.
- Split panel blur enable switches from blur-kernel selectors, migrated older configs,
  and repaired rounded backdrop blur rendering for Floyd panels and the scoreboard.
- Expanded live-control state and regression coverage for the new modules, configuration
  migrations, blur options, scoreboard footer, and text input behavior.

Modrinth builds require both Fabric API and Fabric Language Kotlin.
