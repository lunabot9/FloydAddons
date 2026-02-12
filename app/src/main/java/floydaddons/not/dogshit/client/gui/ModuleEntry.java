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

import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Represents one toggleable module in the ClickGUI.
 * Handles left-click (toggle) and right-click (expand settings).
 */
public class ModuleEntry {
    private final String name;
    private final String description;
    private final BooleanSupplier enabledSupplier;
    private final Runnable toggleAction;
    private final List<SubSetting> settings;

    public ModuleEntry(String name, String description, BooleanSupplier enabledSupplier, Runnable toggleAction) {
        this(name, description, enabledSupplier, toggleAction, Collections.emptyList());
    }

    public ModuleEntry(String name, String description, BooleanSupplier enabledSupplier, Runnable toggleAction, List<SubSetting> settings) {
        this.name = name;
        this.description = description;
        this.enabledSupplier = enabledSupplier;
        this.toggleAction = toggleAction;
        this.settings = settings;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return enabledSupplier.getAsBoolean(); }
    public void toggle() { toggleAction.run(); }
    public List<SubSetting> getSettings() { return settings; }
    public boolean hasSettings() { return !settings.isEmpty(); }

    /**
     * Base class for sub-settings displayed when a module is expanded.
     */
    public static abstract class SubSetting {
        private final String label;

        protected SubSetting(String label) {
            this.label = label;
        }

        public String getLabel() { return label; }
    }

    /**
     * A boolean toggle sub-setting.
     */
    public static class BooleanSetting extends SubSetting {
        private final BooleanSupplier getter;
        private final Runnable toggle;

        public BooleanSetting(String label, BooleanSupplier getter, Runnable toggle) {
            super(label);
            this.getter = getter;
            this.toggle = toggle;
        }

        public boolean isEnabled() { return getter.getAsBoolean(); }
        public void toggle() { toggle.run(); }
    }

    /**
     * A slider sub-setting for float values.
     */
    public static class SliderSetting extends SubSetting {
        private final java.util.function.Supplier<Float> getter;
        private final java.util.function.Consumer<Float> setter;
        private final float min;
        private final float max;
        private final String format;

        public SliderSetting(String label, java.util.function.Supplier<Float> getter,
                             java.util.function.Consumer<Float> setter, float min, float max, String format) {
            super(label);
            this.getter = getter;
            this.setter = setter;
            this.min = min;
            this.max = max;
            this.format = format;
        }

        public float getValue() { return getter.get(); }
        public void setValue(float v) { setter.accept(Math.max(min, Math.min(max, v))); }
        public float getMin() { return min; }
        public float getMax() { return max; }
        public float getNormalized() { return (getValue() - min) / (max - min); }
        public void setNormalized(float t) { setValue(min + t * (max - min)); }
        public String getFormattedValue() { return String.format(format, getValue()); }
    }

    /**
     * A button sub-setting that opens a screen or runs an action.
     */
    public static class ButtonSetting extends SubSetting {
        private final Runnable action;

        public ButtonSetting(String label, Runnable action) {
            super(label);
            this.action = action;
        }

        public void click() { action.run(); }
    }

    /**
     * A cycle sub-setting that cycles through a list of string options on click.
     */
    public static class CycleSetting extends SubSetting {
        private final java.util.function.Supplier<java.util.List<String>> optionsSupplier;
        private final java.util.function.Supplier<String> getter;
        private final java.util.function.Consumer<String> setter;

        public CycleSetting(String label,
                            java.util.function.Supplier<java.util.List<String>> optionsSupplier,
                            java.util.function.Supplier<String> getter,
                            java.util.function.Consumer<String> setter) {
            super(label);
            this.optionsSupplier = optionsSupplier;
            this.getter = getter;
            this.setter = setter;
        }

        public java.util.List<String> getOptions() { return optionsSupplier.get(); }
        public String getSelected() {
            String sel = getter.get();
            return sel != null ? sel : "";
        }

        public void cycleForward() {
            java.util.List<String> opts = getOptions();
            if (opts.isEmpty()) return;
            int idx = opts.indexOf(getSelected());
            setter.accept(opts.get((idx + 1) % opts.size()));
        }
    }

    /**
     * A color picker sub-setting that shows a clickable color preview square.
     * Clicking opens ColorPickerScreen (which includes chroma toggle).
     */
    public static class ColorSetting extends SubSetting {
        private final java.util.function.Supplier<Integer> colorGetter;
        private final java.util.function.Consumer<Integer> colorSetter;
        private final java.util.function.BooleanSupplier chromaGetter;
        private final java.util.function.Consumer<Boolean> chromaSetter;

        public ColorSetting(String label,
                            java.util.function.Supplier<Integer> colorGetter,
                            java.util.function.Consumer<Integer> colorSetter,
                            java.util.function.BooleanSupplier chromaGetter,
                            java.util.function.Consumer<Boolean> chromaSetter) {
            super(label);
            this.colorGetter = colorGetter;
            this.colorSetter = colorSetter;
            this.chromaGetter = chromaGetter;
            this.chromaSetter = chromaSetter;
        }

        public int getColor() { return colorGetter.get(); }
        public void setColor(int color) { colorSetter.accept(color); }
        public boolean isChroma() { return chromaGetter.getAsBoolean(); }
        public void setChroma(boolean v) { chromaSetter.accept(v); }

        /** Returns the live display color (animated chroma or static). */
        public int getDisplayColor() {
            if (isChroma()) {
                float hue = (float) ((System.currentTimeMillis() % 4000) / 4000.0);
                return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f) & 0xFFFFFF);
            }
            return getColor();
        }
    }

    /**
     * A text input sub-setting for editable string values.
     */
    public static class TextSetting extends SubSetting {
        private final java.util.function.Supplier<String> getter;
        private final java.util.function.Consumer<String> setter;

        public TextSetting(String label,
                           java.util.function.Supplier<String> getter,
                           java.util.function.Consumer<String> setter) {
            super(label);
            this.getter = getter;
            this.setter = setter;
        }

        public String getValue() { return getter.get(); }
        public void setValue(String value) { setter.accept(value); }
    }

    /**
     * An inline expandable block filter list (for X-Ray blocks).
     */
    public static class BlockFilterSetting extends SubSetting {
        public BlockFilterSetting(String label) { super(label); }
    }

    /**
     * An inline expandable mob filter list (for Mob ESP filters).
     */
    public static class MobFilterSetting extends SubSetting {
        public MobFilterSetting(String label) { super(label); }
    }
}
