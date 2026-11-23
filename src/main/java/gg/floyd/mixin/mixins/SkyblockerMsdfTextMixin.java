package gg.floyd.mixin.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.SamplerCache;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import gg.floyd.utils.font.SkyblockerMsdfCompat;
import net.minecraft.client.gui.font.TextRenderable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "de.hysky.skyblocker.utils.render.primitive.TextPrimitiveRenderer$1", remap = false)
public abstract class SkyblockerMsdfTextMixin {
    @WrapOperation(method = "draw(Lnet/minecraft/client/gui/font/TextRenderable;)V", require = 0,
            at = @At(value = "INVOKE", target = "Lde/hysky/skyblocker/utils/render/primitive/TextPrimitiveRenderer;getPipeline(ZZ)Lcom/mojang/blaze3d/pipeline/RenderPipeline;"))
    private RenderPipeline floyd$msdfPipeline(boolean throughWalls, boolean intensity,
            Operation<RenderPipeline> original, @Local(argsOnly = true) TextRenderable text) {
        return SkyblockerMsdfCompat.pipeline(original.call(throughWalls, intensity), throughWalls, text);
    }

    @WrapOperation(method = "draw(Lnet/minecraft/client/gui/font/TextRenderable;)V", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/SamplerCache;getClampToEdge(Lcom/mojang/blaze3d/textures/FilterMode;)Lcom/mojang/blaze3d/textures/GpuSampler;"))
    private GpuSampler floyd$msdfSampler(SamplerCache cache, FilterMode filter,
            Operation<GpuSampler> original, @Local(argsOnly = true) TextRenderable text) {
        return original.call(cache, SkyblockerMsdfCompat.filter(filter, text));
    }
}
