package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.HidersConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeadFeatureRenderer.class)
public class HiderHeadMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$hideHead(MatrixStack matrixStack, OrderedRenderCommandQueue queue, int light, LivingEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState playerState)) return;
        String mode = HidersConfig.getNoArmorMode();
        if ("OFF".equals(mode)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        boolean isSelf = playerState.id == mc.player.getId();
        boolean hide = switch (mode) {
            case "SELF" -> isSelf;
            case "OTHERS" -> !isSelf;
            case "ALL" -> true;
            default -> false;
        };
        if (hide) ci.cancel();
    }
}
