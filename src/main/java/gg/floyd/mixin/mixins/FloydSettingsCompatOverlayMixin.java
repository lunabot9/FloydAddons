package gg.floyd.mixin.mixins;

import gg.floyd.clickgui.FloydOneConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.polyfrost.oneconfig.internal.OneConfig", remap = false)
public abstract class FloydSettingsCompatOverlayMixin {
    @Inject(method = "prepareHud", at = @At("HEAD"), cancellable = true)
    private static void floyd$skipSettingsHudCapture(CallbackInfo ci) {
        if (!FloydOneConfig.INSTANCE.isFloydSettingsScreen()) return;
        // Block both the offscreen example capture and the native HUD wrapper layer.
        // Cancelling the later compatibility hook alone leaves captured edit borders.
        FloydOneConfig.recordSuppressedCompatOverlay();
        ci.cancel();
    }

    @Inject(method = "submitHud", at = @At("HEAD"), cancellable = true)
    private static void floyd$skipSettingsHudExamples(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!FloydOneConfig.INSTANCE.isFloydSettingsScreen()) return;
        ci.cancel();
    }
}
