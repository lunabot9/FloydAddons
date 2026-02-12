package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.SkinConfig;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class PlayerSizeMixin {
    @Inject(method = "scale(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At("RETURN"), require = 0)
    private void floydaddons$applyCustomScale(PlayerEntityRenderState state, MatrixStack matrices, CallbackInfo ci) {
        float x = SkinConfig.getPlayerScaleX();
        float y = SkinConfig.getPlayerScaleY();
        float z = SkinConfig.getPlayerScaleZ();
        if (x != 1.0f || y != 1.0f || z != 1.0f) {
            if (y < 0) {
                // Translate upward so the flipped model stays visible (Odin-style fix)
                matrices.translate(0.0f, Math.abs(y) * 1.5f, 0.0f);
            }
            matrices.scale(x, y, z);
        }
    }
}
