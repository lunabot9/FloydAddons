package floydaddons.not.dogshit.mixin;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("clipToSpace")
    float invokeClipToSpace(float desiredCameraDistance);
}
