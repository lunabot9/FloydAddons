FloydAddons 2.4.5

This release finishes the ClickGUI text and layout cleanup across search fields,
setting rows, and Minecraft menu screens, while also refreshing the matching
FoidAddons builds for all supported Minecraft versions.

Changes in this release:
- Centered ClickGUI search text inside its pill and aligned typed text with the
  search-field background.
- Removed duplicate search placeholders while a field is active.
- Added width-aware sizing for long setting names, selected values, keybinds,
  sliders, dropdowns, HUD controls, and searchable-list headers so labels no
  longer overlap neighboring buttons or values.
- Removed duplicate Minecraft screen titles and expanded title clipping so
  Singleplayer, Multiplayer, and Options headings render once without being cut
  off.
- Rebuilt the FoidAddons variants through the Gradle branding pipeline so their
  generated Fabric metadata retains all bundled NanoVG and MSDF dependencies.
- Published matching FoidAddons versions with Fabric API and Fabric Language
  Kotlin marked as required dependencies on Modrinth.

Included GitHub artifacts:
- `FloydAddons-2.4.5-26.1.jar`
- `FloydAddons-2.4.5-26.1.2.jar`
- `FloydAddons-2.4.5-26.2.jar`

Matching FoidAddons builds are available from the FoidAddons Modrinth project.
