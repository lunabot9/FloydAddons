package gg.floyd.mixin.accessors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftFontAccessor {

    @Accessor("font")
    Font floydaddons$getFont();

    @Accessor("font")
    @Mutable
    void floydaddons$setFont(Font font);
}
