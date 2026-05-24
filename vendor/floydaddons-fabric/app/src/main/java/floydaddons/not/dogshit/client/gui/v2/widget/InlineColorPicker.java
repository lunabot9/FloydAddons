package floydaddons.not.dogshit.client.gui.v2.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.awt.Color;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Compact embedded color picker designed to fit inside an accordion body. Layout:
 * 60x60 SV square, narrow vertical hue bar, hex input, current/new swatches.
 *
 * <p>This intentionally has NO chroma toggle, NO fade toggle — those features remain
 * on the existing modal {@code ColorPickerScreen} only.
 */
public class InlineColorPicker {
    private static final int SV_SIZE = 60;
    private static final int HUE_BAR_W = 8;
    private static final int HUE_BAR_H = 60;
    private static final int SWATCH_SIZE = 14;
    private static final int GAP = 6;
    private static final int HEX_W = 56;
    private static final int HEX_H = 12;

    private int x, y;
    private final IntSupplier getColor;
    private final IntConsumer setColor;

    private float hue, sat, val;
    private final int originalColor;

    private boolean draggingSV = false;
    private boolean draggingHue = false;

    private TextFieldWidget hexField;
    private boolean updatingHex = false;

    public InlineColorPicker(int x, int y, IntSupplier getColor, IntConsumer setColor) {
        this.x = x;
        this.y = y;
        this.getColor = getColor;
        this.setColor = setColor;
        this.originalColor = getColor.getAsInt();
        syncHsvFromColor(originalColor);
        ensureHexField();
    }

    private void ensureHexField() {
        if (hexField != null) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        hexField = new TextFieldWidget(tr, x, y, HEX_W, HEX_H, Text.literal("Hex"));
        hexField.setMaxLength(6);
        hexField.setEditableColor(0xFFFFFFFF);
        hexField.setUneditableColor(0xFFFFFFFF);
        hexField.setDrawsBackground(false);
        updatingHex = true;
        hexField.setText(toHex(getColor.getAsInt()));
        updatingHex = false;
        hexField.setChangedListener(text -> {
            if (!updatingHex) {
                int parsed = parseHex(text, currentColor());
                syncHsvFromColor(parsed);
                setColor.accept(parsed);
            }
        });
    }

    public void setPos(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return SV_SIZE + GAP + HUE_BAR_W + GAP + Math.max(HEX_W, SWATCH_SIZE * 2 + GAP); }
    public int getHeight() { return Math.max(SV_SIZE, HEX_H + GAP + SWATCH_SIZE); }

    private int svX() { return x; }
    private int svY() { return y; }
    private int hueBarX() { return svX() + SV_SIZE + GAP; }
    private int hueBarY() { return svY(); }
    private int rightX() { return hueBarX() + HUE_BAR_W + GAP; }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ensureHexField();

        // SV picker
        int sx = svX(), sy = svY();
        for (int i = 0; i < SV_SIZE; i++) {
            float s = i / (float) (SV_SIZE - 1);
            int topColor = Color.HSBtoRGB(hue, s, 1.0f) | 0xFF000000;
            int bottomColor = 0xFF000000;
            ctx.fillGradient(sx + i, sy, sx + i + 1, sy + SV_SIZE, topColor, bottomColor);
        }
        // SV cursor
        int hx = sx + (int) (sat * (SV_SIZE - 1));
        int hy = sy + (int) ((1.0f - val) * (SV_SIZE - 1));
        ctx.fill(hx - 2, hy - 2, hx + 3, hy + 3, 0xFF000000);
        ctx.fill(hx - 1, hy - 1, hx + 2, hy + 2, 0xFFFFFFFF);
        V2Theme.drawRoundedBorder(ctx, sx - 1, sy - 1, SV_SIZE + 2, SV_SIZE + 2, 0, V2Theme.METAL_MID);

        // Hue bar
        int hbx = hueBarX(), hby = hueBarY();
        for (int j = 0; j < HUE_BAR_H; j++) {
            float h = j / (float) (HUE_BAR_H - 1);
            int c = Color.HSBtoRGB(h, 1.0f, 1.0f) | 0xFF000000;
            ctx.fill(hbx, hby + j, hbx + HUE_BAR_W, hby + j + 1, c);
        }
        int hueY = hby + (int) (hue * (HUE_BAR_H - 1));
        ctx.fill(hbx - 1, hueY, hbx + HUE_BAR_W + 1, hueY + 1, 0xFFFFFFFF);
        V2Theme.drawRoundedBorder(ctx, hbx - 1, hby - 1, HUE_BAR_W + 2, HUE_BAR_H + 2, 0, V2Theme.METAL_MID);

        // Hex field background
        int rx = rightX();
        ctx.fill(rx - 1, y - 1, rx + HEX_W + 1, y + HEX_H + 1, 0xFF000000);
        V2Theme.drawRoundedBorder(ctx, rx - 1, y - 1, HEX_W + 2, HEX_H + 2, 0, V2Theme.METAL_MID);
        hexField.setX(rx);
        hexField.setY(y);
        hexField.render(ctx, mouseX, mouseY, delta);

        // Swatches: original (left), current (right)
        int swatchY = y + HEX_H + GAP;
        ctx.fill(rx, swatchY, rx + SWATCH_SIZE, swatchY + SWATCH_SIZE, originalColor | 0xFF000000);
        V2Theme.drawRoundedBorder(ctx, rx - 1, swatchY - 1, SWATCH_SIZE + 2, SWATCH_SIZE + 2, 0, V2Theme.METAL_MID);
        int curX = rx + SWATCH_SIZE + GAP;
        ctx.fill(curX, swatchY, curX + SWATCH_SIZE, swatchY + SWATCH_SIZE, currentColor() | 0xFF000000);
        V2Theme.drawRoundedBorder(ctx, curX - 1, swatchY - 1, SWATCH_SIZE + 2, SWATCH_SIZE + 2, 0, V2Theme.METAL_MID);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        ensureHexField();
        int sx = svX(), sy = svY();
        if (mx >= sx && mx < sx + SV_SIZE && my >= sy && my < sy + SV_SIZE) {
            draggingSV = true;
            updateSV(mx - sx, my - sy);
            updateHexField();
            setColor.accept(currentColor());
            return true;
        }
        int hbx = hueBarX(), hby = hueBarY();
        if (mx >= hbx && mx < hbx + HUE_BAR_W && my >= hby && my < hby + HUE_BAR_H) {
            draggingHue = true;
            updateHue(my - hby);
            updateHexField();
            setColor.accept(currentColor());
            return true;
        }
        // Hex field click: focus it
        if (mx >= hexField.getX() && mx < hexField.getX() + hexField.getWidth()
                && my >= hexField.getY() && my < hexField.getY() + hexField.getHeight()) {
            hexField.setFocused(true);
            return true;
        }
        hexField.setFocused(false);
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button != 0) return false;
        if (draggingSV) {
            updateSV(mx - svX(), my - svY());
            updateHexField();
            setColor.accept(currentColor());
            return true;
        }
        if (draggingHue) {
            updateHue(my - hueBarY());
            updateHexField();
            setColor.accept(currentColor());
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0) return false;
        boolean was = draggingSV || draggingHue;
        draggingSV = false;
        draggingHue = false;
        return was;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        ensureHexField();
        if (hexField.isFocused()) {
            return hexField.keyPressed(new KeyInput(keyCode, scanCode, modifiers));
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        ensureHexField();
        if (hexField.isFocused()) {
            return hexField.charTyped(new CharInput(chr, modifiers));
        }
        return false;
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

    private void syncHsvFromColor(int color) {
        float[] hsv = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
    }

    private void updateHexField() {
        if (hexField == null) return;
        updatingHex = true;
        hexField.setText(toHex(currentColor()));
        updatingHex = false;
    }

    private static String toHex(int color) {
        return String.format("%06X", color & 0xFFFFFF);
    }

    private static int parseHex(String text, int fallback) {
        if (text == null) return fallback;
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 3) {
            s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        }
        if (s.length() != 6) return fallback;
        try {
            return 0xFF000000 | Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
