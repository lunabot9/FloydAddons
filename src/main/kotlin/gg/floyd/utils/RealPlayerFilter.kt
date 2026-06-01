package gg.floyd.utils

import gg.floyd.FloydAddonsMod.mc
import net.minecraft.world.entity.Entity
import java.util.Locale
import java.util.regex.Pattern

/**
 * Shared "real player vs server-faked NPC" heuristic. A real player's name appears in the tab
 * player-info list and matches a valid Minecraft username pattern; server NPC holograms (donutsmp's
 * "/<command>" entries, decorated/colored strings, etc.) do not. This mirrors the filter
 * [gg.floyd.features.impl.render.FloydMobEsp] already applies, so Player ESP hides the same fakes.
 *
 * The tab-name set is cached for [TAB_LIST_CACHE_MS] since [isRealPlayer] is called from render
 * paths. Touches no GL — only `mc.connection`.
 */
object RealPlayerFilter {
    private val FORMATTING_CODE = Regex("§.")
    private val playerNamePattern: Pattern = Pattern.compile("[a-zA-Z0-9_]{3,16}")
    private const val TAB_LIST_CACHE_MS = 1_000L

    @Volatile private var tabListNames: Set<String> = emptySet()
    private var lastRefreshMs = 0L

    /** Whether [entity]'s name matches a valid-pattern entry currently in the tab player list. */
    fun isRealPlayer(entity: Entity): Boolean {
        refresh()
        val name = entity.name.string.replace(FORMATTING_CODE, "").lowercase(Locale.ROOT)
        return tabListNames.contains(name)
    }

    private fun refresh(now: Long = System.currentTimeMillis()) {
        if (now - lastRefreshMs < TAB_LIST_CACHE_MS) return
        lastRefreshMs = now
        val connection = mc.connection ?: run {
            tabListNames = emptySet()
            return
        }
        tabListNames = connection.listedOnlinePlayers
            .asSequence()
            .mapNotNull { it.profile.name }
            .filter { playerNamePattern.matcher(it).matches() }
            .map { it.lowercase(Locale.ROOT) }
            .toSet()
    }
}
