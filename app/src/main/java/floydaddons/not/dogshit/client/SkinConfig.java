package floydaddons.not.dogshit.client;

public final class SkinConfig {
    private static boolean selfEnabled = false;
    private static boolean othersEnabled = false;
    private static String selectedSkin = "george-floyd.png";

    private SkinConfig() {}

    public static boolean selfEnabled() { return selfEnabled; }
    public static boolean othersEnabled() { return othersEnabled; }
    public static void setSelfEnabled(boolean v) { selfEnabled = v; }
    public static void setOthersEnabled(boolean v) { othersEnabled = v; }

    public static String getSelectedSkin() { return selectedSkin; }
    public static void setSelectedSkin(String name) { selectedSkin = name != null ? name : ""; }

    /** Convenience: delegates to the unified config. */
    public static void save() { FloydAddonsConfig.save(); }
}
