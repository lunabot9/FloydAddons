package gg.floyd.mixin.mixins;

import androidx.compose.runtime.Composer;
import androidx.compose.ui.Modifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The native settings shell mounts this editor behind its menu, including during prewarm. */
@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.hud.screens.HudDesignStudioKt", remap = false)
public abstract class FloydSettingsHudDragLayerMixin {
    @Inject(method = "HudDragLayer", at = @At("HEAD"), cancellable = true)
    private static void floyd$omitEmbeddedHudEditor(Modifier modifier, Composer composer, int changed, int defaults, CallbackInfo ci) {
        // Floyd provides its own explicit HUD editor. The full native HUD studio is
        // separate; this layer only adds movable third-party previews behind settings.
        ci.cancel();
    }
}
