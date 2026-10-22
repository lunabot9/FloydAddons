package gg.floyd.mixin.accessors;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Position a local sack panel beside the container's actual layout. */
@Mixin(AbstractContainerScreen.class)
public interface SackContainerLayoutAccessor {
    @Accessor("leftPos") int floyd$sackLeft();
    @Accessor("topPos") int floyd$sackTop();
    @Accessor("imageHeight") int floyd$sackHeight();
}
