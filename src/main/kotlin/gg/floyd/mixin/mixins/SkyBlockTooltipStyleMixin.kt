package gg.floyd.mixin.mixins

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import gg.floyd.features.impl.render.FloydSkyBlockPackDisabler
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import net.minecraft.resources.Identifier
import org.spongepowered.asm.mixin.Mixin

@Mixin(GuiGraphicsExtractor::class)
abstract class SkyBlockTooltipStyleMixin {
    @WrapMethod(method = ["tooltip"])
    private fun replaceSkyBlockTooltipStyle(
        font: Font,
        lines: MutableList<ClientTooltipComponent>,
        x: Int,
        y: Int,
        positioner: ClientTooltipPositioner,
        style: Identifier?,
        original: Operation<Void>,
    ) {
        val vanillaStyle = if (
            FloydSkyBlockPackDisabler.enabled && style?.namespace == "hypixel_skyblock"
        ) null else style
        original.call(font, lines, x, y, positioner, vanillaStyle)
    }
}
