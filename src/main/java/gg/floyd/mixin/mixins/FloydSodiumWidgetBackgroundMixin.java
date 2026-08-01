package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.misc.FloydMenuScreenStyling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.widgets.AbstractWidget", remap = false)
public abstract class FloydSodiumWidgetBackgroundMixin {

    @Inject(method = "drawRect", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$softenSodiumWidgetFill(
            GuiGraphicsExtractor context,
            int x1,
            int y1,
            int x2,
            int y2,
            int color,
            CallbackInfo ci) {
        context.fill(x1, y1, x2, y2, FloydMenuScreenStyling.softenCustomScreenFill(Minecraft.getInstance().screen, color));
        ci.cancel();
    }
}
