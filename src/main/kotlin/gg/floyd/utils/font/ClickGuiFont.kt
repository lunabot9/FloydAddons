package gg.floyd.utils.font

import gg.floyd.FloydAddonsMod
import net.minecraft.client.gui.Font
import net.minecraft.resources.Identifier

/**
 * The ClickGUI's pinned font: a [Font] over the `floydaddons:clickgui` FontSet
 * (`assets/floydaddons/font/clickgui.json` — always the bundled `font.ttf`, MSDF-upgraded by
 * [gg.floyd.utils.FloydFontProviders] when the natives are available), so the ClickGUI keeps the
 * Floyd custom font no matter what the global Font module does to `minecraft:default` (toggled
 * off → safe bundled fallback, or a BYO .ttf — both used to restyle only the normal text range).
 *
 * Unlike the per-panel surfaces ([FloydFonts.panelCustom], toggleable and BYO-following), this is
 * unconditional and bundled-only: the ClickGUI has no font toggle by design. The remap mechanics
 * (and the D6 measurement invariant) are documented on [FloydFonts.remapped].
 */
object ClickGuiFont {

    /** The pinned ClickGUI font id; [gg.floyd.utils.FloydFontProviders] special-cases it. */
    @JvmField
    val FONT_ID: Identifier = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "clickgui")

    // Lazy because mc.font must exist (client init); render-thread only, like every caller.
    val font: Font by lazy(LazyThreadSafetyMode.NONE) { FloydFonts.remapped(FONT_ID) }
}
