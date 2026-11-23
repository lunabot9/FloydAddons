package gg.floyd.mixin.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.SamplerCache;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import gg.floyd.utils.font.FloydMsdfRenderable;
import gg.floyd.utils.font.FloydGuiWorldRenderable;
import gg.floyd.utils.FloydTtfGlyph;
import gg.floyd.mixin.accessors.SheetGlyphInstanceAccessor;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Use linear sampling for Floyd MSDF and supersampled TTF glyphs only. Pack icons and vanilla
 * bitmap glyphs retain their original sampler, even when they share an atlas page with Floyd. */
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
        boolean smooth = this.renderable instanceof FloydMsdfRenderable
                || this.renderable instanceof FloydGuiWorldRenderable
                || (this.renderable instanceof SheetGlyphInstanceAccessor instance
                    && ((FloydTtfGlyph) instance.floyd$getGlyph()).floyd$usesLinearFiltering());
        return original.call(cache, smooth ? FilterMode.LINEAR : filter);
    }
}
