package floydaddons.not.dogshit.client;

public final class SkinConfig {
    private static boolean customEnabled = true;
    private static boolean selfEnabled = true;
    private static boolean othersEnabled = false;
    private static String selectedSkin = "george-floyd.png";
    private static float playerScaleX = 1.0f;
    private static float playerScaleY = 1.0f;
    private static float playerScaleZ = 1.0f;

    private SkinConfig() {}

    public static boolean customEnabled() { return customEnabled; }
    public static boolean selfEnabled() { return selfEnabled; }
    public static boolean othersEnabled() { return othersEnabled; }
    public static void setCustomEnabled(boolean v) { customEnabled = v; }
    public static void setSelfEnabled(boolean v) { selfEnabled = v; }
    public static void setOthersEnabled(boolean v) { othersEnabled = v; }

    public static String getSelectedSkin() { return selectedSkin; }
    public static void setSelectedSkin(String name) { selectedSkin = name != null ? name : ""; }

    public static float getPlayerScaleX() { return playerScaleX; }
    public static void setPlayerScaleX(float v) { playerScaleX = Math.max(-1.0f, Math.min(5.0f, v)); }

    public static float getPlayerScaleY() { return playerScaleY; }
    public static void setPlayerScaleY(float v) { playerScaleY = Math.max(-1.0f, Math.min(5.0f, v)); }

    public static float getPlayerScaleZ() { return playerScaleZ; }
    public static void setPlayerScaleZ(float v) { playerScaleZ = Math.max(-1.0f, Math.min(5.0f, v)); }

    /** Backward compat: set all axes uniformly. */
    public static void setPlayerScale(float v) {
        v = Math.max(-1.0f, Math.min(5.0f, v));
        playerScaleX = v; playerScaleY = v; playerScaleZ = v;
    }

    /** Backward compat: returns X axis (representative for uniform scale). */
    public static float getPlayerScale() { return playerScaleX; }

    /** Convenience: delegates to the unified config. */
    public static void save() { FloydAddonsConfig.save(); }
}
