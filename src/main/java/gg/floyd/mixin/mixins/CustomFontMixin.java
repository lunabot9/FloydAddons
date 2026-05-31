package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.render.FloydCustomFont;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Global font override: while {@link FloydCustomFont} is enabled, every resolution of the default
 * font ({@code minecraft:default}) is redirected to {@code floydaddons:inter}, which layers the
 * Inter TTF over the vanilla glyph providers (vanilla fallback for glyphs Inter lacks). Because it
 * is still vanilla {@link net.minecraft.client.gui.Font} rendering, color, bold/italic and blur all
 * behave exactly as for normal text.
 */
@Mixin(FontManager.class)
public class CustomFontMixin {

    @ModifyVariable(method = "getFontSetRaw", at = @At("HEAD"), argsOnly = true)
    private Identifier floydaddons$redirectDefaultFont(Identifier original) {
        return FloydCustomFont.redirect(original);
    }
}
