package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.CameraConfig;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean thirdPerson;
    @Shadow private float cameraY;

    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void moveBy(float x, float y, float z);

    @ModifyArg(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"), require = 0)
    private float floydaddons$f5Distance(float original) {
        if (CameraConfig.isFreecamEnabled() || CameraConfig.isFreelookEnabled()) return original;
        return CameraConfig.getF5CameraDistance();
    }

    @Inject(method = "update", at = @At("RETURN"))
    private void floydaddons$cameraUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                          boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (CameraConfig.isFreecamEnabled()) {
            // Frame-based movement: reads keys and applies velocity each render frame
            CameraConfig.updateFreecamMovement();
            setPos(CameraConfig.getFreecamX(), CameraConfig.getFreecamY(), CameraConfig.getFreecamZ());
            setRotation(CameraConfig.getFreecamYaw(), CameraConfig.getFreecamPitch());
            this.thirdPerson = true;
        } else if (CameraConfig.isFreelookEnabled() && focusedEntity != null) {
            Vec3d lerpedPos = focusedEntity.getLerpedPos(tickDelta);
            setPos(lerpedPos.x, lerpedPos.y + (double) this.cameraY, lerpedPos.z);
            setRotation(CameraConfig.getFreelookYaw(), CameraConfig.getFreelookPitch());
            float clipped = ((CameraAccessor) (Object) this).invokeClipToSpace(CameraConfig.getFreelookDistance());
            moveBy(-clipped, 0, 0);
        }
    }
}
