# Floyd settings menus

Floyd owns its primary settings screen. It reuses OneConfig's bundled control library,
but does not register a Floyd configuration page in OneConfig or restyle OneConfig's own menu.

- **N** or `/fa` opens the Floyd settings screen.
- **Right Shift** or `/fa clickgui` opens the secondary panel Click GUI.
- Mod Menu opens the same Floyd settings screen.
- `/fa legacygui` and `/fa oldgui` remain aliases for the primary screen.

The sidebar contains Render, Hiders, Camera, Cosmetic, QoL, and Misc, with **Edit HUD**
and **Theme** at the bottom. Closing the HUD editor returns to the Floyd settings screen.
The Theme page provides six presets and custom color controls, isolated from OneConfig's theme.
Category and preset highlights slide smoothly between their measured destinations.

Controls edit the existing live module settings and preserve Floyd's configuration files.
Search includes settings across categories. Action buttons have descriptive labels, and
file selectors provide inline previous/next arrows. Wheel scrolling is slower and prepared
before first use. The opening shortcut is consumed so it cannot type into a retained text field.

The Floyd menu retains its opening and closing fades, with no ambient, opening, or closing
audio. OneConfig's own audio and animations remain available in its separate menu.
The main shortcut can be changed under Render > Menus > Open Floyd Addons.

Each supported runtime jar includes the matching upstream control distribution and Compose
bundle. Fabric Loader 0.19.5 and Fabric Language Kotlin 1.14.1+kotlin.2.4.20 or newer are required.
