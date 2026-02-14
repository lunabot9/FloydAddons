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

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages mob ESP runtime state and entity matching.
 * Uses simple direct matching — the renderer checks matches() on each entity
 * and uses NpcTracker to skip duplicate armor stands.
 */
public final class MobEspManager {
    // Parsed from mob-esp.json: name matches (original case) and entity type ID matches
    private static Set<String> nameFilters = Collections.emptySet();
    private static Set<Identifier> typeFilters = Collections.emptySet();

    // Raw entries for serialization back to JSON
    private static List<Map<String, String>> rawEntries = new ArrayList<>();

    // Per-entry color: filter key (lowercase) -> {color, chromaFlag (1=true, 0=false)}
    private static Map<String, int[]> filterColors = new HashMap<>();

    // Debug labels mode: auto-expires after 10 seconds
    private static volatile boolean debugLabelsActive = false;
    private static long debugLabelsExpireMs = 0;

    private static final String STAR_CHAR = "\u272F"; // ✯

    // Cached real player names from the tab list, refreshed every 1s
    private static volatile Set<String> tabListNames = Collections.emptySet();
    private static long lastTabListRefreshMs = 0;
    private static final long TAB_LIST_CACHE_MS = 1_000L;

    // --- Armor stand → mob resolution cache ---
    // armor stand entity ID → paired mob entity ID
    private static final Map<Integer, Integer> armorStandToMob = new ConcurrentHashMap<>();
    // mob entity IDs that are ESP targets (resolved from armor stands)
    private static final Set<Integer> resolvedMobIds = ConcurrentHashMap.newKeySet();
    // mob entity ID → stripped armor stand name (for color lookup and debug)
    private static final Map<Integer, String> resolvedMobNames = new ConcurrentHashMap<>();
    // Stable render target list — rebuilt by tickScan(), consumed by renderer
    private static final List<Entity> renderTargets = new ArrayList<>();
    private static int scanTickCounter = 0;
    private static final int SCAN_INTERVAL = 10; // every 0.5 seconds

    // Known dungeon miniboss names — matched on armor stands when star mobs enabled
    private static final Set<String> MINIBOSS_NAMES = Set.of(
            "shadow assassin", "lost adventurer", "frozen adventurer",
            "king midas", "angry archaeologist"
    );

    // Mob names that spawn as player entities (not regular mobs)
    private static final Set<String> PLAYER_MOB_NAMES = Set.of(
            "shadow assassin", "lost adventurer", "diamond guy", "king midas"
    );

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
    public static List<Map<String, String>> getRawEntries() { return Collections.unmodifiableList(rawEntries); }

    /**
     * Loads filters from the parsed config entries.
     * Each entry is a map with either a "name" or "mob" key.
     */
    public static void loadFilters(List<Map<String, String>> entries) {
        rawEntries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
        reparse();
    }

    public static void addNameFilter(String name) {
        rawEntries.add(new LinkedHashMap<>(Map.of("name", name)));
        reparse();
    }

    public static void addNameFilter(String name, int color, boolean chroma) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("color", String.format("#%06X", color & 0xFFFFFF));
        entry.put("chroma", String.valueOf(chroma));
        rawEntries.add(entry);
        reparse();
    }

    public static boolean removeNameFilter(String name) {
        boolean removed = rawEntries.removeIf(e ->
                e.containsKey("name") && e.get("name").equalsIgnoreCase(name));
        if (removed) reparse();
        return removed;
    }

    public static void addTypeFilter(String typeId) {
        rawEntries.add(new LinkedHashMap<>(Map.of("mob", typeId)));
        reparse();
    }

    public static void addTypeFilter(String typeId, int color, boolean chroma) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("mob", typeId);
        entry.put("color", String.format("#%06X", color & 0xFFFFFF));
        entry.put("chroma", String.valueOf(chroma));
        rawEntries.add(entry);
        reparse();
    }

    public static boolean removeTypeFilter(String typeId) {
        boolean removed = rawEntries.removeIf(e ->
                e.containsKey("mob") && e.get("mob").equalsIgnoreCase(typeId));
        if (removed) reparse();
        return removed;
    }

    public static void clearFilters() {
        rawEntries.clear();
        reparse();
    }

    /**
     * Set or update the color for an existing filter entry.
     * @param filterKey the name or mob type ID
     * @param color ARGB color
     * @param chroma whether to use chroma cycling
     */
    public static void setFilterColor(String filterKey, int color, boolean chroma) {
        for (Map<String, String> entry : rawEntries) {
            String val = entry.get("name");
            if (val == null) val = entry.get("mob");
            if (val != null && val.equalsIgnoreCase(filterKey)) {
                entry.put("color", String.format("#%06X", color & 0xFFFFFF));
                entry.put("chroma", String.valueOf(chroma));
                break;
            }
        }
        reparse();
    }

    /**
     * Returns {color, chromaFlag} for a filter key, or null if no custom color set.
     */
    public static int[] getColorForFilter(String filterKey) {
        return filterColors.get(filterKey.toLowerCase());
    }

    /**
     * Returns {color, chromaFlag} for an entity based on its matching filter,
     * or the default ESP color if no per-entry color is set.
     */
    public static int[] getColorForEntity(Entity entity) {
        // Check resolved mob name (from armor stand pairing) for color
        String resolvedName = resolvedMobNames.get(entity.getId());
        if (resolvedName != null && !nameFilters.isEmpty()) {
            for (String filter : nameFilters) {
                if (resolvedName.contains(filter.toLowerCase())) {
                    int[] c = filterColors.get(filter.toLowerCase());
                    if (c != null) return c;
                }
            }
        }
        // Check name filters first
        if (!nameFilters.isEmpty()) {
            String displayName = stripColorCodes(entity.getName().getString()).toLowerCase();
            for (String filter : nameFilters) {
                if (displayName.contains(filter.toLowerCase())) {
                    int[] c = filterColors.get(filter.toLowerCase());
                    if (c != null) return c;
                }
            }
            if (entity.hasCustomName() && entity.getCustomName() != null) {
                String customName = stripColorCodes(entity.getCustomName().getString()).toLowerCase();
                for (String filter : nameFilters) {
                    if (customName.contains(filter.toLowerCase())) {
                        int[] c = filterColors.get(filter.toLowerCase());
                        if (c != null) return c;
                    }
                }
            }
            String cachedNpcName = NpcTracker.getCachedName(entity);
            if (cachedNpcName != null) {
                for (String filter : nameFilters) {
                    if (cachedNpcName.contains(filter.toLowerCase())) {
                        int[] c = filterColors.get(filter.toLowerCase());
                        if (c != null) return c;
                    }
                }
            }
        }
        // Check type filters
        if (!typeFilters.isEmpty()) {
            Identifier typeId = EntityType.getId(entity.getType());
            String typeStr = typeId.toString().toLowerCase();
            int[] c = filterColors.get(typeStr);
            if (c != null) return c;
        }
        // Default ESP color
        return new int[]{
                RenderConfig.getDefaultEspColor(),
                RenderConfig.isDefaultEspChromaEnabled() ? 1 : 0
        };
    }

    private static void reparse() {
        nameFilters = rawEntries.stream()
                .filter(e -> e.containsKey("name"))
                .map(e -> e.get("name"))
                .collect(Collectors.toUnmodifiableSet());

        typeFilters = rawEntries.stream()
                .filter(e -> e.containsKey("mob"))
                .map(e -> Identifier.of(e.get("mob")))
                .collect(Collectors.toUnmodifiableSet());

        // Parse per-entry colors
        Map<String, int[]> colors = new HashMap<>();
        for (Map<String, String> entry : rawEntries) {
            String key = entry.get("name");
            if (key == null) key = entry.get("mob");
            if (key == null) continue;
            String colorStr = entry.get("color");
            if (colorStr == null) continue;
            int color = parseHexColor(colorStr);
            boolean chroma = "true".equalsIgnoreCase(entry.get("chroma"));
            colors.put(key.toLowerCase(), new int[]{color, chroma ? 1 : 0});
        }
        filterColors = colors;
    }

    private static int parseHexColor(String hex) {
        if (hex == null) return 0xFFFFFFFF;
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return 0xFFFFFFFF;
        try {
            return 0xFF000000 | Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFFFF;
        }
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
    private static boolean isRealPlayer(Entity entity) {
        refreshTabListNames();
        String name = stripColorCodes(entity.getName().getString()).toLowerCase();
        return tabListNames.contains(name);
    }

    private static void refreshTabListNames() {
        long now = System.currentTimeMillis();
        if (now - lastTabListRefreshMs < TAB_LIST_CACHE_MS) return;
        lastTabListRefreshMs = now;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) { tabListNames = Collections.emptySet(); return; }
        Set<String> names = new HashSet<>();
        for (var entry : mc.getNetworkHandler().getPlayerList()) {
            String n = entry.getProfile().name();
            if (n != null && n.matches("[a-zA-Z0-9_]{3,16}")) {
                names.add(n.toLowerCase());
            }
        }
        tabListNames = names;
    }

    public static boolean matches(Entity entity) {
        if (isRealPlayer(entity)) return false;

        // Mobs resolved from armor stand pairings always match
        if (resolvedMobIds.contains(entity.getId())) return true;

        // Armor stands are never direct ESP targets — tickScan() resolves them to mobs
        if (entity instanceof ArmorStandEntity) return false;

        // Player-entity mobs (Shadow Assassin, Lost Adventurer, etc.) when star mobs enabled
        if (entity instanceof PlayerEntity && RenderConfig.isMobEspStarMobs()) {
            String name = stripColorCodes(entity.getName().getString()).toLowerCase();
            if (PLAYER_MOB_NAMES.contains(name)) return true;
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
                if (displayName.contains(filter.toLowerCase())) return true;
            }

            if (entity.hasCustomName() && entity.getCustomName() != null) {
                String customName = stripColorCodes(entity.getCustomName().getString()).toLowerCase();
                for (String filter : nameFilters) {
                    if (customName.contains(filter.toLowerCase())) return true;
                }
            }

            // Check NPC tracker cache (armor stand name resolved to this player entity)
            String cachedNpcName = NpcTracker.getCachedName(entity);
            if (cachedNpcName != null) {
                for (String filter : nameFilters) {
                    if (cachedNpcName.contains(filter.toLowerCase())) return true;
                }
            }
        }

        return false;
    }

    /** Strip Minecraft color codes (section sign + char) from a string. */
    private static String stripColorCodes(String s) {
        return s.replaceAll("\u00a7.", "");
    }

    // --- Armor stand → mob resolution ---

    /**
     * Called every client tick. Periodically scans armor stands, resolves them
     * to nearby mob entities, and caches the pairings. This eliminates per-frame
     * lookups and prevents flickering.
     */
    public static void tickScan() {
        if (!isEnabled() || !hasFilters()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) {
            clearCaches();
            return;
        }

        if (++scanTickCounter < SCAN_INTERVAL) return;
        scanTickCounter = 0;

        boolean starMobsEnabled = RenderConfig.isMobEspStarMobs();

        // Cleanup stale entries (entities despawned or unloaded)
        armorStandToMob.entrySet().removeIf(entry -> {
            Entity as = mc.world.getEntityById(entry.getKey());
            Entity mob = mc.world.getEntityById(entry.getValue());
            if (as == null || mob == null) {
                resolvedMobIds.remove(entry.getValue());
                resolvedMobNames.remove(entry.getValue());
                return true;
            }
            return false;
        });

        // Scan armor stands for new pairings
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ArmorStandEntity as)) continue;
            if (!as.hasCustomName() || as.getCustomName() == null) continue;
            if (armorStandToMob.containsKey(as.getId())) continue; // already cached

            String stripped = stripColorCodes(as.getCustomName().getString());
            String lowerStripped = stripped.toLowerCase();

            boolean matched = false;

            // Star character check
            if (starMobsEnabled && stripped.contains(STAR_CHAR)) matched = true;

            // Miniboss name check (when star mobs enabled)
            if (!matched && starMobsEnabled) {
                for (String name : MINIBOSS_NAMES) {
                    if (lowerStripped.contains(name)) { matched = true; break; }
                }
            }

            // Name filter check
            if (!matched && !nameFilters.isEmpty()) {
                for (String filter : nameFilters) {
                    if (lowerStripped.contains(filter.toLowerCase())) { matched = true; break; }
                }
            }

            if (!matched) continue;

            // Find the nearest mob entity near this armor stand
            Entity mob = findNearbyMob(mc, as);
            if (mob != null) {
                armorStandToMob.put(as.getId(), mob.getId());
                resolvedMobIds.add(mob.getId());
                resolvedMobNames.put(mob.getId(), lowerStripped);
            }
        }

        // Rebuild the stable render target list
        List<Entity> newTargets = new ArrayList<>();
        Set<Integer> addedIds = new HashSet<>();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (matches(entity) && addedIds.add(entity.getId())) {
                newTargets.add(entity);
            }
        }
        renderTargets.clear();
        renderTargets.addAll(newTargets);
    }

    /**
     * Find the closest non-armor-stand entity near the given armor stand.
     * Uses a wide search radius (1.5 blocks horizontal, 5 blocks vertical below)
     * to handle mob drift and stacking.
     */
    private static Entity findNearbyMob(MinecraftClient mc, Entity armorStand) {
        double asX = armorStand.getX();
        double asY = armorStand.getY();
        double asZ = armorStand.getZ();

        Entity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ArmorStandEntity) continue;
            if (entity == mc.player) continue;
            if (isRealPlayer(entity)) continue;

            double dx = entity.getX() - asX;
            double dz = entity.getZ() - asZ;
            double horizDistSq = dx * dx + dz * dz;
            if (horizDistSq > 2.25) continue; // within 1.5 blocks horizontal

            double dy = entity.getY() - asY;
            if (dy > 1.0 || dy < -5.0) continue; // mob is at or below the nametag

            double totalDist = horizDistSq + (dy * dy);
            if (totalDist < closestDist) {
                closestDist = totalDist;
                closest = entity;
            }
        }

        return closest;
    }

    /**
     * Returns the stable render target list. Removes dead/despawned entities lazily.
     * The renderer should draw from this list instead of re-evaluating matches().
     */
    public static List<Entity> getRenderTargets() {
        renderTargets.removeIf(Entity::isRemoved);
        return renderTargets;
    }

    /** Clear all resolution caches (e.g. on world change). */
    public static void clearCaches() {
        armorStandToMob.clear();
        resolvedMobIds.clear();
        resolvedMobNames.clear();
        renderTargets.clear();
        scanTickCounter = 0;
    }

    /** Returns the resolved mob entity ID for an armor stand, or null. */
    public static Integer getResolvedMob(int armorStandId) {
        return armorStandToMob.get(armorStandId);
    }

    /** Returns true if this armor stand has been matched and paired with a mob. */
    public static boolean isMatchedArmorStand(int entityId) {
        return armorStandToMob.containsKey(entityId);
    }

    /** Returns the stripped armor stand name for a resolved mob, or null. */
    public static String getResolvedMobName(int entityId) {
        return resolvedMobNames.get(entityId);
    }
}
