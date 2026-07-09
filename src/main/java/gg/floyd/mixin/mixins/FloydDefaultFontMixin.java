package gg.floyd.mixin.mixins;

import com.mojang.datafixers.util.Pair;
import gg.floyd.utils.FloydFontProviders;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Lets Floyd's global custom-font override be toggled and filtered by rewriting providers loaded
 * for {@code minecraft:default}, while also adjusting Floyd-owned panel/ClickGUI font ids.
 */
@Mixin(FontManager.class)
public class FloydDefaultFontMixin {

    @SuppressWarnings("unchecked")
    @Inject(method = "loadResourceStack", at = @At("RETURN"), cancellable = true)
    private static void floydaddons$adjustDefaultFont(
            List<Resource> resources,
            Identifier fontId,
            CallbackInfoReturnable<List<Pair<?, GlyphProviderDefinition.Conditional>>> cir) {
        List<Pair<?, GlyphProviderDefinition.Conditional>> loaded = cir.getReturnValue();
        List<Pair<?, GlyphProviderDefinition.Conditional>> adjusted =
                FloydFontProviders.adjustDefaultFont(fontId, loaded);
        if (adjusted != loaded) {
            cir.setReturnValue(adjusted);
        }
    }
}
