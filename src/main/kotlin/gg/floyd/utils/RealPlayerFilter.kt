package gg.floyd.utils

import gg.floyd.FloydAddonsMod.mc
import net.minecraft.world.entity.Entity
import java.util.UUID

/**
 * Shared "real player vs server-faked NPC" heuristic for Player ESP.
 *
 * A real connected player — Java OR Bedrock (Geyser/Floodgate) — always has a tab player-list entry
 * keyed by their account UUID, so membership is tested by **UUID**, not by name. The previous
 * name-pattern test (`[a-zA-Z0-9_]{3,16}`) wrongly excluded Bedrock players, whose in-game name
 * carries a Geyser prefix (e.g. `.Gamertag`), can contain spaces, and may exceed 16 chars. Fake /
 * hologram NPC player entities (Citizens-style) are not present in the tab list by UUID, so they
 * stay filtered.
 *
 * The UUID set is cached for [TAB_LIST_CACHE_MS] since [isRealPlayer] is called from render paths.
 * Touches no GL — only `mc.connection`.
 */
object RealPlayerFilter {
    private const val TAB_LIST_CACHE_MS = 1_000L

    @Volatile private var tabListUuids: Set<UUID> = emptySet()
    private var lastRefreshMs = 0L

    /** Whether [entity]'s UUID is in the tab player list (a real connected player, Java or Bedrock). */
    fun isRealPlayer(entity: Entity): Boolean {
        refresh()
        return tabListUuids.contains(entity.uuid)
    }

    private fun refresh(now: Long = System.currentTimeMillis()) {
        if (now - lastRefreshMs < TAB_LIST_CACHE_MS) return
        lastRefreshMs = now
        val connection = mc.connection ?: run {
            tabListUuids = emptySet()
            return
        }
        tabListUuids = connection.listedOnlinePlayers
            .asSequence()
            .map { it.profile.id }
            .toSet()
    }
}
