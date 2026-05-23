package floydaddons.not.dogshit.client.esp;
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
 * Manages the stalk target (runtime state, not persisted).
 */
public final class StalkManager {
    private static String targetName = "";
    private static boolean enabled = false;

    private StalkManager() {}

    public static boolean isEnabled() { return enabled; }
    public static String getTargetName() { return targetName; }

    public static void setTarget(String name) {
        if (name == null || name.isBlank()) {
            targetName = "";
            enabled = false;
        } else {
            targetName = name;
            enabled = true;
        }
    }

    public static void disable() {
        enabled = false;
        targetName = "";
    }
}
