package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.HidersConfig;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Filters out explosion particles when the corresponding hider is enabled.
 */
@Mixin(ParticleManager.class)
public class HiderParticleMixin {

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$filterParticles(ParticleEffect effect, double x, double y, double z,
                                              double vx, double vy, double vz, CallbackInfoReturnable<Particle> ci) {
        if (effect == null) return;
        var type = effect.getType();
        if (HidersConfig.isRemoveExplosionParticlesEnabled()) {
            if (type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER) {
                ci.setReturnValue(null);
            }
        }
    }
}
