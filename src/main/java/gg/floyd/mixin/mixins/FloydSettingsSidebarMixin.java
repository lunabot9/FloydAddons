package gg.floyd.mixin.mixins;

import androidx.compose.runtime.Composer;
import gg.floyd.clickgui.FloydOneConfig;
import gg.floyd.clickgui.FloydSettingsShellKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.components.SidebarKt", remap = false)
public class FloydSettingsSidebarMixin {
    @Inject(method = "Sidebar", at = @At("HEAD"), cancellable = true, remap = false)
    private static void floydaddons$sidebar(Composer composer, int changed, CallbackInfo ci) {
        if (!FloydOneConfig.INSTANCE.isFloydPage()) return;
        FloydSettingsShellKt.FloydSidebar(composer, 0);
        ci.cancel();
    }
}
