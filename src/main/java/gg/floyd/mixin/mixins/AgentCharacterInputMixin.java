package gg.floyd.mixin.mixins;

import gg.floyd.events.ScreenEvent;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import gg.floyd.agent.FloydAgent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric 26.1 has no allowCharType event, so use the same input hook on every target. */
@Mixin(KeyboardHandler.class)
public abstract class AgentCharacterInputMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void floydPushToTalk(long window, int action, KeyEvent input, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (window == client.getWindow().handle() && FloydAgent.INSTANCE.handlePushToTalkKey(input.key(), action)) ci.cancel();
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void floydCharacterInput(long window, CharacterEvent input, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (window == client.getWindow().handle() && client.screen != null &&
                new ScreenEvent.CharType(client.screen, input).postAndCatch()) ci.cancel();
    }
}
