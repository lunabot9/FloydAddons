package gg.floyd.utils.font

import gg.floyd.FloydAddonsMod
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.mixin.accessors.FontProviderAccessor
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GlyphSource
import net.minecraft.client.gui.font.glyphs.EffectGlyph
import net.minecraft.network.chat.FontDescription
import net.minecraft.resources.Identifier

/**
 * The ClickGUI's pinned font: a [Font] over the `floydaddons:clickgui` FontSet
 * (`assets/floydaddons/font/clickgui.json` — always the bundled `font.ttf`, MSDF-upgraded by
 * [gg.floyd.utils.FloydFontProviders] when the natives are available), so the ClickGUI keeps the
 * Floyd custom font no matter what the global Font module does to `minecraft:default` (toggled
 * off → vanilla bitmap, or a BYO .ttf — both used to restyle the ClickGUI too).
 *
 * Implementation: wraps `mc.font`'s [Font.Provider] (FontManager's session-lived cached provider —
 * the same instance across resource reloads, so this wrapper never goes stale) and remaps the
 * DEFAULT font description to `floydaddons:clickgui`. Plain-string draw/measure calls resolve
 * through `Style.EMPTY` → [FontDescription.DEFAULT], so every ClickGUI text run lands in the
 * pinned FontSet while explicit style fonts (none in the ClickGUI today) pass through untouched.
 * Both rendering ([gg.floyd.utils.render.NvgTextReplay]) and measurement
 * ([MsdfFontMetrics] via NVGRenderer) use this same instance, preserving the D6 invariant that
 * layout widths come from the exact FontSet the glyphs render with.
 */
object ClickGuiFont {

    /** The pinned ClickGUI font id; [gg.floyd.utils.FloydFontProviders] special-cases it. */
    @JvmField
    val FONT_ID: Identifier = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "clickgui")

    private val DESCRIPTION = FontDescription.Resource(FONT_ID)

    // Lazy because mc.font must exist (client init); render-thread only, like every caller.
    val font: Font by lazy(LazyThreadSafetyMode.NONE) {
        val base = (mc.font as FontProviderAccessor).`floydaddons$getProvider`()
        Font(object : Font.Provider {
            override fun glyphs(description: FontDescription): GlyphSource =
                base.glyphs(if (description == FontDescription.DEFAULT) DESCRIPTION else description)

            override fun effect(): EffectGlyph = base.effect()
        })
    }
}
