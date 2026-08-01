package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.misc.FloydMenuScreenStyling;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class FloydMenuOverlayMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void floydaddons$renderMenuBackgroundInline(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (FloydMenuScreenStyling.shouldUseDirectBackground(screen)) {
            return;
        }
        if (FloydMenuScreenStyling.shouldReplaceBackground(screen)) {
            FloydMenuScreenStyling.renderBackground(context);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void floydaddons$renderMenuOverlay(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        FloydMenuScreenStyling.renderOverlay((Screen) (Object) this, context, partialTick);
    }
}
