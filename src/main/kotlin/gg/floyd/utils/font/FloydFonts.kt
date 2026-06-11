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
 * The mod's pinned [Font] surfaces, each a remap of `mc.font`'s session-lived provider (the same
 * instance across resource reloads, so these wrappers never go stale) sending the DEFAULT font
 * description to a Floyd-owned FontSet instead of `minecraft:default`:
 *
 *  - [vanilla]   → `floydaddons:vanilla` (`font/vanilla.json` — the exact vanilla provider refs,
 *    untouched by [gg.floyd.utils.FloydFontProviders]); the vanilla bitmap font even while the
 *    global override restyles `minecraft:default`.
 *  - [panelCustom] → `floydaddons:panel` (`font/panel.json` — rewritten by FloydFontProviders with
 *    the SAME runtime-metrics/BYO/MSDF treatment the enabled global override gives the default
 *    font, but never dropped); the custom font even while `minecraft:default` is vanilla.
 *
 * The HUD panels pick between the two via [gg.floyd.features.impl.render.FloydFont.panelFont] —
 * per-panel toggles are pure instance selection, so they apply INSTANTLY (no resource reload).
 * Plain-string draw/measure calls resolve through `Style.EMPTY` → [FontDescription.DEFAULT], so
 * every run lands in the pinned FontSet while explicit style fonts pass through untouched. Both
 * rendering and measurement use the same instance (the D6 invariant: widths come from the exact
 * FontSet the glyphs render with).
 */
object FloydFonts {

    @JvmField
    val VANILLA_FONT_ID: Identifier = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "vanilla")

    @JvmField
    val PANEL_FONT_ID: Identifier = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "panel")

    // Lazy because mc.font must exist (client init); render-thread only, like every caller.
    val vanilla: Font by lazy(LazyThreadSafetyMode.NONE) { remapped(VANILLA_FONT_ID) }

    val panelCustom: Font by lazy(LazyThreadSafetyMode.NONE) { remapped(PANEL_FONT_ID) }

    /** A [Font] over mc.font's provider with [FontDescription.DEFAULT] remapped to [target]. */
    internal fun remapped(target: Identifier): Font {
        val base = (mc.font as FontProviderAccessor).`floydaddons$getProvider`()
        val description = FontDescription.Resource(target)
        return Font(object : Font.Provider {
            override fun glyphs(requested: FontDescription): GlyphSource =
                base.glyphs(if (requested == FontDescription.DEFAULT) description else requested)

            override fun effect(): EffectGlyph = base.effect()
        })
    }
}
