package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.RenderConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.world.MutableWorldProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels server time packets while Time Changer is enabled to prevent flicker.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class TimeUpdateMixin {
    @Inject(method = "onWorldTimeUpdate", at = @At("HEAD"), cancellable = true)
    private void floydaddons$blockServerTime(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        if (!RenderConfig.isCustomTimeEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        try {
            MutableWorldProperties props = mc.world.getLevelProperties();
            RenderConfig.applyCustomTime(props);
            ci.cancel();
        } catch (Exception ignored) {}
    }
}
