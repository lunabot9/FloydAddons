package floydaddons.not.dogshit.client.gui;
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
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Camera settings screen: Freecam speed, Freelook distance, F5 Customizer options.
 */
public class CameraScreen extends Screen {
    private final Screen parent;

    private ButtonWidget freecamToggle;
    private SliderWidget freecamSpeedSlider;
    private ButtonWidget freelookToggle;
    private SliderWidget freelookDistanceSlider;
    private ButtonWidget f5DisableFrontToggle;
    private ButtonWidget f5DisableBackToggle;
    private ButtonWidget f5ScrollToggle;
    private ButtonWidget f5ResetToggle;
    private SliderWidget f5DistanceSlider;
    private ButtonWidget doneButton;

    private static final int BOX_WIDTH = 320;
    private static final int BOX_HEIGHT = 304;
    private static final int DRAG_BAR_HEIGHT = 18;
    private static final long FADE_DURATION_MS = 90;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_SPACING = 26;
    private static final int FULL_W = 220;
    private static final int HALF_W = 108;
    private static final int PAIR_GAP = 4;

    private int panelX, panelY;
    private boolean draggingPanel = false;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartPanelX, dragStartPanelY;

    private long openStartMs;
    private long closeStartMs;
    private boolean closing = false;

    public CameraScreen(Screen parent) {
        super(Text.literal("Camera"));
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

        // Row 0: "Freecam" header (drawn in render)
        // Row 1: Freecam toggle + Speed slider side by side
        freecamToggle = ButtonWidget.builder(Text.literal(freecamLabel()), b -> {
            CameraConfig.toggleFreecam();
            b.setMessage(Text.literal(freecamLabel()));
            FloydAddonsConfig.save();
        }).dimensions(le, rowY(1), HALF_W, ROW_HEIGHT).build();

        freecamSpeedSlider = new SliderWidget(
                le + HALF_W + PAIR_GAP, rowY(1), HALF_W, ROW_HEIGHT,
                Text.literal(freecamSpeedLabel()),
                normalizeFreecamSpeed(CameraConfig.getFreecamSpeed())
        ) {
            @Override protected void updateMessage() { setMessage(Text.literal(freecamSpeedLabel())); }
            @Override protected void applyValue() {
                CameraConfig.setFreecamSpeed(denormalizeFreecamSpeed(this.value));
                FloydAddonsConfig.save();
            }
            @Override public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {}
        };

        // Row 2: "Freelook" header (drawn in render)
        // Row 3: Freelook toggle + Distance slider side by side
        freelookToggle = ButtonWidget.builder(Text.literal(freelookLabel()), b -> {
            CameraConfig.toggleFreelook();
            b.setMessage(Text.literal(freelookLabel()));
            FloydAddonsConfig.save();
        }).dimensions(le, rowY(3), HALF_W, ROW_HEIGHT).build();

        freelookDistanceSlider = new SliderWidget(
                le + HALF_W + PAIR_GAP, rowY(3), HALF_W, ROW_HEIGHT,
                Text.literal(freelookDistanceLabel()),
                normalizeFreelookDistance(CameraConfig.getFreelookDistance())
        ) {
            @Override protected void updateMessage() { setMessage(Text.literal(freelookDistanceLabel())); }
            @Override protected void applyValue() {
                CameraConfig.setFreelookDistance(denormalizeFreelookDistance(this.value));
                FloydAddonsConfig.save();
            }
            @Override public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {}
        };

        // Row 4: "F5 Customizer" header (drawn in render)
        // Row 5: F5 Disable Front + Disable Back
        f5DisableFrontToggle = ButtonWidget.builder(Text.literal(f5FrontLabel()), b -> {
            CameraConfig.setF5DisableFront(!CameraConfig.isF5DisableFront());
            b.setMessage(Text.literal(f5FrontLabel()));
            FloydAddonsConfig.save();
        }).dimensions(le, rowY(5), HALF_W, ROW_HEIGHT).build();

        f5DisableBackToggle = ButtonWidget.builder(Text.literal(f5BackLabel()), b -> {
            CameraConfig.setF5DisableBack(!CameraConfig.isF5DisableBack());
            b.setMessage(Text.literal(f5BackLabel()));
            FloydAddonsConfig.save();
        }).dimensions(le + HALF_W + PAIR_GAP, rowY(5), HALF_W, ROW_HEIGHT).build();

        // Row 6: F5 Scroll Distance toggle
        f5ScrollToggle = ButtonWidget.builder(Text.literal(f5ScrollLabel()), b -> {
            CameraConfig.setF5ScrollEnabled(!CameraConfig.isF5ScrollEnabled());
            b.setMessage(Text.literal(f5ScrollLabel()));
            FloydAddonsConfig.save();
        }).dimensions(le, rowY(6), FULL_W, ROW_HEIGHT).build();

        // Row 7: F5 Reset On Toggle
        f5ResetToggle = ButtonWidget.builder(Text.literal(f5ResetLabel()), b -> {
            CameraConfig.setF5ResetOnToggle(!CameraConfig.isF5ResetOnToggle());
            b.setMessage(Text.literal(f5ResetLabel()));
            FloydAddonsConfig.save();
        }).dimensions(le, rowY(7), FULL_W, ROW_HEIGHT).build();

        // Row 8: F5 Camera Distance slider
        f5DistanceSlider = new SliderWidget(
                le, rowY(8), FULL_W, ROW_HEIGHT,
                Text.literal(f5DistanceLabel()),
                normalizeF5Distance(CameraConfig.getF5CameraDistance())
        ) {
            @Override protected void updateMessage() { setMessage(Text.literal(f5DistanceLabel())); }
            @Override protected void applyValue() {
                CameraConfig.setF5CameraDistance(denormalizeF5Distance(this.value));
                FloydAddonsConfig.save();
            }
            @Override public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {}
        };

        // Done
        doneButton = ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(panelX + (BOX_WIDTH - 100) / 2, panelY + BOX_HEIGHT - 30, 100, ROW_HEIGHT)
                .build();

        addDrawableChild(freecamToggle);
        addDrawableChild(freecamSpeedSlider);
        addDrawableChild(freelookToggle);
        addDrawableChild(freelookDistanceSlider);
        addDrawableChild(f5DisableFrontToggle);
        addDrawableChild(f5DisableBackToggle);
        addDrawableChild(f5ScrollToggle);
        addDrawableChild(f5ResetToggle);
        addDrawableChild(f5DistanceSlider);
        addDrawableChild(doneButton);
    }

    private String freecamLabel() { return "Freecam: " + (CameraConfig.isFreecamEnabled() ? "ON" : "OFF"); }
    private String freecamSpeedLabel() { return "Speed: " + String.format("%.1f", CameraConfig.getFreecamSpeed()); }
    private String freelookLabel() { return "Freelook: " + (CameraConfig.isFreelookEnabled() ? "ON" : "OFF"); }
    private String freelookDistanceLabel() { return "Dist: " + String.format("%.1f", CameraConfig.getFreelookDistance()); }
    private String f5FrontLabel() { return "Disable Front: " + (CameraConfig.isF5DisableFront() ? "ON" : "OFF"); }
    private String f5BackLabel() { return "Disable Back: " + (CameraConfig.isF5DisableBack() ? "ON" : "OFF"); }
    private String f5ScrollLabel() { return "Scrolling Changes Distance: " + (CameraConfig.isF5ScrollEnabled() ? "ON" : "OFF"); }
    private String f5ResetLabel() { return "Reset F5 Scrolling: " + (CameraConfig.isF5ResetOnToggle() ? "ON" : "OFF"); }
    private String f5DistanceLabel() { return "F5 Distance: " + String.format("%.1f", CameraConfig.getF5CameraDistance()); }

    private double normalizeFreecamSpeed(float v) { return (v - 0.1f) / 9.9f; }
    private float denormalizeFreecamSpeed(double n) { return (float)(n * 9.9 + 0.1); }
    private double normalizeFreelookDistance(float v) { return (v - 1.0f) / 19.0f; }
    private float denormalizeFreelookDistance(double n) { return (float)(n * 19.0 + 1.0); }
    private double normalizeF5Distance(float v) { return (v - 1.0f) / 19.0f; }
    private float denormalizeF5Distance(double n) { return (float)(n * 19.0 + 1.0); }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        closeStartMs = Util.getMeasuringTimeMs();
        FloydAddonsConfig.save();
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

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

        context.fill(left, top, right, bottom, applyAlpha(0xAA000000, guiAlpha));
        InventoryHudRenderer.drawChromaBorder(context, left - 1, top - 1, right + 1, bottom + 1, guiAlpha);

        styleButton(context, freecamToggle, guiAlpha, mouseX, mouseY);
        styleSlider(context, freecamSpeedSlider, guiAlpha, mouseX, mouseY, CameraConfig.getFreecamSpeed(), 0.1f, 10.0f);
        styleButton(context, freelookToggle, guiAlpha, mouseX, mouseY);
        styleSlider(context, freelookDistanceSlider, guiAlpha, mouseX, mouseY, CameraConfig.getFreelookDistance(), 1.0f, 20.0f);
        styleButton(context, f5DisableFrontToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, f5DisableBackToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, f5ScrollToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, f5ResetToggle, guiAlpha, mouseX, mouseY);
        styleSlider(context, f5DistanceSlider, guiAlpha, mouseX, mouseY, CameraConfig.getF5CameraDistance(), 1.0f, 20.0f);
        styleButton(context, doneButton, guiAlpha, mouseX, mouseY);

        String title = "Camera";
        int titleWidth = textRenderer.getWidth(title);
        context.drawTextWithShadow(textRenderer, title, panelX + (BOX_WIDTH - titleWidth) / 2, panelY + 6, resolveTextColor(guiAlpha, 0f));

        // Section headers
        drawSectionHeader(context, "Freecam", rowY(0), guiAlpha);
        drawSectionHeader(context, "Freelook", rowY(2), guiAlpha);
        drawSectionHeader(context, "F5 Customizer", rowY(4), guiAlpha);

        matrices.popMatrix();
    }

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        if (click.button() == 0) {
            double mx = click.x(), my = click.y();
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
            panelX = clamp(dragStartPanelX + (int) (click.x() - dragStartMouseX), 0, width - BOX_WIDTH);
            panelY = clamp(dragStartPanelY + (int) (click.y() - dragStartMouseY), 0, height - BOX_HEIGHT);
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
        freecamToggle.setX(le);              freecamToggle.setY(rowY(1));
        freecamSpeedSlider.setX(le + HALF_W + PAIR_GAP); freecamSpeedSlider.setY(rowY(1));
        freelookToggle.setX(le);             freelookToggle.setY(rowY(3));
        freelookDistanceSlider.setX(le + HALF_W + PAIR_GAP); freelookDistanceSlider.setY(rowY(3));
        f5DisableFrontToggle.setX(le);       f5DisableFrontToggle.setY(rowY(5));
        f5DisableBackToggle.setX(le + HALF_W + PAIR_GAP); f5DisableBackToggle.setY(rowY(5));
        f5ScrollToggle.setX(le);             f5ScrollToggle.setY(rowY(6));
        f5ResetToggle.setX(le);              f5ResetToggle.setY(rowY(7));
        f5DistanceSlider.setX(le);           f5DistanceSlider.setY(rowY(8));
        doneButton.setX(panelX + (BOX_WIDTH - 100) / 2); doneButton.setY(panelY + BOX_HEIGHT - 30);
    }

    private void drawSectionHeader(DrawContext context, String text, int y, float alpha) {
        int le = leftEdge();
        int tw = textRenderer.getWidth(text);
        int tx = le + (FULL_W - tw) / 2;
        int ty = y + (ROW_HEIGHT - textRenderer.fontHeight) / 2;
        // Subtle line on each side
        int lineY = ty + textRenderer.fontHeight / 2;
        int lineColor = applyAlpha(0xFF555555, alpha);
        context.fill(le, lineY, tx - 4, lineY + 1, lineColor);
        context.fill(tx + tw + 4, lineY, le + FULL_W, lineY + 1, lineColor);
        context.drawTextWithShadow(textRenderer, text, tx, ty, resolveTextColor(alpha, 0f));
    }

    private void styleButton(DrawContext context, ButtonWidget button, float alpha, int mouseX, int mouseY) {
        int bx = button.getX(), by = button.getY(), bw = button.getWidth(), bh = button.getHeight();
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        context.fill(bx, by, bx + bw, by + bh, applyAlpha(hover ? 0xFF666666 : 0xFF555555, alpha));
        InventoryHudRenderer.drawButtonBorder(context, bx - 1, by - 1, bx + bw + 1, by + bh + 1, alpha);
        String label = button.getMessage().getString();
        int tw = textRenderer.getWidth(label);
        context.drawTextWithShadow(textRenderer, label, bx + (bw - tw) / 2, by + (bh - textRenderer.fontHeight) / 2, resolveTextColor(alpha, 0f));
    }

    private void styleSlider(DrawContext context, SliderWidget slider, float alpha, int mouseX, int mouseY,
                              float value, float min, float max) {
        int bx = slider.getX(), by = slider.getY(), bw = slider.getWidth(), bh = slider.getHeight();
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        context.fill(bx, by, bx + bw, by + bh, applyAlpha(hover ? 0xFF666666 : 0xFF555555, alpha));
        float pct = (value - min) / (max - min);
        int fillW = (int) ((bw - 4) * pct);
        context.fill(bx + 2, by + 2, bx + 2 + fillW, by + bh - 2, applyAlpha(0xFF888888, alpha));
        InventoryHudRenderer.drawButtonBorder(context, bx - 1, by - 1, bx + bw + 1, by + bh + 1, alpha);
        String label = slider.getMessage().getString();
        int tw = textRenderer.getWidth(label);
        context.drawTextWithShadow(textRenderer, label, bx + (bw - tw) / 2, by + (bh - textRenderer.fontHeight) / 2, resolveTextColor(alpha, 0f));
    }

    private int resolveTextColor(float alpha, float offset) {
        int base = RenderConfig.isButtonTextChromaEnabled() ? RenderConfig.chromaColor(offset) : RenderConfig.getButtonTextColor();
        return applyAlpha(base, alpha);
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
