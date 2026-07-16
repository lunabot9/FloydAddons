package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.camera.FloydCamera;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SectionOcclusionGraph.class)
public class SectionOcclusionGraphMixin {
    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean floydaddons$disableSmartCullForFreecam(boolean smartCull) {
        if (FloydCamera.freecamActive()) {
            return false;
        }
        return smartCull;
    }
}
