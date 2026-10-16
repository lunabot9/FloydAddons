package gg.floyd.mixin.mixins;

import gg.floyd.clickgui.FloydOneConfig;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.compose.impls.OneConfigUIScreen", remap = false)
public abstract class FloydSettingsHudPreviewMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void floyd$keepHudEditorClosed(CallbackInfo ci) {
        if (!FloydOneConfig.INSTANCE.isFloydMenuRequested()) return;
        // Upstream treats its settings screen as HUD editing and shows example HUDs
        // from every registered mod. Floyd has its own explicit Edit HUD action.
        HudManager.isConfigUiOpen = false;
        HudManager.overrideShowInScreens = false;
    }
}
