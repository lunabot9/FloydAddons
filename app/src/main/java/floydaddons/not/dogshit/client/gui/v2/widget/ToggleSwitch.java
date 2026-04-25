package floydaddons.not.dogshit.client.gui.v2.widget;

import net.minecraft.client.gui.DrawContext;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Pill-track on/off toggle. The track changes color, the knob slides between left/right
 * with a short tween. Matches the Hiders.png style — no green/blue accent, all grayscale.
 */
public class ToggleSwitch {
    private static final float TWEEN_PER_SEC = 12f; // ~83ms full transition

    private int x, y, trackW, trackH;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;

    /** Animated knob position 0 (off) to 1 (on). */
    private float knobPos;

    public ToggleSwitch(int x, int y, int trackW, int trackH,
                        BooleanSupplier getter, Consumer<Boolean> setter) {
        this.x = x;
        this.y = y;
        this.trackW = trackW;
        this.trackH = trackH;
        this.getter = getter;
        this.setter = setter;
        this.knobPos = getter.getAsBoolean() ? 1f : 0f;
    }

    public ToggleSwitch(int x, int y, BooleanSupplier getter, Consumer<Boolean> setter) {
        this(x, y, V2Theme.TOGGLE_TRACK_W, V2Theme.TOGGLE_TRACK_H, getter, setter);
    }

    public void setPos(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return trackW; }
    public int getHeight() { return trackH; }

    public boolean isOn() {
        return getter.getAsBoolean();
    }

    public boolean isHovered(double mx, double my) {
        return mx >= x && mx < x + trackW && my >= y && my < y + trackH;
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean on = isOn();
        float target = on ? 1f : 0f;
        float step = TWEEN_PER_SEC * delta / 20f; // delta is in ticks (~1/20s)
        if (knobPos < target) knobPos = Math.min(target, knobPos + step);
        else if (knobPos > target) knobPos = Math.max(target, knobPos - step);

        int trackColor = V2Theme.lerpColor(V2Theme.TOGGLE_OFF_TRACK, V2Theme.TOGGLE_ON_TRACK, knobPos);
        int radius = trackH / 2;
        V2Theme.fillRoundedRect(ctx, x, y, trackW, trackH, radius, trackColor);

        int knobDiameter = trackH - 2;
        int travel = trackW - knobDiameter - 2;
        int knobX = x + 1 + Math.round(travel * knobPos);
        int knobY = y + 1;
        V2Theme.fillRoundedRect(ctx, knobX, knobY, knobDiameter, knobDiameter, knobDiameter / 2,
                V2Theme.TOGGLE_KNOB);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        if (!isHovered(mx, my)) return false;
        boolean newVal = !isOn();
        setter.accept(newVal);
        return true;
    }
}
