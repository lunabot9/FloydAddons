package floydaddons.not.dogshit.mixin.mixins;

import floydaddons.not.dogshit.client.features.impl.hiders.FloydDisableArrows;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArrowLayer.class)
public class HiderArrowLayerMixin {
    @Inject(method = "numStuck(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)I", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$disableAttachedArrows(AvatarRenderState state, CallbackInfoReturnable<Integer> cir) {
        if (FloydDisableArrows.shouldDisableAttachedArrows()) {
            FloydDisableArrows.recordAttachedArrows();
            cir.setReturnValue(0);
        }
    }
}
