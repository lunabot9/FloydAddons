package floydaddons.not.dogshit.client.gui.v2.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Accordion row that expands to show body content in place. Header is a metallic pill
 * with the title (left) and a chevron (right). When expanded the chevron flips, the
 * header gets a white outline, and the body content renders below the header.
 */
public class AccordionRow {

    /** Body content rendered when the accordion is expanded. */
    public interface Body {
        int getHeight();
        void render(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY, float delta);
        default boolean mouseClicked(double mx, double my, int button) { return false; }
        default boolean mouseReleased(double mx, double my, int button) { return false; }
        default boolean mouseDragged(double mx, double my, int button, double dx, double dy) { return false; }
        default boolean mouseScrolled(double mx, double my, double horiz, double vert) { return false; }
        default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
        default boolean charTyped(char chr, int modifiers) { return false; }
    }

    private static final float TWEEN_PER_SEC = 9f; // ~110ms full transition

    private int x, y, w, headerH;
    private final String title;
    private final Body body;
    private boolean expanded = false;
    /** 0 = collapsed, 1 = fully expanded. */
    private float expandAnim = 0f;

    public AccordionRow(int x, int y, int w, int headerH, String title, Body body) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.headerH = headerH;
        this.title = title;
        this.body = body;
    }

    public AccordionRow setExpanded(boolean expanded) {
        this.expanded = expanded;
        return this;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setPos(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setWidth(int w) {
        this.w = w;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return w; }
    public int getHeaderHeight() { return headerH; }

    public int getTotalHeight() {
        if (body == null) return headerH;
        return headerH + Math.round(body.getHeight() * expandAnim);
    }

    private boolean headerHovered(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + headerH;
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Animate
        float target = expanded ? 1f : 0f;
        float step = TWEEN_PER_SEC * delta / 20f;
        if (expandAnim < target) expandAnim = Math.min(target, expandAnim + step);
        else if (expandAnim > target) expandAnim = Math.max(target, expandAnim - step);

        // Header bg
        V2Theme.drawMetallicGradient(ctx, x, y, w, headerH, V2Theme.BUTTON_RADIUS);
        if (expanded) {
            V2Theme.drawRoundedBorder(ctx, x, y, w, headerH, V2Theme.BUTTON_RADIUS, V2Theme.OUTLINE_ACTIVE);
        }

        // Title
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Text styled = Text.literal(title).styled(s -> s.withBold(true));
        int textY = y + (headerH - tr.fontHeight) / 2 + 1;
        ctx.drawText(tr, styled, x + 14, textY, V2Theme.TEXT_PRIMARY, false);

        // Chevron (uses the bold style — '>' when collapsed, 'v' when expanded)
        String chev = expandAnim >= 0.5f ? "v" : ">";
        Text chevText = Text.literal(chev).styled(s -> s.withBold(true));
        int chevW = tr.getWidth(chevText);
        ctx.drawText(tr, chevText, x + w - 14 - chevW, textY, V2Theme.TEXT_CHEVRON, false);

        // Body (clipped via animation height)
        if (body != null && expandAnim > 0f) {
            int bodyH = Math.round(body.getHeight() * expandAnim);
            int bodyY = y + headerH;
            ctx.fill(x, bodyY, x + w, bodyY + bodyH, V2Theme.BG_BODY);
            // Render body content; consumer uses (x, bodyY, w) as anchor
            ctx.enableScissor(x, bodyY, x + w, bodyY + bodyH);
            try {
                body.render(ctx, x, bodyY, w, mouseX, mouseY, delta);
            } finally {
                ctx.disableScissor();
            }
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (headerHovered(mx, my) && button == 0) {
            expanded = !expanded;
            return true;
        }
        if (body != null && expanded && expandAnim > 0.5f) {
            int bodyY = y + headerH;
            int bodyH = body.getHeight();
            if (mx >= x && mx < x + w && my >= bodyY && my < bodyY + bodyH) {
                return body.mouseClicked(mx, my, button);
            }
        }
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (body != null) return body.mouseReleased(mx, my, button);
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (body != null) return body.mouseDragged(mx, my, button, dx, dy);
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        if (body != null && expanded) {
            int bodyY = y + headerH;
            int bodyH = body.getHeight();
            if (mx >= x && mx < x + w && my >= bodyY && my < bodyY + bodyH) {
                return body.mouseScrolled(mx, my, horiz, vert);
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (body != null && expanded) return body.keyPressed(keyCode, scanCode, modifiers);
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (body != null && expanded) return body.charTyped(chr, modifiers);
        return false;
    }
}
