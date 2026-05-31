package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod
import gg.floyd.features.Category
import gg.floyd.features.Module
import net.minecraft.resources.Identifier

/**
 * Globally renders Minecraft text in the bundled Inter font.
 *
 * It works by redirecting the default font set ([Font.getFontSet] via CustomFontMixin) to a
 * `floydaddons:inter` font that layers the Inter TTF over the vanilla glyph providers. Because:
 *  - glyphs absent from Inter (icons / private-use symbols) fall through to the vanilla providers,
 *    so they still render and keep their advance widths (fixes text jumping to the corner);
 *  - color and bold/italic are applied by vanilla [Font] exactly as before;
 *  - it is still vanilla [Font] rendering into the main framebuffer, so it blurs identically to
 *    the text it replaces under pause/overlay blur.
 */
object FloydCustomFont : Module(
    name = "Custom Font",
    category = Category.RENDER,
    description = "Renders all game text in the Inter font, with vanilla fallback for icons.",
) {
    private val DEFAULT_FONT: Identifier = Identifier.fromNamespaceAndPath("minecraft", "default")
    private val INTER_FONT: Identifier = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "inter")

    /** Redirects the default font to Inter while enabled. Called from CustomFontMixin. */
    @JvmStatic
    fun redirect(original: Identifier): Identifier =
        if (enabled && original == DEFAULT_FONT) INTER_FONT else original
}
