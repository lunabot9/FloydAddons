package gg.floyd.mixin.mixins;

import gg.floyd.mixin.accessors.CameraAccessor;
import gg.floyd.features.impl.camera.FloydCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean detached;
    @Shadow private float eyeHeight;
    @Shadow private Entity entity;

    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow protected abstract void move(float zoom, float dy, float dx);

    @Redirect(
        method = "alignWithEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F")
    )
    private float floydaddons$customThirdPersonDistance(Camera instance, float originalDistance) {
        float desired = FloydCamera.currentF5Distance();
        if (FloydCamera.freecamActive() || FloydCamera.freelookActive()) desired = originalDistance;
        if (FloydCamera.shouldNoClipF5() && this.detached) return desired;
        return ((CameraAccessor) this).floydaddons$invokeGetMaxZoom(desired);
    }

    @Inject(method = "update", at = @At("RETURN"))
    private void floydaddons$cameraUpdate(net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        if (FloydCamera.freecamActive()) {
            FloydCamera.updateFreecamMovement();
            setPosition(FloydCamera.freecamX(), FloydCamera.freecamY(), FloydCamera.freecamZ());
            setRotation(FloydCamera.freecamYaw(), FloydCamera.freecamPitch());
            this.detached = true;
            return;
        }

        if (FloydCamera.freelookActive() && this.entity != null) {
            float partialTick = this.entity.level().tickRateManager().isEntityFrozen(this.entity)
                ? 1.0F
                : deltaTracker.getGameTimeDeltaPartialTick(true);
            Vec3 pos = this.entity.getPosition(partialTick);
            setPosition(pos.x, pos.y + this.eyeHeight, pos.z);
            setRotation(FloydCamera.freelookYaw(), FloydCamera.freelookPitch());
            float desired = FloydCamera.currentFreelookDistance();
            float clipped = FloydCamera.shouldNoClipF5()
                ? desired
                : ((CameraAccessor) this).floydaddons$invokeGetMaxZoom(desired);
            move(-clipped, 0.0F, 0.0F);
        }
    }
}
