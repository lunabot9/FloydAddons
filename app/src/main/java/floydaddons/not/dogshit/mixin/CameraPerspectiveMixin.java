package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.CameraConfig;
import floydaddons.not.dogshit.client.FloydAddonsConfig;
import net.minecraft.client.option.Perspective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Perspective.class)
public class CameraPerspectiveMixin {

    @Inject(method = "next", at = @At("HEAD"), cancellable = true)
    private void floydaddons$skipDisabledPerspectives(CallbackInfoReturnable<Perspective> cir) {
        // Disable freecam/freelook when cycling perspectives with F5
        if (CameraConfig.isFreecamEnabled()) {
            CameraConfig.toggleFreecam();
        }
        if (CameraConfig.isFreelookEnabled()) {
            CameraConfig.toggleFreelook();
        }

        // Reset F5 scroll distance to default if enabled
        if (CameraConfig.isF5ResetOnToggle()) {
            CameraConfig.setF5CameraDistance(CameraConfig.getF5DefaultDistance());
            FloydAddonsConfig.save();
        }

        if (!CameraConfig.isF5DisableFront() && !CameraConfig.isF5DisableBack()) return;

        Perspective current = (Perspective) (Object) this;
        Perspective[] values = Perspective.values();
        Perspective next = current;

        for (int i = 0; i < values.length; i++) {
            next = values[(next.ordinal() + 1) % values.length];
            if (isAllowed(next)) {
                cir.setReturnValue(next);
                return;
            }
        }
        cir.setReturnValue(Perspective.FIRST_PERSON);
    }

    private static boolean isAllowed(Perspective p) {
        if (p == Perspective.THIRD_PERSON_FRONT && CameraConfig.isF5DisableFront()) return false;
        if (p == Perspective.THIRD_PERSON_BACK && CameraConfig.isF5DisableBack()) return false;
        return true;
    }
}
