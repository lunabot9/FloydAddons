package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.misc.FloydMenuScreenStyling;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class FloydMenuScreenMixin {

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void floydaddons$renderMenuBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!FloydMenuScreenStyling.shouldReplaceBackground(screen)) return;
        // Only cancel the vanilla background once the media frame is actually on screen; while the
        // first frames are still decoding, let vanilla draw so there is no black flash.
        if (FloydMenuScreenStyling.renderBackground(context)) ci.cancel();
    }
}
