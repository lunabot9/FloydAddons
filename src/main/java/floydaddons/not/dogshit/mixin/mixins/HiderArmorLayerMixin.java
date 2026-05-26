package floydaddons.not.dogshit.mixin.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import floydaddons.not.dogshit.client.features.impl.hiders.FloydNoArmor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class HiderArmorLayerMixin<S extends HumanoidRenderState, M extends HumanoidModel<S>, A extends HumanoidModel<S>> {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$hideArmor(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (state instanceof AvatarRenderState playerState && FloydNoArmor.shouldHideArmorFor(playerState.id)) {
            FloydNoArmor.recordArmorLayer();
            ci.cancel();
        }
    }
}
