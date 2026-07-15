package gg.floyd.mixin.mixins

import gg.floyd.features.impl.render.FloydSkyBlockPackAssets
import net.minecraft.client.Minecraft
import net.minecraft.server.packs.repository.RepositorySource
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.ModifyArg

@Mixin(Minecraft::class)
abstract class SkyBlockPackRepositoryMixin {
    @ModifyArg(
        method = ["<init>"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/packs/repository/PackRepository;<init>([Lnet/minecraft/server/packs/repository/RepositorySource;)V",
        ),
        index = 0,
    )
    private fun addSkyBlockFallbackPack(
        repositorySources: Array<RepositorySource>,
    ): Array<RepositorySource> = repositorySources + FloydSkyBlockPackAssets.Repository()
}
