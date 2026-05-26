package floydaddons.not.dogshit.mixin.mixins;

import floydaddons.not.dogshit.client.features.impl.hiders.FloydRemoveTabPing;
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
        if (FloydRemoveTabPing.shouldRemoveTabPing()) {
            FloydRemoveTabPing.recordTabPing();
            ci.cancel();
        }
    }
}
