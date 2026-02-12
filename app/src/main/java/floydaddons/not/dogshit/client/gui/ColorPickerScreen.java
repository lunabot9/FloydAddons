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
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.awt.Color;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Color picker with SV rectangle gradient + vertical hue bar.
 * Changes are only applied when the Apply button is pressed.
 */
public class ColorPickerScreen extends Screen {
    private static final int BOX_WIDTH = 240;
    private static final int BOX_HEIGHT = 220;
    private static final int DRAG_BAR_HEIGHT = 16;
    private static final int SV_SIZE = 128;
    private static final int HUE_BAR_W = 14;
    private static final int HUE_BAR_H = 128;
    private static final int PREVIEW_SIZE = 28;
    private static final long FADE_DURATION_MS = 90;

    private final Screen parent;
    private final String titleText;
    private final Consumer<Integer> applyCallback;
    private final BooleanSupplier chromaGetter;
    private final Consumer<Boolean> chromaSetter;

    private int panelX, panelY;
    private boolean dragging = false;
    private boolean draggingSV = false;
    private boolean draggingHue = false;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartPanelX, dragStartPanelY;

    private long openStartMs;
    private long closeStartMs;
    private boolean closing = false;

    private float hue;
    private float sat;
    private float val;

    private int originalColor;
    private boolean originalChroma;

    private ButtonWidget applyButton;
    private ButtonWidget cancelButton;
    private TextFieldWidget hexField;
    private ButtonWidget chromaButton;
    private boolean chromaEnabled;

    /** Guard flag to prevent setText -> changedListener -> setText infinite loop */
    private boolean updatingHex = false;

    public ColorPickerScreen(Screen parent, String titleText, int initialColor,
                             Consumer<Integer> applyCallback,
                             BooleanSupplier chromaGetter, Consumer<Boolean> chromaSetter) {
        super(Text.literal(titleText + " Picker"));
        this.parent = parent;
        this.titleText = titleText;
        this.applyCallback = applyCallback;
        this.chromaGetter = chromaGetter;
        this.chromaSetter = chromaSetter;
        this.originalColor = initialColor;

        float[] hsv = Color.RGBtoHSB((initialColor >> 16) & 0xFF,
                (initialColor >> 8) & 0xFF, initialColor & 0xFF, null);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
    }

    private int svX() { return panelX + 16; }
    private int svY() { return panelY + 26; }
    private int hueBarX() { return svX() + SV_SIZE + 12; }
    private int hueBarY() { return svY(); }

    @Override
    protected void init() {
        openStartMs = Util.getMeasuringTimeMs();
        closing = false;

        panelX = (width - BOX_WIDTH) / 2;
        panelY = (height - BOX_HEIGHT) / 2;
        chromaEnabled = chromaGetter.getAsBoolean();
        originalChroma = chromaEnabled;

        hexField = new TextFieldWidget(textRenderer, 0, 0, 80, 18, Text.literal("Hex"));
        hexField.setMaxLength(6);
        hexField.setEditableColor(0xFFFFFFFF);
        hexField.setUneditableColor(0xFFFFFFFF);
        hexField.setDrawsBackground(false);
        updatingHex = true;
        hexField.setText(colorToHex(currentColor()));
        updatingHex = false;
        hexField.setChangedListener(text -> {
            if (!updatingHex) {
                syncHsvFromHex();
            }
        });
        addSelectableChild(hexField);

        chromaButton = ButtonWidget.builder(Text.literal(chromaLabel()), b -> toggleChroma())
                .dimensions(0, 0, 90, 18).build();
        addDrawableChild(chromaButton);

        applyButton = ButtonWidget.builder(Text.literal("Apply"), b -> applyAndClose())
                .dimensions(0, 0, 80, 18).build();
        cancelButton = ButtonWidget.builder(Text.literal("Cancel"), b -> cancelAndClose())
                .dimensions(0, 0, 80, 18).build();
        addDrawableChild(applyButton);
        addDrawableChild(cancelButton);

        repositionWidgets();
    }

    private void applyAndClose() {
        try {
            applyCallback.accept(currentColor());
            chromaSetter.accept(chromaEnabled);
            RenderConfig.save();
        } catch (Exception ignored) {}
        close();
    }

    private void cancelAndClose() {
        try {
            applyCallback.accept(originalColor);
            chromaSetter.accept(originalChroma);
            RenderConfig.save();
        } catch (Exception ignored) {}
        close();
    }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        closeStartMs = Util.getMeasuringTimeMs();
    }

    private void repositionWidgets() {
        int buttonsY = panelY + BOX_HEIGHT - 32;
        int hexY = panelY + BOX_HEIGHT - 56;
        int hashW = textRenderer.getWidth("#") + 4;
        hexField.setX(panelX + 16 + hashW);
        hexField.setY(hexY);
        chromaButton.setX(panelX + 16 + hashW + 80 + 8);
        chromaButton.setY(hexY);
        applyButton.setX(panelX + 20);
        applyButton.setY(buttonsY);
        cancelButton.setX(panelX + BOX_WIDTH - 100);
        cancelButton.setY(buttonsY);
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

        int left = panelX;
        int top = panelY;
        int right = left + BOX_WIDTH;
        int bottom = top + BOX_HEIGHT;

        context.fill(left, top, right, bottom, applyAlpha(0xCC000000, guiAlpha));
        InventoryHudRenderer.drawChromaBorder(context, left - 1, top - 1, right + 1, bottom + 1, guiAlpha);

        String ttl = titleText + " Picker";
        int tw = textRenderer.getWidth(ttl);
        context.drawTextWithShadow(textRenderer, ttl, left + (BOX_WIDTH - tw) / 2, top + 6,
                resolveTextColor(guiAlpha));

        drawSVPicker(context, guiAlpha);
        drawHueBar(context, guiAlpha);
        drawPreview(context, guiAlpha);
        drawHexField(context, guiAlpha, mouseX, mouseY, delta);

        styleButton(context, chromaButton, guiAlpha, mouseX, mouseY);
        styleButton(context, applyButton, guiAlpha, mouseX, mouseY);
        styleButton(context, cancelButton, guiAlpha, mouseX, mouseY);
    }

    private void drawSVPicker(DrawContext context, float alpha) {
        int x0 = svX();
        int y0 = svY();

        if (chromaEnabled) {
            // Grayed out with pulsing chroma overlay
            context.fill(x0, y0, x0 + SV_SIZE, y0 + SV_SIZE, applyAlpha(0xFF222222, alpha));
            int chromaFlash = RenderConfig.chromaColor((System.currentTimeMillis() % 4000) / 4000f);
            context.fill(x0, y0, x0 + SV_SIZE, y0 + SV_SIZE, applyAlpha(chromaFlash, alpha * 0.15f));
        } else {
            for (int x = 0; x < SV_SIZE; x++) {
                float s = x / (float) (SV_SIZE - 1);
                int topColor = applyAlpha(Color.HSBtoRGB(hue, s, 1.0f) | 0xFF000000, alpha);
                int bottomColor = applyAlpha(0xFF000000, alpha);
                context.fillGradient(x0 + x, y0, x0 + x + 1, y0 + SV_SIZE, topColor, bottomColor);
            }

            int hx = x0 + (int) (sat * (SV_SIZE - 1));
            int hy = y0 + (int) ((1.0f - val) * (SV_SIZE - 1));
            context.fill(hx - 3, hy - 3, hx + 4, hy + 4, applyAlpha(0xFF000000, alpha));
            context.fill(hx - 2, hy - 2, hx + 3, hy + 3, applyAlpha(0xFFFFFFFF, alpha));
        }

        InventoryHudRenderer.drawButtonBorder(context, x0 - 1, y0 - 1, x0 + SV_SIZE + 1, y0 + SV_SIZE + 1, alpha);
    }

    private void drawHueBar(DrawContext context, float alpha) {
        int x0 = hueBarX();
        int y0 = hueBarY();

        if (chromaEnabled) {
            // Grayed out with pulsing chroma overlay
            context.fill(x0, y0, x0 + HUE_BAR_W, y0 + HUE_BAR_H, applyAlpha(0xFF222222, alpha));
            int chromaFlash = RenderConfig.chromaColor((System.currentTimeMillis() % 4000) / 4000f);
            context.fill(x0, y0, x0 + HUE_BAR_W, y0 + HUE_BAR_H, applyAlpha(chromaFlash, alpha * 0.15f));
        } else {
            for (int y = 0; y < HUE_BAR_H; y++) {
                float h = y / (float) (HUE_BAR_H - 1);
                int c = applyAlpha(Color.HSBtoRGB(h, 1.0f, 1.0f) | 0xFF000000, alpha);
                context.fill(x0, y0 + y, x0 + HUE_BAR_W, y0 + y + 1, c);
            }

            int sy = y0 + (int) (hue * (HUE_BAR_H - 1));
            context.fill(x0 - 2, sy - 1, x0 + HUE_BAR_W + 2, sy + 2, applyAlpha(0xFF000000, alpha));
            context.fill(x0 - 1, sy, x0 + HUE_BAR_W + 1, sy + 1, applyAlpha(0xFFFFFFFF, alpha));
        }

        InventoryHudRenderer.drawButtonBorder(context, x0 - 1, y0 - 1, x0 + HUE_BAR_W + 1, y0 + HUE_BAR_H + 1, alpha);
    }

    private void drawPreview(DrawContext context, float alpha) {
        int px = hueBarX() + HUE_BAR_W + 10;
        int py = hueBarY();
        int previewColor = chromaEnabled
                ? RenderConfig.chromaColor((System.currentTimeMillis() % 4000) / 4000f)
                : currentColor();
        context.fill(px, py, px + PREVIEW_SIZE, py + PREVIEW_SIZE, applyAlpha(previewColor, alpha));
        InventoryHudRenderer.drawButtonBorder(context, px - 1, py - 1,
                px + PREVIEW_SIZE + 1, py + PREVIEW_SIZE + 1, alpha);
    }

    private void drawHexField(DrawContext context, float alpha, int mouseX, int mouseY, float delta) {
        int fieldX = hexField.getX();
        int fieldY = hexField.getY();
        int fieldW = hexField.getWidth();
        int fieldH = hexField.getHeight();

        int hashW = textRenderer.getWidth("#") + 4;
        context.fill(fieldX - hashW - 2, fieldY - 2,
                fieldX + fieldW + 2, fieldY + fieldH + 2,
                applyAlpha(0xFF000000, alpha));
        InventoryHudRenderer.drawButtonBorder(context,
                fieldX - hashW - 3, fieldY - 3,
                fieldX + fieldW + 3, fieldY + fieldH + 3, alpha);

        int hashColor = applyAlpha(0xFF888888, alpha);
        context.drawTextWithShadow(textRenderer, "#",
                fieldX - hashW + 2,
                fieldY + (fieldH - textRenderer.fontHeight) / 2,
                hashColor);

        hexField.render(context, mouseX, mouseY, delta);
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
        context.drawTextWithShadow(textRenderer, label, tx, ty, resolveTextColor(alpha));
    }

    private int resolveTextColor(float alpha) {
        int base = (RenderConfig.isButtonTextChromaEnabled())
                ? RenderConfig.chromaColor((System.currentTimeMillis() % 4000) / 4000f)
                : RenderConfig.getButtonTextColor();
        return applyAlpha(base, alpha);
    }

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        double mx = click.x();
        double my = click.y();
        if (click.button() == 0) {
            if (mx >= panelX && mx <= panelX + BOX_WIDTH && my >= panelY && my <= panelY + DRAG_BAR_HEIGHT) {
                dragging = true;
                dragStartMouseX = mx;
                dragStartMouseY = my;
                dragStartPanelX = panelX;
                dragStartPanelY = panelY;
                return true;
            }
            if (!chromaEnabled) {
                int sx = svX(), sy = svY();
                if (mx >= sx && mx <= sx + SV_SIZE && my >= sy && my <= sy + SV_SIZE) {
                    draggingSV = true;
                    updateSV(mx - sx, my - sy);
                    updateHexFromColor();
                    return true;
                }
                int hbx = hueBarX(), hby = hueBarY();
                if (mx >= hbx && mx <= hbx + HUE_BAR_W && my >= hby && my <= hby + HUE_BAR_H) {
                    draggingHue = true;
                    updateHue(my - hby);
                    updateHexFromColor();
                    return true;
                }
            }
            if (hitButton(chromaButton, mx, my)) {
                toggleChroma();
                return true;
            }
            if (hitButton(applyButton, mx, my)) {
                applyAndClose();
                return true;
            }
            if (hitButton(cancelButton, mx, my)) {
                cancelAndClose();
                return true;
            }
        }
        return super.mouseClicked(click, ignoresInput);
    }

    private boolean hitButton(ButtonWidget button, double mx, double my) {
        return mx >= button.getX() && mx <= button.getX() + button.getWidth()
                && my >= button.getY() && my <= button.getY() + button.getHeight();
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (click.button() == 0) {
            if (dragging) {
                int newX = dragStartPanelX + (int) (click.x() - dragStartMouseX);
                int newY = dragStartPanelY + (int) (click.y() - dragStartMouseY);
                panelX = clamp(newX, 0, width - BOX_WIDTH);
                panelY = clamp(newY, 0, height - BOX_HEIGHT);
                repositionWidgets();
                return true;
            }
            if (draggingSV) {
                updateSV(click.x() - svX(), click.y() - svY());
                updateHexFromColor();
                return true;
            }
            if (draggingHue) {
                updateHue(click.y() - hueBarY());
                updateHexFromColor();
                return true;
            }
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            boolean wasDragging = dragging || draggingSV || draggingHue;
            dragging = false;
            draggingSV = false;
            draggingHue = false;
            if (wasDragging) return true;
        }
        return super.mouseReleased(click);
    }

    private void updateSV(double localX, double localY) {
        sat = (float) Math.max(0, Math.min(1, localX / (SV_SIZE - 1)));
        val = (float) Math.max(0, Math.min(1, 1.0 - localY / (SV_SIZE - 1)));
    }

    private void updateHue(double localY) {
        hue = (float) Math.max(0, Math.min(1, localY / (HUE_BAR_H - 1)));
    }

    private int currentColor() {
        return Color.HSBtoRGB(hue, sat, val) | 0xFF000000;
    }

    /** Update hex field text from current HSV without triggering the change listener loop. */
    private void updateHexFromColor() {
        updatingHex = true;
        hexField.setText(colorToHex(currentColor()));
        updatingHex = false;
    }

    /** Parse hex field text and update HSV. Does NOT write back to hexField. */
    private void syncHsvFromHex() {
        int parsed = parseHex(hexField.getText(), currentColor());
        float[] hsv = Color.RGBtoHSB((parsed >> 16) & 0xFF, (parsed >> 8) & 0xFF, parsed & 0xFF, null);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
    }

    private void toggleChroma() {
        chromaEnabled = !chromaEnabled;
        chromaButton.setMessage(Text.literal(chromaLabel()));
    }

    private String chromaLabel() {
        return chromaEnabled ? "Chroma: ON" : "Chroma: OFF";
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private String colorToHex(int color) {
        return String.format("%06X", color & 0xFFFFFF);
    }

    private int parseHex(String text, int fallback) {
        if (text == null) return fallback;
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 3) {
            s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        }
        if (s.length() != 6) return fallback;
        try {
            int v = Integer.parseInt(s, 16);
            return 0xFF000000 | v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
