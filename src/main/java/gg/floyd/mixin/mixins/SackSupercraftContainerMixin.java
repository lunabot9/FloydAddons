package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.render.FloydSackTracker;
import gg.floyd.supercraft.SackSupercraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Forwards server-owned container snapshots to the sack features. */
@Mixin(ClientPacketListener.class)
public abstract class SackSupercraftContainerMixin {
    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void floyd$sackSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        SackSupercraft.serverSlot(packet.getContainerId(), packet.getSlot(), packet.getItem());
        FloydSackTracker.INSTANCE.serverSlots(packet.getContainerId(), packet.getSlot(), List.of(packet.getItem()));
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void floyd$sackContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        SackSupercraft.serverContent(packet.containerId(), packet.items());
        FloydSackTracker.INSTANCE.serverSlots(packet.containerId(), 0, packet.items());
    }
}
