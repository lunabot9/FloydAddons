package gg.floyd.mixin.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import gg.floyd.features.impl.render.FloydSkyBlockPackDisabler;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GuiGraphicsExtractor.class)
public abstract class SkyBlockTooltipStyleMixin {
    @WrapMethod(method = "tooltip")
    private void replaceSkyBlockTooltipStyle(Font font, List<ClientTooltipComponent> lines, int x, int y,
        ClientTooltipPositioner positioner, Identifier style, Operation<Void> original) {
        var vanilla = FloydSkyBlockPackDisabler.INSTANCE.getEnabled() && style != null && style.getNamespace().equals("hypixel_skyblock")
            ? null : style;
        original.call(font, lines, x, y, positioner, vanilla);
    }
}
