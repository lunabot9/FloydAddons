package com.odtheking.mixin.mixins;

import com.odtheking.odin.features.impl.render.FloydRender;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class FloydTimeUpdateMixin {
    @Inject(method = "handleSetTime", at = @At("HEAD"), cancellable = true)
    private void floydaddons$blockServerTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        if (!FloydRender.shouldUseCustomTime()) return;
        FloydRender.applyCustomTimeOverride();
        ci.cancel();
    }
}
