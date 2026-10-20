package gg.floyd.mixin.mixins;

import gg.floyd.clickgui.FloydSettingsModelPreviews;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** OneConfig composites its scene after vanilla GUI extraction; native previews must follow it. */
@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.compose.SkiaCtx", remap = false)
public abstract class FloydSettingsModelOverlayMixin {
    @Inject(method = "draw()V", at = @At("TAIL"), remap = false)
    private void floydaddons$drawModelsAfterCompose(CallbackInfo ci) {
        FloydSettingsModelPreviews.renderAfterCompose();
    }
}
