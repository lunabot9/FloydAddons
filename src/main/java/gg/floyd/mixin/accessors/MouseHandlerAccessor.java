package gg.floyd.mixin.accessors;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Keeps bridge pointer input consistent for screens which read the native pointer position. */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("xpos") void floydaddons$setX(double x);
    @Accessor("ypos") void floydaddons$setY(double y);
}
