package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.render.FloydSkyBlockPackAssets;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Minecraft.class)
public abstract class SkyBlockPackRepositoryMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/packs/repository/PackRepository;<init>([Lnet/minecraft/server/packs/repository/RepositorySource;)V"), index = 0)
    private RepositorySource[] addSkyBlockFallbackPack(RepositorySource[] sources) {
        var expanded = Arrays.copyOf(sources, sources.length + 1);
        expanded[sources.length] = new FloydSkyBlockPackAssets.Repository();
        return expanded;
    }
}
