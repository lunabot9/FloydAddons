FloydAddons 2.4.2

What's new

- Reworked the active ClickGUI presentation with the Oringo-style panel layout, centered search/footer treatment, and cleaner per-setting rendering while preserving the underlying module behavior.
- Tightened the color picker and searchable-list editors so long labels, hex fields, and toggle rows fit cleanly instead of spilling outside their bounds.
- Fixed the active ClickGUI color picker drag path so the selector now tracks vertical mouse movement correctly instead of collapsing toward the bottom edge.

Improvements and fixes

- Refined custom-font and HUD text rendering paths, including scoreboard/font compatibility seams and safer per-panel text measurement across supported Minecraft targets.
- Updated SkyBlock pack fallback/disabler assets and related compatibility code paths for custom item rendering.
- Kept the Floor Drop ESP / world-to-screen fixes in the current release line and retained the relaxed X-Ray light-texture mixin requirement so incompatible environments fail less aggressively.
- Updated the GitHub release workflow so releases stay notes-only and Floyd/Foid jar assets are no longer uploaded to GitHub Releases.

Build targets

- Minecraft 26.1
- Minecraft 26.1.2
- Minecraft 26.2
