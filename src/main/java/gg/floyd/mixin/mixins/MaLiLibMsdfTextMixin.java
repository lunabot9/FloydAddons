package gg.floyd.mixin.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import gg.floyd.utils.font.SkyblockerMsdfCompat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** MaLiLib copies glyph textures but independently selects its world shader. */
@Pseudo
@Mixin(targets = "fi.dy.masa.malilib.render.text.MaLiLibWorldTextRenderer", remap = false)
public abstract class MaLiLibMsdfTextMixin {
    @WrapOperation(method = "acceptRenderable", require = 0,
            at = @At(value = "INVOKE", target = "Lfi/dy/masa/malilib/render/text/MaLiLibWorldTextRenderer;selectColor(Lnet/minecraft/client/gui/Font$DisplayMode;)Lcom/mojang/blaze3d/pipeline/RenderPipeline;"))
    private RenderPipeline floyd$msdfPipeline(@Coerce Object renderer, Font.DisplayMode mode,
            Operation<RenderPipeline> original, @Local(argsOnly = true) TextRenderable text) {
        return SkyblockerMsdfCompat.isMsdf(text) ? text.renderType(mode).pipeline() : original.call(renderer, mode);
    }
}
