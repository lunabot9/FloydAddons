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

/**
 * Config backing the Hiders module — visual toggle features.
 * All fields default to false (off). Persistence goes through FloydAddonsConfig.
 */
public final class HidersConfig {
    private static boolean noHurtCamera;
    private static boolean removeFireOverlay;
    private static boolean disableHungerBar;
    private static boolean hidePotionEffects;
    private static boolean thirdPersonCrosshair;
    private static boolean hideEntityFire;
    private static boolean disableAttachedArrows;
    private static boolean removeFallingBlocks;
    private static boolean removeExplosionParticles;
    private static boolean removeTabPing;
    private static String noArmorMode = "OFF";

    private HidersConfig() {}

    public static boolean isNoHurtCameraEnabled() { return noHurtCamera; }
    public static void setNoHurtCameraEnabled(boolean v) { noHurtCamera = v; }

    public static boolean isRemoveFireOverlayEnabled() { return removeFireOverlay; }
    public static void setRemoveFireOverlayEnabled(boolean v) { removeFireOverlay = v; }

    public static boolean isDisableHungerBarEnabled() { return disableHungerBar; }
    public static void setDisableHungerBarEnabled(boolean v) { disableHungerBar = v; }

    public static boolean isHidePotionEffectsEnabled() { return hidePotionEffects; }
    public static void setHidePotionEffectsEnabled(boolean v) { hidePotionEffects = v; }

    public static boolean isThirdPersonCrosshairEnabled() { return thirdPersonCrosshair; }
    public static void setThirdPersonCrosshairEnabled(boolean v) { thirdPersonCrosshair = v; }

    public static boolean isHideEntityFireEnabled() { return hideEntityFire; }
    public static void setHideEntityFireEnabled(boolean v) { hideEntityFire = v; }

    public static boolean isDisableAttachedArrowsEnabled() { return disableAttachedArrows; }
    public static void setDisableAttachedArrowsEnabled(boolean v) { disableAttachedArrows = v; }

    public static boolean isRemoveFallingBlocksEnabled() { return removeFallingBlocks; }
    public static void setRemoveFallingBlocksEnabled(boolean v) { removeFallingBlocks = v; }

    public static boolean isRemoveExplosionParticlesEnabled() { return removeExplosionParticles; }
    public static void setRemoveExplosionParticlesEnabled(boolean v) { removeExplosionParticles = v; }

    public static boolean isRemoveTabPingEnabled() { return removeTabPing; }
    public static void setRemoveTabPingEnabled(boolean v) { removeTabPing = v; }

    public static String getNoArmorMode() { return noArmorMode; }
    public static void setNoArmorMode(String v) { noArmorMode = v; }
    public static boolean isNoArmorEnabled() { return !"OFF".equals(noArmorMode); }

    /** Convenience: delegates to the unified config. */
    public static void save() { FloydAddonsConfig.save(); }
}
