package floydaddons.not.dogshit.client;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages mob ESP runtime state and entity matching.
 * Uses simple direct matching — the renderer checks matches() on each entity
 * and uses NpcTracker to skip duplicate armor stands.
 */
public final class MobEspManager {
    // Parsed from mob-esp.json: lowercase name matches and entity type ID matches
    private static Set<String> nameFilters = Collections.emptySet();
    private static Set<Identifier> typeFilters = Collections.emptySet();

    // Debug labels mode: auto-expires after 10 seconds
    private static volatile boolean debugLabelsActive = false;
    private static long debugLabelsExpireMs = 0;

    private static final String STAR_CHAR = "\u272F"; // ✯

    private MobEspManager() {}

    public static boolean isEnabled() { return RenderConfig.isMobEspEnabled(); }

    public static void toggle() {
        RenderConfig.toggleMobEsp();
    }

    public static void setEnabled(boolean value) {
        RenderConfig.setMobEspEnabled(value);
    }

    public static boolean isTracersEnabled() { return RenderConfig.isMobEspTracers(); }
    public static boolean isHitboxesEnabled() { return RenderConfig.isMobEspHitboxes(); }
    public static boolean isStarMobsEnabled() { return RenderConfig.isMobEspStarMobs(); }

    public static boolean isDebugActive() {
        if (!debugLabelsActive) return false;
        if (System.currentTimeMillis() > debugLabelsExpireMs) {
            debugLabelsActive = false;
            return false;
        }
        return true;
    }

    public static void setDebugActive(boolean active) {
        debugLabelsActive = active;
        if (active) {
            debugLabelsExpireMs = System.currentTimeMillis() + 10_000;
        }
    }

    public static Set<String> getNameFilters() { return nameFilters; }
    public static Set<Identifier> getTypeFilters() { return typeFilters; }

    /**
     * Loads filters from the parsed config entries.
     * Each entry is a map with either a "name" or "mob" key.
     */
    public static void loadFilters(List<Map<String, String>> entries) {
        if (entries == null || entries.isEmpty()) {
            nameFilters = Collections.emptySet();
            typeFilters = Collections.emptySet();
            return;
        }

        nameFilters = entries.stream()
                .filter(e -> e.containsKey("name"))
                .map(e -> e.get("name").toLowerCase())
                .collect(Collectors.toUnmodifiableSet());

        typeFilters = entries.stream()
                .filter(e -> e.containsKey("mob"))
                .map(e -> Identifier.of(e.get("mob")))
                .collect(Collectors.toUnmodifiableSet());
    }

    public static boolean hasFilters() {
        return !nameFilters.isEmpty() || !typeFilters.isEmpty() || RenderConfig.isMobEspStarMobs();
    }

    /**
     * Check if an entity matches configured filters.
     *
     * For armor stands: checks star name and name filters on custom name.
     * For non-armor-stands: checks type ID, display name, custom name, NPC cache name.
     */
    public static boolean matches(Entity entity) {
        if (entity instanceof ArmorStandEntity as) {
            if (!as.hasCustomName() || as.getCustomName() == null) return false;
            String stripped = stripColorCodes(as.getCustomName().getString());

            // Star mob detection: armor stand nametag contains ✯
            if (RenderConfig.isMobEspStarMobs() && stripped.contains(STAR_CHAR)) return true;

            // Name filter on armor stand custom name
            if (!nameFilters.isEmpty()) {
                String lower = stripped.toLowerCase();
                for (String filter : nameFilters) {
                    if (lower.contains(filter)) return true;
                }
            }
            return false;
        }

        // Non-armor-stand: entity type ID match
        if (!typeFilters.isEmpty()) {
            Identifier typeId = EntityType.getId(entity.getType());
            if (typeFilters.contains(typeId)) return true;
        }

        // Non-armor-stand: name filter match
        if (!nameFilters.isEmpty()) {
            String displayName = stripColorCodes(entity.getName().getString()).toLowerCase();
            for (String filter : nameFilters) {
                if (displayName.contains(filter)) return true;
            }

            if (entity.hasCustomName() && entity.getCustomName() != null) {
                String customName = stripColorCodes(entity.getCustomName().getString()).toLowerCase();
                for (String filter : nameFilters) {
                    if (customName.contains(filter)) return true;
                }
            }

            // Check NPC tracker cache (armor stand name resolved to this player entity)
            String cachedNpcName = NpcTracker.getCachedName(entity);
            if (cachedNpcName != null) {
                for (String filter : nameFilters) {
                    if (cachedNpcName.contains(filter)) return true;
                }
            }
        }

        return false;
    }

    /** Strip Minecraft color codes (section sign + char) from a string. */
    private static String stripColorCodes(String s) {
        return s.replaceAll("\u00a7.", "");
    }
}
