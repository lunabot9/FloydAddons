package gg.floyd.features.impl.render

import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.ColorSetting
import gg.floyd.clickgui.settings.impl.KeybindSetting
import gg.floyd.clickgui.settings.impl.MapSetting
import gg.floyd.clickgui.settings.impl.SearchableListSetting
import gg.floyd.clickgui.settings.impl.StringSetting
import gg.floyd.events.RenderEvent
import gg.floyd.events.TickEvent
import gg.floyd.events.core.on
import gg.floyd.clickgui.settings.AlwaysActive
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.utils.Color
import gg.floyd.utils.Colors
import gg.floyd.utils.render.drawTracer
import gg.floyd.utils.render.drawWireFrameBox
import gg.floyd.utils.render.drawText
import gg.floyd.utils.renderBoundingBox
import gg.floyd.utils.renderPos
import gg.floyd.utils.modMessage
import gg.floyd.utils.moduleToggle
import net.minecraft.resources.Identifier
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern
import org.lwjgl.glfw.GLFW

@AlwaysActive
object FloydMobEsp : Module(
    name = "Mob ESP",
    category = Category.RENDER,
    description = "Floyd mob ESP filters, tracers, hitboxes, star mob matching, and stalk rendering."
) {
    val tracers by BooleanSetting("Tracers", false, desc = "Draws mob ESP tracers.")
    val hitboxes by BooleanSetting("Hitboxes", false, desc = "Draws mob ESP hitboxes.")
    val starMobs by BooleanSetting("Star Mobs", false, desc = "Highlights starred mob labels and nearby mobs.")
    private val toggleKey by KeybindSetting("Toggle Mob ESP", GLFW.GLFW_KEY_UNKNOWN, desc = "Floyd Mob ESP toggle key.").onPress {
        toggle()
        if (ClickGUIModule.enableNotification) moduleToggle(name, enabled)
    }
    private val defaultColor by ColorSetting("Default ESP Color", Colors.ACCENT.copy(), desc = "Default color for Mob ESP tracers and hitboxes (toggle chroma inside the picker).")
    private val stalkColor by ColorSetting("Tracer Color", Colors.ACCENT.copy(), desc = "Color for the stalk tracer (toggle chroma inside the picker).")
    private var editorNameFilter by StringSetting("Name Filter", "shadow assassin", 96, desc = "Name filter edited by the name buttons below.")
    private var editorTypeFilter by StringSetting("Type Filter", "minecraft:zombie", 96, desc = "Entity type ID edited by the type buttons below.")
    private var editorColor by StringSetting("Filter Color", "55FF55", 8, desc = "Hex color applied by the color buttons below.")
    private val editorChroma by BooleanSetting("Filter Chroma", false, desc = "Uses chroma for the edited filter color.")
    private val addEditorName by ActionSetting("Add Name Filter", desc = "Adds the name filter above.") {
        val name = runCatching { validUserNameFilter(editorNameFilter) }.getOrElse {
            modMessage(it.message ?: "Invalid name filter.")
            return@ActionSetting
        }
        addNameFilter(name)
        ModuleManager.saveConfigurations()
        modMessage("Added mob ESP name filter: $name")
    }
    private val removeEditorName by ActionSetting("Remove Name Filter", desc = "Removes the name filter above.") {
        val name = runCatching { validUserNameFilter(editorNameFilter) }.getOrElse {
            modMessage(it.message ?: "Invalid name filter.")
            return@ActionSetting
        }
        val removed = removeNameFilter(name)
        ModuleManager.saveConfigurations()
        modMessage(if (removed) "Removed mob ESP name filter: $name" else "Name filter not found: $name")
    }
    private val colorEditorName by ActionSetting("Color Name Filter", desc = "Applies the color/chroma settings above to the name filter.") {
        updateEditorColor(isName = true)
    }
    private val addEditorType by ActionSetting("Add Type Filter", desc = "Adds the entity type filter above.") {
        val type = runCatching { validTypeFilterId(editorTypeFilter) }.getOrElse {
            modMessage(it.message ?: "Invalid entity type ID.")
            return@ActionSetting
        }
        addTypeFilter(type)
        ModuleManager.saveConfigurations()
        modMessage("Added mob ESP type filter: $type")
    }
    private val removeEditorType by ActionSetting("Remove Type Filter", desc = "Removes the entity type filter above.") {
        val type = runCatching { validTypeFilterId(editorTypeFilter) }.getOrElse {
            modMessage(it.message ?: "Invalid entity type ID.")
            return@ActionSetting
        }
        val removed = removeTypeFilter(type)
        ModuleManager.saveConfigurations()
        modMessage(if (removed) "Removed mob ESP type filter: $type" else "Type filter not found: $type")
    }
    private val colorEditorType by ActionSetting("Color Type Filter", desc = "Applies the color/chroma settings above to the type filter.") {
        updateEditorColor(isName = false)
    }
    private val listEditorFilters by ActionSetting("List Filters", desc = "Prints Mob ESP filters and colors in chat.") {
        modMessage(filterListSummary())
    }
    private val clearEditorFilters by ActionSetting("Clear Filters", desc = "Clears Mob ESP name/type filters and colors.") {
        clearFilters()
        ModuleManager.saveConfigurations()
        modMessage("Cleared all mob ESP filters.")
    }
    private val addLookedAtName by ActionSetting("Add Looked At Name", desc = "Adds a name filter for the entity under the crosshair.") {
        val target = lookedAtFilterTarget() ?: return@ActionSetting modMessage("Look at a non-player entity first.")
        val name = suggestedName(target) ?: return@ActionSetting modMessage("Looked-at entity has no usable name.")
        addNameFilter(name)
        ModuleManager.saveConfigurations()
        modMessage("Added mob ESP name filter: $name")
    }
    private val addLookedAtType by ActionSetting("Add Looked At Type", desc = "Adds a type filter for the entity under the crosshair.") {
        val target = lookedAtFilterTarget() ?: return@ActionSetting modMessage("Look at a non-player entity first.")
        val type = entityTypeId(target)
        addTypeFilter(type)
        ModuleManager.saveConfigurations()
        modMessage("Added mob ESP type filter: $type")
    }
    private val typeList by SearchableListSetting(
        "All Mob Types",
        optionsProvider = { typeListOptions() },
        selectedProvider = { typeFilterIds() },
        onToggle = { id ->
            if (id in typeFilterIds()) removeTypeFilter(id) else addTypeFilter(id)
            ModuleManager.saveConfigurations()
        },
        desc = "Search nearby + all vanilla mob types; click to toggle a type filter."
    )
    private val nameFilters by MapSetting("Name Filters", mutableMapOf<String, Boolean>()).hide()
    private val typeFilters by MapSetting("Type Filters", mutableMapOf<String, Boolean>()).hide()
    private val nameFilterColors by MapSetting("Name Filter Colors", mutableMapOf<String, String>()).hide()
    private val typeFilterColors by MapSetting("Type Filter Colors", mutableMapOf<String, String>()).hide()
    private val rawEntries = mutableListOf<MutableMap<String, String>>()
    private val allEntityTypeIds by lazy { BuiltInRegistries.ENTITY_TYPE.keySet().map { it.toString() }.sorted() }
    // Cache the nearby-entity scan + full registry for the "All Mob Types" list so it isn't rebuilt (with an entity walk) every frame.
    private var cachedTypeOptions: List<String> = emptyList()
    private var cachedTypeOptionsMs = 0L
    private fun typeListOptions(): List<String> {
        val now = System.currentTimeMillis()
        if (cachedTypeOptions.isEmpty() || now - cachedTypeOptionsMs > 500L) {
            cachedTypeOptions = (nearbyTypeSuggestions() + allEntityTypeIds).distinct()
            cachedTypeOptionsMs = now
        }
        return cachedTypeOptions
    }

    private val armorStandToMob = mutableMapOf<Int, Int>()
    private val matchedArmorStandIds = mutableSetOf<Int>()
    private val resolvedMobIds = mutableSetOf<Int>()
    private val resolvedMobNames = mutableMapOf<Int, String>()
    private val renderTargetIds = mutableSetOf<Int>()
    private val npcNameCache = mutableMapOf<Int, String>()
    private val npcArmorStandIds = mutableSetOf<Int>()
    @Volatile private var tabListNames: Set<String> = emptySet()
    private var lastTabListRefreshMs = 0L
    private var scanCooldown = 0
    private var stalkTarget = ""
    private val tracerRenderHits = AtomicLong()
    private val hitboxRenderHits = AtomicLong()
    private val matchedRenderHits = AtomicLong()
    private val stalkRenderHits = AtomicLong()
    private var debugLabelsExpireMs = 0L

    init {
        on<TickEvent.ClientEnd> {
            if (enabled && hasFilters() && (mc.level == null || mc.player == null)) reloadRuntimeCaches()
        }

        on<TickEvent.End> {
            if (enabled && hasFilters()) scanNpcNames()
        }

        on<RenderEvent.Extract> {
            val level = mc.level ?: return@on
            val player = mc.player ?: return@on
            if (player.isRemoved) return@on

            drawStalkTracer()

            if (!enabled) return@on

            if (++scanCooldown >= 10) {
                scanCooldown = 0
                scanTargets()
            }

            if (debugLabelsActive()) drawDebugLabels()

            for (entity in renderTargets(level)) {
                if (entity === player) continue
                matchedRenderHits.incrementAndGet()
                val color = colorFor(entity)
                if (tracers) {
                    tracerRenderHits.incrementAndGet()
                    drawTracer(entity.renderPos.add(0.0, entity.bbHeight / 2.0, 0.0), color, depth = false, thickness = 2f)
                }
                if (hitboxes) {
                    hitboxRenderHits.incrementAndGet()
                    drawWireFrameBox(entity.renderBoundingBox, color, thickness = 2f, depth = false)
                }
            }
        }
    }

    private fun scanTargets() {
        val level = mc.level ?: return clearResolvedTargets()
        if (!hasFilters()) return clearResolvedTargets()

        armorStandToMob.entries.removeIf { (_, mobId) ->
            if (level.getEntity(mobId) == null) {
                resolvedMobIds.remove(mobId)
                resolvedMobNames.remove(mobId)
                renderTargetIds.remove(mobId)
                true
            } else {
                false
            }
        }

        val armorStands = level.entitiesForRendering()
            .filterIsInstance<ArmorStand>()
            .filter { isStarMobLabel(it) || matchesNameFilter(it) }
            .toList()

        val entities = level.entitiesForRendering().filterIsInstance<LivingEntity>().filterNot { it is ArmorStand }.toList()
        for (stand in armorStands) {
            if (armorStandToMob.containsKey(stand.id)) continue
            val nearest = entities
                .filter { it !== mc.player && !isRealPlayer(it) && isNearArmorStandLabel(it, stand) }
                .minByOrNull { it.distanceToSqr(stand) }
            if (nearest != null) {
                armorStandToMob[stand.id] = nearest.id
                resolvedMobIds.add(nearest.id)
                resolvedMobNames[nearest.id] = strippedName(stand).lowercase(Locale.ROOT)
            }
        }

        matchedArmorStandIds.clear()
        matchedArmorStandIds.addAll(armorStandToMob.keys)

        renderTargetIds.clear()
        for (entity in level.entitiesForRendering()) {
            if (entity === mc.player) continue
            if (matches(entity)) renderTargetIds.add(entity.id)
        }
    }

    private fun hasFilters(): Boolean =
        activeNameFilters().isNotEmpty() || activeTypeFilters().isNotEmpty() || starMobs

    private fun renderTargets(level: net.minecraft.client.multiplayer.ClientLevel): List<Entity> {
        if (renderTargetIds.isEmpty()) return emptyList()
        val targets = renderTargetIds.mapNotNull(level::getEntity).filterNot(Entity::isRemoved)
        renderTargetIds.retainAll(targets.mapTo(mutableSetOf()) { it.id })
        return targets
    }

    private fun matches(entity: Entity): Boolean {
        if (isRealPlayer(entity)) return false
        if (resolvedMobIds.contains(entity.id)) return true
        if (entity is ArmorStand) return false
        if (entity is Player && starMobs && playerMobNames.contains(strippedName(entity).lowercase(Locale.ROOT))) return true
        return matchesNameFilter(entity) || matchesTypeFilter(entity)
    }

    private fun matchesNameFilter(entity: Entity): Boolean {
        val names = entityNames(entity)
        return activeNameFilters().any { filter -> names.any { it.contains(filter) } }
    }

    private fun matchesTypeFilter(entity: Entity): Boolean {
        val id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString().lowercase(Locale.ROOT)
        return activeTypeFilters().contains(id)
    }

    private fun isStarMobLabel(entity: Entity): Boolean =
        starMobs && (isStarMobLabelText(entity.displayName.string) || entity.customName?.string?.let(::isStarMobLabelText) == true)

    internal fun isStarMobLabelText(text: String): Boolean {
        val stripped = stripFormatting(text).lowercase(Locale.ROOT)
        return hasStar(stripped) || knownMinibossNames.any { stripped.contains(it) }
    }

    private fun hasStar(text: String): Boolean = text.contains(STAR_MOB_MARKER)

    private fun activeNameFilters(): Set<String> =
        nameFilters.filterValues { it }.keys.map { it.lowercase(Locale.ROOT) }.toSet()

    private fun findNameFilterKey(name: String): String? =
        nameFilters.keys.firstOrNull { it.equals(name, ignoreCase = true) }

    private fun nameFilterColorKey(name: String): String =
        name.lowercase(Locale.ROOT)

    private fun activeTypeFilters(): Set<String> =
        typeFilters.filterValues { it }.keys.map { it.lowercase(Locale.ROOT) }.toSet()

    fun nameFilterIds(): Set<String> =
        nameFilters.filterValues { it }.keys.toSortedSet(String.CASE_INSENSITIVE_ORDER)

    fun typeFilterIds(): Set<String> = activeTypeFilters().toSortedSet()

    fun nearbyNameSuggestions(): List<String> {
        val suggestions = nearbySuggestions()["names"]
        return (suggestions as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    }

    fun nearbyTypeSuggestions(): List<String> {
        val suggestions = nearbySuggestions()["types"]
        return (suggestions as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    }

    fun addNameFilter(name: String): Boolean {
        rawEntries.add(linkedMapOf("name" to name))
        reparseRawEntries()
        return true
    }

    fun validUserNameFilter(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("Name filter cannot be blank.")
        return trimmed
    }

    fun removeNameFilter(name: String): Boolean {
        val removed = rawEntries.removeIf { entry ->
            entry.containsKey("name") && entry["name"]?.equals(name, ignoreCase = true) == true
        }
        if (removed) reparseRawEntries()
        return removed
    }

    fun addTypeFilter(id: String): Boolean {
        rawEntries.add(linkedMapOf("mob" to id.lowercase(Locale.ROOT)))
        reparseRawEntries()
        return true
    }

    fun validTypeFilterId(id: String): String {
        val normalized = id.lowercase(Locale.ROOT)
        return Identifier.tryParse(normalized)?.toString()
            ?: throw IllegalArgumentException("Invalid entity type ID: $id")
    }

    fun removeTypeFilter(id: String): Boolean {
        val key = id.lowercase(Locale.ROOT)
        val removed = rawEntries.removeIf { entry ->
            entry.containsKey("mob") && entry["mob"]?.equals(key, ignoreCase = true) == true
        }
        if (removed) reparseRawEntries()
        return removed
    }

    fun removeUserTypeFilter(id: String): Boolean =
        removeTypeFilter(validTypeFilterId(id))

    fun clearFilters() {
        rawEntries.clear()
        nameFilters.clear()
        typeFilters.clear()
        nameFilterColors.clear()
        typeFilterColors.clear()
        clearResolvedTargets()
        resolvedMobNames.clear()
        npcNameCache.clear()
        npcArmorStandIds.clear()
    }

    private fun colorFor(entity: Entity): Color {
        val colorData = filterColorFor(entity)
        if (colorData != null) return colorData.toColor()

        return defaultColor
    }

    fun setNameFilterColor(name: String, hexColor: String, chroma: Boolean): Boolean {
        val key = findNameFilterKey(name) ?: return false
        if (nameFilters[key] != true) return false
        val color = parseHexColor(hexColor) ?: throw IllegalArgumentException("Expected hex color like #55FF55 or 55FF55.")
        for (entry in rawEntries) {
            val value = entry["name"] ?: entry["mob"]
            if (value != null && value.equals(name, ignoreCase = true)) {
                entry["color"] = "#$color"
                entry["chroma"] = chroma.toString()
                break
            }
        }
        reparseRawEntries()
        return true
    }

    fun setTypeFilterColor(id: String, hexColor: String, chroma: Boolean): Boolean {
        val key = id.lowercase(Locale.ROOT)
        if (typeFilters[key] != true) return false
        val color = parseHexColor(hexColor) ?: throw IllegalArgumentException("Expected hex color like #55FF55 or 55FF55.")
        for (entry in rawEntries) {
            val value = entry["mob"] ?: entry["name"]
            if (value != null && value.equals(key, ignoreCase = true)) {
                entry["color"] = "#$color"
                entry["chroma"] = chroma.toString()
                break
            }
        }
        reparseRawEntries()
        return true
    }

    fun setUserTypeFilterColor(id: String, hexColor: String, chroma: Boolean): Boolean =
        setTypeFilterColor(validTypeFilterId(id), hexColor, chroma)

    fun colorSummaryForName(name: String): String = colorSummary(nameFilterColors[nameFilterColorKey(name)])

    fun colorSummaryForType(id: String): String = colorSummary(typeFilterColors[id.lowercase(Locale.ROOT)])

    fun filterListSummary(): String {
        val starMobsState = if (starMobs) "ON" else "OFF"
        return buildString {
            appendLine("--- Mob ESP Filters ---")
            if (nameFilterIds().isEmpty() && typeFilterIds().isEmpty()) {
                appendLine("No filters configured.")
            } else {
                for (name in nameFilterIds()) appendLine("name: $name")
                for (type in typeFilterIds()) appendLine("type: $type")
            }
            append("Star mobs: $starMobsState")
        }
    }

    fun toggleSummary(): String =
        if (enabled) {
            "Mob ESP enabled (${nameFilterIds().size} names, ${typeFilterIds().size} types)"
        } else {
            "Mob ESP disabled"
        }

    fun stalk(name: String) {
        stalkTarget = name.trim()
    }

    fun stopStalk(): String? {
        val previous = stalkTarget.takeIf { it.isNotBlank() }
        stalkTarget = ""
        return previous
    }

    fun stalkEnabled(): Boolean = stalkTarget.isNotBlank()

    fun stalkTarget(): String = stalkTarget

    fun reloadRuntimeCaches() {
        clearResolvedTargets()
        npcNameCache.clear()
        npcArmorStandIds.clear()
        scanCooldown = 0
    }

    private fun clearResolvedTargets() {
        armorStandToMob.clear()
        matchedArmorStandIds.clear()
        resolvedMobIds.clear()
        resolvedMobNames.clear()
        renderTargetIds.clear()
    }

    fun state(): Map<String, Any?> {
        val level = mc.level
        val player = mc.player
        val matchedNearby = if (level != null && player != null) {
            level.entitiesForRendering()
                .filter { it !== player && it.distanceToSqr(player) <= 2500.0 && matches(it) }
                .map(::describeEntityMatch)
                .take(24)
                .toList()
        } else emptyList()

        return mapOf(
            "enabled" to enabled,
            "tracers" to tracers,
            "hitboxes" to hitboxes,
            "starMobs" to starMobs,
            "nameFilters" to nameFilterIds().toList(),
            "typeFilters" to typeFilterIds().toList(),
            "filterColors" to mapOf(
                "names" to nameFilterIds().associateWith(::colorSummaryForName),
                "types" to typeFilterIds().associateWith(::colorSummaryForType)
            ),
            "nearbySuggestions" to nearbySuggestions(),
            "resolvedMobCount" to resolvedMobIds.size,
            "matchedArmorStandCount" to matchedArmorStandIds.size,
            "npcCacheCount" to npcNameCache.size,
            "debugLabelsActive" to debugLabelsActive(),
            "stalkEnabled" to stalkEnabled(),
            "stalkTarget" to stalkTarget,
            "settings" to mapOf(
                "defaultChroma" to defaultColor.chroma,
                "stalkChroma" to stalkColor.chroma
            ),
            "nearbyMatched" to matchedNearby,
            "renderHits" to mapOf(
                "matched" to matchedRenderHits.get(),
                "tracers" to tracerRenderHits.get(),
                "hitboxes" to hitboxRenderHits.get(),
                "stalk" to stalkRenderHits.get()
            )
        )
    }

    fun loadSidecarEntries(entries: List<Map<String, String>>) {
        rawEntries.clear()
        rawEntries.addAll(entries.map { LinkedHashMap(it) })
        reparseRawEntries()
    }

    fun sidecarEntries(): List<Map<String, String>> =
        rawEntries.map { LinkedHashMap(it) }

    private fun reparseRawEntries() {
        nameFilters.clear()
        typeFilters.clear()
        nameFilterColors.clear()
        typeFilterColors.clear()

        for (entry in rawEntries) {
            entry["name"]?.let { name ->
                nameFilters[name] = true
                entry["color"]?.let { color ->
                    nameFilterColors[nameFilterColorKey(name)] = sidecarEncodedColor(color, "true".equals(entry["chroma"], ignoreCase = true))
                }
            }
            entry["mob"]?.let { type ->
                val normalized = type.lowercase(Locale.ROOT)
                typeFilters[normalized] = true
                entry["color"]?.let { color ->
                    typeFilterColors[normalized] = sidecarEncodedColor(color, "true".equals(entry["chroma"], ignoreCase = true))
                }
            }
        }
    }

    fun debugSummary(): String {
        val level = runCatching { mc.level }.getOrNull()
        val player = runCatching { mc.player }.getOrNull()
        val nearby = if (level != null && player != null) {
            level.entitiesForRendering().count { it !== player && it.distanceToSqr(player) <= 2500.0 }
        } else 0
        return buildString {
            appendLine("--- Mob ESP Debug --- (in-world labels for 10s)")
            appendLine("Enabled: $enabled HasFilters: ${hasFilters()}")
            appendLine("Names: ${nameFilterIds()} Types: ${typeFilterIds()}")
            appendLine("Resolved mobs: ${resolvedMobIds.size}")
            appendLine("NPC cache: ${npcNameCache.size}")
            append("Nearby entities <=50 blocks: $nearby")
        }
    }

    fun reloadSummary(): String = "Mob ESP config reloaded"

    fun enableDebugLabels(nowMillis: Long = System.currentTimeMillis(), durationMs: Long = 10_000L) {
        debugLabelsExpireMs = nowMillis + durationMs
    }

    fun debugLabelsActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val active = debugLabelsExpireMs > nowMillis
        if (!active) debugLabelsExpireMs = 0L
        return active
    }

    fun knownMinibossNameIds(): Set<String> = knownMinibossNames

    fun cachedNpcNameFor(entity: Entity): String? = npcNameCache[entity.id]

    private fun updateEditorColor(isName: Boolean) {
        val result = runCatching {
            if (isName) setNameFilterColor(editorNameFilter, editorColor, editorChroma)
            else setUserTypeFilterColor(editorTypeFilter, editorColor, editorChroma)
        }
        val updated = result.getOrElse {
            modMessage(it.message ?: "Invalid mob ESP color.")
            return
        }
        if (!updated) {
            modMessage(if (isName) "Name filter not found: $editorNameFilter" else "Type filter not found: $editorTypeFilter")
            return
        }
        ModuleManager.saveConfigurations()
        val target = if (isName) editorNameFilter else editorTypeFilter
        val color = if (isName) colorSummaryForName(target) else colorSummaryForType(target)
        modMessage("Updated mob ESP filter color: $target -> $color")
    }

    private fun RenderEvent.Extract.drawStalkTracer() {
        val targetName = stalkTarget.takeIf { it.isNotBlank() } ?: return
        val target = mc.level?.players()?.firstOrNull { it.name.string.equals(targetName, ignoreCase = true) } ?: return
        stalkRenderHits.incrementAndGet()
        drawTracer(target.renderPos.add(0.0, target.bbHeight / 2.0, 0.0), stalkColor, depth = false, thickness = 2f)
    }

    private fun RenderEvent.Extract.drawDebugLabels() {
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (activeNameFilters().isEmpty() && activeTypeFilters().isEmpty()) return

        for (entity in level.entitiesForRendering()) {
            if (entity === player || entity.distanceToSqr(player) > 2500.0) continue
            val label = debugLabelFor(entity)
            val distance = kotlin.math.sqrt(entity.distanceToSqr(player)).toFloat()
            drawText(label, entity.renderPos.add(0.0, entity.bbHeight + 0.5, 0.0), kotlin.math.max(1.0f, distance / 10f), false)
        }
    }

    private fun describeEntityMatch(entity: Entity): Map<String, Any?> {
        val typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString().lowercase(Locale.ROOT)
        val names = entityNames(entity)
        return mapOf(
            "id" to entity.id,
            "type" to typeId,
            "name" to entity.name.string,
            "displayName" to entity.displayName.string,
            "matchedByType" to activeTypeFilters().contains(typeId),
            "matchedByName" to activeNameFilters().any { filter -> names.any { it.contains(filter) } },
            "resolvedName" to resolvedMobNames[entity.id]
        )
    }

    private fun debugLabelFor(entity: Entity): String {
        val typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString().lowercase(Locale.ROOT)
        return buildString {
            append(typeId)
            append(" | ")
            append(stripFormatting(entity.name.string))
            npcNameCache[entity.id]?.let { append(" | npc=").append(it) }
            if (entity is ArmorStand && matchedArmorStandIds.contains(entity.id)) append(" AS-PAIRED")
            resolvedMobNames[entity.id]?.let { append(" | from=").append(it) }
            if (matches(entity)) append(" MATCH")
        }
    }

    private fun nearbySuggestions(): Map<String, Any?> {
        val level = mc.level
        val player = mc.player
        if (level == null || player == null) return mapOf(
            "names" to emptyList<String>(),
            "types" to emptyList<String>(),
            "lookedAt" to null
        )

        val activeNames = activeNameFilters()
        val activeTypes = activeTypeFilters()
        val names = linkedSetOf<String>()
        val types = linkedSetOf<String>()
        for (entity in level.entitiesForRendering()) {
            if (entity === player || isRealPlayer(entity)) continue
            suggestedName(entity)?.lowercase(Locale.ROOT)?.takeIf { it !in activeNames }?.let(names::add)
            entityTypeId(entity).takeIf { it !in activeTypes }?.let(types::add)
        }

        return mapOf(
            "names" to names.sorted().take(32),
            "types" to types.sorted().take(32),
            "lookedAt" to lookedAtFilterTarget()?.let {
                mapOf(
                    "id" to it.id,
                    "name" to (suggestedName(it) ?: it.name.string),
                    "type" to entityTypeId(it)
                )
            }
        )
    }

    private fun lookedAtFilterTarget(): Entity? {
        val hit = mc.hitResult as? EntityHitResult ?: return null
        val entity = hit.entity
        return entity.takeUnless { it === mc.player || isRealPlayer(it) }
    }

    private fun suggestedName(entity: Entity): String? =
        listOfNotNull(
            entity.customName?.string,
            entity.displayName.string,
            entity.name.string,
            npcNameCache[entity.id]
        )
            .map { stripFormatting(it).trim() }
            .firstOrNull { it.isNotBlank() }

    private fun entityTypeId(entity: Entity): String =
        BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString().lowercase(Locale.ROOT)

    private fun filterColorFor(entity: Entity): FilterColor? {
        val resolvedName = resolvedMobNames[entity.id]
        if (resolvedName != null) {
            for (filter in activeNameFilters()) {
                if (resolvedName.contains(filter)) return nameFilterColors[filter]?.let(::decodeColor)
            }
        }

        val entityNames = entityNames(entity)
        for (filter in activeNameFilters()) {
            if (entityNames.any { it.contains(filter) }) return nameFilterColors[filter]?.let(::decodeColor)
        }

        val typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString().lowercase(Locale.ROOT)
        return typeFilterColors[typeId]?.let(::decodeColor)
    }

    private fun entityNames(entity: Entity): List<String> =
        listOfNotNull(entity.displayName.string, entity.customName?.string, npcNameCache[entity.id])
            .map { stripFormatting(it).lowercase(Locale.ROOT) }

    private fun scanNpcNames() {
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (level.gameTime % 10L != 0L) return

        val foundNpcArmorStands = mutableSetOf<Int>()
        for (armorStand in level.entitiesForRendering().filterIsInstance<ArmorStand>()) {
            val name = armorStand.customName?.string?.let(::stripFormatting)?.trim()?.lowercase(Locale.ROOT) ?: continue
            if (name.isEmpty() || ignoredNpcLabels.contains(name) || name.contains("❤") || name.startsWith("[lv")) continue

            val nearestNpc = level.players()
                .asSequence()
                .filter { it !== player && looksLikeNpcName(it.name.string) }
                .filter { isNearNpcLabel(it, armorStand) }
                .minByOrNull { horizontalDistanceSqr(it, armorStand) }
                ?: continue

            npcNameCache[nearestNpc.id] = name
            foundNpcArmorStands.add(armorStand.id)
            markNearbyNpcLabels(armorStand, foundNpcArmorStands)
        }

        npcArmorStandIds.clear()
        npcArmorStandIds.addAll(foundNpcArmorStands)
        npcNameCache.entries.removeIf { (id, _) -> level.getEntity(id) == null }
    }

    private fun markNearbyNpcLabels(origin: ArmorStand, ids: MutableSet<Int>) {
        val level = mc.level ?: return
        for (stand in level.entitiesForRendering().filterIsInstance<ArmorStand>()) {
            if (horizontalDistanceSqr(stand, origin) <= 4.0 && kotlin.math.abs(stand.y - origin.y) <= 4.0) {
                ids.add(stand.id)
            }
        }
    }

    private fun isNearNpcLabel(player: Player, armorStand: ArmorStand): Boolean {
        val dy = player.y - armorStand.y
        return dy <= 1.0 && dy >= -4.0 && horizontalDistanceSqr(player, armorStand) <= 4.0
    }

    private fun horizontalDistanceSqr(first: Entity, second: Entity): Double {
        val dx = first.x - second.x
        val dz = first.z - second.z
        return dx * dx + dz * dz
    }

    private fun looksLikeNpcName(name: String): Boolean = npcNamePattern.matcher(name).matches()

    private fun isNearArmorStandLabel(entity: Entity, armorStand: ArmorStand): Boolean {
        val dx = entity.x - armorStand.x
        val dz = entity.z - armorStand.z
        if (dx * dx + dz * dz > 2.25) return false
        val dy = entity.y - armorStand.y
        return dy <= 1.0 && dy >= -5.0
    }

    private fun isRealPlayer(entity: Entity): Boolean {
        refreshTabListNames()
        val name = stripFormatting(entity.name.string).lowercase(Locale.ROOT)
        return tabListNames.contains(name)
    }

    private fun refreshTabListNames(nowMillis: Long = System.currentTimeMillis()) {
        if (nowMillis - lastTabListRefreshMs < TAB_LIST_CACHE_MS) return
        lastTabListRefreshMs = nowMillis
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

    private fun strippedName(entity: Entity): String = stripFormatting(entity.displayName.string)

    private fun chromaColor(): Color {
        val hue = (System.currentTimeMillis() % 4000L) / 4000.0f
        return Color(java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f))
    }

    private fun encodeColor(hexColor: String, chroma: Boolean): String {
        val normalized = parseHexColor(hexColor) ?: throw IllegalArgumentException("Expected hex color like #55FF55 or 55FF55.")
        return "$normalized|$chroma"
    }

    private fun decodeColor(encoded: String): FilterColor? {
        val parts = encoded.split("|", limit = 2)
        val color = parseHexColor(parts.getOrNull(0) ?: return null) ?: return null
        val chroma = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: false
        return FilterColor(color, chroma)
    }

    private fun sidecarEncodedColor(hexColor: String, chroma: Boolean): String =
        "${parseHexColor(hexColor) ?: "FFFFFF"}|$chroma"

    private fun parseHexColor(raw: String): String? {
        val hex = raw.trim().removePrefix("#")
        if (!Regex("[0-9a-fA-F]{6}").matches(hex)) return null
        return hex.uppercase(Locale.ROOT)
    }

    private fun colorSummary(encoded: String?): String {
        val color = encoded?.let(::decodeColor) ?: return "default"
        return "#${color.hex}${if (color.chroma) " chroma" else ""}"
    }

    private fun sidecarEntry(key: String, value: String, encodedColor: String?): Map<String, String> {
        val color = encodedColor?.let(::decodeColor)
        return buildMap {
            put(key, value)
            if (color != null) {
                put("color", "#${color.hex}")
                put("chroma", color.chroma.toString())
            }
        }
    }

    private fun FilterColor.toColor(): Color {
        if (chroma) return chromaColor()
        return Color(hex + "FF")
    }

    private data class FilterColor(val hex: String, val chroma: Boolean)

    private fun stripFormatting(text: String): String = text.replace(Regex("§."), "")

    private val playerMobNames = setOf(
        "shadow assassin",
        "lost adventurer",
        "diamond guy",
        "king midas"
    )

    private val ignoredNpcLabels = setOf("click", "armor stand")
    private val npcNamePattern: Pattern = Pattern.compile("^[a-z0-9]{8,12}$")
    private val playerNamePattern: Pattern = Pattern.compile("[a-zA-Z0-9_]{3,16}")
    private const val TAB_LIST_CACHE_MS = 1_000L
    private const val STAR_MOB_MARKER = "✯"

    private val knownMinibossNames: Set<String> = setOf(
        "shadow assassin",
        "lost adventurer",
        "frozen adventurer",
        "king midas",
        "angry archaeologist"
    )
}
