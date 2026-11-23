package gg.floyd.mixin.mixins;

import gg.floyd.utils.FloydTtfGlyph;
import gg.floyd.utils.font.FloydGuiWorldRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BakedSheetGlyph.class)
public abstract class FloydTtfGlyphMixin implements FloydTtfGlyph {
    @Unique private boolean floyd$linearFiltering;
    @Unique private BakedGlyph floyd$guiGlyph;

    @Override
    public boolean floyd$usesLinearFiltering() { return floyd$linearFiltering; }

    @Override
    public void floyd$setLinearFiltering() { floyd$linearFiltering = true; }
    @Override
    public BakedGlyph floyd$getGuiGlyph() { return floyd$guiGlyph; }

    @Override
    public void floyd$setGuiGlyph(BakedGlyph glyph) { floyd$guiGlyph = glyph; }

    @Inject(method = "createGlyph", at = @At("RETURN"), cancellable = true)
    private void floyd$guiMsdf(float x, float y, int color, int shadowColor, Style style,
            float boldOffset, float shadowOffset, CallbackInfoReturnable<TextRenderable.Styled> cir) {
        if (floyd$guiGlyph != null) {
            cir.setReturnValue(new FloydGuiWorldRenderable(cir.getReturnValue(),
                    floyd$guiGlyph.createGlyph(x, y, color, shadowColor, style, boldOffset, shadowOffset)));
        }
    }
}

