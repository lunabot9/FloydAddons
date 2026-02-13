package floydaddons.not.dogshit.client.features.misc;
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
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Tracks NPC identity by associating armor stand name tags with nearby player entities.
 *
 * Hypixel SkyBlock NPCs are composed of:
 *   - A minecraft:player entity with a randomized name (the visible model)
 *   - Stacked minecraft:armor_stand entities above it (name tag + "CLICK" label)
 *
 * This tracker resolves the name so MobEspManager can match the player entity.
 * It also tracks which armor stands are NPC labels so the renderer can skip them
 * (avoiding duplicate tracers to the same NPC).
 */
public final class NpcTracker {

    // Runtime cache: player entity network ID -> resolved NPC name (lowercase, stripped)
    private static final Map<Integer, String> runtimeCache = new ConcurrentHashMap<>();

    // Armor stand entity IDs that are NPC labels (should be skipped by renderer)
    private static final Set<Integer> npcArmorStandIds = ConcurrentHashMap.newKeySet();

    // Names to skip when scanning armor stands (not real NPC names)
    private static final Set<String> IGNORED_NAMES = Set.of(
            "click", "armor stand"
    );

    // Pattern to detect NPC-like random player names: 8-12 chars of lowercase + digits
    private static final Pattern NPC_NAME_PATTERN = Pattern.compile("^[a-z0-9]{8,12}$");

    private static long lastScanTick = 0;
    private static final long SCAN_INTERVAL_TICKS = 10; // scan every 0.5 seconds

    private NpcTracker() {}

    /**
     * Called each client tick. Periodically scans to associate armor stand names
     * with player entities and track which armor stands are NPC labels.
     */
    public static void tick() {
        if (!MobEspManager.isEnabled() || !MobEspManager.hasFilters()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return;

        long currentTick = mc.world.getTime();
        if (currentTick - lastScanTick < SCAN_INTERVAL_TICKS) return;
        lastScanTick = currentTick;

        scan(mc);
    }

    private static void scan(MinecraftClient mc) {
        Set<Integer> foundArmorStandIds = new HashSet<>();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ArmorStandEntity armorStand)) continue;
            if (!armorStand.hasCustomName() || armorStand.getCustomName() == null) continue;

            String rawName = armorStand.getCustomName().getString();
            String stripped = stripColorCodes(rawName).trim().toLowerCase();

            if (stripped.isEmpty()) continue;
            if (IGNORED_NAMES.contains(stripped)) continue;
            if (stripped.contains("\u2764") || stripped.startsWith("[lv")) continue;

            // Find the nearest NPC-like player entity near this armor stand
            double asX = armorStand.getX();
            double asY = armorStand.getY();
            double asZ = armorStand.getZ();

            PlayerEntity closest = null;
            double closestDist = Double.MAX_VALUE;

            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player) continue;
                if (!looksLikeNpcName(player.getName().getString())) continue;

                double dx = player.getX() - asX;
                double dy = player.getY() - asY;
                double dz = player.getZ() - asZ;

                if (dy > 1.0 || dy < -4.0) continue;
                double horizDist = dx * dx + dz * dz;
                if (horizDist > 4.0) continue;

                if (horizDist < closestDist) {
                    closestDist = horizDist;
                    closest = player;
                }
            }

            if (closest != null) {
                runtimeCache.put(closest.getId(), stripped);
                // Mark this armor stand AND the CLICK stand as NPC labels
                foundArmorStandIds.add(armorStand.getId());
                // Also find nearby "CLICK" armor stands to suppress
                markNearbyClickStands(mc, asX, asY, asZ, foundArmorStandIds);
            }
        }

        npcArmorStandIds.clear();
        npcArmorStandIds.addAll(foundArmorStandIds);
    }

    /** Find "CLICK" and other label armor stands near an NPC and mark them. */
    private static void markNearbyClickStands(MinecraftClient mc, double x, double y, double z, Set<Integer> ids) {
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ArmorStandEntity as)) continue;
            double dx = as.getX() - x;
            double dy = as.getY() - y;
            double dz = as.getZ() - z;
            if (dx * dx + dz * dz > 4.0 || Math.abs(dy) > 4.0) continue;
            ids.add(as.getId());
        }
    }

    /**
     * Returns true if this armor stand is part of an NPC (name tag, CLICK label, etc.)
     * and should NOT get its own tracer (the player entity tracer covers it).
     */
    public static boolean isNpcArmorStand(Entity entity) {
        return npcArmorStandIds.contains(entity.getId());
    }

    /**
     * Heuristic: NPC player entities on Hypixel have random alphanumeric names.
     */
    private static boolean looksLikeNpcName(String name) {
        return NPC_NAME_PATTERN.matcher(name).matches();
    }

    /** Returns the cached NPC name for a player entity, or null if unknown. */
    public static String getCachedName(Entity entity) {
        return runtimeCache.get(entity.getId());
    }

    /** Reverse lookup: find the entity whose cached NPC name matches (case-insensitive). */
    public static Entity findEntityByName(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || name == null) return null;
        for (Map.Entry<Integer, String> entry : runtimeCache.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return mc.world.getEntityById(entry.getKey());
            }
        }
        return null;
    }

    /** Clears all runtime state. */
    public static void clear() {
        runtimeCache.clear();
        npcArmorStandIds.clear();
    }

    private static String stripColorCodes(String s) {
        return s.replaceAll("\u00a7.", "");
    }
}
