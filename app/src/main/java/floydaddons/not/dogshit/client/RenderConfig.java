package floydaddons.not.dogshit.client;

/**
 * Config backing the Render screen (inventory HUD).
 */
public final class RenderConfig {
    private static boolean inventoryHudEnabled = false;
    private static int inventoryHudX = 12;
    private static int inventoryHudY = 12;
    private static float inventoryHudScale = 1.1f;
    private static boolean floydHatEnabled = false;

    private RenderConfig() {}

    public static boolean isInventoryHudEnabled() { return inventoryHudEnabled; }
    public static void setInventoryHudEnabled(boolean enabled) { inventoryHudEnabled = enabled; }

    public static int getInventoryHudX() { return inventoryHudX; }
    public static int getInventoryHudY() { return inventoryHudY; }
    public static void setInventoryHudX(int x) { inventoryHudX = x; }
    public static void setInventoryHudY(int y) { inventoryHudY = y; }
    public static float getInventoryHudScale() { return inventoryHudScale; }
    public static void setInventoryHudScale(float scale) { inventoryHudScale = 1.1f; }

    public static boolean isFloydHatEnabled() { return floydHatEnabled; }
    public static void setFloydHatEnabled(boolean v) { floydHatEnabled = v; }

    /** Convenience: delegates to the unified config. */
    public static void save() { FloydAddonsConfig.save(); }
}
