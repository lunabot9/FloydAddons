package gg.floyd.utils.font

import gg.floyd.utils.FloydFontProviders
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
 *    the selected runtime metrics and BYO font, with MSDF confined to this owned
 *    font, but never dropped); the custom font even while `minecraft:default` is vanilla.
 *
 * Scoreboard and inventory HUD use [gg.floyd.features.impl.render.FloydFont.panelFont] to keep
 * the custom font independently of the global Font module's toggle.
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

    /** Inspect baked glyphs, rather than settings alone, for shared-font compatibility checks. */
    fun glyphState(): Map<String, Any> {
        val base = (mc.font as FontProviderAccessor).`floydaddons$getProvider`()
        fun glyphs(description: FontDescription) = "Au/016".map { char ->
            val glyph = base.glyphs(description).getGlyph(char.code)
            mapOf("character" to char.toString(), "type" to glyph.javaClass.simpleName,
                "msdf" to (glyph is FloydMsdfGlyph), "advance" to glyph.info().getAdvance(false),
                "guiMsdf" to ((glyph as? gg.floyd.utils.FloydTtfGlyph)?.`floyd$getGuiGlyph`() is FloydMsdfGlyph),
                "linearFilteredTtf" to ((glyph as? gg.floyd.utils.FloydTtfGlyph)?.`floyd$usesLinearFiltering`() == true))
        }
        return mapOf(
            "defaultGlyphs" to glyphs(FontDescription.DEFAULT),
            "panelGlyphs" to glyphs(FontDescription.Resource(PANEL_FONT_ID)),
            "symbolGlyphs" to listOf(0x263D, 0x23E3, 0xE001).map { cp ->
                val panel = (panelCustom as FontProviderAccessor).`floydaddons$getProvider`()
                    .glyphs(FontDescription.DEFAULT).getGlyph(cp)
                val live = base.glyphs(FontDescription.DEFAULT).getGlyph(cp)
                mapOf("codepoint" to cp, "usesLiveGlyph" to (panel === live), "advance" to panel.info().getAdvance(false))
            },
            "skyblockerMsdf" to SkyblockerMsdfCompat.state(),
            "worldFixture" to WorldFontCompatFixture.state(),
        )
    }

    /** A [Font] over mc.font's provider with [FontDescription.DEFAULT] remapped to [target]. */
    internal fun remapped(target: Identifier): Font {
        val base = (mc.font as FontProviderAccessor).`floydaddons$getProvider`()
        val description = FontDescription.Resource(target)
        return Font(object : Font.Provider {
            override fun glyphs(requested: FontDescription): GlyphSource =
                if (requested == FontDescription.DEFAULT) withLiveSymbols(base.glyphs(description), base.glyphs(requested))
                else base.glyphs(requested)

            override fun effect(): EffectGlyph = base.effect()
        })
    }
    /** Pinned fonts own letters; server/resource-pack symbols resolve through the live default. */
    internal fun withLiveSymbols(custom: GlyphSource, live: GlyphSource): GlyphSource = object : GlyphSource {
        override fun getGlyph(codepoint: Int) =
            (if (FloydFontProviders.shouldUseCustomFontGlyph(codepoint)) custom else live).getGlyph(codepoint)

        override fun getRandomGlyph(random: net.minecraft.util.RandomSource, codepoint: Int) =
            (if (FloydFontProviders.shouldUseCustomFontGlyph(codepoint)) custom else live).getRandomGlyph(random, codepoint)
    }

}
