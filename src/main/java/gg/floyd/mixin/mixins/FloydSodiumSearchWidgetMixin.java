package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.misc.FloydMenuScreenStyling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.widgets.SearchWidget", remap = false)
public abstract class FloydSodiumSearchWidgetMixin {

    @Redirect(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"),
        require = 0
    )
    private void floydaddons$softenSodiumSearchFill(
            GuiGraphicsExtractor context,
            int x1,
            int y1,
            int x2,
            int y2,
            int color) {
        context.fill(x1, y1, x2, y2, FloydMenuScreenStyling.softenCustomScreenFill(Minecraft.getInstance().screen, color));
    }
}
