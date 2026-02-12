package floydaddons.not.dogshit.client.features.hud;
import floydaddons.not.dogshit.client.*;
import floydaddons.not.dogshit.client.config.*;
import floydaddons.not.dogshit.client.gui.*;
import floydaddons.not.dogshit.client.features.hud.*;
import floydaddons.not.dogshit.client.features.visual.*;
import floydaddons.not.dogshit.client.features.cosmetic.*;
import floydaddons.not.dogshit.client.features.misc.*;
import floydaddons.not.dogshit.client.esp.*;
import floydaddons.not.dogshit.client.skin.*;
import floydaddons.not.dogshit.client.util.*;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * HUD settings screen with toggles for Inventory HUD and Scoreboard,
 * plus an Edit Layout button.
 */
public class HudScreen extends Screen {
    private final Screen parent;

    private ButtonWidget inventoryToggle;
    private ButtonWidget scoreboardToggle;
    private ButtonWidget editLayoutButton;
    private ButtonWidget doneButton;

    private static final int BOX_WIDTH = 320;
    private static final int BOX_HEIGHT = 160;
    private static final int DRAG_BAR_HEIGHT = 18;
    private static final long FADE_DURATION_MS = 90;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_SPACING = 26;
    private static final int FULL_W = 220;

    private int panelX;
    private int panelY;
    private boolean draggingPanel = false;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartPanelX, dragStartPanelY;

    private long openStartMs;
    private long closeStartMs;
    private boolean closing = false;

    public HudScreen(Screen parent) {
        super(Text.literal("HUD Settings"));
        this.parent = parent;
    }

    private int leftEdge() { return panelX + (BOX_WIDTH - FULL_W) / 2; }
    private int rowY(int row) { return panelY + 26 + row * ROW_SPACING; }

    @Override
    protected void init() {
        openStartMs = Util.getMeasuringTimeMs();
        closing = false;

        panelX = (width - BOX_WIDTH) / 2;
        panelY = (height - BOX_HEIGHT) / 2;

        int le = leftEdge();

        // Row 0: Inventory HUD toggle (full width)
        inventoryToggle = ButtonWidget.builder(Text.literal(inventoryLabel()), b -> {
            RenderConfig.setInventoryHudEnabled(!RenderConfig.isInventoryHudEnabled());
            b.setMessage(Text.literal(inventoryLabel()));
            RenderConfig.save();
        }).dimensions(le, rowY(0), FULL_W, ROW_HEIGHT).build();

        // Row 1: Scoreboard toggle (full width)
        scoreboardToggle = ButtonWidget.builder(Text.literal(scoreboardLabel()), b -> {
            RenderConfig.setCustomScoreboardEnabled(!RenderConfig.isCustomScoreboardEnabled());
            b.setMessage(Text.literal(scoreboardLabel()));
            RenderConfig.save();
        }).dimensions(le, rowY(1), FULL_W, ROW_HEIGHT).build();

        // Row 2: Edit Layout button (full width)
        editLayoutButton = ButtonWidget.builder(Text.literal("Edit Layout"), b -> {
            if (client != null) client.setScreen(new MoveHudScreen(this));
        }).dimensions(le, rowY(2), FULL_W, ROW_HEIGHT).build();

        // Done button at bottom center
        doneButton = ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(panelX + (BOX_WIDTH - 100) / 2, panelY + BOX_HEIGHT - 30, 100, ROW_HEIGHT)
                .build();

        addDrawableChild(inventoryToggle);
        addDrawableChild(scoreboardToggle);
        addDrawableChild(editLayoutButton);
        addDrawableChild(doneButton);
    }

    private String inventoryLabel() { return "Inventory HUD: " + (RenderConfig.isInventoryHudEnabled() ? "ON" : "OFF"); }
    private String scoreboardLabel() { return "Scoreboard: " + (RenderConfig.isCustomScoreboardEnabled() ? "ON" : "OFF"); }

    @Override
    public void close() {
        requestClose();
    }

    private void requestClose() {
        if (closing) return;
        closing = true;
        closeStartMs = Util.getMeasuringTimeMs();
        RenderConfig.save();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // keep world visible; no blur
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float openProgress = Math.min(1.0f, (Util.getMeasuringTimeMs() - openStartMs) / (float) FADE_DURATION_MS);
        float closeProgress = closing ? Math.min(1.0f, (Util.getMeasuringTimeMs() - closeStartMs) / (float) FADE_DURATION_MS) : 0f;
        if (closing && closeProgress >= 1.0f) {
            if (client != null) client.setScreen(parent);
            return;
        }
        float guiAlpha = closing ? (1.0f - closeProgress) : openProgress;
        if (guiAlpha <= 0f) return;

        float scale = 0.85f + guiAlpha * 0.15f;

        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) (panelX + BOX_WIDTH / 2), (float) (panelY + BOX_HEIGHT / 2));
        matrices.scale(scale, scale);
        matrices.translate((float) -(panelX + BOX_WIDTH / 2), (float) -(panelY + BOX_HEIGHT / 2));

        int left = panelX;
        int top = panelY;
        int right = left + BOX_WIDTH;
        int bottom = top + BOX_HEIGHT;

        int baseColor = applyAlpha(0xAA000000, guiAlpha);
        context.fill(left, top, right, bottom, baseColor);
        InventoryHudRenderer.drawChromaBorder(context, left - 1, top - 1, right + 1, bottom + 1, guiAlpha);

        styleButton(context, inventoryToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, scoreboardToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, editLayoutButton, guiAlpha, mouseX, mouseY);
        styleButton(context, doneButton, guiAlpha, mouseX, mouseY);

        // Title
        String title = "HUD Settings";
        int titleWidth = textRenderer.getWidth(title);
        int tx = panelX + (BOX_WIDTH - titleWidth) / 2;
        int ty = panelY + 6;
        context.drawTextWithShadow(textRenderer, title, tx, ty, resolveTextColor(guiAlpha, 0f));

        matrices.popMatrix();
    }

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        double mx = click.x();
        double my = click.y();
        if (click.button() == 0) {
            if (mx >= panelX && mx <= panelX + BOX_WIDTH && my >= panelY && my <= panelY + DRAG_BAR_HEIGHT) {
                draggingPanel = true;
                dragStartMouseX = mx;
                dragStartMouseY = my;
                dragStartPanelX = panelX;
                dragStartPanelY = panelY;
                return true;
            }
        }
        return super.mouseClicked(click, ignoresInput);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (click.button() == 0 && draggingPanel) {
            int newX = dragStartPanelX + (int) (click.x() - dragStartMouseX);
            int newY = dragStartPanelY + (int) (click.y() - dragStartMouseY);
            newX = clamp(newX, 0, width - BOX_WIDTH);
            newY = clamp(newY, 0, height - BOX_HEIGHT);
            panelX = newX;
            panelY = newY;
            repositionWidgets();
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0 && draggingPanel) {
            draggingPanel = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    private void repositionWidgets() {
        int le = leftEdge();
        inventoryToggle.setX(le);      inventoryToggle.setY(rowY(0));
        scoreboardToggle.setX(le);     scoreboardToggle.setY(rowY(1));
        editLayoutButton.setX(le);     editLayoutButton.setY(rowY(2));
        doneButton.setX(panelX + (BOX_WIDTH - 100) / 2); doneButton.setY(panelY + BOX_HEIGHT - 30);
    }

    private void styleButton(DrawContext context, ButtonWidget button, float alpha, int mouseX, int mouseY) {
        int bx = button.getX();
        int by = button.getY();
        int bw = button.getWidth();
        int bh = button.getHeight();
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        int fill = applyAlpha(hover ? 0xFF666666 : 0xFF555555, alpha);
        context.fill(bx, by, bx + bw, by + bh, fill);
        InventoryHudRenderer.drawButtonBorder(context, bx - 1, by - 1, bx + bw + 1, by + bh + 1, alpha);
        String label = button.getMessage().getString();
        int textWidth = textRenderer.getWidth(label);
        int tx = bx + (bw - textWidth) / 2;
        int ty = by + (bh - textRenderer.fontHeight) / 2;
        float offset = (System.currentTimeMillis() % 4000) / 4000f;
        context.drawTextWithShadow(textRenderer, label, tx, ty, resolveTextColor(alpha, offset));
    }

    private int resolveTextColor(float alpha, float chromaOffset) {
        int base = (RenderConfig.isButtonTextChromaEnabled())
                ? chromaColor(chromaOffset)
                : RenderConfig.getButtonTextColor();
        return applyAlpha(base, alpha);
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private int chromaColor(float offset) {
        if (!(RenderConfig.isButtonTextChromaEnabled())) return RenderConfig.getButtonTextColor();
        return RenderConfig.chromaColor(offset);
    }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

}
