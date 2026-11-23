package gg.floyd.utils;

/** Marks only Floyd's supersampled TTF glyphs; keeps vanilla glyph and renderable types intact. */
public interface FloydTtfGlyph {
    boolean floyd$usesLinearFiltering();
    void floyd$setLinearFiltering();
    net.minecraft.client.gui.font.glyphs.BakedGlyph floyd$getGuiGlyph();
    void floyd$setGuiGlyph(net.minecraft.client.gui.font.glyphs.BakedGlyph glyph);
}
