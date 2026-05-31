package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.hiders.FloydHiders;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {

    @Inject(method = "renderPingIcon", at = @At("HEAD"), cancellable = true)
    private void floydaddons$removeTabPing(GuiGraphics guiGraphics, int width, int x, int y, PlayerInfo playerInfo, CallbackInfo ci) {
        if (FloydHiders.shouldRemoveTabPing()) {
            FloydHiders.recordTabPing();
            ci.cancel();
        }
    }
}
