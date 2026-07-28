package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.render.FloydSkyBlockPackDisabler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
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
        at = @At("HEAD"),
        cancellable = true
    )
    private void floydaddons$disableSkyBlockPack(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        if (!FloydSkyBlockPackDisabler.shouldDisable(packet.url())) return;

        // The packet handler is first entered on Netty's thread, where Minecraft's original body
        // schedules it onto the render thread. Let that first call continue, then win at HEAD when
        // the scheduled invocation re-enters. A high priority keeps lower-priority pack mixins from
        // starting the download before Floyd can cancel it.
        if (!Minecraft.getInstance().isSameThread()) return;

        connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED));
        connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
        ci.cancel();
    }
}
