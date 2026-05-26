package floydaddons.not.dogshit.mixin.mixins;

import floydaddons.not.dogshit.client.features.impl.cosmetic.FloydCapeLayer;
import floydaddons.not.dogshit.client.features.impl.cosmetic.FloydConeHatLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Shadow
    protected abstract boolean addLayer(RenderLayer renderLayer);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void floydaddons$addCosmeticLayers(EntityRendererProvider.Context context, EntityModel model, float shadowRadius, CallbackInfo ci) {
        if (!((Object) this instanceof AvatarRenderer<?> renderer)) return;

        RenderLayerParent<AvatarRenderState, PlayerModel> parent = renderer;
        addLayer(new FloydCapeLayer(parent));
        addLayer(new FloydConeHatLayer(parent));
    }
}
