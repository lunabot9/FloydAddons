package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.SkinConfig;
import floydaddons.not.dogshit.client.skin.SkinManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(method = "getTexture(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)Lnet/minecraft/util/Identifier;",
            at = @At("HEAD"), cancellable = true)
    private void floydaddons$swapSkin(PlayerEntityRenderState state, CallbackInfoReturnable<Identifier> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getSession() == null) return;
        if (mc.player == null) return;
        if (!SkinConfig.customEnabled()) return;
        boolean isSelf = state.id == mc.player.getId();
        boolean use = isSelf ? SkinConfig.selfEnabled() : SkinConfig.othersEnabled();
        if (!use) return;

        Identifier custom = SkinManager.getCustomTexture(mc);
        if (custom != null) cir.setReturnValue(custom);
    }

}

