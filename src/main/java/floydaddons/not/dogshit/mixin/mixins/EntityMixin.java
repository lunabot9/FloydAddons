package floydaddons.not.dogshit.mixin.mixins;

import floydaddons.not.dogshit.client.features.impl.hiders.FloydHideEntityFire;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "displayFireAnimation", at = @At("HEAD"), cancellable = true)
    public void onDisplayFireAnimation(CallbackInfoReturnable<Boolean> cir) {
        if (FloydHideEntityFire.shouldHideEntityFire()) {
            FloydHideEntityFire.recordEntityFire();
            cir.setReturnValue(false);
        }
    }
}
