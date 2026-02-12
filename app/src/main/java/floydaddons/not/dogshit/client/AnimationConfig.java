package floydaddons.not.dogshit.client;

/**
 * Config for custom held-item animations (like Dulkir/Odin).
 * All fields persist through FloydAddonsConfig.
 */
public final class AnimationConfig {
    private static boolean enabled;
    private static int posX;
    private static int posY;
    private static int posZ;
    private static int rotX;
    private static int rotY;
    private static int rotZ;
    private static float scale = 1.0f;
    private static int swingDuration = 6;
    private static boolean cancelReEquip;
    private static boolean hidePlayerHand;
    private static boolean classicClick;

    private AnimationConfig() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; }

    public static int getPosX() { return posX; }
    public static void setPosX(int v) { posX = Math.max(-150, Math.min(150, v)); }

    public static int getPosY() { return posY; }
    public static void setPosY(int v) { posY = Math.max(-150, Math.min(150, v)); }

    public static int getPosZ() { return posZ; }
    public static void setPosZ(int v) { posZ = Math.max(-150, Math.min(50, v)); }

    public static int getRotX() { return rotX; }
    public static void setRotX(int v) { rotX = Math.max(-180, Math.min(180, v)); }

    public static int getRotY() { return rotY; }
    public static void setRotY(int v) { rotY = Math.max(-180, Math.min(180, v)); }

    public static int getRotZ() { return rotZ; }
    public static void setRotZ(int v) { rotZ = Math.max(-180, Math.min(180, v)); }

    public static float getScale() { return scale; }
    public static void setScale(float v) { scale = Math.max(0.1f, Math.min(2.0f, v)); }

    public static int getSwingDuration() { return swingDuration; }
    public static void setSwingDuration(int v) { swingDuration = Math.max(1, Math.min(100, v)); }

    public static boolean isCancelReEquip() { return cancelReEquip; }
    public static void setCancelReEquip(boolean v) { cancelReEquip = v; }

    public static boolean isHidePlayerHand() { return hidePlayerHand; }
    public static void setHidePlayerHand(boolean v) { hidePlayerHand = v; }

    public static boolean isClassicClick() { return classicClick; }
    public static void setClassicClick(boolean v) { classicClick = v; }

    /** Convenience: delegates to the unified config. */
    public static void save() { FloydAddonsConfig.save(); }
}
