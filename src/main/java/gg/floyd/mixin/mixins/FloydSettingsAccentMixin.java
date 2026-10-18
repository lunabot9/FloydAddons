package gg.floyd.mixin.mixins;

import gg.floyd.clickgui.FloydOneConfigTheme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Resolve the shared controls' accent only inside Floyd's own rendering scope. */
@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.themes.ProviderKt", remap = false)
public abstract class FloydSettingsAccentMixin {
    @Inject(method = "getAccent", at = @At("HEAD"), cancellable = true)
    private static void floyd$localAccent(CallbackInfoReturnable<Long> cir) {
        if (gg.floyd.clickgui.FloydSettingsScreen.isRenderingFloyd()) {
            cir.setReturnValue(FloydOneConfigTheme.packedAccent());
        }
    }
}
