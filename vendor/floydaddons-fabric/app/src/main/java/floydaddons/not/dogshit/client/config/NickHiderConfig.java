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

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class NickHiderConfig {

    private static String nickname = "George Floyd";
    private static boolean enabled = false;

    // Per-player name mappings (managed by FloydAddonsConfig)
    private static Map<String, String> nameMappings = Collections.emptyMap();

    private NickHiderConfig() {}

    public static String getNickname() { return nickname; }
    public static void setNickname(String nick) { if (nick != null && !nick.isEmpty()) nickname = nick; }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    public static Map<String, String> getNameMappings() { return nameMappings; }

    /** Called by FloydAddonsConfig when loading name mappings from disk. */
    public static void setNameMappingsRaw(Map<String, String> raw) {
        nameMappings = Map.copyOf(raw);
    }

    public static Path getNamesConfigPath() {
        return FloydAddonsConfig.getNamesPath();
    }

    /** Convenience: delegates to the unified config. */
    public static void save() { FloydAddonsConfig.save(); }
    public static void loadNameMappings() { FloydAddonsConfig.loadNameMappings(); }

    public static void addNameMapping(String ign, String fakeName) {
        Map<String, String> mutable = new HashMap<>(nameMappings);
        mutable.put(ign, fakeName);
        nameMappings = Map.copyOf(mutable);
    }

    public static boolean removeNameMapping(String ign) {
        String keyToRemove = null;
        for (String key : nameMappings.keySet()) {
            if (key.equalsIgnoreCase(ign)) {
                keyToRemove = key;
                break;
            }
        }
        if (keyToRemove == null) return false;
        Map<String, String> mutable = new HashMap<>(nameMappings);
        mutable.remove(keyToRemove);
        nameMappings = Map.copyOf(mutable);
        return true;
    }

    public static void clearNameMappings() {
        nameMappings = Collections.emptyMap();
    }
}
