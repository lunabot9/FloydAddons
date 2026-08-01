package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.misc.FloydMenuScreenStyling;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen", remap = false)
public abstract class FloydReesesVideoSettingsScreenMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
    private void floydaddons$renderStableBackground(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        FloydMenuScreenStyling.renderBackground(context);
    }
}
