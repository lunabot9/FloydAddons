package gg.floyd.utils

import gg.floyd.FloydAddonsMod.mc
import net.minecraft.world.entity.Entity
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Shared "real player vs server-faked NPC" heuristic, keyed by UUID with presence HYSTERESIS.
 *
 * The decisive protocol fact (Hypixel/donut-style NPC mechanics): a spawned player entity is auto-added
 * to the tab player-info list on spawn, the server then immediately REMOVES the NPC from tab, and only
 * re-adds it for ~1 frame on each ~15s keep-alive. So the signal is INVERTED from intuition — REAL
 * players are STEADILY listed; NPC holograms FLICKER in and out of tab. A naive instantaneous
 * `listed this frame?` check therefore (a) flickers real players off whenever their entry transiently
 * drops, and (b) flashes fakes on during their brief keep-alive re-adds (the two bugs we hit).
 *
 * Fix: key by [Entity.getUUID] (immune to the colored/decorated/duplicate NAMES that NPCs exploit, which
 * defeated the old name-matching), sample the listed-UUID set a few times a second, and gate visibility
 * with two-sided hysteresis: PROMOTE a UUID to shown only after it has been continuously listed for
 * [CONFIRM_MS]; once shown, DEMOTE only after it has been continuously UNlisted for [FORGET_MS]. Because
 * an NPC's keep-alive blip lasts ~1 frame it never accumulates [CONFIRM_MS] of continuous presence, and
 * because [FORGET_MS] (~1s) is far below the ~15s keep-alive period a real player's transient drop never
 * reaches the demote threshold — effectively zero flicker for real players, near-zero NPC leakage.
 *
 * Touches no GL — only `mc.connection`. All bookkeeping is cheap; the render path just reads a boolean.
 */
object RealPlayerFilter {
    /** A real Minecraft username. Decorated NPC tab entries (colored, command-like, spaced) fail this,
     *  so they never enter the "listed-real" set even when the server parks them in the tab list. */
    private val playerNamePattern: Pattern = Pattern.compile("[a-zA-Z0-9_]{3,16}")

    /** Re-sample the tab list at most this often (~every other tick). */
    private const val SAMPLE_MS = 100L

    /** Continuously-listed duration required before a UUID is first shown. */
    private const val CONFIRM_MS = 1_500L

    /** Continuously-unlisted duration required before a shown UUID is hidden (bridges transient drops). */
    private const val FORGET_MS = 1_000L

    /** Drop trackers untouched this long so the map can't grow without bound on crowded servers. */
    private const val EVICT_MS = 30_000L

    private class Tracker {
        var listedStreakStartMs = 0L   // start of the current continuous-listed streak (0 = not currently listed)
        var lastListedMs = 0L          // last sample at which this UUID was listed
        var lastTouchMs = 0L           // last sample at which this UUID existed in the tab at all
        var shown = false
    }

    private val trackers = ConcurrentHashMap<UUID, Tracker>()
    @Volatile private var lastSampleMs = 0L

    /** The debounced "this entity is a real, steadily-listed player" verdict. */
    fun isRealPlayer(entity: Entity): Boolean {
        sample(System.currentTimeMillis())
        return trackers[entity.uuid]?.shown == true
    }

    private fun sample(now: Long) {
        if (now - lastSampleMs < SAMPLE_MS) return
        lastSampleMs = now

        val connection = mc.connection ?: run {
            trackers.clear()
            return
        }

        // Only count tab entries whose GameProfile name is a valid Minecraft username. This is the gate
        // that actually rejects the decorated/command-named NPC holograms; keying the survivors by UUID
        // then gives the immune-to-name-spoofing identity, and the hysteresis below removes the flicker.
        val listed: Set<UUID> = connection.listedOnlinePlayers
            .asSequence()
            .filter { playerNamePattern.matcher(it.profile.name ?: "").matches() }
            .map { it.profile.id }
            .toHashSet()

        // Promote currently-listed UUIDs.
        for (id in listed) {
            val t = trackers.getOrPut(id) { Tracker() }
            if (t.listedStreakStartMs == 0L) t.listedStreakStartMs = now
            t.lastListedMs = now
            t.lastTouchMs = now
            if (!t.shown && now - t.listedStreakStartMs >= CONFIRM_MS) t.shown = true
        }

        // Demote/evict UUIDs absent this sample.
        val it = trackers.entries.iterator()
        while (it.hasNext()) {
            val (id, t) = it.next()
            if (id in listed) continue
            t.listedStreakStartMs = 0L                                    // streak broken
            if (t.shown && now - t.lastListedMs >= FORGET_MS) t.shown = false
            if (now - t.lastTouchMs >= EVICT_MS) it.remove()
        }
    }
}
