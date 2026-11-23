package gg.floyd.mixin.accessors;

import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.font.glyphs.BakedSheetGlyph$GlyphInstance")
public interface SheetGlyphInstanceAccessor {
    @Accessor("glyph")
    BakedSheetGlyph floyd$getGlyph();
}
