package gg.floyd.mixin.mixins;

import gg.floyd.supercraft.SackSupercraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Updates the craft worker only from server-owned container snapshots. */
@Mixin(ClientPacketListener.class)
public abstract class SackSupercraftContainerMixin {
    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void floyd$sackSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        SackSupercraft.serverSlot(packet.getContainerId(), packet.getSlot(), packet.getItem());
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void floyd$sackContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        SackSupercraft.serverContent(packet.containerId(), packet.items());
    }
}
