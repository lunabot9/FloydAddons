package floydaddons.not.dogshit.mixin.accessors;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("getMaxZoom")
    float floydaddons$invokeGetMaxZoom(float desiredCameraDistance);
}
