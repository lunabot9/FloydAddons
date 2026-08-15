# FloydAddons 2.4.8

This release adds a guided SkyBlock Level 7 route, refreshes FloydAddons' menu
presentation, and fixes interaction and layout problems across both GUI styles for
Minecraft `26.1`, `26.1.2`, and `26.2`.

Changes in this release:

- Added a persistent 119-step SkyBlock Level 7 guide with a movable HUD, automatic
  progress detection, manual previous/next/reset controls, and live Treecapitator
  lowest-BIN guidance from SkyCofl.
- Redesigned the custom main-menu background around FloydAddons' amber palette with
  animated stars, aurora curtains, reflective water, and updated color defaults.
- Fixed custom and styled vanilla menu text alignment by measuring the exact NanoVG
  render path and using stable widget/title slots for centered labels.
- Fixed ClickGUI layouts retaining stale wrapped rows after enough horizontal space
  becomes available, and kept narrow fallback layouts visible by collapsing their
  default panels into compact header rows.
- Expanded boolean-setting clicks to the full setting row in the modern ClickGUI.
- Improved the legacy GUI with working popup slider/color/title dragging, responsive
  navigation buttons, safer text and selector layouts, visible scrollbars and
  truncation indicators, and accurate numeric-input cursor placement.
- Added regression coverage for the guide route and Treecapitator price parser, menu
  shader/layout behavior, and stale ClickGUI row recovery.

Downloads:

- `FloydAddons-2.4.8-26.1.jar` for Minecraft `26.1`
- `FloydAddons-2.4.8-26.1.2.jar` for Minecraft `26.1.2`
- `FloydAddons-2.4.8-26.2.jar` for Minecraft `26.2`
- `SHA256SUMS-v2.4.8.txt` for artifact verification

Modrinth builds require both Fabric API and Fabric Language Kotlin.
