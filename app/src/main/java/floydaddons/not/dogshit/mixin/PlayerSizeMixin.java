package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.SkinConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
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
        if (x == 1.0f && y == 1.0f && z == 1.0f) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        String target = SkinConfig.getPlayerSizeTarget();
        switch (target) {
            case "Self" -> {
                if (state.id != mc.player.getId()) return;
            }
            case "Real Players" -> {
                if (mc.world == null || mc.getNetworkHandler() == null) return;
                Entity entity = mc.world.getEntityById(state.id);
                if (!(entity instanceof PlayerEntity pe)) return;
                if (mc.getNetworkHandler().getPlayerListEntry(pe.getUuid()) == null) return;
            }
            // "All" -> apply to everyone, no filter
        }

        if (y < 0) {
            matrices.translate(0.0f, Math.abs(y) * 1.5f, 0.0f);
        }
        matrices.scale(x, y, z);
    }
}
