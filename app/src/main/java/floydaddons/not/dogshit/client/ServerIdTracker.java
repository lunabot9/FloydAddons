package floydaddons.not.dogshit.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects the current Hypixel server ID from the scoreboard once,
 * caches it, and provides literal strings for cheap replacement.
 * Rescans periodically and resets on world/server change.
 */
public final class ServerIdTracker {
    private static volatile String[] cachedIds = {};
    private static long lastScanTick = -100;
    private static final int SCAN_INTERVAL = 20; // ~1 second
    private static boolean wasInWorld = false;

    // Only used for periodic scoreboard scanning, NOT on every rendered text
    private static final Pattern SCAN_PATTERN = Pattern.compile(
            "(?i)(?:(?:mini|mega|lobby|limbo|housing|prototype|node|legacylobby)\\d{1,4}[a-z]{0,2}|[m]\\d{2,4}[a-z]{1,2})"
    );

    private ServerIdTracker() {}

    /** Returns cached server ID strings to replace (may be empty). */
    public static String[] getCachedIds() { return cachedIds; }

    /** Called every client tick from the main tick loop. */
    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            if (wasInWorld) {
                cachedIds = new String[0];
                wasInWorld = false;
            }
            return;
        }

        if (!wasInWorld) {
            wasInWorld = true;
            cachedIds = new String[0];
            lastScanTick = -100; // force immediate scan on join
        }

        long tick = client.world.getTime();
        if (tick - lastScanTick < SCAN_INTERVAL) return;
        lastScanTick = tick;

        scanScoreboard(client);
    }

    private static void scanScoreboard(MinecraftClient client) {
        Scoreboard scoreboard = client.world.getScoreboard();

        // Resolve sidebar objective (team-specific first, then generic)
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

            Matcher matcher = SCAN_PATTERN.matcher(text);
            if (matcher.find()) {
                buildCachedIds(matcher.group());
                return;
            }
        }
    }

    private static void buildCachedIds(String found) {
        // Skip rebuild if already cached for this ID
        if (cachedIds.length > 0 && cachedIds[0].equalsIgnoreCase(found)) return;

        List<String> ids = new ArrayList<>();
        ids.add(found);

        // If abbreviated (m/M + digits + letters), also add the full prefix form
        if (found.length() >= 3
                && (found.charAt(0) == 'm' || found.charAt(0) == 'M')
                && Character.isDigit(found.charAt(1))) {
            String rest = found.substring(1);
            if (Character.isUpperCase(found.charAt(0))) {
                ids.add("mega" + rest);
            } else {
                ids.add("mini" + rest);
            }
        }

        cachedIds = ids.toArray(new String[0]);
    }
}
