package gg.floyd.mixin.mixins;

import gg.floyd.clickgui.FloydOneConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep Floyd's native surface refreshed when the upstream cached blit loses its frame. */
@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen", remap = false)
public abstract class FloydSettingsFrameMixin {
    @Shadow private boolean sceneDirty;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void floyd$refreshFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (FloydOneConfig.INSTANCE.isFloydPage()) sceneDirty = true;
    }
}
