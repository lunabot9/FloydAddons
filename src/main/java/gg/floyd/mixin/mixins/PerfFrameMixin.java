package gg.floyd.mixin.mixins;

import gg.floyd.utils.perf.FloydPerf;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Frame boundary for the /perf measurement harness: Minecraft.runTick runs exactly once per frame
 * on the render thread, so HEAD-to-HEAD deltas are the true frame period (including swap/vsync
 * wait) — the number that 1/fps comes from. FloydPerf.onFrameStart is a volatile null-check when
 * no sampling window is armed.
 */
@Mixin(Minecraft.class)
public class PerfFrameMixin {

    @Inject(method = "runTick(Z)V", at = @At("HEAD"))
    private void floydaddons$perfFrameBoundary(boolean tick, CallbackInfo ci) {
        FloydPerf.onFrameStart();
    }
}
