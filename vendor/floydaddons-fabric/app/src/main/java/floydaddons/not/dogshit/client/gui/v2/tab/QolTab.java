package floydaddons.not.dogshit.client.gui.v2.tab;

import net.minecraft.client.gui.DrawContext;
import floydaddons.not.dogshit.client.gui.v2.widget.ContentPane;

/**
 * Empty QoL tab. The pane is intentionally blank until QoL features exist.
 */
public class QolTab implements V2Tab {

    private final ContentPane pane = new ContentPane(0, 0, 0, 0, "QoL");

    @Override
    public String displayName() {
        return "QoL";
    }

    @Override
    public void layout(int x, int y, int w, int h) {
        pane.setBounds(x, y, w, h);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        pane.render(ctx, mouseX, mouseY, delta);
    }
}
