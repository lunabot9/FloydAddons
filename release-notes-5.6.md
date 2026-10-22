# Floyd Addons 5.6

## Added

- Collapsible modules: only the enable row appears initially. A plain rotating arrow beside the switch slides settings open and closed inside a full-width card. Toggles work independently, and search opens matching settings automatically.
- Sack Supercraft in QoL, with clickable regular and enchanted sack names through `/supercraft` and a button inside supported sacks.
- A rounded sack panel with spaced upgrade icons, smooth X markers for ignored outputs, and a clear "No upgrades" message for sacks without crafting upgrades.
- Optional snow inside Floyd's settings GUI, off by default and controlled from Theme.

## Supercraft fixes and improvements

- Normal sacks upgrade materials once into their direct enchanted counterpart. Wheat becomes Enchanted Wheat, sugar cane becomes Enchanted Sugar, and sunflowers become Enchanted Sunflowers; bread and sunflower oil recipes are excluded.
- Enchanted sacks continue through supported resource compression tiers. Gemstones compact Rough → Flawed → Fine and stop at Fine.
- Read gemstone grade counts separately instead of treating the weighted "Stored" total as Rough gemstones, fixing repeated recipes and stalled upgrades.
- Added Chili Pepper → Stuffed Chili Pepper to the normal Combat Sack.
- Ignore lists display upgrade targets and prevent crafting into ignored outputs. Saved exclusions and `/supercraft except` and `/supercraft allow` remain supported.
- Shift-click the real golden pickaxe to select the maximum, verify its updated amount and recipe costs, then craft and wait for confirmation before continuing. Decorative gemstone glyphs no longer enter recipe commands.
- Supercraft controls and bottom-right notifications keep the GUI font even when the Font module is off.

## Visual improvements

- Matched the title-screen background to Midnight Silver while keeping distinct blue, violet, and silver aurora ribbons.
- Added layered snow behind the title-screen buttons, with separate depths, sizes, speeds, and drift, plus its own Theme toggle.
- Removed snow stretching and grid artifacts, softened edges, and prevented animation-clock jumps.

## Downloads and validation

Built and tested for Minecraft 26.1, 26.1.2, and 26.2, with fresh offline client checks for crafting and the settings GUI. Live-server compatibility remains unverified. Fabric API and Fabric Language Kotlin are required. Choose the matching runtime JAR; updated SHA-256 checksums are attached.

Supercraft stops when confirmation is missing or a menu is unsupported. The server's maximum may include matching materials from inventory and other storage. Forge, trade, and equipment recipes are excluded.
