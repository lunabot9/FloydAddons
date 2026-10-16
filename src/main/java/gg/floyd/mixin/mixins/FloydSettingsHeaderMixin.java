package gg.floyd.mixin.mixins;

import androidx.compose.runtime.Composer;
import gg.floyd.clickgui.FloydOneConfig;
import gg.floyd.clickgui.FloydSettingsShellKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.components.HeaderKt", remap = false)
public class FloydSettingsHeaderMixin {
    @Inject(method = "Header", at = @At("HEAD"), cancellable = true, remap = false)
    private static void floydaddons$header(Composer composer, int changed, CallbackInfo ci) {
        if (!FloydOneConfig.INSTANCE.isFloydPage()) return;
        FloydSettingsShellKt.FloydSettingsHeader(composer, 0);
        ci.cancel();
    }
}
