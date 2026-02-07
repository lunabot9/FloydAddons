package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.NickHiderConfig;
import floydaddons.not.dogshit.client.NickTextUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class NickHiderChatHudMixin {

    private static final ThreadLocal<Boolean> floydaddons$inside = ThreadLocal.withInitial(() -> false);

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), cancellable = true)
    private void floydaddons$nickChat(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        if (floydaddons$inside.get()) return;
        if (!NickHiderConfig.isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        String username = client.getSession().getUsername();
        String nick = NickHiderConfig.getNickname();

        Text replaced = NickTextUtil.replaceLiteralTextIgnoreCase(message, username, nick);
        // Replace message only if something changed
        if (replaced != message) {
            floydaddons$inside.set(true);
            ((ChatHud) (Object) this).addMessage(replaced, signature, indicator);
            floydaddons$inside.set(false);
            ci.cancel();
        }
    }
}
