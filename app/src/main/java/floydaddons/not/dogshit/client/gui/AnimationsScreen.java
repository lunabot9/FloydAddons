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
 * Animations settings screen: held-item position, rotation, scale, swing duration, and toggles.
 */
public class AnimationsScreen extends Screen {
    private final Screen parent;

    private ButtonWidget enabledToggle;
    private SliderWidget posXSlider, posYSlider, posZSlider;
    private SliderWidget rotXSlider, rotYSlider, rotZSlider;
    private SliderWidget scaleSlider, swingDurationSlider;
    private ButtonWidget cancelReEquipToggle, hideHandToggle, classicClickToggle;
    private ButtonWidget doneButton;

    private static final int BOX_WIDTH = 320;
    private static final int BOX_HEIGHT = 400;
    private static final int DRAG_BAR_HEIGHT = 18;
    private static final long FADE_DURATION_MS = 90;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_SPACING = 24;
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

    public AnimationsScreen(Screen parent) {
        super(Text.literal("Animations"));
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

        // Row 0: Enabled toggle
        enabledToggle = ButtonWidget.builder(Text.literal(enabledLabel()), b -> {
            AnimationConfig.setEnabled(!AnimationConfig.isEnabled());
            b.setMessage(Text.literal(enabledLabel()));
            FloydAddonsConfig.save();
        }).dimensions(le, rowY(0), FULL_W, ROW_HEIGHT).build();

        // Row 1-3: Position sliders
        posXSlider = makeIntSlider(le, rowY(1), "Pos X", AnimationConfig.getPosX(), -150, 150,
                v -> AnimationConfig.setPosX(v), () -> AnimationConfig.getPosX());
        posYSlider = makeIntSlider(le, rowY(2), "Pos Y", AnimationConfig.getPosY(), -150, 150,
                v -> AnimationConfig.setPosY(v), () -> AnimationConfig.getPosY());
        posZSlider = makeIntSlider(le, rowY(3), "Pos Z", AnimationConfig.getPosZ(), -150, 50,
                v -> AnimationConfig.setPosZ(v), () -> AnimationConfig.getPosZ());

        // Row 4-6: Rotation sliders
        rotXSlider = makeIntSlider(le, rowY(4), "Rot X", AnimationConfig.getRotX(), -180, 180,
                v -> AnimationConfig.setRotX(v), () -> AnimationConfig.getRotX());
        rotYSlider = makeIntSlider(le, rowY(5), "Rot Y", AnimationConfig.getRotY(), -180, 180,
                v -> AnimationConfig.setRotY(v), () -> AnimationConfig.getRotY());
        rotZSlider = makeIntSlider(le, rowY(6), "Rot Z", AnimationConfig.getRotZ(), -180, 180,
                v -> AnimationConfig.setRotZ(v), () -> AnimationConfig.getRotZ());

        // Row 7: Scale slider
        scaleSlider = new SliderWidget(le, rowY(7), FULL_W, ROW_HEIGHT,
                Text.literal(scaleLabel()), normalizeScale(AnimationConfig.getScale())) {
            @Override protected void updateMessage() { setMessage(Text.literal(scaleLabel())); }
            @Override protected void applyValue() {
                AnimationConfig.setScale(denormalizeScale(this.value));
                FloydAddonsConfig.save();
            }
            @Override public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {}
        };

        // Row 8: Swing Duration slider
        swingDurationSlider = makeIntSlider(le, rowY(8), "Swing Duration", AnimationConfig.getSwingDuration(), 1, 100,
                v -> AnimationConfig.setSwingDuration(v), () -> AnimationConfig.getSwingDuration());

        // Row 9-11: Boolean toggles
        cancelReEquipToggle = ButtonWidget.builder(Text.literal(cancelReEquipLabel()), b -> {
            AnimationConfig.setCancelReEquip(!AnimationConfig.isCancelReEquip());
            b.setMessage(Text.literal(cancelReEquipLabel()));
            FloydAddonsConfig.save();
        }).dimensions(le, rowY(9), FULL_W, ROW_HEIGHT).build();

        hideHandToggle = ButtonWidget.builder(Text.literal(hideHandLabel()), b -> {
            AnimationConfig.setHidePlayerHand(!AnimationConfig.isHidePlayerHand());
            b.setMessage(Text.literal(hideHandLabel()));
            FloydAddonsConfig.save();
        }).dimensions(le, rowY(10), FULL_W, ROW_HEIGHT).build();

        classicClickToggle = ButtonWidget.builder(Text.literal(classicClickLabel()), b -> {
            AnimationConfig.setClassicClick(!AnimationConfig.isClassicClick());
            b.setMessage(Text.literal(classicClickLabel()));
            FloydAddonsConfig.save();
        }).dimensions(le, rowY(11), FULL_W, ROW_HEIGHT).build();

        // Done button
        doneButton = ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(panelX + (BOX_WIDTH - 100) / 2, panelY + BOX_HEIGHT - 30, 100, ROW_HEIGHT)
                .build();

        addDrawableChild(enabledToggle);
        addDrawableChild(posXSlider);
        addDrawableChild(posYSlider);
        addDrawableChild(posZSlider);
        addDrawableChild(rotXSlider);
        addDrawableChild(rotYSlider);
        addDrawableChild(rotZSlider);
        addDrawableChild(scaleSlider);
        addDrawableChild(swingDurationSlider);
        addDrawableChild(cancelReEquipToggle);
        addDrawableChild(hideHandToggle);
        addDrawableChild(classicClickToggle);
        addDrawableChild(doneButton);
    }

    private SliderWidget makeIntSlider(int x, int y, String name, int current, int min, int max,
                                        java.util.function.IntConsumer setter, java.util.function.IntSupplier getter) {
        double norm = (current - min) / (double) (max - min);
        return new SliderWidget(x, y, FULL_W, ROW_HEIGHT, Text.literal(name + ": " + current), norm) {
            @Override protected void updateMessage() {
                setMessage(Text.literal(name + ": " + getter.getAsInt()));
            }
            @Override protected void applyValue() {
                int val = (int) Math.round(this.value * (max - min) + min);
                setter.accept(val);
                FloydAddonsConfig.save();
            }
            @Override public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {}
        };
    }

    private String enabledLabel() { return "Animations: " + (AnimationConfig.isEnabled() ? "ON" : "OFF"); }
    private String scaleLabel() { return "Scale: " + String.format("%.2f", AnimationConfig.getScale()); }
    private String cancelReEquipLabel() { return "Cancel Re-Equip: " + (AnimationConfig.isCancelReEquip() ? "ON" : "OFF"); }
    private String hideHandLabel() { return "Hide Hand: " + (AnimationConfig.isHidePlayerHand() ? "ON" : "OFF"); }
    private String classicClickLabel() { return "Classic Click: " + (AnimationConfig.isClassicClick() ? "ON" : "OFF"); }

    private double normalizeScale(float v) { return (v - 0.1f) / 1.9f; }
    private float denormalizeScale(double n) { return (float) (n * 1.9 + 0.1); }

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

        // Render all widgets with custom styling
        styleButton(context, enabledToggle, guiAlpha, mouseX, mouseY);
        styleSlider(context, posXSlider, guiAlpha, mouseX, mouseY, AnimationConfig.getPosX(), -150, 150);
        styleSlider(context, posYSlider, guiAlpha, mouseX, mouseY, AnimationConfig.getPosY(), -150, 150);
        styleSlider(context, posZSlider, guiAlpha, mouseX, mouseY, AnimationConfig.getPosZ(), -150, 50);
        styleSlider(context, rotXSlider, guiAlpha, mouseX, mouseY, AnimationConfig.getRotX(), -180, 180);
        styleSlider(context, rotYSlider, guiAlpha, mouseX, mouseY, AnimationConfig.getRotY(), -180, 180);
        styleSlider(context, rotZSlider, guiAlpha, mouseX, mouseY, AnimationConfig.getRotZ(), -180, 180);
        styleSlider(context, scaleSlider, guiAlpha, mouseX, mouseY, AnimationConfig.getScale(), 0.1f, 2.0f);
        styleSlider(context, swingDurationSlider, guiAlpha, mouseX, mouseY, AnimationConfig.getSwingDuration(), 1, 100);
        styleButton(context, cancelReEquipToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, hideHandToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, classicClickToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, doneButton, guiAlpha, mouseX, mouseY);

        String title = "Animations";
        int titleWidth = textRenderer.getWidth(title);
        context.drawTextWithShadow(textRenderer, title, panelX + (BOX_WIDTH - titleWidth) / 2, panelY + 6, resolveTextColor(guiAlpha, 0f));

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
        enabledToggle.setX(le);          enabledToggle.setY(rowY(0));
        posXSlider.setX(le);             posXSlider.setY(rowY(1));
        posYSlider.setX(le);             posYSlider.setY(rowY(2));
        posZSlider.setX(le);             posZSlider.setY(rowY(3));
        rotXSlider.setX(le);             rotXSlider.setY(rowY(4));
        rotYSlider.setX(le);             rotYSlider.setY(rowY(5));
        rotZSlider.setX(le);             rotZSlider.setY(rowY(6));
        scaleSlider.setX(le);            scaleSlider.setY(rowY(7));
        swingDurationSlider.setX(le);    swingDurationSlider.setY(rowY(8));
        cancelReEquipToggle.setX(le);    cancelReEquipToggle.setY(rowY(9));
        hideHandToggle.setX(le);         hideHandToggle.setY(rowY(10));
        classicClickToggle.setX(le);     classicClickToggle.setY(rowY(11));
        doneButton.setX(panelX + (BOX_WIDTH - 100) / 2);
        doneButton.setY(panelY + BOX_HEIGHT - 30);
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
