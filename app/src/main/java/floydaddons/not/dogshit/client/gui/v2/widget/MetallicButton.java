package floydaddons.not.dogshit.client.gui.v2.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

/**
 * Pill-shaped button with a metallic gradient fill. Hover / pressed / active visuals
 * match the Figma frames (Main - QoL.png sidebar tabs).
 */
public class MetallicButton {
    protected int x, y, w, h;
    protected String label;
    protected Runnable onClick;
    protected boolean active;
    protected boolean enabled = true;
    protected int radius = V2Theme.BUTTON_RADIUS;

    private boolean pressed = false;

    public MetallicButton(int x, int y, int w, int h, String label, Runnable onClick) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.label = label;
        this.onClick = onClick;
    }

    public MetallicButton setActive(boolean active) {
        this.active = active;
        return this;
    }

    public MetallicButton setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public MetallicButton setLabel(String label) {
        this.label = label;
        return this;
    }

    public MetallicButton setRadius(int radius) {
        this.radius = radius;
        return this;
    }

    public MetallicButton setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return w; }
    public int getHeight() { return h; }
    public boolean isActive() { return active; }

    public boolean isHovered(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean hover = enabled && isHovered(mouseX, mouseY);
        renderBackground(ctx, hover);
        if (active) {
            V2Theme.drawRoundedBorder(ctx, x, y, w, h, radius, V2Theme.OUTLINE_ACTIVE);
        }
        renderLabel(ctx);
    }

    protected void renderBackground(DrawContext ctx, boolean hover) {
        int top = V2Theme.METAL_TOP;
        int mid = V2Theme.METAL_MID;
        int bot = V2Theme.METAL_BOT;
        if (!enabled) {
            top = V2Theme.lerpColor(top, V2Theme.BG_PANE, 0.5f);
            mid = V2Theme.lerpColor(mid, V2Theme.BG_PANE, 0.5f);
            bot = V2Theme.lerpColor(bot, V2Theme.BG_PANE, 0.5f);
        } else if (pressed) {
            top = V2Theme.lerpColor(top, V2Theme.METAL_BOT, 0.25f);
            mid = V2Theme.lerpColor(mid, V2Theme.METAL_BOT, 0.25f);
        } else if (hover) {
            top = V2Theme.lerpColor(top, 0xFFFFFFFF, 0.15f);
            mid = V2Theme.lerpColor(mid, 0xFFFFFFFF, 0.15f);
            bot = V2Theme.lerpColor(bot, 0xFFFFFFFF, 0.15f);
        }
        V2Theme.drawMetallicGradient(ctx, x, y, w, h, radius, top, mid, bot);
    }

    protected void renderLabel(DrawContext ctx) {
        if (label == null || label.isEmpty()) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Text styled = Text.literal(label).styled(s -> s.withBold(true));
        int textW = tr.getWidth(styled);
        int tx = x + (w - textW) / 2;
        int ty = y + (h - tr.fontHeight) / 2 + 1;
        ctx.drawText(tr, styled, tx, ty, V2Theme.TEXT_PRIMARY, false);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!enabled || button != 0) return false;
        if (!isHovered(mx, my)) return false;
        pressed = true;
        return true;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0) return false;
        boolean wasPressed = pressed;
        pressed = false;
        if (wasPressed && enabled && isHovered(mx, my)) {
            if (onClick != null) onClick.run();
            return true;
        }
        return false;
    }
}
