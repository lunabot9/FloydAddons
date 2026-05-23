package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.HidersConfig;
import net.minecraft.client.gui.hud.PlayerListHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the ping (latency) icon on the tab player list when RemoveTabPing is enabled.
 */
@Mixin(PlayerListHud.class)
public class HiderPlayerListMixin {

    @Inject(method = "renderLatencyIcon", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$removeTabPing(CallbackInfo ci) {
        if (HidersConfig.isRemoveTabPingEnabled()) ci.cancel();
    }
}
