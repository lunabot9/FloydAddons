// SOURCE: https://github.com/Noamm9/NoammAddons/blob/master/src/main/java/noammaddons/mixins/MixinRendererLivingEntity.java
// Key mixin for ESP/CHAM rendering system - hooks entity rendering pipeline
package noammaddons.mixins;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.EntityLivingBase;
import noammaddons.events.RenderEntityModelEvent;
import noammaddons.features.impl.esp.ChamNametags;
import noammaddons.features.impl.esp.EspSettings;
import noammaddons.utils.EspUtils;
import noammaddons.utils.RenderHelper;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;
import java.nio.FloatBuffer;

import static noammaddons.events.EventDispatcher.postAndCatch;
import static noammaddons.utils.EspUtils.ESPType.CHAM;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity {
    @Final @Shadow private static DynamicTexture textureBrightness;
    @Shadow protected ModelBase mainModel;
    @Shadow protected FloatBuffer brightnessBuffer;

    // Fire RenderEntityModelEvent for custom ESP processing
    @Inject(method = "renderModel", at = @At("HEAD"))
    private <T extends EntityLivingBase> void onRenderModel(T entity, float p2, float p3, float p4, float p5, float p6, float scaleFactor, CallbackInfo ci) {
        postAndCatch(new RenderEntityModelEvent(entity, p2, p3, p4, p5, p6, scaleFactor, mainModel));
    }

    // Process ESP queue after layers render
    @Inject(method = "renderLayers", at = @At("RETURN"))
    private <T extends EntityLivingBase> void onRenderLayers(T entity, float p2, float p3, float pt, float p5, float p6, float p7, float p8, CallbackInfo ci) {
        EspUtils.INSTANCE.getQueue().forEach(CHAM::addEntity);
        EspUtils.INSTANCE.getQueue().clear();
    }

    // Override brightness for CHAM coloring
    @Inject(method = "setBrightness", at = @At("HEAD"), cancellable = true)
    private <T extends EntityLivingBase> void setBrightness(T entity, float pt, boolean combine, CallbackInfoReturnable<Boolean> cir) {
        Color chamColor = CHAM.getColor(entity);
        if (chamColor == null) return;
        // ... GL state setup for custom color overlay (see full source)
        cir.setReturnValue(true);
    }

    // Enable phase (see-through-walls) for CHAM entities
    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("HEAD"))
    private <T extends EntityLivingBase> void injectChamsPre(T entity, double x, double y, double z, float yaw, float pt, CallbackInfo ci) {
        if (CHAM.getEntities().containsKey(entity) && EspSettings.INSTANCE.getPhase()) {
            RenderHelper.enableChums();
        }
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("RETURN"))
    private <T extends EntityLivingBase> void injectChamsPost(T entity, double x, double y, double z, float a, float b, CallbackInfo ci) {
        if (CHAM.getEntities().containsKey(entity)) {
            RenderHelper.disableChums();
        }
    }

    // Cham nametags - enable phase for nametag rendering
    @Inject(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At("HEAD"))
    private <T extends EntityLivingBase> void nameChamsPre(T entity, double x, double y, double z, CallbackInfo ci) {
        if (!ChamNametags.INSTANCE.enabled) return;
        RenderHelper.enableChums();
    }

    @Inject(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At("RETURN"))
    private <T extends EntityLivingBase> void nameChamsPost(T entity, double x, double y, double z, CallbackInfo ci) {
        if (!ChamNametags.INSTANCE.enabled) return;
        RenderHelper.disableChums();
    }
}
