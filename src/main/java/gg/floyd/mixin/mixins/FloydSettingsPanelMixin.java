package gg.floyd.mixin.mixins;

import androidx.compose.runtime.Composer;
import gg.floyd.clickgui.FloydSettingsShellKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.screens.ModConfigKt", remap = false)
public class FloydSettingsPanelMixin {
    @Inject(method = "ModConfig", at = @At("HEAD"), cancellable = true, remap = false)
    private static void floydaddons$category(String id, String initialCategory, Composer composer, int changed, int defaults, CallbackInfo ci) {
        if (!"floydaddons".equals(id)) return;
        FloydSettingsShellKt.FloydSettingsPanel(composer, 0);
        ci.cancel();
    }
}
