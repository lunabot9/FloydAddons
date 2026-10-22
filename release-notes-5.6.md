# Floyd Addons 5.6

## Added
- Sack Supercraft in QoL. Use `/supercraft` for grouped, clickable regular and enchanted sack names and command completion, or start from the button in an opened sack.
- Craft eligible resources through their highest supported tiers: open the recipe, shift-click the pickaxe to select the maximum, craft, then continue through the remaining items.
- A left-side sack panel with the Supercraft Sack button at the top. Click item icons to save exclusions; ignored items show a red X. `/supercraft except <item>` and `/supercraft allow <item>` also work, and automatic crafting when opening sacks is optional.
- Optional snowfall inside Floyd's settings GUI, off by default and controlled from Theme.

## Improved
- Matched the title-screen background to Midnight Silver while keeping distinct blue, violet, and silver aurora ribbons.
- Added visible near, middle, and distant snow behind the title-screen buttons, with separate sizes, speeds, and drift.
- Gave title-screen snow its own Theme toggle, separate from GUI snow.
- Removed stretching and grid-line artifacts from the snow effect, softened its edges, and prevented animation-clock wraparound jumps.
- Matched Supercraft detection to the real recipe screen and golden-pickaxe tooltip, including recipe screens titled with only the item's name.

## Crafting behavior
Sack Supercraft waits for the updated maximum amount and matching recipe costs before crafting, then waits for confirmation before moving on. Missing confirmations or unsupported menu layouts stop the run. The server's maximum can include matching materials in inventory and other storage. Forge, trade, and equipment recipes are excluded.

## Validation and downloads
Built and tested for Minecraft **26.1**, **26.1.2**, and **26.2**, with fresh offline client checks. Live Hypixel behavior remains unverified. **Fabric API** and **Fabric Language Kotlin** are required. Choose the matching runtime JAR; SHA-256 checksums are attached.
