package com.odtheking.mixin.mixins;

import com.odtheking.odin.features.impl.misc.FloydCompatibility;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class FloydWatchdogMessageMixin {
    @Inject(method = "handleSystemChat", at = @At("HEAD"), cancellable = true)
    private void floydaddons$hideSystemWatchdog(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (shouldHide(packet.content())) ci.cancel();
    }

    @Inject(method = "handleDisguisedChat", at = @At("HEAD"), cancellable = true)
    private void floydaddons$hideDisguisedWatchdog(ClientboundDisguisedChatPacket packet, CallbackInfo ci) {
        if (shouldHide(packet.message())) ci.cancel();
    }

    private static boolean shouldHide(Component component) {
        if (!FloydCompatibility.shouldHideWatchdogMessages() || component == null) return false;

        String lower = component.getString().toLowerCase();
        return lower.contains("[watchdog announcement]")
            || lower.contains("watchdog has banned")
            || lower.contains("staff have banned an additional")
            || lower.contains("blacklisted modifications are a bannable offense");
    }
}
