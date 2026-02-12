package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.CameraConfig;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freezes player movement during freecam by swapping input with an empty one.
 * The player tick still runs (sends position packets, prevents timeout) but
 * receives no movement input.
 */
@Mixin(ClientPlayerEntity.class)
public class CameraMovementMixin {
    @Shadow public Input input;

    @Unique private Input floydaddons$savedInput;

    @Inject(method = "tick", at = @At("HEAD"))
    private void floydaddons$freezeInput(CallbackInfo ci) {
        if (CameraConfig.isFreecamEnabled()) {
            floydaddons$savedInput = this.input;
            this.input = new Input();
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void floydaddons$restoreInput(CallbackInfo ci) {
        if (floydaddons$savedInput != null) {
            this.input = floydaddons$savedInput;
            floydaddons$savedInput = null;
        }
    }
}
