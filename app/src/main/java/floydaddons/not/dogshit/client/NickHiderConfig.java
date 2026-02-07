package floydaddons.not.dogshit.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NickHiderConfig {

    public enum HideOthersMode {
        OFF("Off"),
        CONFIG_ONLY("Config File Only"),
        CONFIG_AND_DEFAULT("File + Default");

        private final String label;
        HideOthersMode(String label) { this.label = label; }
        public String getLabel() { return label; }

        public HideOthersMode next() {
            HideOthersMode[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    private static String nickname = "George Floyd";
    private static boolean enabled = false;
    private static HideOthersMode hideOthersMode = HideOthersMode.OFF;
    private static String othersNickname = "George Floyd";

    // Per-player name mappings (managed by FloydAddonsConfig)
    private static Map<String, String> nameMappings = Collections.emptyMap();
    private static Map<String, String> nameMappingsLower = Collections.emptyMap();

    // Cached set of other player names, refreshed every second
    private static Set<String> cachedOtherNames = Collections.emptySet();
    private static long lastNameCacheUpdate = 0;

    private NickHiderConfig() {}

    public static String getNickname() { return nickname; }
    public static void setNickname(String nick) { if (nick != null && !nick.isEmpty()) nickname = nick; }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    public static HideOthersMode getHideOthersMode() { return hideOthersMode; }
    public static void setHideOthersMode(HideOthersMode mode) { hideOthersMode = mode; }

    public static boolean isHideOthers() { return hideOthersMode != HideOthersMode.OFF; }

    public static String getOthersNickname() { return othersNickname; }
    public static void setOthersNickname(String nick) { if (nick != null && !nick.isEmpty()) othersNickname = nick; }

    public static Map<String, String> getNameMappings() { return nameMappings; }
    public static Map<String, String> getNameMappingsLower() { return nameMappingsLower; }

    public static String getMappedName(String ign) {
        if (ign == null) return null;
        return nameMappingsLower.get(ign.toLowerCase());
    }

    /** Called by FloydAddonsConfig when loading name mappings from disk. */
    static void setNameMappingsRaw(Map<String, String> raw) {
        nameMappings = Map.copyOf(raw);
        Map<String, String> lower = new HashMap<>();
        for (var entry : raw.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                lower.put(entry.getKey().toLowerCase(), entry.getValue());
            }
        }
        nameMappingsLower = Map.copyOf(lower);
    }

    public static Path getNamesConfigPath() {
        return FloydAddonsConfig.getNamesPath();
    }

    public static Set<String> getCachedOtherNames() {
        long now = System.currentTimeMillis();
        if (now - lastNameCacheUpdate > 1000) {
            lastNameCacheUpdate = now;
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.getNetworkHandler() != null && client.getSession() != null) {
                    Set<String> names = new HashSet<>();
                    UUID selfId = client.getSession().getUuidOrNull();
                    for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                        if (entry.getProfile() != null && !entry.getProfile().id().equals(selfId)) {
                            String name = entry.getProfile().name();
                            if (name != null && !name.isEmpty() && !name.startsWith("!")) {
                                names.add(name);
                            }
                        }
                    }
                    cachedOtherNames = names;
                }
            } catch (Exception ignored) {
            }
        }
        return cachedOtherNames;
    }

    /** Convenience: delegates to the unified config. */
    public static void save() { FloydAddonsConfig.save(); }
    public static void loadNameMappings() { FloydAddonsConfig.loadNameMappings(); }
}
