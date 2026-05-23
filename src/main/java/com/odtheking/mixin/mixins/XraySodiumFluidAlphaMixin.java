package com.odtheking.mixin.mixins;

import com.odtheking.odin.features.impl.render.FloydXray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer", remap = false)
public class XraySodiumFluidAlphaMixin {
    @Redirect(
        method = "updateQuad",
        at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/api/util/ColorARGB;toABGR(I)I"),
        require = 0
    )
    private int floydaddons$modifyFluidColor(int argb) {
        if (FloydXray.isActive()) {
            argb = (FloydXray.alpha() << 24) | (argb & 0x00FFFFFF);
        }
        return toAbgr(argb);
    }

    @Unique
    private static int toAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >> 16) | ((argb & 0x000000FF) << 16);
    }
}
