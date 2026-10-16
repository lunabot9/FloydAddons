# FloydAddons 5.2

FloydAddons 5.2 introduces the new primary settings menu and improves SkyBlock
resource-pack compatibility for Minecraft 26.1, 26.1.2, and 26.2.

- Replaced the legacy primary menu with native settings controls, styled in the
  main menu's warm brown and amber palette with a stacked Floyd Addons wordmark.
- Simplified navigation to Render, Hiders, Camera, Cosmetic, QoL, and Misc.
- The primary menu opens with N or `/fa`; Right Shift opens the secondary ClickGUI.
- ClickGUI category panels open expanded by default, including compact layouts.
- Removed ambient menu music and the menu opening sound while preserving other UI sounds.
- Fixed cached settings frames disappearing during navigation or after reopening.
- Improved SkyBlock pack removal by retaining complete fallback models and textures,
  avoiding missing textures and inappropriate paper fallbacks for unregistered items.
- Includes the latest upstream player-head fixes, recording identity fixes, and
  support for user-provided player model files.

Downloads:

- `FloydAddons-5.2-26.1.jar` for Minecraft 26.1
- `FloydAddons-5.2-26.1.2.jar` for Minecraft 26.1.2
- `FloydAddons-5.2-26.2.jar` for Minecraft 26.2
- `SHA256SUMS-v5.2.txt` for artifact verification

Requires Java 25, Fabric Loader 0.19.5 or newer, a matching Fabric API, and
Fabric Language Kotlin 1.14.1+kotlin.2.4.20 or newer. The native settings libraries
are included in the runtime jars.
