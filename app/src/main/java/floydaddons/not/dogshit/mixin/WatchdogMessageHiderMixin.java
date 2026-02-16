package floydaddons.not.dogshit.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks Hypixel Watchdog announcement spam from appearing in chat.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class WatchdogMessageHiderMixin {
    @Inject(method = "onGameMessage", at = @At("HEAD"), cancellable = true)
    private void floydaddons$hideWatchdog(GameMessageS2CPacket packet, CallbackInfo ci) {
        Text text = packet.content();
        if (text == null) return;

        String plain = text.getString();
        if (plain == null || plain.isEmpty()) return;

        String lower = plain.toLowerCase();
        if (lower.contains("[watchdog announcement]")
                || lower.contains("watchdog has banned")
                || lower.contains("staff have banned an additional")
                || lower.contains("blacklisted modifications are a bannable offense")) {
            ci.cancel();
        }
    }
}
