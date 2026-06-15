package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.misc.FloydMenuScreenStyling;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class FloydMenuScreenMixin {

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void floydaddons$renderMenuBackground(GuiGraphics context, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!FloydMenuScreenStyling.shouldReplaceBackground(screen)) return;
        FloydMenuScreenStyling.renderBackground(context, partialTick);
        ci.cancel();
    }

    @Inject(method = "renderWithTooltipAndSubtitles", at = @At("TAIL"))
    private void floydaddons$renderMenuOverlay(GuiGraphics context, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        FloydMenuScreenStyling.renderOverlay((Screen) (Object) this, context, partialTick);
    }
}
