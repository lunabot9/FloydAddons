package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.hiders.FloydHiders;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "displayFireAnimation", at = @At("HEAD"), cancellable = true)
    public void onDisplayFireAnimation(CallbackInfoReturnable<Boolean> cir) {
        if (FloydHiders.shouldHideEntityFire()) {
            FloydHiders.recordEntityFire();
            cir.setReturnValue(false);
        }
    }
}
