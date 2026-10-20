package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.pvp.FloydAutoExperiments;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes applied server updates; never cancels or rewrites a packet. */
@Mixin(ClientPacketListener.class)
public class ExperimentContainerUpdateMixin {
    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void floyd$experimentSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        FloydAutoExperiments.serverSlot(packet.getContainerId(), packet.getStateId(), packet.getSlot(), packet.getItem());
    }
    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void floyd$experimentContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        FloydAutoExperiments.serverContent(packet.containerId(), packet.stateId(), packet.items());
    }
}
