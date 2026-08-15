package gg.floyd.features.impl.render

import gg.floyd.clickgui.HudSizeRegistry
import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.events.ChatPacketEvent
import gg.floyd.events.TickEvent
import gg.floyd.events.WorldEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.features.impl.render.FloydSkyBlockItemFallbacks.customData
import gg.floyd.features.impl.render.FloydSkyBlockItemFallbacks.skyBlockId
import gg.floyd.utils.render.HudPanel
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.*
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam
import kotlin.math.max

/**
 * A movable, sequential fresh-profile guide for reaching SkyBlock Level 7.
 *
 * Detection is intentionally passive: it reads chat, menus, item metadata, equipment, nearby NPC
 * labels and scoreboard state, and never clicks, sends commands, or moves the player. One step is
 * advanced per client tick so a single broad signal cannot skip a section of the route.
 */
object FloydSkyBlockLevel7Guide : Module(
    name = "SkyBlock LVL 7 Guide",
    category = Category.RENDER,
    description = "Gives exact manual instructions and passively advances the 119-step fresh-profile SkyBlock Level 7 route.",
    toggled = false,
) {
    private const val HUD_NAME = "SkyBlock LVL 7 Guide HUD"
    private const val MAX_STEP = 119
    private const val COMPLETE_STEP = MAX_STEP + 1
    private const val PANEL_WIDTH = 340
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
    private const val MUTED_COLOR = 0xFFADB5C2.toInt()
    private const val SUCCESS_COLOR = 0xFF55FF55.toInt()
    private const val CHAT_SIGNAL_MS = 4_000L

    private var currentStep by NumberSetting(
        "Saved Guide Step",
        1,
        1,
        COMPLETE_STEP,
        1,
        desc = "Persisted route position. Use Previous, Next, or Reset to change it.",
    ).hide()

    @Suppress("unused")
    private val previousStep by ActionSetting("Previous Step", desc = "Moves the guide back one step.") {
        setStep((currentStep - 1).coerceAtLeast(1))
    }

    @Suppress("unused")
    private val nextStep by ActionSetting("Next Step", desc = "Manually completes the current step and moves forward.") {
        setStep((currentStep + 1).coerceAtMost(COMPLETE_STEP))
    }

    @Suppress("unused")
    private val resetGuide by ActionSetting("Reset Guide", desc = "Returns the guide to step 1.") {
        setStep(1)
    }

    private val guideHud by HUD(
        HUD_NAME,
        "Move and resize the SkyBlock Level 7 guide.",
        false,
        18,
        210,
        1f,
    ) { example -> drawGuide(example) }

    private val knownSkillLevels = mutableMapOf<String, Int>()
    private var baseline = SkyBlockGuideBaseline()
    private var baselineStep = -1
    private var stepStartedAtMs = System.currentTimeMillis()
    private var lastChat = ""
    private var lastChatAtMs = 0L
    private var lastPlayerPosition: Triple<Double, Double, Double>? = null
    private var lastLevelIdentity: Any? = null
    private var teleportedUntilMs = 0L
    private var tickDivider = 0
    private var lastSkyBlock = false
    private var lastAdvanceDetection: String? = null

    init {
        HudSizeRegistry.register(HUD_NAME) { PANEL_WIDTH to panelHeight(currentStep) }

        on<ChatPacketEvent> {
            lastChat = value
            lastChatAtMs = System.currentTimeMillis()
            rememberSkill(value)
        }

        on<WorldEvent.Load> {
            teleportedUntilMs = System.currentTimeMillis() + 3_000L
            lastPlayerPosition = null
            lastLevelIdentity = null
        }
        on<WorldEvent.Unload> {
            lastChat = ""
            lastChatAtMs = 0L
            lastPlayerPosition = null
            lastLevelIdentity = null
            lastSkyBlock = false
        }

        on<TickEvent.ClientEnd> {
            if (!enabled) return@on
            updateTeleportSignal()
            if (++tickDivider < 2) return@on
            tickDivider = 0
            if (currentStep in 47..50) TreecapitatorLowestBinPrice.refreshIfNeeded()

            syncBaselineToStep()
            val observation = observation()
            lastSkyBlock = isSkyBlock(observation.scoreboard)
            rememberSkills(observation)
            if (!lastSkyBlock || currentStep !in 1..MAX_STEP) return@on
            if (SkyBlockLevel7GuideRoute.isComplete(currentStep, observation, baseline)) {
                lastAdvanceDetection = SkyBlockLevel7GuideRoute.step(currentStep)?.detection
                setStep(currentStep + 1)
            }
        }
    }

    override fun onEnable() {
        baselineStep = -1
        stepStartedAtMs = System.currentTimeMillis()
        if (currentStep in 47..50) TreecapitatorLowestBinPrice.refreshIfNeeded(force = true)
        super.onEnable()
    }

    fun state(): Map<String, Any?> {
        val step = SkyBlockLevel7GuideRoute.step(currentStep)
        val treecapPrice = TreecapitatorLowestBinPrice.snapshot()
        return mapOf(
            "enabled" to enabled,
            "isSkyBlock" to lastSkyBlock,
            "complete" to (currentStep >= COMPLETE_STEP),
            "currentStep" to currentStep.coerceAtMost(MAX_STEP),
            "stepCount" to MAX_STEP,
            "label" to (step?.label ?: "Guide complete"),
            "instruction" to (step?.let(::resolvedInstruction) ?: "All 119 manual tasks are finished."),
            "detector" to (step?.detection ?: "Complete"),
            "lastAdvanceDetection" to lastAdvanceDetection,
            "treecapLowestBin" to treecapPrice.price,
            "treecapPriceStatus" to treecapPrice.status,
            "treecapPriceLastUpdatedMs" to treecapPrice.lastUpdatedMs,
            "treecapPriceError" to treecapPrice.error,
            "treecapPriceSource" to TreecapitatorLowestBinPrice.SOURCE_NAME,
            "knownSkillLevels" to knownSkillLevels.toMap(),
            "hud" to mapOf(
                "x" to guideHud.x,
                "y" to guideHud.y,
                "scale" to guideHud.scale,
                "width" to PANEL_WIDTH,
                "height" to panelHeight(currentStep),
            ),
        )
    }

    private fun setStep(step: Int) {
        val resolved = step.coerceIn(1, COMPLETE_STEP)
        if (currentStep == resolved && baselineStep == resolved) return
        currentStep = resolved
        baselineStep = -1
        stepStartedAtMs = System.currentTimeMillis()
        baseline = captureBaseline()
        baselineStep = resolved
        if (resolved in 47..50) TreecapitatorLowestBinPrice.refreshIfNeeded(force = true)
        ModuleManager.saveConfigurations()
    }

    private fun syncBaselineToStep() {
        if (baselineStep == currentStep) return
        baseline = captureBaseline()
        baselineStep = currentStep
        stepStartedAtMs = System.currentTimeMillis()
    }

    private fun captureBaseline(): SkyBlockGuideBaseline {
        val items = inventoryItems()
        val scoreboard = scoreboardLines()
        return SkyBlockGuideBaseline(
            inventory = items,
            experienceLevel = mc.player?.experienceLevel ?: 0,
            purse = SkyBlockLevel7GuideRoute.parsePurse(scoreboard),
        )
    }

    private fun observation(): SkyBlockGuideObservation {
        val now = System.currentTimeMillis()
        val scoreboard = scoreboardLines()
        val player = mc.player
        val screen = mc.screen as? AbstractContainerScreen<*>
        val armor = if (player == null) emptyMap() else mapOf(
            "head" to describe(player.getItemBySlot(EquipmentSlot.HEAD)),
            "chest" to describe(player.getItemBySlot(EquipmentSlot.CHEST)),
            "legs" to describe(player.getItemBySlot(EquipmentSlot.LEGS)),
            "feet" to describe(player.getItemBySlot(EquipmentSlot.FEET)),
        ).filterValues { it != null }.mapValues { it.value!! }

        return SkyBlockGuideObservation(
            chat = if (now - lastChatAtMs <= CHAT_SIGNAL_MS) lastChat else "",
            screenTitle = mc.screen?.title?.string.orEmpty(),
            screenItems = screen?.menu?.slots.orEmpty().mapNotNull { slot -> describe(slot.item) },
            inventory = inventoryItems(),
            nearbyNames = nearbyNames(),
            scoreboard = scoreboard,
            skillLevels = knownSkillLevels.toMap(),
            experienceLevel = player?.experienceLevel ?: 0,
            purse = SkyBlockLevel7GuideRoute.parsePurse(scoreboard),
            heldItem = player?.mainHandItem?.let(::describe),
            armor = armor,
            sneaking = player?.isShiftKeyDown == true,
            teleported = now <= teleportedUntilMs,
            elapsedOnStepMs = (now - stepStartedAtMs).coerceAtLeast(0L),
            treecapLowestBin = TreecapitatorLowestBinPrice.snapshot().price,
        )
    }

    private fun inventoryItems(): List<SkyBlockGuideItem> {
        val inventory = mc.player?.inventory ?: return emptyList()
        return (0 until inventory.containerSize).mapNotNull { describe(inventory.getItem(it)) }
    }

    private fun nearbyNames(): List<String> {
        val level = mc.level ?: return emptyList()
        val player = mc.player ?: return emptyList()
        return level.entitiesForRendering()
            .asSequence()
            .filter { it !== player && it.distanceToSqr(player) <= 625.0 }
            .flatMap { entity -> sequenceOf(entity.name.string, entity.customName?.string.orEmpty()) }
            .filter(String::isNotBlank)
            .distinct()
            .take(80)
            .toList()
    }

    private fun describe(stack: ItemStack): SkyBlockGuideItem? {
        if (stack.isEmpty) return null
        val id = skyBlockId(stack.customData)
            ?: BuiltInRegistries.ITEM.getKey(stack.item).path.uppercase()
        val lore = stack.get(DataComponents.LORE)?.lines()?.map { it.string }.orEmpty()
        return SkyBlockGuideItem(
            id = id,
            name = stack.hoverName.string,
            count = stack.count,
            enchanted = stack.isEnchanted,
            lore = lore,
        )
    }

    private fun scoreboardLines(): List<String> {
        val level = mc.level ?: return emptyList()
        val player = mc.player ?: return emptyList()
        val scoreboard = level.scoreboard
        val team = scoreboard.getPlayersTeam(player.scoreboardName)
        val objective = team?.color
            ?.let { gg.floyd.utils.teamDisplaySlot(it) }
            ?.let(scoreboard::getDisplayObjective)
            ?: scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)

        val lines = mutableListOf<String>()
        objective?.let { current ->
            lines += current.displayName.string
            scoreboard.listPlayerScores(current)
                .asSequence()
                .filterNot(PlayerScoreEntry::isHidden)
                .sortedWith(compareByDescending<PlayerScoreEntry> { it.value() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner() })
                .take(20)
                .forEach { entry ->
                    val entryTeam = scoreboard.getPlayersTeam(entry.owner())
                    lines += PlayerTeam.formatNameForTeam(entryTeam, entry.ownerName()).string
                    lines += entry.formatValue(current.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT)).string
                }
        }
        mc.connection?.listedOnlinePlayers.orEmpty().forEach { info ->
            info.tabListDisplayName?.string?.let(lines::add)
        }
        return lines
    }

    private fun updateTeleportSignal() {
        val now = System.currentTimeMillis()
        val level = mc.level
        val player = mc.player
        if (level == null || player == null) {
            lastPlayerPosition = null
            lastLevelIdentity = null
            return
        }

        val current = Triple(player.x, player.y, player.z)
        val previous = lastPlayerPosition
        val levelChanged = lastLevelIdentity != null && lastLevelIdentity !== level
        if (levelChanged || previous?.let { squaredDistance(it, current) > 2_500.0 } == true) {
            teleportedUntilMs = now + 3_000L
        }
        lastLevelIdentity = level
        lastPlayerPosition = current
    }

    private fun squaredDistance(a: Triple<Double, Double, Double>, b: Triple<Double, Double, Double>): Double {
        val dx = a.first - b.first
        val dy = a.second - b.second
        val dz = a.third - b.third
        return dx * dx + dy * dy + dz * dz
    }

    private fun rememberSkill(text: String) {
        val (skill, level) = SkyBlockLevel7GuideRoute.parseSkillLevel(text) ?: return
        knownSkillLevels[skill] = max(knownSkillLevels[skill] ?: 0, level)
    }

    private fun rememberSkills(observation: SkyBlockGuideObservation) {
        rememberSkill(observation.chat)
        observation.screenItems.forEach { item ->
            rememberSkill(item.name)
            item.lore.forEach(::rememberSkill)
        }
    }

    private fun isSkyBlock(lines: List<String>): Boolean =
        lines.any { SkyBlockLevel7GuideRoute.normalize(it).contains("skyblock") }

    private fun GuiGraphics.drawGuide(example: Boolean): Pair<Int, Int> {
        val shownStep = if (example && currentStep >= COMPLETE_STEP) 1 else currentStep
        val completed = !example && shownStep >= COMPLETE_STEP
        val routeStep = SkyBlockLevel7GuideRoute.step(shownStep)
        val label = if (completed) "Guide complete!" else routeStep?.label ?: "Farm fields"
        val instruction = if (completed) {
            "All 119 manual tasks are finished."
        } else {
            routeStep?.let(::resolvedInstruction) ?: "Break crops in the Hub farm until one enters your inventory."
        }
        val detector = if (completed) "Progress is saved" else routeStep?.detection ?: "Crop items enter inventory"
        val labelLines = wrap(label, PANEL_WIDTH - 16)
        val instructionLines = wrap("Do: $instruction", PANEL_WIDTH - 16)
        val detectorLines = wrap("Done when: $detector", PANEL_WIDTH - 16)
        val height = contentHeight(labelLines.size, instructionLines.size, detectorLines.size)
        val target = FloydPanelStyle.PanelTarget.SKYBLOCK_LEVEL_7_GUIDE
        val multiplier = mc.window.guiScale.toFloat() / NVGRenderer.devicePixelRatio()

        NVGPIPRenderer.draw(
            this,
            0,
            0,
            PANEL_WIDTH,
            height,
            multiplier,
            localCoordinates = true,
            backdropBlur = HudPanel.nvgBlur(PANEL_WIDTH, height, target),
        ) {
            HudPanel.drawNvgPanel(
                PANEL_WIDTH,
                height,
                target,
                HudPanel.panelBorderColors(target, guideHud.x, guideHud.y),
            )
        }

        drawString(mc.font, "SkyBlock LVL 7 Guide", 8, 7, TEXT_COLOR, true)
        val progress = if (completed) "119 / 119" else "Step ${shownStep.coerceAtMost(MAX_STEP)} / $MAX_STEP"
        drawString(mc.font, progress, PANEL_WIDTH - 8 - mc.font.width(progress), 7, if (completed) SUCCESS_COLOR else TEXT_COLOR, true)
        var y = 19
        labelLines.forEach { line ->
            drawString(mc.font, line, 8, y, TEXT_COLOR, true)
            y += mc.font.lineHeight
        }
        y += 2
        instructionLines.forEach { line ->
            drawString(mc.font, line, 8, y, TEXT_COLOR, true)
            y += mc.font.lineHeight
        }
        y += 3
        detectorLines.forEach { line ->
            drawString(mc.font, line, 8, y, if (completed) SUCCESS_COLOR else MUTED_COLOR, true)
            y += mc.font.lineHeight
        }
        return PANEL_WIDTH to height
    }

    private fun panelHeight(step: Int): Int {
        val routeStep = SkyBlockLevel7GuideRoute.step(step)
        val label = routeStep?.label ?: "Guide complete!"
        val instruction = routeStep?.let(::resolvedInstruction) ?: "All 119 manual tasks are finished."
        val detector = routeStep?.detection ?: "Progress is saved"
        return contentHeight(
            wrap(label, PANEL_WIDTH - 16).size,
            wrap("Do: $instruction", PANEL_WIDTH - 16).size,
            wrap("Done when: $detector", PANEL_WIDTH - 16).size,
        )
    }

    private fun contentHeight(labelLines: Int, instructionLines: Int, detectorLines: Int): Int =
        31 + (labelLines + instructionLines + detectorLines) * mc.font.lineHeight

    private fun resolvedInstruction(step: SkyBlockLevel7Step): String =
        step.instruction.replace(
            "{treecapTarget}",
            TreecapitatorLowestBinPrice.snapshot().price
                ?.let { "${String.format("%,d", it)} coins" }
                ?: "the live lowest BIN (fetching price)",
        )

    private fun wrap(text: String, maxWidth: Int): List<String> {
        if (mc.font.width(text) <= maxWidth) return listOf(text)
        val lines = mutableListOf<String>()
        var current = ""
        for (word in text.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && mc.font.width(candidate) > maxWidth) {
                lines += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines.ifEmpty { listOf(text) }
    }

}
