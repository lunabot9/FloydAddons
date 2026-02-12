package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.AnimationConfig;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class ItemInHandRendererMixin {

    /**
     * Apply custom position/rotation/scale transforms right BEFORE the inner
     * renderItem() call inside renderFirstPersonItem. This injects AFTER all
     * vanilla transforms (swing, equip, eat/drink), so rotations happen around
     * the item's positioned center, not the player origin.
     */
    @Inject(method = "renderFirstPersonItem",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"),
            require = 0)
    private void floydaddons$applyItemTransforms(AbstractClientPlayerEntity player, float tickProgress,
                                                  float pitch, Hand hand, float swingProgress, ItemStack stack,
                                                  float equipProgress, MatrixStack matrices,
                                                  OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        if (!AnimationConfig.isEnabled() || hand != Hand.MAIN_HAND) return;

        float posX = AnimationConfig.getPosX() / 100.0f;
        float posY = AnimationConfig.getPosY() / 100.0f;
        float posZ = AnimationConfig.getPosZ() / 100.0f;
        matrices.translate(posX, posY, posZ);

        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(AnimationConfig.getRotX()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(AnimationConfig.getRotY()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(AnimationConfig.getRotZ()));

        float scale = AnimationConfig.getScale();
        if (scale != 1.0f) {
            matrices.scale(scale, scale, scale);
        }
    }

    /**
     * Hide the player hand/arm when holding nothing (empty main hand).
     */
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$hideEmptyHand(AbstractClientPlayerEntity player, float tickProgress,
                                            float pitch, Hand hand, float swingProgress, ItemStack stack,
                                            float equipProgress, MatrixStack matrices,
                                            OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        if (AnimationConfig.isEnabled() && AnimationConfig.isHidePlayerHand()
                && hand == Hand.MAIN_HAND && stack.isEmpty()) {
            ci.cancel();
        }
    }

    /**
     * Cancel re-equip animation: replace vanilla equip offset with a fixed position.
     */
    @Inject(method = "applyEquipOffset", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$cancelReEquip(MatrixStack matrices, Arm arm, float equipProgress, CallbackInfo ci) {
        if (AnimationConfig.isEnabled() && AnimationConfig.isCancelReEquip()) {
            int i = arm == Arm.RIGHT ? 1 : -1;
            matrices.translate(i * 0.56f, -0.52f, -0.72f);
            ci.cancel();
        }
    }
}
