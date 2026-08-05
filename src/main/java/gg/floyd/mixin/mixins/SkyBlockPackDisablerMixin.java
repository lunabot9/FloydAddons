package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.render.FloydSkyBlockPackDisabler;
import gg.floyd.features.impl.render.FloydSkyBlockPackAssets;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientCommonPacketListenerImpl.class, priority = 2000)
public abstract class SkyBlockPackDisablerMixin {
    @Shadow @Final private Connection connection;

    @Inject(
        method = "handleResourcePackPush",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER
        ),
        cancellable = true
    )
    private void floydaddons$disableSkyBlockPack(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        if (!FloydSkyBlockPackDisabler.shouldDisable(packet.url())) return;

        FloydSkyBlockPackAssets.refreshFromLivePack(packet.url(), packet.hash());
        connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED));
        connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
        ci.cancel();
    }

    @Inject(method = "handleResourcePackPop", at = @At("TAIL"))
    private void floydaddons$handleSkyBlockPackPop(ClientboundResourcePackPopPacket packet, CallbackInfo ci) {
        // Floyd keeps its local compatibility pack mounted; pack pop only ends the server-forced phase.
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void floydaddons$resetSkyBlockPackState(DisconnectionDetails details, CallbackInfo ci) {
        // The next push packet will refresh the cached URL if Hypixel changes it.
    }
}
