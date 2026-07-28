What's new

- Fixed the Auto Clicker clicking far slower than its configured CPS, especially when left-clicking into air and hitting vanilla's miss cooldown.
- Tightened the Auto Clicker CPS timing so the configured min/max range maps much more closely to the actual click rate.
- Restored the SkyBlock Pack Disabler module, its fallback assets, and the Legacy GUI controls that were removed by mistake.

Improvements and fixes

- Changed the Auto Clicker scheduler to catch up from the prior scheduled deadline instead of dropping overdue clicks when the game tick rate is slower than the requested delay.
- Removed the old out-of-band timing jitter that could push effective CPS outside the configured range.
- Bypassed vanilla's `missTime` throttle only while Floyd's Auto Clicker is actively driving left-click attack input, avoiding the old ~2 CPS cap on misses.
- Added focused Auto Clicker regression coverage for CPS-range timing and catch-up scheduling behavior.
- Re-added the SkyBlock Pack Disabler test coverage and resource-pack fallback assets on top of the clicker fixes.

Downloads

- FloydAddons-2.3.3-26.1.jar
- FloydAddons-2.3.3-26.1.2.jar
- FloydAddons-2.3.3-26.2.jar
