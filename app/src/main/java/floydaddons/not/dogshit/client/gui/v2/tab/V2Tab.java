package floydaddons.not.dogshit.client.gui.v2.tab;

import net.minecraft.client.gui.DrawContext;

/**
 * Contract every V2 tab implements. The hub screen routes layout/render/input to the
 * currently active tab.
 */
public interface V2Tab {
    String displayName();
    void layout(int x, int y, int w, int h);
    void render(DrawContext ctx, int mouseX, int mouseY, float delta);
    default boolean mouseClicked(double mx, double my, int button) { return false; }
    default boolean mouseReleased(double mx, double my, int button) { return false; }
    default boolean mouseDragged(double mx, double my, int button, double dx, double dy) { return false; }
    default boolean mouseScrolled(double mx, double my, double horiz, double vert) { return false; }
    default boolean keyPressed(int keyCode, int scanCode, int mods) { return false; }
    default boolean charTyped(char chr, int mods) { return false; }
    default void onShown() {}
    default void onHidden() {}
}
