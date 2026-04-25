package floydaddons.not.dogshit.client.gui.v2.tab;

import floydaddons.not.dogshit.client.gui.v2.widget.V2Theme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Empty placeholder QoL tab. Shows a centered "coming in V2.0" message until the
 * QoL feature set is implemented in a later wave.
 */
public class QolTab implements V2Tab {

    private int x, y, w, h;

    @Override
    public String displayName() {
        return "QoL";
    }

    @Override
    public void layout(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Background pane to match the rest of the UI
        V2Theme.fillRoundedRect(ctx, x, y, w, h, V2Theme.PANE_RADIUS, V2Theme.BG_PANE);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String msg = "QoL features coming in V2.0";
        int textW = tr.getWidth(msg);
        int textX = x + (w - textW) / 2;
        int textY = y + (h - tr.fontHeight) / 2;
        ctx.drawText(tr, Text.literal(msg), textX, textY, V2Theme.TEXT_SECONDARY, false);
    }
}
