package floydaddons.not.dogshit.client.features.impl.render

import floydaddons.not.dogshit.client.FloydAddonsMod.mc
import floydaddons.not.dogshit.client.clickgui.settings.impl.BooleanSetting
import floydaddons.not.dogshit.client.clickgui.settings.impl.ColorSetting
import floydaddons.not.dogshit.client.clickgui.settings.impl.NumberSetting
import floydaddons.not.dogshit.client.features.Category
import floydaddons.not.dogshit.client.features.Module
import floydaddons.not.dogshit.client.features.impl.player.FloydNickHider
import floydaddons.not.dogshit.client.utils.Color
import floydaddons.not.dogshit.client.utils.render.ItemStateRenderer.Companion.drawItemStack
import floydaddons.not.dogshit.client.utils.render.RoundRectPIPRenderer
import floydaddons.not.dogshit.client.utils.ui.rendering.NVGPIPRenderer
import floydaddons.not.dogshit.client.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

object FloydHud : Module(
    name = "HUD",
    category = Category.RENDER,
    description = "Floyd inventory HUD, scoreboard HUD, and movable HUD editors.",
    toggled = true,
) {
    private const val INVENTORY_HUD_DEFAULT_X = 1540
    private const val INVENTORY_HUD_DEFAULT_Y = 24
    private const val INVENTORY_HUD_DEFAULT_SCALE = 2f

    private const val SCOREBOARD_HUD_DEFAULT_X = 1680
    private const val SCOREBOARD_HUD_DEFAULT_Y = 250
    private const val SCOREBOARD_HUD_DEFAULT_SCALE = 1f
    private const val INVENTORY_ITEM_SCALE_FACTOR = 0.75f
    private const val INVENTORY_COUNT_FONT_SLOT_FACTOR = 0.713f
    private const val INVENTORY_COUNT_VISUAL_HEIGHT_FACTOR = 0.62f
    private const val INVENTORY_ITEM_BASE_SIZE = 16f
    private const val INVENTORY_COUNT_OFFSET_X = 11f
    private const val INVENTORY_COUNT_OFFSET_Y = 4f
    private const val INVENTORY_COUNT_PIP_PADDING = 3f
    private const val INVENTORY_COUNT_SHADOW_OFFSET = 2f
    private const val INVENTORY_COUNT_ANCHOR_SAMPLE = "64"
    private const val INVENTORY_MINECRAFT_COUNT_OFFSET_RIGHT = 1f
    private const val INVENTORY_MINECRAFT_COUNT_OFFSET_BOTTOM = 2f
    private const val SCOREBOARD_FONT_SIZE = 12f
    private const val HUD_CHROMA_DURATION_MS = 4000L
    private const val HUD_FADE_DURATION_MS = 8000L
    private val SCOREBOARD_MIN_WIDTH_SAMPLES = listOf(
        "05/25/26 fL0YD",
        "Early Autumn 13th",
        "Purse: 999,999,999",
        "Craft a workbench",
        "Voidgloom Seraph IV"
    )

    private val vanillaScoreboardWouldRender = AtomicBoolean(false)

    val inventoryHudScale by NumberSetting("Inventory HUD Scale", 1.1f, 0.5f, 5.0f, 0.05f, desc = "Inventory HUD scale.")
    val scoreboardHudScale by NumberSetting("Scoreboard HUD Scale", 1.0f, 0.5f, 3.0f, 0.05f, desc = "Scoreboard HUD scale.")
    private val hudCornerRadius by NumberSetting("HUD Corner Radius", 0, 0, 12, 1, desc = "Rounded corner radius for Floyd HUD panels.")
    private val inventoryHudMinecraftStackFont by BooleanSetting("Inventory HUD Minecraft Stack Font", false, desc = "Uses Minecraft's default font for inventory stack counts.")
    private val scoreboardHudMinecraftFont by BooleanSetting("Scoreboard HUD Minecraft Font", false, desc = "Uses Minecraft's default font for the scoreboard text.")
    private val inventoryHudColor by ColorSetting("Inventory HUD Color", Color(0xFFFFFFFF.toInt()), desc = "Primary color for the inventory HUD border.")
    private val inventoryHudChroma by BooleanSetting("Inventory HUD Chroma", true, desc = "Cycles the inventory HUD accent through chroma.")
    private val inventoryHudFadeColor by ColorSetting("Inventory HUD Fade Color", Color(0xFF55FFFF.toInt()), desc = "Secondary color for the inventory HUD fade.")
    private val inventoryHudFade by BooleanSetting("Inventory HUD Fade", false, desc = "Fades the inventory HUD accent between two colors.")
    private val scoreboardHudColor by ColorSetting("Scoreboard HUD Color", Color(0xFFFFFFFF.toInt()), desc = "Primary color for the scoreboard HUD border and FloydAddons footer.")
    private val scoreboardHudChroma by BooleanSetting("Scoreboard HUD Chroma", true, desc = "Cycles the scoreboard HUD accent through chroma.")
    private val scoreboardHudFadeColor by ColorSetting("Scoreboard HUD Fade Color", Color(0xFF55FFFF.toInt()), desc = "Secondary color for the scoreboard HUD fade.")
    private val scoreboardHudFade by BooleanSetting("Scoreboard HUD Fade", false, desc = "Fades the scoreboard HUD accent between two colors.")

    private val inventoryHud by HUD("Inventory HUD", "Displays the main inventory in a movable Floyd HUD.", true, INVENTORY_HUD_DEFAULT_X, INVENTORY_HUD_DEFAULT_Y, INVENTORY_HUD_DEFAULT_SCALE, anchorRight = true) {
        drawInventoryHud(it)
    }

    private val scoreboardHud by HUD("Scoreboard HUD", "Displays a movable Floyd-styled scoreboard.", true, SCOREBOARD_HUD_DEFAULT_X, SCOREBOARD_HUD_DEFAULT_Y, SCOREBOARD_HUD_DEFAULT_SCALE, anchorRight = true) { example ->
        drawScoreboardHud(example)
    }

    fun state(): Map<String, Any?> {
        val objective = sidebarObjective()
        return mapOf(
            "enabled" to enabled,
            "inventoryHud" to mapOf(
                "enabled" to (enabled && inventoryHud.enabled),
                "scale" to inventoryHudScale,
                "x" to inventoryHud.x,
                "y" to inventoryHud.y,
                "hudScale" to inventoryHud.scale,
                "color" to "#${inventoryHudColor.hex()}",
                "chroma" to inventoryHudChroma,
                "fade" to inventoryHudFade,
                "fadeColor" to "#${inventoryHudFadeColor.hex()}"
            ),
            "scoreboardHud" to mapOf(
                "enabled" to (enabled && scoreboardHud.enabled),
                "customScoreboard" to FloydRender.shouldUseCustomScoreboard(),
                "scale" to scoreboardHudScale,
                "sidebarObjective" to objective?.name,
                "vanillaWouldRender" to vanillaScoreboardWouldRender.get(),
                "wouldRender" to shouldDrawScoreboardHud(
                    example = false,
                    customScoreboard = FloydRender.shouldUseCustomScoreboard(),
                    objectivePresent = objective != null,
                    consumeVanillaSignal = false
                ),
                "x" to scoreboardHud.x,
                "y" to scoreboardHud.y,
                "hudScale" to scoreboardHud.scale,
                "color" to "#${scoreboardHudColor.hex()}",
                "chroma" to scoreboardHudChroma,
                "fade" to scoreboardHudFade,
                "fadeColor" to "#${scoreboardHudFadeColor.hex()}"
            ),
            "cornerRadius" to hudCornerRadius
        )
    }

    @JvmStatic
    fun markVanillaScoreboardWouldRender() {
        vanillaScoreboardWouldRender.set(true)
    }

    internal fun resetVanillaScoreboardWouldRender() {
        vanillaScoreboardWouldRender.set(false)
    }

    internal fun shouldDrawScoreboardHud(
        example: Boolean,
        customScoreboard: Boolean,
        objectivePresent: Boolean,
        moduleEnabled: Boolean = enabled,
        hudEnabled: Boolean = scoreboardHud.enabled,
        consumeVanillaSignal: Boolean = true
    ): Boolean {
        if (example) return objectivePresent
        if (!moduleEnabled || !hudEnabled || !customScoreboard || !objectivePresent) {
            if (consumeVanillaSignal) vanillaScoreboardWouldRender.set(false)
            return false
        }
        return if (consumeVanillaSignal) vanillaScoreboardWouldRender.getAndSet(false) else vanillaScoreboardWouldRender.get()
    }

    @JvmStatic
    fun shouldCancelVanillaScoreboard(
        customScoreboard: Boolean,
        objectivePresent: Boolean
    ): Boolean = shouldCancelVanillaScoreboard(customScoreboard, objectivePresent, enabled, scoreboardHud.enabled)

    @JvmStatic
    fun shouldCancelVanillaScoreboard(
        customScoreboard: Boolean,
        objectivePresent: Boolean,
        moduleEnabled: Boolean,
        hudEnabled: Boolean
    ): Boolean = moduleEnabled && hudEnabled && customScoreboard && objectivePresent

    private fun GuiGraphics.drawInventoryHud(example: Boolean): Pair<Int, Int> {
        val inventory = mc.player?.inventory
        val slotSize = (18 * inventoryHudScale).roundToInt().coerceAtLeast(12)
        val width = 9 * slotSize
        val height = 3 * slotSize
        val borderColors = inventoryHudBorderColors()
        fillPanel(width, height, borderColors)

        if (inventory != null) {
            for (slot in 0 until 27) {
                val col = slot % 9
                val row = slot / 9
                val stack = inventory.getItem(slot + 9)
                if (stack.isEmpty) continue

                pose().pushMatrix()
                val itemScale = (slotSize * INVENTORY_ITEM_SCALE_FACTOR) / 16f
                val x = col * slotSize + (slotSize - 16 * itemScale) / 2f
                val y = row * slotSize + (slotSize - 16 * itemScale) / 2f
                pose().translate(x, y)
                pose().scale(itemScale, itemScale)
                drawItemStack(stack, 0, 0)
                pose().popMatrix()

                if (stack.count > 1) {
                    val count = stack.count.toString()
                    if (inventoryHudMinecraftStackFont) {
                        drawInventoryMinecraftStackCount(count, x, y, itemScale, 0xFFFFFFFF.toInt())
                    } else {
                        val countFontSize = INVENTORY_ITEM_BASE_SIZE * INVENTORY_COUNT_FONT_SLOT_FACTOR / INVENTORY_ITEM_SCALE_FACTOR
                        val countWidth = inventoryCountTextWidth(count, countFontSize)
                        val countAnchorWidth = max(countWidth, inventoryCountTextWidth(INVENTORY_COUNT_ANCHOR_SAMPLE, countFontSize))
                        val countX = INVENTORY_ITEM_BASE_SIZE - countAnchorWidth + INVENTORY_COUNT_OFFSET_X
                        val countY = INVENTORY_ITEM_BASE_SIZE - countFontSize * INVENTORY_COUNT_VISUAL_HEIGHT_FACTOR + INVENTORY_COUNT_OFFSET_Y
                        drawInventoryStackCount(count, x, y, itemScale, countX, countY, countWidth, countAnchorWidth, countFontSize, 0xFFFFFFFF.toInt())
                    }
                }
            }
        }
        return width to height
    }

    private fun GuiGraphics.drawInventoryMinecraftStackCount(
        count: String,
        itemX: Float,
        itemY: Float,
        itemScale: Float,
        textColor: Int
    ) {
        val textWidth = mc.font.width(count).toFloat()
        val countX = INVENTORY_ITEM_BASE_SIZE - textWidth - INVENTORY_MINECRAFT_COUNT_OFFSET_RIGHT
        val countY = INVENTORY_ITEM_BASE_SIZE - mc.font.lineHeight - INVENTORY_MINECRAFT_COUNT_OFFSET_BOTTOM

        pose().pushMatrix()
        pose().translate(itemX, itemY)
        pose().scale(itemScale, itemScale)
        drawString(mc.font, count, countX.roundToInt(), countY.roundToInt(), textColor, true)
        pose().popMatrix()
    }

    private fun GuiGraphics.drawInventoryStackCount(
        count: String,
        itemX: Float,
        itemY: Float,
        itemScale: Float,
        countX: Float,
        countY: Float,
        countWidth: Float,
        countAnchorWidth: Float,
        countFontSize: Float,
        textColor: Int
    ) {
        val pipWidth = ceil(countAnchorWidth + INVENTORY_COUNT_SHADOW_OFFSET + INVENTORY_COUNT_PIP_PADDING * 2f).toInt()
        val pipHeight = ceil(inventoryCountTextHeight(countFontSize) + INVENTORY_COUNT_SHADOW_OFFSET + INVENTORY_COUNT_PIP_PADDING * 2f).toInt()
        val countTextX = INVENTORY_COUNT_PIP_PADDING + (countAnchorWidth - countWidth)

        pose().pushMatrix()
        pose().translate(
            itemX + (countX - INVENTORY_COUNT_PIP_PADDING) * itemScale,
            itemY + (countY - INVENTORY_COUNT_PIP_PADDING) * itemScale
        )
        pose().scale(itemScale, itemScale)
        NVGPIPRenderer.draw(
            this,
            0,
            0,
            pipWidth,
            pipHeight
        ) {
            NVGRenderer.textShadow(
                count,
                countTextX,
                INVENTORY_COUNT_PIP_PADDING,
                countFontSize,
                textColor,
                NVGRenderer.defaultFont
            )
        }
        pose().popMatrix()
    }

    private fun GuiGraphics.drawScoreboardHud(example: Boolean): Pair<Int, Int> {
        val objective = sidebarObjective() ?: return if (example) drawScoreboardExample() else 0 to 0
        if (!shouldDrawScoreboardHud(example, FloydRender.shouldUseCustomScoreboard(), objectivePresent = true)) return 0 to 0
        val scoreboard = mc.level?.scoreboard ?: return if (example) drawScoreboardExample() else 0 to 0
        val sortedEntries = scoreboard.listPlayerScores(objective)
            .filterNot(PlayerScoreEntry::isHidden)
            .sortedWith(compareByDescending<PlayerScoreEntry> { it.value() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner() })

        val lines = ArrayList<ScoreLine>(minOf(sortedEntries.size, 15))
        for (entry in sortedEntries.take(15)) {
            val team = scoreboard.getPlayersTeam(entry.owner())
            val name = PlayerTeam.formatNameForTeam(team, entry.ownerName())
            val score = entry.formatValue(objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT))
            val nameText = styledText(FloydNickHider.replaceSequence(name.visualOrderText))
            val scoreText = styledText(FloydNickHider.replaceSequence(score.visualOrderText))
            lines += ScoreLine(nameText, scoreText, textWidth(nameText), textWidth(scoreText))
        }

        if (lines.size > 1) lines.removeAt(lines.lastIndex)
        if (lines.isEmpty()) return if (example) drawScoreboardExample() else 0 to 0

        return drawScoreboardBox(objective.displayName, lines, "FloydAddons")
    }

    private fun GuiGraphics.drawScoreboardExample(): Pair<Int, Int> {
        val lines = mutableListOf(
            scoreLine("Early Autumn 13th"),
            scoreLine("5:10am"),
            scoreLine("The End"),
            scoreLine(""),
            scoreLine("Purse: 462,916,179"),
            scoreLine("Bits: 15,092"),
            scoreLine(""),
            scoreLine("Slayer Quest"),
            scoreLine("Voidgloom Seraph IV"),
            scoreLine("11/146 Kills"),
        )
        return drawScoreboardBox(Component.literal("SKYBLOCK"), lines, "FloydAddons")
    }

    private fun GuiGraphics.drawScoreboardBox(
        title: Component,
        lines: List<ScoreLine>,
        footer: String
    ): Pair<Int, Int> {
        val titleText = FloydNickHider.replaceSequence(title.visualOrderText)
        val footerText = Component.literal(footer).visualOrderText
        val styledTitleText = styledText(titleText)
        val borderColors = scoreboardHudBorderColors()
        val styledFooterText = styledFooterText(footerText)
        val titleWidth = textWidth(styledTitleText)
        val footerWidth = textWidth(styledFooterText)
        val colonWidth = textWidth(": ")
        var maxLineWidth = max(max(titleWidth, footerWidth), scoreboardMinimumContentWidth())
        for (line in lines) {
            val width = line.nameWidth + if (line.scoreWidth > 0f) colonWidth + line.scoreWidth else 0f
            maxLineWidth = max(maxLineWidth, width)
        }

        val fontSize = scoreboardTextHeight()
        val padding = ceil(6f * scoreboardHudScale).toInt()
        val lineHeight = ceil(fontSize + 4f * scoreboardHudScale).toInt()
        val titlePad = ceil(5f * scoreboardHudScale).toInt()
        val boxWidth = ceil(maxLineWidth + padding * 2).toInt()
        val titleBarHeight = lineHeight + titlePad * 2
        val footerBarHeight = lineHeight + titlePad * 2
        val boxHeight = titleBarHeight + lines.size * lineHeight + footerBarHeight

        val textElements = ArrayList<ScoreboardText>(lines.size * 2 + 2)
        textElements += ScoreboardText(styledTitleText, (boxWidth - titleWidth) / 2f, titlePad.toFloat())

        var lineY = titleBarHeight
        val scoreRight = boxWidth - padding
        for (line in lines) {
            if (line.scoreWidth > 0f) {
                textElements += ScoreboardText(line.name, padding.toFloat(), lineY.toFloat())
                textElements += ScoreboardText(line.score, scoreRight - line.scoreWidth, lineY.toFloat())
            } else {
                textElements += ScoreboardText(line.name, padding.toFloat(), lineY.toFloat())
            }
            lineY += lineHeight
        }

        textElements += ScoreboardText(styledFooterText, (boxWidth - footerWidth) / 2f, (lineY + titlePad).toFloat())
        drawScoreboardPanelAndText(boxWidth, boxHeight, textElements, borderColors)
        return boxWidth to boxHeight
    }

    private fun scoreLine(name: String): ScoreLine {
        val text = styledText(Component.literal(name).visualOrderText)
        return ScoreLine(text, StyledScoreboardText.EMPTY, textWidth(text), 0f)
    }

    private fun textWidth(text: String): Float =
        if (scoreboardHudMinecraftFont) mc.font.width(text) * scoreboardHudScale
        else NVGRenderer.textWidth(text, scoreboardFontSize(), NVGRenderer.defaultFont)

    private fun textWidth(text: FormattedCharSequence): Float =
        textWidth(styledText(text))

    private fun textWidth(text: StyledScoreboardText): Float {
        var width = 0f
        for (segment in text.segments) {
            width += if (scoreboardHudMinecraftFont || segment.minecraftFont) {
                mc.font.width(segment.text) * scoreboardHudScale
            } else {
                NVGRenderer.textWidth(segment.text, scoreboardFontSize(), NVGRenderer.defaultFont)
            }
        }
        return width
    }

    internal fun scoreboardPlainText(component: Component): String {
        val text = StringBuilder()
        component.visualOrderText.accept { _, _, codePoint ->
            text.appendCodePoint(codePoint)
            true
        }
        return text.toString()
    }

    private fun GuiGraphics.drawScoreboardPanelAndText(
        boxWidth: Int,
        boxHeight: Int,
        texts: List<ScoreboardText>,
        borderColors: HudBorderColors
    ) {
        fillPanel(boxWidth, boxHeight, borderColors)
        if (scoreboardHudMinecraftFont) {
            pose().pushMatrix()
            pose().scale(scoreboardHudScale, scoreboardHudScale)
            for (text in texts) {
                drawMinecraftScoreboardText(text)
            }
            pose().popMatrix()
        } else {
            NVGPIPRenderer.draw(this, 0, 0, boxWidth, boxHeight, renderScaleMultiplier = mc.window.guiScale.toFloat()) {
                for (text in texts) {
                    drawScoreboardText(text)
                }
            }
            pose().pushMatrix()
            pose().scale(scoreboardHudScale, scoreboardHudScale)
            for (text in texts) {
                drawScoreboardMinecraftFallbacks(text)
            }
            pose().popMatrix()
        }
    }

    private fun drawScoreboardText(text: ScoreboardText) {
        var segmentX = text.x
        for (segment in text.value.segments) {
            if (segment.minecraftFont) {
                segmentX += mc.font.width(segment.text) * scoreboardHudScale
                continue
            }
            NVGRenderer.text(segment.text, segmentX, text.y, scoreboardFontSize(), segment.color, NVGRenderer.defaultFont)
            segmentX += NVGRenderer.textWidth(segment.text, scoreboardFontSize(), NVGRenderer.defaultFont)
        }
    }

    private fun GuiGraphics.drawMinecraftScoreboardText(text: ScoreboardText) {
        var segmentX = text.x / scoreboardHudScale
        val textY = text.y / scoreboardHudScale
        for (segment in text.value.segments) {
            drawString(mc.font, segment.text, segmentX.roundToInt(), textY.roundToInt(), segment.color, false)
            segmentX += mc.font.width(segment.text)
        }
    }

    private fun GuiGraphics.drawScoreboardMinecraftFallbacks(text: ScoreboardText) {
        var segmentX = text.x / scoreboardHudScale
        val textY = text.y / scoreboardHudScale
        for (segment in text.value.segments) {
            if (segment.minecraftFont) {
                drawString(mc.font, segment.text, segmentX.roundToInt(), textY.roundToInt(), segment.color, false)
            }
            segmentX += if (segment.minecraftFont) mc.font.width(segment.text).toFloat()
            else NVGRenderer.textWidth(segment.text, scoreboardFontSize(), NVGRenderer.defaultFont) / scoreboardHudScale
        }
    }

    private fun styledText(text: FormattedCharSequence, forcedColor: Int? = null): StyledScoreboardText {
        val segments = mutableListOf<ScoreboardTextSegment>()
        val currentText = StringBuilder()
        var currentColor: Int? = null
        var currentMinecraftFont = false

        fun flush() {
            if (currentText.isEmpty()) return
            segments += ScoreboardTextSegment(currentText.toString(), currentColor ?: 0xFFFFFFFF.toInt(), currentMinecraftFont)
            currentText.clear()
        }

        text.accept { _, style, codePoint ->
            val color = forcedColor ?: scoreboardStyleColor(style)
            val minecraftFont = shouldUseMinecraftFontCodePoint(codePoint)
            if ((currentColor != null && currentColor != color) || (currentText.isNotEmpty() && currentMinecraftFont != minecraftFont)) flush()
            currentColor = color
            currentMinecraftFont = minecraftFont
            currentText.appendCodePoint(codePoint)
            true
        }
        flush()
        return StyledScoreboardText(segments)
    }

    private fun styledFooterText(text: FormattedCharSequence): StyledScoreboardText {
        val chars = ArrayList<String>(text.length())
        text.accept { _, _, codePoint ->
            chars += String(Character.toChars(codePoint))
            true
        }
        if (chars.isEmpty()) return StyledScoreboardText.EMPTY

        val baseOffset = hudRotationOffset(scoreboardHud.x, scoreboardHud.y, 0.38f)
        val segments = ArrayList<ScoreboardTextSegment>(chars.size)
        for (index in chars.indices) {
            val char = chars[index]
            val phase = if (chars.size == 1) baseOffset else offsetPhase(baseOffset, index.toFloat() / chars.size.toFloat())
            segments += ScoreboardTextSegment(
                char,
                accentColor(scoreboardHudColor, scoreboardHudChroma, scoreboardHudFade, scoreboardHudFadeColor, phase),
                minecraftFont = false
            )
        }
        return StyledScoreboardText(segments)
    }

    private fun scoreboardStyleColor(style: Style): Int {
        val color = style.color?.value ?: return 0xFFFFFFFF.toInt()
        return 0xFF000000.toInt() or (color and 0x00FFFFFF)
    }

    private fun sidebarObjective(): Objective? {
        val player = mc.player ?: return null
        val scoreboard = mc.level?.scoreboard ?: return null
        val team = scoreboard.getPlayersTeam(player.scoreboardName)
        val teamObjective = team?.color?.let(DisplaySlot::teamColorToSlot)?.let(scoreboard::getDisplayObjective)
        return teamObjective ?: scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
    }

    private fun GuiGraphics.fillPanel(width: Int, height: Int, borderColors: HudBorderColors = monochromeBorderColors(chromaColor(0f))) {
        val radius = hudCornerRadius.toFloat().coerceAtLeast(0f)
        val fillColor = 0x40000000
        RoundRectPIPRenderer.submit(
            this,
            0, 0, width, height,
            fillColor, fillColor, fillColor, fillColor,
            radius, radius, radius, radius,
            borderColors.topLeft, borderColors.topRight, borderColors.bottomRight, borderColors.bottomLeft, 2f
        )
    }

    private fun inventoryHudAccentColor(): Int =
        accentColor(inventoryHudColor, inventoryHudChroma, inventoryHudFade, inventoryHudFadeColor, hudRotationOffset(inventoryHud.x, inventoryHud.y, 0.08f))

    private fun scoreboardHudAccentColor(): Int =
        accentColor(scoreboardHudColor, scoreboardHudChroma, scoreboardHudFade, scoreboardHudFadeColor, hudRotationOffset(scoreboardHud.x, scoreboardHud.y, 0.38f))

    private fun inventoryHudBorderColors(): HudBorderColors =
        circularBorderColors(inventoryHudColor, inventoryHudChroma, inventoryHudFade, inventoryHudFadeColor, hudRotationOffset(inventoryHud.x, inventoryHud.y, 0.08f))

    private fun scoreboardHudBorderColors(): HudBorderColors =
        circularBorderColors(scoreboardHudColor, scoreboardHudChroma, scoreboardHudFade, scoreboardHudFadeColor, hudRotationOffset(scoreboardHud.x, scoreboardHud.y, 0.38f))

    private fun accentColor(base: Color, chroma: Boolean, fade: Boolean, fadeColor: Color, offset: Float): Int {
        if (chroma) return chromaColor(offset)
        if (fade) return blendColors(base.rgba, fadeColor.rgba, fadeProgress(offset))
        return base.rgba
    }

    private fun circularBorderColors(base: Color, chroma: Boolean, fade: Boolean, fadeColor: Color, offset: Float): HudBorderColors =
        HudBorderColors(
            topLeft = accentColor(base, chroma, fade, fadeColor, offset),
            topRight = accentColor(base, chroma, fade, fadeColor, offsetPhase(offset, 0.25f)),
            bottomRight = accentColor(base, chroma, fade, fadeColor, offsetPhase(offset, 0.5f)),
            bottomLeft = accentColor(base, chroma, fade, fadeColor, offsetPhase(offset, 0.75f))
        )

    private fun monochromeBorderColors(color: Int): HudBorderColors =
        HudBorderColors(color, color, color, color)

    private fun hudRotationOffset(x: Int, y: Int, seed: Float): Float =
        (((x * 0.00035f) + (y * 0.0002f) + seed) % 1f + 1f) % 1f

    private fun offsetPhase(offset: Float, delta: Float): Float =
        ((offset + delta) % 1f + 1f) % 1f

    private fun fadeProgress(offset: Float): Float {
        val angle = animationPhase(HUD_FADE_DURATION_MS, offset) * (2f * PI.toFloat())
        return ((sin(angle) + 1f) * 0.5f).coerceIn(0f, 1f)
    }

    private fun blendColors(start: Int, end: Int, progress: Float): Int {
        val t = progress.coerceIn(0f, 1f)
        val startA = start ushr 24 and 0xFF
        val startR = start ushr 16 and 0xFF
        val startG = start ushr 8 and 0xFF
        val startB = start and 0xFF
        val endA = end ushr 24 and 0xFF
        val endR = end ushr 16 and 0xFF
        val endG = end ushr 8 and 0xFF
        val endB = end and 0xFF
        val alpha = (startA + ((endA - startA) * t)).roundToInt()
        val red = (startR + ((endR - startR) * t)).roundToInt()
        val green = (startG + ((endG - startG) * t)).roundToInt()
        val blue = (startB + ((endB - startB) * t)).roundToInt()
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun scoreboardFontSize(): Float = SCOREBOARD_FONT_SIZE * scoreboardHudScale

    private fun scoreboardTextHeight(): Float =
        if (scoreboardHudMinecraftFont) mc.font.lineHeight * scoreboardHudScale
        else scoreboardFontSize()

    private fun shouldUseMinecraftFontCodePoint(codePoint: Int): Boolean =
        codePoint !in 0x20..0x7E

    private fun inventoryCountTextWidth(text: String, countFontSize: Float): Float =
        NVGRenderer.textWidth(text, countFontSize, NVGRenderer.defaultFont)

    private fun inventoryCountTextHeight(countFontSize: Float): Float =
        countFontSize

    private fun scoreboardMinimumContentWidth(): Float =
        SCOREBOARD_MIN_WIDTH_SAMPLES.maxOf { sample -> textWidth(sample) }

    private fun chromaColor(offset: Float): Int {
        val hue = animationPhase(HUD_CHROMA_DURATION_MS, offset)
        return 0xFF000000.toInt() or (java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f) and 0x00FFFFFF)
    }

    private fun animationPhase(durationMs: Long, offset: Float): Float =
        ((((System.currentTimeMillis() % durationMs) / durationMs.toFloat()) + offset) % 1f + 1f) % 1f

    private data class ScoreLine(val name: StyledScoreboardText, val score: StyledScoreboardText, val nameWidth: Float, val scoreWidth: Float)
    private data class ScoreboardText(val value: StyledScoreboardText, val x: Float, val y: Float)
    private data class StyledScoreboardText(val segments: List<ScoreboardTextSegment>) {
        companion object {
            val EMPTY = StyledScoreboardText(emptyList())
        }
    }
    private data class HudBorderColors(val topLeft: Int, val topRight: Int, val bottomRight: Int, val bottomLeft: Int)
    private data class ScoreboardTextSegment(val text: String, val color: Int, val minecraftFont: Boolean = false)
}
