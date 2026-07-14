package gg.floyd.mixin.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.SamplerCache;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import gg.floyd.utils.font.FloydMsdfRenderable;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * GUI text samples glyph pages with a hardcoded NEAREST sampler; MSDF glyphs are unusable without
 * LINEAR filtering. Swaps the filter mode only when the renderable is Floyd's MSDF type, leaving
 * every vanilla glyph untouched. Targets the 1-arg {@code getClampToEdge(FilterMode)} overload
 * inside {@code GlyphRenderState.textureSetup()} (the lightmap's LINEAR call lives inside
 * {@code TextureSetup}, so this wrap is unambiguous).
 */
@Mixin(GlyphRenderState.class)
public abstract class GlyphRenderStateMixin {

    @Shadow
    @Final
    private TextRenderable renderable;

    @WrapOperation(
            method = "textureSetup()Lnet/minecraft/client/gui/render/TextureSetup;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/SamplerCache;getClampToEdge(Lcom/mojang/blaze3d/textures/FilterMode;)Lcom/mojang/blaze3d/textures/GpuSampler;"))
    private GpuSampler floyd$linearForMsdf(SamplerCache cache, FilterMode filter, Operation<GpuSampler> original) {
        return original.call(cache, this.renderable instanceof FloydMsdfRenderable ? FilterMode.LINEAR : filter);
    }
}
