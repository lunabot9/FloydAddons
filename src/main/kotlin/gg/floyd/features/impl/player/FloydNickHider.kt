package gg.floyd.features.impl.player

import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.MapSetting
import gg.floyd.clickgui.settings.impl.StringSetting
import gg.floyd.events.TickEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.impl.hiders.FloydHiders
import gg.floyd.utils.ChatChroma
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.scores.DisplaySlot
import java.util.Locale
import java.util.Optional
import java.util.UUID
import java.util.regex.Pattern

object FloydNickHider : Module(
    name = "Nick Hider",
    category = Category.PLAYER,
    description = "Floyd nickname replacement and per-player name mappings.",
    toggled = true,
) {
    val nickHiderEnabled by BooleanSetting("Enabled", false, desc = "Enables nickname and name-mapping replacement.")
    var nickname by StringSetting("Default Nick", "George Floyd", 32, desc = "Replacement name used by nick hider.")
    val selfNameChroma by BooleanSetting("Self Name Chroma", false, desc = "Cycles your own replaced nickname through chroma.")
    val nameMappings by MapSetting("Name Mappings", mutableMapOf<String, String>()).hide()

    private val serverIds = FloydServerIdAccumulator()
    private var knownTabUuid: UUID? = null
    private var lastScanTick = -100L
    private var wasInWorld = false
    private var lastPlayerRef: Any? = null
    private var rapidScan = false
    private var rapidScanStartTick = 0L
    private var lastScanSource = ScanSource.NONE
    private var lastScannedText = ""
    private var lastHitTick = -1L
    private var scanHits = 0L
    private var tabEntryCount = 0
    private var scoreboardLineCount = 0
    private var scoreboardObjectiveNames = emptyList<String>()
    private var scoreboardTrackedPlayerCount = 0
    private var scoreboardSelectedObjective: String? = null
    private var scoreboardSidebarObjective: String? = null
    private var scoreboardTeamSlotObjective: String? = null

    init {
        on<TickEvent.End> {
            tickServerIdTracker()
        }
    }

    @JvmStatic
    fun hasReplacements(): Boolean =
        nickHiderEnabled || FloydHiders.serverIdHider || FloydHiders.profileIdHider

    @JvmStatic
    fun replaceString(text: String): String {
        if (!hasReplacements() || text.isEmpty()) return text
        var result = text
        if (nickHiderEnabled) {
            mc.user.name.takeIf { it.isNotBlank() }?.let {
                result = replaceIgnoreCase(result, it, nickname)
            }
            for ((find, replace) in nameMappings) {
                if (find.isNotEmpty()) {
                    result = replaceIgnoreCase(result, find, replace)
                }
            }
        }
        if (FloydHiders.serverIdHider) {
            result = serverIds.replaceDateServerId(result, SERVER_ID_REPLACEMENT)
            for (id in serverIds.cachedIds()) {
                result = replaceIgnoreCase(result, id, SERVER_ID_REPLACEMENT)
            }
            result = FloydServerIdAccumulator.FULL_SERVER_ID_PATTERN.matcher(result).replaceAll(SERVER_ID_REPLACEMENT)
        }
        if (FloydHiders.profileIdHider) result = PROFILE_ID_PATTERN.matcher(result).replaceAll(PROFILE_ID_REPLACEMENT)
        return result
    }

    @JvmStatic
    fun replaceComponent(component: Component): Component? {
        if (!hasReplacements()) return null
        var result = component
        var changed = false

        if (nickHiderEnabled) {
            mc.user.name.takeIf { it.isNotBlank() }?.let {
                replaceLiteralComponentIgnoreCase(result, it, nickname).also { replacement ->
                    result = replacement.component
                    changed = changed || replacement.changed
                }
            }
            for ((find, replace) in nameMappings) {
                if (find.isNotEmpty()) {
                    replaceLiteralComponentIgnoreCase(result, find, replace).also { replacement ->
                        result = replacement.component
                        changed = changed || replacement.changed
                    }
                }
            }
        }
        if (FloydHiders.serverIdHider) {
            replaceDateServerIdInComponent(result, SERVER_ID_REPLACEMENT).also { replacement ->
                result = replacement.component
                changed = changed || replacement.changed
            }
            for (id in serverIds.cachedIds()) {
                replaceLiteralComponentIgnoreCase(result, id, SERVER_ID_REPLACEMENT).also { replacement ->
                    result = replacement.component
                    changed = changed || replacement.changed
                }
            }
            replaceRegexComponent(result, FloydServerIdAccumulator.FULL_SERVER_ID_PATTERN, SERVER_ID_REPLACEMENT).also { replacement ->
                result = replacement.component
                changed = changed || replacement.changed
            }
        }
        if (FloydHiders.profileIdHider) {
            replaceRegexComponent(result, PROFILE_ID_PATTERN, PROFILE_ID_REPLACEMENT).also { replacement ->
                result = replacement.component
                changed = changed || replacement.changed
            }
        }

        return if (changed) result else null
    }

    @JvmStatic
    fun replaceSequence(text: FormattedCharSequence): FormattedCharSequence {
        if (!hasReplacements()) return text
        val styled = StyledText.from(text) ?: return text
        var changed = false

        if (nickHiderEnabled) {
            mc.user.name.takeIf { it.isNotBlank() }?.let {
                changed = styled.replaceIgnoreCase(
                    it,
                    nickname,
                    if (selfNameChroma) { index, style -> ChatChroma.applyToStyle(style, index, speedMs = 12.0) } else null
                ) || changed
            }
            for ((find, replace) in nameMappings) {
                if (find.isNotEmpty()) {
                    changed = styled.replaceIgnoreCase(find, replace) || changed
                }
            }
        }
        if (FloydHiders.serverIdHider) {
            changed = styled.replaceDateServerId(SERVER_ID_REPLACEMENT) || changed
            for (id in serverIds.cachedIds()) {
                changed = styled.replaceIgnoreCase(id, SERVER_ID_REPLACEMENT) || changed
            }
            changed = styled.replaceRegex(FloydServerIdAccumulator.FULL_SERVER_ID_PATTERN, SERVER_ID_REPLACEMENT) || changed
        }
        if (FloydHiders.profileIdHider) {
            changed = styled.replaceRegex(PROFILE_ID_PATTERN, PROFILE_ID_REPLACEMENT) || changed
        }

        return if (changed) styled.toSequence() else text
    }

    fun currentServerIdForDisplay(): String = serverIds.currentId.hideFirstChar()

    fun cachedServerIdsForDisplay(): List<String> = serverIds.cachedIds().map { it.hideFirstChar() }

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "settings" to mapOf(
            "nickHider" to nickHiderEnabled,
            "serverIdHider" to FloydHiders.serverIdHider,
            "profileIdHider" to FloydHiders.profileIdHider,
            "nickname" to nickname,
            "selfNameChroma" to selfNameChroma,
            "nameMappings" to nameMappings.toSortedMap(String.CASE_INSENSITIVE_ORDER),
            "mappingCount" to nameMappings.size
        ),
        "serverId" to mapOf(
            "current" to currentServerIdForDisplay(),
            "cached" to cachedServerIdsForDisplay(),
            "knownTabUuid" to knownTabUuid?.toString(),
            "rapidScan" to rapidScan,
            "lastScanSource" to lastScanSource.id,
            "lastScannedText" to lastScannedText.hideKnownServerIds(),
            "lastHitTick" to lastHitTick.takeIf { it >= 0L },
            "scanHits" to scanHits,
            "tabEntryCount" to tabEntryCount,
            "scoreboardLineCount" to scoreboardLineCount,
            "scoreboard" to mapOf(
                "selectedObjective" to scoreboardSelectedObjective,
                "sidebarObjective" to scoreboardSidebarObjective,
                "teamSlotObjective" to scoreboardTeamSlotObjective,
                "objectives" to scoreboardObjectiveNames,
                "trackedPlayerCount" to scoreboardTrackedPlayerCount
            )
        )
    )

    fun debugScanServerIdText(text: String, scoreboard: Boolean): Boolean {
        if (!FloydHiders.serverIdHider) return false
        return recordScan(
            source = if (scoreboard) ScanSource.DEBUG_SCOREBOARD else ScanSource.DEBUG_TEXT,
            text = text,
            tick = mc.level?.gameTime,
            hit = if (scoreboard) serverIds.scanScoreboardText(text) else serverIds.scanText(text)
        )
    }

    fun debugSummary(): String = buildString {
        val currentDisplay = currentServerIdForDisplay()
        val displayIds = cachedServerIdsForDisplay()
        appendLine("--- Server ID Hider Debug ---")
        appendLine("Enabled: ${FloydHiders.serverIdHider}")
        appendLine("Replacement: $SERVER_ID_REPLACEMENT")
        appendLine("Current server: ${currentDisplay.ifEmpty { "(none detected)" }}")
        appendLine(
            if (displayIds.isEmpty()) {
                "Accumulated IDs: (none - nothing will be hidden)"
            } else {
                "Accumulated IDs (${displayIds.size}): ${displayIds.joinToString(", ") { "\"$it\"" }}"
            }
        )
        appendLine("Known tab UUID: ${knownTabUuid?.toString()?.take(8)?.plus("...") ?: "(none)"}")
        appendLine("Rapid scan: ${if (rapidScan) "ACTIVE" else "inactive"}")
        appendLine("Last source: ${lastScanSource.id}")
        appendLine("Last scanned: ${lastScannedText.hideKnownServerIds().ifBlank { "(none)" }}")
        appendLine("Scan hits: $scanHits")
        appendLine("Last hit tick: ${lastHitTick.takeIf { it >= 0L } ?: "(none)"}")
        appendLine("Tab entries: $tabEntryCount")
        appendLine("Scoreboard lines: $scoreboardLineCount")
        appendLine("Scoreboard selected objective: ${scoreboardSelectedObjective ?: "(none)"}")
        appendLine("Scoreboard sidebar objective: ${scoreboardSidebarObjective ?: "(none)"}")
        appendLine("Scoreboard team-slot objective: ${scoreboardTeamSlotObjective ?: "(none)"}")
        appendLine("Scoreboard objectives: ${scoreboardObjectiveNames.ifEmpty { listOf("(none)") }.joinToString(", ")}")
        appendLine("Scoreboard tracked players: $scoreboardTrackedPlayerCount")
    }

    fun setSelfNickname(fakeName: String) {
        if (fakeName.isNotEmpty()) nickname = fakeName
    }

    fun nameMappingsSummary(): String = buildString {
        appendLine("--- Name Mappings ---")
        appendLine("Default nick: $nickname")
        if (nameMappings.isEmpty()) {
            append("No player mappings configured.")
        } else {
            append(nameMappings.entries.joinToString("\n") { "${it.key} → ${it.value}" })
        }
    }

    fun addNameMapping(realName: String, fakeName: String): Boolean {
        val added = nameMappings[realName] == null
        nameMappings[realName] = fakeName
        return added
    }

    fun removeNameMapping(realName: String): Boolean {
        val key = nameMappings.keys.firstOrNull { it.equals(realName, ignoreCase = true) } ?: return false
        nameMappings.remove(key)
        return true
    }

    fun clearNameMappings() {
        nameMappings.clear()
    }

    fun mappingIds(): Set<String> = nameMappings.keys.toSortedSet()

    private fun tickServerIdTracker() {
        if (!FloydHiders.serverIdHider) return
        val level = mc.level
        val player = mc.player
        if (level == null || player == null) {
            if (wasInWorld) {
                serverIds.clear()
                knownTabUuid = null
                lastPlayerRef = null
                rapidScan = false
                wasInWorld = false
                lastScanSource = ScanSource.DISCONNECT
                lastScannedText = ""
                lastHitTick = -1L
                scanHits = 0L
                tabEntryCount = 0
                scoreboardLineCount = 0
                scoreboardObjectiveNames = emptyList()
                scoreboardTrackedPlayerCount = 0
                scoreboardSelectedObjective = null
                scoreboardSidebarObjective = null
                scoreboardTeamSlotObjective = null
            }
            return
        }

        if (player !== lastPlayerRef) {
            lastPlayerRef = player
            if (wasInWorld) {
                rapidScan = true
                rapidScanStartTick = level.gameTime
                lastScanTick = -100L
            }
        }

        if (!wasInWorld) {
            wasInWorld = true
            rapidScan = true
            rapidScanStartTick = level.gameTime
            lastScanTick = -100L
        }

        val tick = level.gameTime
        if (rapidScan && tick - rapidScanStartTick >= 40L) rapidScan = false
        val interval = if (rapidScan) 1L else 20L
        if (tick - lastScanTick < interval) return
        lastScanTick = tick

        if (!scanKnownTabEntry()) {
            if (!scanTabList()) scanScoreboard()
        }
    }

    private fun scanKnownTabEntry(): Boolean {
        val uuid = knownTabUuid ?: return false
        val entry = mc.connection?.getPlayerInfo(uuid) ?: run {
            knownTabUuid = null
            return false
        }
        val text = entry.tabListDisplayName?.string ?: run {
            knownTabUuid = null
            return false
        }
        return recordScan(ScanSource.KNOWN_TAB, text, mc.level?.gameTime, serverIds.scanText(text)).also {
            if (!it) knownTabUuid = null
        }
    }

    private fun scanTabList(): Boolean {
        val entries = runCatching { mc.connection?.listedOnlinePlayers?.toList().orEmpty() }.getOrDefault(emptyList())
        tabEntryCount = entries.size
        var fallbackText = ""
        var fallbackUuid: UUID? = null
        for (entry in entries) {
            val text = entry.tabListDisplayName?.string ?: continue
            if (FloydServerIdAccumulator.SERVER_LINE_PATTERN.matcher(text).find()) {
                knownTabUuid = entry.profile.id
                return recordScan(ScanSource.TAB_LIST, text, mc.level?.gameTime, serverIds.scanText(text))
            }
            if (fallbackText.isEmpty() && FloydServerIdAccumulator.FULL_SERVER_ID_PATTERN.matcher(text).find()) {
                fallbackText = text
                fallbackUuid = entry.profile.id
            }
        }
        if (fallbackText.isNotEmpty()) {
            knownTabUuid = fallbackUuid
            return recordScan(ScanSource.TAB_LIST_FALLBACK, fallbackText, mc.level?.gameTime, serverIds.scanText(fallbackText))
        }
        lastScanSource = ScanSource.TAB_LIST_MISS
        lastScannedText = ""
        return false
    }

    private fun scanScoreboard(): Boolean {
        val scoreboard = mc.level?.scoreboard ?: return false
        val player = mc.player ?: return false
        scoreboardObjectiveNames = scoreboard.objectiveNames.sorted()
        scoreboardTrackedPlayerCount = scoreboard.trackedPlayers.size
        val teamObjective = scoreboard.getPlayersTeam(player.scoreboardName)?.color?.let(DisplaySlot::teamColorToSlot)?.let(scoreboard::getDisplayObjective)
        val sidebarObjective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
        scoreboardTeamSlotObjective = teamObjective?.name
        scoreboardSidebarObjective = sidebarObjective?.name
        val objective = teamObjective ?: sidebarObjective
        scoreboardSelectedObjective = objective?.name

        scoreboardLineCount = 0
        if (objective != null && scanScoreboardObjective(objective, ScanSource.SCOREBOARD_TITLE, ScanSource.SCOREBOARD_LINE)) return true

        return scoreboard.objectives.any { fallback ->
            if (fallback == objective) return@any false
            scoreboardSelectedObjective = fallback.name
            scanScoreboardObjective(fallback, ScanSource.SCOREBOARD_ANY_TITLE, ScanSource.SCOREBOARD_ANY_LINE)
        }
    }

    private fun scanScoreboardObjective(
        objective: net.minecraft.world.scores.Objective,
        titleSource: ScanSource,
        lineSource: ScanSource,
    ): Boolean {
        val scoreboard = mc.level?.scoreboard ?: return false
        val title = objective.displayName.string
        if (recordScan(titleSource, title, mc.level?.gameTime, serverIds.scanScoreboardText(title))) return true
        return scoreboard.listPlayerScores(objective).any { score ->
            val team = scoreboard.getPlayersTeam(score.owner)
            val line = if (team == null) score.owner else team.playerPrefix.string + score.owner + team.playerSuffix.string
            scoreboardLineCount += 1
            recordScan(lineSource, line, mc.level?.gameTime, serverIds.scanScoreboardText(line))
        }
    }

    private fun recordScan(source: ScanSource, text: String, tick: Long?, hit: Boolean): Boolean {
        lastScanSource = source
        lastScannedText = text
        if (hit) {
            scanHits += 1
            lastHitTick = tick ?: lastHitTick
        }
        return hit
    }

    private fun replaceIgnoreCase(input: String, find: String, replace: String): String =
        FloydServerIdAccumulator.replaceIgnoreCase(input, find, replace)

    internal fun replaceInSequenceForTest(
        text: FormattedCharSequence,
        find: String,
        replace: String,
    ): FormattedCharSequence {
        val styled = StyledText.from(text) ?: return text
        return if (styled.replaceIgnoreCase(find, replace)) styled.toSequence() else text
    }

    internal fun replaceDateServerIdInSequenceForTest(
        text: FormattedCharSequence,
        replace: String,
    ): FormattedCharSequence {
        val styled = StyledText.from(text) ?: return text
        return if (styled.replaceDateServerId(replace)) styled.toSequence() else text
    }

    internal fun replaceInComponentForTest(
        component: Component,
        find: String,
        replace: String,
    ): Component = replaceLiteralComponentIgnoreCase(component, find, replace).component

    private fun String.hideFirstChar(): String =
        if (length > 1) first() + "\u200B" + substring(1) else this

    private fun String.hideKnownServerIds(): String {
        var output = this
        for (id in serverIds.cachedIds()) {
            output = replaceIgnoreCase(output, id, id.hideFirstChar())
        }
        return output
    }

    private enum class ScanSource(val id: String) {
        NONE("none"),
        DISCONNECT("disconnect"),
        KNOWN_TAB("known_tab"),
        TAB_LIST("tab_list"),
        TAB_LIST_FALLBACK("tab_list_fallback"),
        TAB_LIST_MISS("tab_list_miss"),
        SCOREBOARD_TITLE("scoreboard_title"),
        SCOREBOARD_LINE("scoreboard_line"),
        SCOREBOARD_ANY_TITLE("scoreboard_any_title"),
        SCOREBOARD_ANY_LINE("scoreboard_any_line"),
        DEBUG_TEXT("debug_text"),
        DEBUG_SCOREBOARD("debug_scoreboard")
    }

    private const val SERVER_ID_REPLACEMENT = "fL0YD"
    private const val PROFILE_ID_REPLACEMENT = "Profile ID: [hidden]"
    private val PROFILE_ID_PATTERN = Pattern.compile(
        "(?i)profile\\s*id:\\s*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    )
    private val DATE_PATTERN: Pattern = Pattern.compile("\\d{2}/\\d{2}/\\d{2}")

    private fun replaceLiteralComponentIgnoreCase(component: Component, find: String, replace: String): ComponentReplacement {
        if (find.isEmpty() || !component.string.lowercase(Locale.ROOT).contains(find.lowercase(Locale.ROOT))) {
            return ComponentReplacement(component, false)
        }

        val result = Component.empty()
        var changed = false
        component.visit<Unit>(
            { style, content ->
                if (content.isNotEmpty()) {
                    val replaced = replaceIgnoreCase(content, find, replace)
                    if (replaced != content) changed = true
                    result.append(Component.literal(replaced).withStyle(style))
                }
                Optional.empty()
            },
            component.style
        )

        if (!changed) {
            val flat = replaceIgnoreCase(component.string, find, replace)
            if (flat != component.string) return ComponentReplacement(Component.literal(flat).withStyle(component.style), true)
        }
        return ComponentReplacement(if (changed) result else component, changed)
    }

    private fun replaceRegexComponent(component: Component, pattern: Pattern, replace: String): ComponentReplacement {
        if (!pattern.matcher(component.string).find()) return ComponentReplacement(component, false)

        val result = Component.empty()
        var changed = false
        component.visit<Unit>(
            { style, content ->
                if (content.isNotEmpty()) {
                    val replaced = pattern.matcher(content).replaceAll(replace)
                    if (replaced != content) changed = true
                    result.append(Component.literal(replaced).withStyle(style))
                }
                Optional.empty()
            },
            component.style
        )

        if (pattern.matcher(result.string).find()) {
            val flat = pattern.matcher(component.string).replaceAll(replace)
            return ComponentReplacement(Component.literal(flat).withStyle(component.style), flat != component.string)
        }
        return ComponentReplacement(if (changed) result else component, changed)
    }

    private fun replaceDateServerIdInComponent(component: Component, replace: String): ComponentReplacement {
        val text = component.string
        val match = DATE_PATTERN.matcher(text)
        if (!match.find()) return ComponentReplacement(component, false)
        var start = match.end()
        while (start < text.length && text[start].isWhitespace()) start++
        if (start >= text.length) return ComponentReplacement(component, false)
        var end = start
        while (end < text.length && !text[end].isWhitespace()) end++
        val candidate = text.substring(start, end)
        if (candidate.none(Char::isDigit)) return ComponentReplacement(component, false)
        val flat = text.substring(0, start) + replace + text.substring(end)
        return ComponentReplacement(Component.literal(flat).withStyle(component.style), flat != text)
    }

    private data class ComponentReplacement(val component: Component, val changed: Boolean)

    private class StyledText private constructor(
        private val codePoints: MutableList<Int>,
        private val styles: MutableList<Style>,
    ) {
        private fun string(): String = buildString {
            for (codePoint in codePoints) appendCodePoint(codePoint)
        }

        fun replaceIgnoreCase(find: String, replace: String, styleTransform: ((Int, Style) -> Style)? = null): Boolean {
            if (find.isEmpty()) return false
            val text = string()
            if (text.length != codePoints.size) return replaceWholeIfChanged(text.replace(Regex(Pattern.quote(find), RegexOption.IGNORE_CASE), replace))
            val lowerText = text.lowercase(Locale.ROOT)
            val lowerFind = find.lowercase(Locale.ROOT)
            if (!lowerText.contains(lowerFind)) return false

            val resultCodePoints = mutableListOf<Int>()
            val resultStyles = mutableListOf<Style>()
            var index = 0
            while (index < codePoints.size) {
                val hit = lowerText.indexOf(lowerFind, index)
                if (hit < 0) {
                    appendRange(index, codePoints.size, resultCodePoints, resultStyles)
                    break
                }
                appendRange(index, hit, resultCodePoints, resultStyles)
                appendReplacement(replace, styles.getOrElse(hit) { Style.EMPTY }, resultCodePoints, resultStyles, styleTransform)
                index = hit + find.length
            }
            replaceWith(resultCodePoints, resultStyles)
            return true
        }

        fun replaceRegex(pattern: Pattern, replace: String): Boolean {
            val text = string()
            val matcher = pattern.matcher(text)
            if (!matcher.find()) return false
            if (text.length != codePoints.size) return replaceWholeIfChanged(matcher.replaceAll(replace))

            val resultCodePoints = mutableListOf<Int>()
            val resultStyles = mutableListOf<Style>()
            matcher.reset()
            var index = 0
            while (matcher.find()) {
                appendRange(index, matcher.start(), resultCodePoints, resultStyles)
                appendReplacement(replace, styles.getOrElse(matcher.start()) { Style.EMPTY }, resultCodePoints, resultStyles)
                index = matcher.end()
            }
            appendRange(index, codePoints.size, resultCodePoints, resultStyles)
            replaceWith(resultCodePoints, resultStyles)
            return true
        }

        fun replaceDateServerId(replace: String): Boolean {
            val text = string()
            val match = DATE_PATTERN.matcher(text)
            if (!match.find()) return false
            var start = match.end()
            while (start < text.length && text[start].isWhitespace()) start++
            if (start >= text.length) return false
            var end = start
            while (end < text.length && !text[end].isWhitespace()) end++
            val candidate = text.substring(start, end)
            if (candidate.none(Char::isDigit)) return false

            val resultCodePoints = mutableListOf<Int>()
            val resultStyles = mutableListOf<Style>()
            appendRange(0, start, resultCodePoints, resultStyles)
            appendReplacement(replace, styles.getOrElse(start) { Style.EMPTY }, resultCodePoints, resultStyles)
            appendRange(end, codePoints.size, resultCodePoints, resultStyles)
            replaceWith(resultCodePoints, resultStyles)
            return true
        }

        fun toSequence(): FormattedCharSequence {
            val finalCodePoints = codePoints.toList()
            val finalStyles = styles.toList()
            return FormattedCharSequence { visitor ->
                for (index in finalCodePoints.indices) {
                    if (!visitor.accept(index, finalStyles[index], finalCodePoints[index])) return@FormattedCharSequence false
                }
                true
            }
        }

        private fun appendRange(
            start: Int,
            end: Int,
            targetCodePoints: MutableList<Int>,
            targetStyles: MutableList<Style>,
        ) {
            for (index in start until end) {
                targetCodePoints.add(codePoints[index])
                targetStyles.add(styles[index])
            }
        }

        private fun appendReplacement(
            replace: String,
            style: Style,
            targetCodePoints: MutableList<Int>,
            targetStyles: MutableList<Style>,
            styleTransform: ((Int, Style) -> Style)? = null,
        ) {
            var replacementIndex = 0
            replace.codePoints().forEach { codePoint ->
                targetCodePoints.add(codePoint)
                targetStyles.add(styleTransform?.invoke(replacementIndex, style) ?: style)
                replacementIndex++
            }
        }

        private fun replaceWith(newCodePoints: MutableList<Int>, newStyles: MutableList<Style>) {
            codePoints.clear()
            codePoints.addAll(newCodePoints)
            styles.clear()
            styles.addAll(newStyles)
        }

        private fun replaceWholeIfChanged(replaced: String): Boolean {
            if (replaced == string()) return false
            val style = styles.firstOrNull() ?: Style.EMPTY
            codePoints.clear()
            styles.clear()
            replaced.codePoints().forEach { codePoint ->
                codePoints.add(codePoint)
                styles.add(style)
            }
            return true
        }

        companion object {
            fun from(sequence: FormattedCharSequence): StyledText? {
                val codePoints = mutableListOf<Int>()
                val styles = mutableListOf<Style>()
                sequence.accept { _, style, codePoint ->
                    codePoints.add(codePoint)
                    styles.add(style)
                    true
                }
                if (codePoints.isEmpty()) return null
                return StyledText(codePoints, styles)
            }
        }
    }
}
