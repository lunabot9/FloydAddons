package floydaddons.not.dogshit.client.gui.v2.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Right-pane container with rounded background, big title with underline, and a vertical
 * list of {@link Child} entries. Provides scroll if total content height exceeds the
 * visible area.
 *
 * <p>The pane positions and sizes children via {@link Child#layout(int, int, int)} —
 * each child computes its height before being asked to render.
 */
public class ContentPane {

    /**
     * Anything renderable inside the pane. Implementations decide their own height
     * (which can change frame-to-frame, e.g. an expanded accordion).
     */
    public interface Child {
        int getHeight();
        void layout(int x, int y, int w);
        void render(DrawContext ctx, int mouseX, int mouseY, float delta);
        default boolean mouseClicked(double mx, double my, int button) { return false; }
        default boolean mouseReleased(double mx, double my, int button) { return false; }
        default boolean mouseDragged(double mx, double my, int button, double dx, double dy) { return false; }
        default boolean mouseScrolled(double mx, double my, double horiz, double vert) { return false; }
        default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
        default boolean charTyped(char chr, int modifiers) { return false; }
    }

    private static final int TITLE_PAD_TOP = 6;
    private static final int TITLE_PAD_BOTTOM = 6;
    private static final int CONTENT_PAD = 12;
    private static final int CHILD_GAP = 8;
    private static final int SCROLL_STEP = 14;

    private int x, y, w, h;
    private final String title;
    private final List<Child> children = new ArrayList<>();
    private int scroll = 0;

    public ContentPane(int x, int y, int w, int h, String title) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.title = title;
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public ContentPane add(Child child) {
        children.add(child);
        return this;
    }

    public void clearChildren() {
        children.clear();
    }

    public List<Child> getChildren() {
        return children;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return w; }
    public int getHeight() { return h; }

    private int contentTop() {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        return y + TITLE_PAD_TOP + tr.fontHeight + TITLE_PAD_BOTTOM + 4;
    }

    private int contentLeft() { return x + CONTENT_PAD; }
    private int contentWidth() { return w - CONTENT_PAD * 2; }
    private int contentBottom() { return y + h - CONTENT_PAD; }

    private int totalContentHeight() {
        int total = 0;
        for (int i = 0; i < children.size(); i++) {
            total += children.get(i).getHeight();
            if (i < children.size() - 1) total += CHILD_GAP;
        }
        return total;
    }

    private int maxScroll() {
        int visible = contentBottom() - contentTop();
        int total = totalContentHeight();
        return Math.max(0, total - visible);
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Pane background
        V2Theme.fillRoundedRect(ctx, x, y, w, h, V2Theme.PANE_RADIUS, V2Theme.BG_PANE);

        // Title centered
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Text styled = Text.literal(title).styled(s -> s.withBold(true));
        int titleW = tr.getWidth(styled);
        int titleX = x + (w - titleW) / 2;
        int titleY = y + TITLE_PAD_TOP;
        ctx.drawText(tr, styled, titleX, titleY, V2Theme.TEXT_PRIMARY, false);

        // Underline
        int underlineY = titleY + tr.fontHeight + 3;
        ctx.fill(x + CONTENT_PAD, underlineY, x + w - CONTENT_PAD, underlineY + 1, V2Theme.DIVIDER);

        // Layout children
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        int yCursor = contentTop() - scroll;
        for (Child c : children) {
            c.layout(contentLeft(), yCursor, contentWidth());
            yCursor += c.getHeight() + CHILD_GAP;
        }

        // Scissor + render children
        ctx.enableScissor(x, contentTop(), x + w, contentBottom());
        try {
            for (Child c : children) {
                c.render(ctx, mouseX, mouseY, delta);
            }
        } finally {
            ctx.disableScissor();
        }

        // Scrollbar (subtle, only when there's overflow)
        int max = maxScroll();
        if (max > 0) {
            int trackH = contentBottom() - contentTop();
            int barH = Math.max(20, (int) (trackH * (trackH / (float) (trackH + max))));
            int barY = contentTop() + (int) ((trackH - barH) * (scroll / (float) max));
            int barX = x + w - 4;
            ctx.fill(barX, barY, barX + 2, barY + barH, V2Theme.METAL_MID);
        }
    }

    private boolean inContentArea(double mx, double my) {
        return mx >= x && mx < x + w && my >= contentTop() && my < contentBottom();
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!inContentArea(mx, my)) return false;
        for (Child c : children) {
            if (c.mouseClicked(mx, my, button)) return true;
        }
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        boolean any = false;
        for (Child c : children) {
            any |= c.mouseReleased(mx, my, button);
        }
        return any;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        boolean any = false;
        for (Child c : children) {
            any |= c.mouseDragged(mx, my, button, dx, dy);
        }
        return any;
    }

    public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        if (!inContentArea(mx, my)) return false;
        // Forward to children first (e.g. dropdown popup)
        for (Child c : children) {
            if (c.mouseScrolled(mx, my, horiz, vert)) return true;
        }
        // Otherwise scroll the pane
        int max = maxScroll();
        if (max <= 0) return false;
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.round(vert * SCROLL_STEP)));
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (Child c : children) {
            if (c.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        for (Child c : children) {
            if (c.charTyped(chr, modifiers)) return true;
        }
        return false;
    }
}
