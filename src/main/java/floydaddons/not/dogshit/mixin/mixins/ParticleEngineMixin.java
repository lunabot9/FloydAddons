package floydaddons.not.dogshit.mixin.mixins;

import floydaddons.not.dogshit.client.features.impl.hiders.FloydRemoveExplosionParticles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void floydaddons$filterExplosionParticles(
            ParticleOptions effect,
            double x,
            double y,
            double z,
            double vx,
            double vy,
            double vz,
            CallbackInfoReturnable<Particle> cir
    ) {
        if (!FloydRemoveExplosionParticles.shouldRemoveExplosionParticles() || effect == null) return;
        var type = effect.getType();
        if (type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER) {
            FloydRemoveExplosionParticles.recordExplosionParticles();
            cir.setReturnValue(null);
        }
    }
}
