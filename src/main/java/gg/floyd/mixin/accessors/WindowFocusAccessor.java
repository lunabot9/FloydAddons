package gg.floyd.mixin.accessors;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Used only by the integrated-server experiment regression fixture. */
@Mixin(Window.class)
public interface WindowFocusAccessor {
    @Invoker("onFocus")
    void floyd$focus(long window, boolean focused);
}
