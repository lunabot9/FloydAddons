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
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects the current Hypixel server ID from the tab list (primary)
 * or scoreboard (fallback), and accumulates all seen IDs for replacement.
 *
 * Key design:
 * - allSeenIds only grows within a session (never clears on lobby swap).
 * - Remembers the tab list UUID that held the server ID for fast direct lookup.
 * - On lobby swap, scans every tick for ~2 seconds to quickly detect the new ID.
 * - Only clears on full disconnect (client.player == null).
 */
public final class ServerIdTracker {
    // Accumulated set of all server ID strings to replace (only grows, never shrinks mid-session)
    private static final Set<String> allSeenIds = new LinkedHashSet<>();
    // Snapshot array for fast iteration by the text mixin
    private static volatile String[] cachedIdsArray = {};

    // The current full server ID (e.g. "mini28D") for debug display
    private static volatile String currentFullId = "";

    // UUID of the tab list entry that contained the server ID
    private static volatile UUID knownTabUUID = null;

    // Scanning state
    private static long lastScanTick = -100;
    private static final int NORMAL_SCAN_INTERVAL = 20; // ~1 second
    private static boolean wasInWorld = false;

    // Rapid scan: after lobby swap, scan every tick for ~2 seconds
    private static boolean rapidScan = false;
    private static long rapidScanStartTick = 0;
    private static final int RAPID_SCAN_DURATION = 40; // ~2 seconds

    // Track player entity reference to detect lobby swaps
    private static Object lastPlayerRef = null;

    // Known Hypixel server prefixes
    private static final String[] PREFIXES = {
            "mini", "mega", "lobby", "limbo", "housing", "prototype", "node", "legacylobby"
    };

    // Matches: known prefix + 1-4 digits + 0-4 letters (case insensitive)
    private static final Pattern FULL_PATTERN;
    static {
        String group = String.join("|", PREFIXES);
        FULL_PATTERN = Pattern.compile("(?i)(" + group + ")(\\d{1,4}[a-z]{0,4})");
    }

    // Specific pattern: "Server:" followed by a server ID
    private static final Pattern SERVER_LINE_PATTERN;
    static {
        String group = String.join("|", PREFIXES);
        SERVER_LINE_PATTERN = Pattern.compile("(?i)Server:\\s*(" + group + ")(\\d{1,4}[a-z]{0,4})");
    }

    private ServerIdTracker() {}

    /** Exposed for debug command. */
    public static Pattern getFullPattern() { return FULL_PATTERN; }

    /** Returns cached server ID strings to replace (may be empty). */
    public static String[] getCachedIds() { return cachedIdsArray; }

    /** Returns the current full server ID for debug display. */
    public static String getCurrentFullId() { return currentFullId; }

    /** Returns the total number of accumulated IDs. */
    public static int getAccumulatedCount() { return allSeenIds.size(); }

    /** Returns the known tab list UUID, or null if not yet detected. */
    public static UUID getKnownTabUUID() { return knownTabUUID; }

    /** Returns whether rapid scanning is currently active. */
    public static boolean isRapidScanning() { return rapidScan; }

    /**
     * Returns cached IDs with characters inserted to prevent the text mixin
     * from replacing them in debug output.
     */
    public static String[] getCachedIdsForDisplay() {
        String[] ids = cachedIdsArray;
        String[] display = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].length() > 1) {
                display[i] = ids[i].charAt(0) + "\u200B" + ids[i].substring(1);
            } else {
                display[i] = ids[i];
            }
        }
        return display;
    }

    /** Returns current full ID with zero-width space for safe debug display. */
    public static String getCurrentFullIdForDisplay() {
        String id = currentFullId;
        if (id.length() > 1) {
            return id.charAt(0) + "\u200B" + id.substring(1);
        }
        return id;
    }

    /** Called every client tick from the main tick loop. */
    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            if (wasInWorld) {
                // Full disconnect: clear everything
                allSeenIds.clear();
                cachedIdsArray = new String[0];
                currentFullId = "";
                knownTabUUID = null;
                wasInWorld = false;
                lastPlayerRef = null;
                rapidScan = false;
            }
            return;
        }

        // Detect lobby/server swap: player entity reference changes on respawn
        if (client.player != lastPlayerRef) {
            lastPlayerRef = client.player;
            if (wasInWorld) {
                // Lobby swap: do NOT clear allSeenIds - old IDs stay hidden
                // Start rapid scanning to quickly detect the new server ID
                rapidScan = true;
                rapidScanStartTick = client.world.getTime();
                lastScanTick = -100; // force immediate scan
            }
        }

        if (!wasInWorld) {
            wasInWorld = true;
            lastScanTick = -100; // force immediate scan on first join
            rapidScan = true;
            rapidScanStartTick = client.world.getTime();
        }

        long tick = client.world.getTime();

        // End rapid scan after duration expires
        if (rapidScan && tick - rapidScanStartTick >= RAPID_SCAN_DURATION) {
            rapidScan = false;
        }

        int interval = rapidScan ? 1 : NORMAL_SCAN_INTERVAL;
        if (tick - lastScanTick < interval) return;
        lastScanTick = tick;

        // Try direct lookup first if we know the tab UUID
        if (!scanKnownTab(client)) {
            // Fall back to full tab scan
            if (!scanTabList(client)) {
                scanScoreboard(client);
            }
        }
    }

    /**
     * Fast path: directly look up the known tab list entry by UUID.
     */
    private static boolean scanKnownTab(MinecraftClient client) {
        UUID uuid = knownTabUUID;
        if (uuid == null) return false;

        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return false;

        try {
            PlayerListEntry entry = handler.getPlayerListEntry(uuid);
            if (entry == null) {
                // Entry gone (player left or tab list changed) - clear known UUID
                knownTabUUID = null;
                return false;
            }

            Text displayName = entry.getDisplayName();
            if (displayName == null) {
                knownTabUUID = null;
                return false;
            }

            String text = displayName.getString();
            Matcher serverLine = SERVER_LINE_PATTERN.matcher(text);
            if (serverLine.find()) {
                String prefix = serverLine.group(1).toLowerCase();
                String suffix = serverLine.group(2);
                String fullId = prefix + suffix;
                addId(fullId, prefix, suffix);
                return true;
            }

            Matcher general = FULL_PATTERN.matcher(text);
            if (general.find()) {
                String prefix = general.group(1).toLowerCase();
                String suffix = general.group(2);
                String fullId = prefix + suffix;
                addId(fullId, prefix, suffix);
                return true;
            }

            // Known entry no longer contains a server ID - clear and fall through
            knownTabUUID = null;
        } catch (Exception ignored) {}

        return false;
    }

    /**
     * Scans all tab list entries for server IDs.
     * Priority: entries containing "Server:" (Hypixel info tab).
     * Fallback: any entry matching the full pattern.
     */
    private static boolean scanTabList(MinecraftClient client) {
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return false;

        String fallbackFull = null;
        String fallbackPrefix = null;
        String fallbackSuffix = null;
        UUID fallbackUUID = null;

        try {
            for (PlayerListEntry entry : handler.getPlayerList()) {
                Text displayName = entry.getDisplayName();
                if (displayName == null) continue;

                String text = displayName.getString();

                // Priority: "Server:" line
                Matcher serverLine = SERVER_LINE_PATTERN.matcher(text);
                if (serverLine.find()) {
                    String prefix = serverLine.group(1).toLowerCase();
                    String suffix = serverLine.group(2);
                    String fullId = prefix + suffix;

                    // Remember this entry's UUID for fast lookup next time
                    knownTabUUID = entry.getProfile().id();
                    addId(fullId, prefix, suffix);
                    return true;
                }

                // Remember first general match as fallback
                if (fallbackFull == null) {
                    Matcher general = FULL_PATTERN.matcher(text);
                    if (general.find()) {
                        fallbackPrefix = general.group(1).toLowerCase();
                        fallbackSuffix = general.group(2);
                        fallbackFull = fallbackPrefix + fallbackSuffix;
                        fallbackUUID = entry.getProfile().id();
                    }
                }
            }
        } catch (Exception ignored) {
            // ConcurrentModificationException possible during tab list updates
        }

        if (fallbackFull != null) {
            knownTabUUID = fallbackUUID;
            addId(fallbackFull, fallbackPrefix, fallbackSuffix);
            return true;
        }

        return false;
    }

    /**
     * Fallback: scans scoreboard for server IDs.
     */
    private static void scanScoreboard(MinecraftClient client) {
        Scoreboard scoreboard = client.world.getScoreboard();

        ScoreboardObjective objective = null;
        Team playerTeam = scoreboard.getScoreHolderTeam(client.player.getNameForScoreboard());
        if (playerTeam != null) {
            ScoreboardDisplaySlot teamSlot = ScoreboardDisplaySlot.fromFormatting(playerTeam.getColor());
            if (teamSlot != null) {
                objective = scoreboard.getObjectiveForSlot(teamSlot);
            }
        }
        if (objective == null) {
            objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        }
        if (objective == null) return;

        for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
            if (entry.hidden()) continue;
            Team entryTeam = scoreboard.getScoreHolderTeam(entry.owner());
            Text name = Team.decorateName(entryTeam, entry.name());
            String text = name.getString();

            Matcher full = FULL_PATTERN.matcher(text);
            if (full.find()) {
                addId(full.group(0), full.group(1).toLowerCase(), full.group(2));
                return;
            }

            for (String prefix : PREFIXES) {
                char abbrevChar = prefix.charAt(0);
                Pattern abbrPattern = Pattern.compile(
                        "(?i)(?:^|\\s)" + Pattern.quote(String.valueOf(abbrevChar)) + "(\\d{1,4}[a-z]{1,4})(?:\\s|$)");
                Matcher abbr = abbrPattern.matcher(text);
                if (abbr.find()) {
                    String digitsSuffix = abbr.group(1);
                    String abbrId = abbrevChar + digitsSuffix;
                    addAbbreviatedId(abbrId, prefix, digitsSuffix);
                    return;
                }
            }
        }
    }

    /**
     * Adds a full server ID and its abbreviation to the accumulated set.
     * Updates currentFullId and rebuilds the cached array.
     */
    private static void addId(String fullId, String prefix, String suffix) {
        currentFullId = fullId;

        boolean changed = false;
        if (allSeenIds.add(fullId.toLowerCase())) changed = true;

        String abbr = prefix.substring(0, 1) + suffix;
        if (!abbr.equalsIgnoreCase(fullId)) {
            if (allSeenIds.add(abbr.toLowerCase())) changed = true;
        }

        if (changed) {
            rebuildArray();
        }
    }

    /**
     * Adds an abbreviated server ID and possible expansions to the accumulated set.
     */
    private static void addAbbreviatedId(String abbrId, String matchedPrefix, String suffix) {
        currentFullId = abbrId;

        boolean changed = false;
        if (allSeenIds.add(abbrId.toLowerCase())) changed = true;

        char c = matchedPrefix.charAt(0);
        for (String prefix : PREFIXES) {
            if (prefix.charAt(0) == c) {
                String expanded = prefix + suffix;
                if (!expanded.equalsIgnoreCase(abbrId)) {
                    if (allSeenIds.add(expanded.toLowerCase())) changed = true;
                }
            }
        }

        if (changed) {
            rebuildArray();
        }
    }

    /** Rebuilds the volatile array snapshot from allSeenIds. */
    private static void rebuildArray() {
        cachedIdsArray = allSeenIds.toArray(new String[0]);
    }
}
