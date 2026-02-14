package floydaddons.not.dogshit.client.config;
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

import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Config backing the Render screen (inventory HUD).
 */
public final class RenderConfig {
    private static boolean inventoryHudEnabled = false;
    private static int inventoryHudX = 12;
    private static int inventoryHudY = 12;
    private static float inventoryHudScale = 1.1f;
    private static boolean floydHatEnabled = false;
    private static boolean capeEnabled = false;
    private static float coneHatHeight = 0.45f;
    private static float coneHatRadius = 0.25f;
    private static float coneHatYOffset = -0.5f;
    private static float coneHatRotation = 0.0f;
    private static float coneHatRotationSpeed = 0.0f;
    private static boolean buttonTextChromaEnabled = false;
    private static boolean buttonBorderChromaEnabled = false;
    private static boolean guiBorderChromaEnabled = false;
    private static boolean buttonTextFadeEnabled = false;
    private static boolean buttonBorderFadeEnabled = false;
    private static boolean guiBorderFadeEnabled = false;
    private static boolean customTimeEnabled = false;
    private static float customTimeValue = 50f; // 0-100 slider
    private static int guiBorderColor = 0xFFFFFFFF;
    private static int buttonBorderColor = 0xFFFFFFFF;
    private static int buttonTextColor = 0xFFFFFFFF;
    private static int guiBorderFadeColor = 0xFFFFFFFF;
    private static int buttonBorderFadeColor = 0xFFFFFFFF;
    private static int buttonTextFadeColor = 0xFFFFFFFF;
    private static String selectedConeImage = "";
    private static String selectedCapeImage = "";
    private static boolean customScoreboardEnabled = false;
    private static int customScoreboardX = -1;
    private static int customScoreboardY = -1;
    private static boolean serverIdHiderEnabled = false;
    private static boolean profileIdHiderEnabled = true;
    private static volatile boolean xrayEnabled = false;
    private static float xrayOpacity = 0.3f;
    private static volatile Set<String> xrayOpaqueBlocks = defaultXrayOpaqueBlocks();
    private static volatile boolean mobEspEnabled = false;
    private static boolean mobEspTracers = false;
    private static boolean mobEspHitboxes = false;
    private static boolean mobEspStarMobs = false;
    private static int defaultEspColor = 0xFFFFFFFF;
    private static boolean defaultEspChromaEnabled = false;
    private static int stalkTracerColor = 0xFFFFFFFF;
    private static boolean stalkTracerChromaEnabled = false;
    private static int hudCornerRadius = 0;

    private RenderConfig() {}

    public static int getHudCornerRadius() { return hudCornerRadius; }
    public static void setHudCornerRadius(int r) { hudCornerRadius = Math.max(0, Math.min(12, r)); }

    public static boolean isInventoryHudEnabled() { return inventoryHudEnabled; }
    public static void setInventoryHudEnabled(boolean enabled) { inventoryHudEnabled = enabled; }

    public static int getInventoryHudX() { return inventoryHudX; }
    public static int getInventoryHudY() { return inventoryHudY; }
    public static void setInventoryHudX(int x) { inventoryHudX = x; }
    public static void setInventoryHudY(int y) { inventoryHudY = y; }
    public static float getInventoryHudScale() { return inventoryHudScale; }
    public static void setInventoryHudScale(float scale) { inventoryHudScale = Math.max(0.5f, Math.min(2.0f, scale)); }

    public static boolean isFloydHatEnabled() { return floydHatEnabled; }
    public static void setFloydHatEnabled(boolean v) { floydHatEnabled = v; }
    public static boolean isCapeEnabled() { return capeEnabled; }
    public static void setCapeEnabled(boolean v) { capeEnabled = v; }

    public static float getConeHatHeight() { return coneHatHeight; }
    public static void setConeHatHeight(float v) { coneHatHeight = Math.max(0.1f, Math.min(1.5f, v)); }

    public static float getConeHatRadius() { return coneHatRadius; }
    public static void setConeHatRadius(float v) { coneHatRadius = Math.max(0.05f, Math.min(0.8f, v)); }

    public static float getConeHatYOffset() { return coneHatYOffset; }
    public static void setConeHatYOffset(float v) { coneHatYOffset = Math.max(-1.5f, Math.min(0.5f, v)); }

    public static float getConeHatRotation() { return coneHatRotation; }
    public static void setConeHatRotation(float v) { coneHatRotation = ((v % 360f) + 360f) % 360f; }

    public static float getConeHatRotationSpeed() { return coneHatRotationSpeed; }
    public static void setConeHatRotationSpeed(float v) { coneHatRotationSpeed = Math.max(0f, Math.min(360f, v)); }

    public static boolean isButtonTextChromaEnabled() { return buttonTextChromaEnabled; }
    public static void setButtonTextChromaEnabled(boolean v) {
        buttonTextChromaEnabled = v;
        if (v) buttonTextFadeEnabled = false;
    }

    public static boolean isButtonBorderChromaEnabled() { return buttonBorderChromaEnabled; }
    public static void setButtonBorderChromaEnabled(boolean v) {
        buttonBorderChromaEnabled = v;
        if (v) buttonBorderFadeEnabled = false;
    }

    public static boolean isGuiBorderChromaEnabled() { return guiBorderChromaEnabled; }
    public static void setGuiBorderChromaEnabled(boolean v) {
        guiBorderChromaEnabled = v;
        if (v) guiBorderFadeEnabled = false;
    }

    public static boolean isButtonTextFadeEnabled() { return buttonTextFadeEnabled; }
    public static void setButtonTextFadeEnabled(boolean v) {
        buttonTextFadeEnabled = v;
        if (v) buttonTextChromaEnabled = false;
    }

    public static boolean isButtonBorderFadeEnabled() { return buttonBorderFadeEnabled; }
    public static void setButtonBorderFadeEnabled(boolean v) {
        buttonBorderFadeEnabled = v;
        if (v) buttonBorderChromaEnabled = false;
    }

    public static boolean isGuiBorderFadeEnabled() { return guiBorderFadeEnabled; }
    public static void setGuiBorderFadeEnabled(boolean v) {
        guiBorderFadeEnabled = v;
        if (v) guiBorderChromaEnabled = false;
    }

    public static int getGuiBorderColor() { return ensureOpaque(guiBorderColor); }
    public static void setGuiBorderColor(int color) { guiBorderColor = ensureOpaque(color); }

    public static int getButtonBorderColor() { return ensureOpaque(buttonBorderColor); }
    public static void setButtonBorderColor(int color) { buttonBorderColor = ensureOpaque(color); }

    public static int getButtonTextColor() { return ensureOpaque(buttonTextColor); }
    public static void setButtonTextColor(int color) { buttonTextColor = ensureOpaque(color); }

    public static int getGuiBorderFadeColor() { return ensureOpaque(guiBorderFadeColor); }
    public static void setGuiBorderFadeColor(int color) { guiBorderFadeColor = ensureOpaque(color); }

    public static int getButtonBorderFadeColor() { return ensureOpaque(buttonBorderFadeColor); }
    public static void setButtonBorderFadeColor(int color) { buttonBorderFadeColor = ensureOpaque(color); }

    public static int getButtonTextFadeColor() { return ensureOpaque(buttonTextFadeColor); }
    public static void setButtonTextFadeColor(int color) { buttonTextFadeColor = ensureOpaque(color); }

    public static boolean isCustomTimeEnabled() { return customTimeEnabled; }
    public static void setCustomTimeEnabled(boolean v) { customTimeEnabled = v; }
    public static float getCustomTimeValue() { return customTimeValue; }
    public static void setCustomTimeValue(float v) { customTimeValue = Math.max(0f, Math.min(100f, v)); }
    public static long getCustomTimeTicks() {
        // Map 0-100 -> 0-23999 (vanilla day)
        return (long) Math.round((customTimeValue / 100f) * 23999L);
    }

    /** Applies the custom time to the given properties via reflection (handles mapping differences). */
    public static void applyCustomTime(net.minecraft.world.MutableWorldProperties props) {
        if (props == null) return;
        long t = getCustomTimeTicks();
        try {
            if (props instanceof floydaddons.not.dogshit.mixin.ClientWorldPropertiesAccessor acc) {
                acc.floydaddons$setTimeOfDay(t);
                acc.floydaddons$setTime(t);
                return;
            }
        } catch (Exception ignored) {}
        try {
            var m = props.getClass().getMethod("setTimeOfDay", long.class);
            m.invoke(props, t);
            return;
        } catch (Exception ignored) {}
        try {
            var m = props.getClass().getMethod("setTime", long.class);
            m.invoke(props, t);
        } catch (Exception ignored) {}
    }

    public static String getSelectedConeImage() { return selectedConeImage; }
    public static void setSelectedConeImage(String v) { selectedConeImage = v != null ? v : ""; }
    public static String getSelectedCapeImage() { return selectedCapeImage; }
    public static void setSelectedCapeImage(String v) { selectedCapeImage = v != null ? v : ""; }

    public static boolean isCustomScoreboardEnabled() { return customScoreboardEnabled; }
    public static void setCustomScoreboardEnabled(boolean v) { customScoreboardEnabled = v; }

    public static int getCustomScoreboardX() { return customScoreboardX; }
    public static void setCustomScoreboardX(int x) { customScoreboardX = x; }

    public static int getCustomScoreboardY() { return customScoreboardY; }
    public static void setCustomScoreboardY(int y) { customScoreboardY = y; }

    public static boolean isServerIdHiderEnabled() { return serverIdHiderEnabled; }
    public static void setServerIdHiderEnabled(boolean v) { serverIdHiderEnabled = v; }
    public static boolean isProfileIdHiderEnabled() { return profileIdHiderEnabled; }
    public static void setProfileIdHiderEnabled(boolean v) { profileIdHiderEnabled = v; }


    public static boolean isXrayEnabled() { return xrayEnabled; }
    public static float getXrayOpacity() { return xrayOpacity; }
    public static void setXrayOpacity(float v) { xrayOpacity = Math.max(0.05f, Math.min(1.0f, v)); }

    public static void toggleXray() {
        xrayEnabled = !xrayEnabled;
        rebuildChunks();
    }

    public static boolean isMobEspEnabled() { return mobEspEnabled; }
    public static void setMobEspEnabled(boolean v) { mobEspEnabled = v; }
    public static void toggleMobEsp() { mobEspEnabled = !mobEspEnabled; }

    public static boolean isMobEspTracers() { return mobEspTracers; }
    public static void setMobEspTracers(boolean v) { mobEspTracers = v; }

    public static boolean isMobEspHitboxes() { return mobEspHitboxes; }
    public static void setMobEspHitboxes(boolean v) { mobEspHitboxes = v; }

    public static boolean isMobEspStarMobs() { return mobEspStarMobs; }
    public static void setMobEspStarMobs(boolean v) { mobEspStarMobs = v; }

    public static int getDefaultEspColor() { return ensureOpaque(defaultEspColor); }
    public static void setDefaultEspColor(int color) { defaultEspColor = ensureOpaque(color); }

    public static boolean isDefaultEspChromaEnabled() { return defaultEspChromaEnabled; }
    public static void setDefaultEspChromaEnabled(boolean v) { defaultEspChromaEnabled = v; }

    public static int getStalkTracerColor() { return ensureOpaque(stalkTracerColor); }
    public static void setStalkTracerColor(int color) { stalkTracerColor = ensureOpaque(color); }

    public static boolean isStalkTracerChromaEnabled() { return stalkTracerChromaEnabled; }
    public static void setStalkTracerChromaEnabled(boolean v) { stalkTracerChromaEnabled = v; }

    /** Forces a full chunk rebuild for both vanilla and Sodium renderers. */
    public static void rebuildChunks() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.worldRenderer != null) {
            client.worldRenderer.reload();
            try {
                Class<?> swr = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
                Object instance = swr.getMethod("instance").invoke(null);
                swr.getMethod("scheduleRebuildForAllChunks", boolean.class).invoke(instance, true);
            } catch (Exception ignored) {
            }
        }
    }

    public static Set<String> getXrayOpaqueBlocks() { return xrayOpaqueBlocks; }
    public static void setXrayOpaqueBlocks(Set<String> blocks) { xrayOpaqueBlocks = blocks; }

    public static boolean addXrayOpaqueBlock(String id) {
        Set<String> mutable = new LinkedHashSet<>(xrayOpaqueBlocks);
        boolean added = mutable.add(id);
        if (added) {
            xrayOpaqueBlocks = Collections.unmodifiableSet(mutable);
            rebuildChunks();
        }
        return added;
    }

    public static boolean removeXrayOpaqueBlock(String id) {
        Set<String> mutable = new LinkedHashSet<>(xrayOpaqueBlocks);
        boolean removed = mutable.remove(id);
        if (removed) {
            xrayOpaqueBlocks = Collections.unmodifiableSet(mutable);
            rebuildChunks();
        }
        return removed;
    }

    /** Check if a block should retain its original opacity during xray. */
    public static boolean isXrayOpaque(BlockState state) {
        String id = Registries.BLOCK.getId(state.getBlock()).toString();
        return xrayOpaqueBlocks.contains(id);
    }

    public static Set<String> defaultXrayOpaqueBlocks() {
        Set<String> set = new LinkedHashSet<>();
        set.add("minecraft:glass");
        set.add("minecraft:tinted_glass");
        set.add("minecraft:glass_pane");
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime",
                "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green",
                "red", "black"};
        for (String c : colors) {
            set.add("minecraft:" + c + "_stained_glass");
            set.add("minecraft:" + c + "_stained_glass_pane");
        }
        return Collections.unmodifiableSet(set);
    }

    /** Convenience: delegates to the unified config. */
    public static void save() { FloydAddonsConfig.save(); }

    /** Shared chroma utility for UI elements. */
    public static int chromaColor(float offset) {
        double time = (System.currentTimeMillis() % 4000) / 4000.0;
        float hue = (float) ((time + offset) % 1.0);
        int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** Returns the live (animated) button text color with optional chroma/fade. */
    public static int getButtonTextLiveColor(float chromaOffset) {
        return animatedColor(getButtonTextColor(), getButtonTextFadeColor(),
                buttonTextFadeEnabled, buttonTextChromaEnabled, chromaOffset);
    }

    /** Returns the live (animated) button border color with optional chroma/fade. */
    public static int getButtonBorderLiveColor(float chromaOffset) {
        return animatedColor(getButtonBorderColor(), getButtonBorderFadeColor(),
                buttonBorderFadeEnabled, buttonBorderChromaEnabled, chromaOffset);
    }

    /** Returns the live (animated) GUI border color with optional chroma/fade. */
    public static int getGuiBorderLiveColor(float chromaOffset) {
        return animatedColor(getGuiBorderColor(), getGuiBorderFadeColor(),
                guiBorderFadeEnabled, guiBorderChromaEnabled, chromaOffset);
    }

    private static int animatedColor(int base, int fade, boolean fadeEnabled, boolean chromaEnabled, float chromaOffset) {
        if (chromaEnabled) return chromaColor(chromaOffset);
        if (fadeEnabled) return lerpColor(base, fade, fadeProgress());
        return base;
    }

    private static float fadeProgress() {
        // Slow 8s ping-pong cycle to mirror chroma pacing but smoother
        double time = (System.currentTimeMillis() % 8000) / 8000.0;
        return (float) (0.5 - 0.5 * Math.cos(time * Math.PI * 2));
    }

    /** Linear interpolation between two ARGB colors (alpha preserved). */
    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int rr = Math.round(ar + (br - ar) * t);
        int rg = Math.round(ag + (bg - ag) * t);
        int rb = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
    }

    private static int ensureOpaque(int color) {
        return color | 0xFF000000;
    }
}
