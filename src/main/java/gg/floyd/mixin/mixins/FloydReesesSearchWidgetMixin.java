package gg.floyd.mixin.mixins;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import gg.floyd.features.impl.misc.FloydMenuScreenStyling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "me.flashyreese.mods.reeses_sodium_options.client.gui.frame.components.SearchTextFieldComponent", remap = false)
public abstract class FloydReesesSearchWidgetMixin {

    @Redirect(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;IIIII)V"),
        require = 0
    )
    private void floydaddons$softenReesesSearchFill(
            GuiGraphicsExtractor context,
            RenderPipeline pipeline,
            int x1,
            int y1,
            int x2,
            int y2,
            int color) {
        context.fill(pipeline, x1, y1, x2, y2, FloydMenuScreenStyling.softenCustomScreenFill(Minecraft.getInstance().screen, color));
    }
}
