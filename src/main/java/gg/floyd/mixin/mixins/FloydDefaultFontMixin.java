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
 * Lets Floyd's global custom-font override be toggled off (vanilla font) or pointed at a
 * user-supplied .ttf, by rewriting the providers loaded for the {@code minecraft:default} font.
 * Wraps {@code FontManager.loadResourceStack} so the static resource-pack override in
 * {@code assets/minecraft/font/default.json} stays the default behavior while the settings decide
 * the final provider list.
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
