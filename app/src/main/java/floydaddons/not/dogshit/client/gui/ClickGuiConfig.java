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

import java.util.EnumMap;
import java.util.Map;

/**
 * Stores ClickGUI panel positions and state so they persist between sessions.
 */
public final class ClickGuiConfig {
    private static final Map<ModuleCategory, PanelState> panelStates = new EnumMap<>(ModuleCategory.class);

    private ClickGuiConfig() {}

    public static PanelState getState(ModuleCategory category) {
        return panelStates.computeIfAbsent(category, k -> defaultState(k));
    }

    public static void setState(ModuleCategory category, int x, int y, boolean collapsed) {
        PanelState state = getState(category);
        state.x = x;
        state.y = y;
        state.collapsed = collapsed;
    }

    /** Loads panel positions from the Data object (called by FloydAddonsConfig). */
    public static void loadFromData(Map<String, int[]> data) {
        if (data == null) return;
        for (ModuleCategory cat : ModuleCategory.values()) {
            int[] pos = data.get(cat.name());
            if (pos != null && pos.length >= 3) {
                PanelState state = getState(cat);
                state.x = pos[0];
                state.y = pos[1];
                state.collapsed = pos[2] != 0;
            }
        }
    }

    /** Serializes panel positions for saving. */
    public static Map<String, int[]> toData() {
        Map<String, int[]> data = new java.util.LinkedHashMap<>();
        for (ModuleCategory cat : ModuleCategory.values()) {
            PanelState state = getState(cat);
            data.put(cat.name(), new int[]{state.x, state.y, state.collapsed ? 1 : 0});
        }
        return data;
    }

    private static PanelState defaultState(ModuleCategory category) {
        PanelState state = new PanelState();
        int ordinal = category.ordinal();
        state.x = 10 + ordinal * 210;
        state.y = 30;
        state.collapsed = false;
        return state;
    }

    public static class PanelState {
        public int x;
        public int y;
        public boolean collapsed;
        public float scrollOffset;
    }
}
