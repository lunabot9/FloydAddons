package floydaddons.not.dogshit.client.gui.v2.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Pill-shaped slider matching the Cone Hat row sliders. The track is a flat metallic pill;
 * the value text is rendered centered inside the pill. Drag with mouse to update the value.
 *
 * <p>Optional snap-to-step via {@link #withStep(double)}.
 */
public class Slider {
    private int x, y, w, h;
    private final double min;
    private final double max;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;

    private double step = 0.0;
    private int decimals = 2;
    private boolean dragging = false;

    public Slider(int x, int y, int w, int h,
                  double min, double max,
                  DoubleSupplier getter, DoubleConsumer setter) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.min = min;
        this.max = max;
        this.getter = getter;
        this.setter = setter;
    }

    public Slider withStep(double step) {
        this.step = step;
        return this;
    }

    public Slider withDecimals(int decimals) {
        this.decimals = Math.max(0, decimals);
        return this;
    }

    public void setPos(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(int w, int h) {
        this.w = w;
        this.h = h;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return w; }
    public int getHeight() { return h; }

    public boolean isHovered(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Track (metallic pill)
        V2Theme.drawMetallicGradient(ctx, x, y, w, h, V2Theme.SLIDER_RADIUS);

        double v = getter.getAsDouble();
        double frac = clampFraction(v);

        // Knob — small darker pill on top of the track
        int knobW = Math.max(8, h - 2);
        int travel = w - knobW;
        int knobX = x + (int) Math.round(travel * frac);
        V2Theme.fillRoundedRect(ctx, knobX, y + 1, knobW, h - 2, V2Theme.SLIDER_RADIUS - 1,
                V2Theme.METAL_BOT);

        // Value label centered
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String txt = formatValue(v);
        int tw = tr.getWidth(txt);
        int tx = x + (w - tw) / 2;
        int ty = y + (h - tr.fontHeight) / 2 + 1;
        ctx.drawText(tr, txt, tx, ty, V2Theme.TEXT_PRIMARY, false);
    }

    private double clampFraction(double v) {
        if (max == min) return 0.0;
        double f = (v - min) / (max - min);
        return Math.max(0.0, Math.min(1.0, f));
    }

    private String formatValue(double v) {
        if (step >= 1.0 && Math.floor(step) == step && decimals == 2) {
            return Integer.toString((int) Math.round(v));
        }
        return String.format("%." + decimals + "f", v);
    }

    private void updateFromMouse(double mx) {
        double frac = (mx - x) / (double) Math.max(1, w);
        frac = Math.max(0.0, Math.min(1.0, frac));
        double v = min + frac * (max - min);
        if (step > 0.0) {
            v = min + Math.round((v - min) / step) * step;
        }
        v = Math.max(min, Math.min(max, v));
        setter.accept(v);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        if (!isHovered(mx, my)) return false;
        dragging = true;
        updateFromMouse(mx);
        return true;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0) return false;
        boolean wasDragging = dragging;
        dragging = false;
        return wasDragging;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging || button != 0) return false;
        updateFromMouse(mx);
        return true;
    }
}
