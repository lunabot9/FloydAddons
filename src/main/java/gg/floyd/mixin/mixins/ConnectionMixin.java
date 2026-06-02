package gg.floyd.mixin.mixins;

import gg.floyd.events.PacketEvent;
import gg.floyd.events.TickEvent;
import gg.floyd.features.impl.misc.FloydCompatibility;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Connection.class, priority = 500)
public abstract class ConnectionMixin {

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"), cancellable = true)
    private void channelRead0(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ClientboundPingPacket pingPacket && pingPacket.getId() != 0) TickEvent.Server.INSTANCE.postAndCatch();
        if (new PacketEvent.Receive(packet).postAndCatch()) ci.cancel();
    }

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void sendImmediately(Packet<?> packet, ChannelFutureListener channelFutureListener, boolean flush, CallbackInfo ci) {
        if (floydaddons$shouldHideCustomPayload(packet)) {
            ci.cancel();
            return;
        }
        if (new PacketEvent.Send(packet).postAndCatch()) ci.cancel();
    }

    private static boolean floydaddons$shouldHideCustomPayload(Packet<?> packet) {
        if (!FloydCompatibility.shouldHideModChannels()) return false;
        if (!(packet instanceof ServerboundCustomPayloadPacket customPayloadPacket)) return false;

        CustomPacketPayload payload = customPayloadPacket.payload();
        if (payload instanceof BrandPayload) return false;

        Identifier id = payload.type().id();
        String namespace = id.getNamespace();
        String path = id.getPath();

        // OpSec's effective mod hiding is based on the fact that servers do not see FabricLoader's
        // local mod list; they see the client brand plus custom payload channels/registrations.
        // Keep vanilla Minecraft payloads, but suppress Fabric/mod namespaces and the Fabric channel
        // registration payload that advertises them.
        if ("minecraft".equals(namespace) && ("register".equals(path) || "unregister".equals(path))) return true;
        return !"minecraft".equals(namespace);
    }
}
