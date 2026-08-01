package gg.floyd.mixin.accessors;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Screen.class)
public interface ScreenFontAccessor {

    @Accessor("font")
    Font floydaddons$getFont();

    @Accessor("font")
    @Mutable
    void floydaddons$setFont(Font font);
}
