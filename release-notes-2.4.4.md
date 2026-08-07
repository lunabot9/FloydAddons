FloydAddons 2.4.4

This release restores all Calculator text in the scaled HUD panel and improves the
Windows live-client verification workflow used to prove visual changes.

Changes in this release:
- Fixed the Calculator title, mode label, display value, and button labels disappearing
  when deferred Minecraft-font replay was used inside the scaled NanoVG HUD PIP.
- Kept Calculator text in the same immediate NanoVG frame as its shapes so transforms
  remain consistent.
- Added repo-level testing expectations and tracked the Floyd live-client skill in its
  standard `.agents/skills` location.
- Split Loom and packaged-client verification so Loom never receives a duplicate jar,
  requires a version-specific `runClient`, and uses its own bridge port.
- Added Windows guidance for managed-session port checks, hidden/behind-window launches,
  safe Gradle shutdown, and config-preserving profile deployment.
- Updated release automation to publish the version's real changelog when present.

Included artifacts:
- `FloydAddons-2.4.4-26.1.jar`
- `FloydAddons-2.4.4-26.1.2.jar`
- `FloydAddons-2.4.4-26.2.jar`
