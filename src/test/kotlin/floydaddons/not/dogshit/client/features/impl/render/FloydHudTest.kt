package floydaddons.not.dogshit.client.features.impl.render

import net.minecraft.network.chat.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloydHudTest {
    @Test
    fun `custom scoreboard requires vanilla sidebar signal`() {
        FloydHud.resetVanillaScoreboardWouldRender()

        assertFalse(
            FloydHud.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )

        FloydHud.markVanillaScoreboardWouldRender()
        assertTrue(
            FloydHud.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )
        assertFalse(
            FloydHud.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )
    }

    @Test
    fun `scoreboard gate still blocks disabled custom scoreboard or missing objective`() {
        FloydHud.resetVanillaScoreboardWouldRender()
        FloydHud.markVanillaScoreboardWouldRender()

        assertFalse(
            FloydHud.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = false,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )
        assertFalse(
            FloydHud.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )

        FloydHud.markVanillaScoreboardWouldRender()
        assertFalse(
            FloydHud.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = false,
                moduleEnabled = true,
                hudEnabled = true
            )
        )
    }

    @Test
    fun `vanilla scoreboard cancel gate only trips when custom hud actually replaces it`() {
        assertTrue(FloydHud.shouldCancelVanillaScoreboard(customScoreboard = true, objectivePresent = true, moduleEnabled = true, hudEnabled = true))
        assertFalse(FloydHud.shouldCancelVanillaScoreboard(customScoreboard = false, objectivePresent = true, moduleEnabled = true, hudEnabled = true))
        assertFalse(FloydHud.shouldCancelVanillaScoreboard(customScoreboard = true, objectivePresent = true, moduleEnabled = true, hudEnabled = false))
        assertFalse(FloydHud.shouldCancelVanillaScoreboard(customScoreboard = true, objectivePresent = false, moduleEnabled = true, hudEnabled = true))
    }

    @Test
    fun `inventory HUD stack counts match the Floyd release overlay`() {
        val root = Path.of("").toAbsolutePath()
        val floyd = Files.readString(root.resolve("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/features/hud/InventoryHudRenderer.java"))
        val active = Files.readString(root.resolve("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/render/FloydHud.kt"))
        val itemRenderer = Files.readString(root.resolve("src/main/kotlin/floydaddons/not/dogshit/client/utils/render/ItemRenderer.kt"))

        assertTrue(floyd.contains("return Math.max(12, Math.round(BASE_SLOT * RenderConfig.getInventoryHudScale()));"))
        assertTrue(active.contains("val slotSize = (18 * inventoryHudScale).roundToInt().coerceAtLeast(12)"))
        assertTrue(active.contains("val borderColors = inventoryHudBorderColors()"))
        assertTrue(active.contains("fillPanel(width, height, borderColors)"))
        assertTrue(active.contains("BooleanSetting(\"Inventory HUD Minecraft Stack Font\", false"))
        assertTrue(active.contains("private const val INVENTORY_ITEM_SCALE_FACTOR = 0.75f"))
        assertTrue(active.contains("private const val INVENTORY_COUNT_FONT_SLOT_FACTOR = 0.713f"))
        assertTrue(active.contains("private const val INVENTORY_COUNT_VISUAL_HEIGHT_FACTOR = 0.62f"))
        assertTrue(active.contains("private const val INVENTORY_ITEM_BASE_SIZE = 16f"))
        assertTrue(active.contains("private const val INVENTORY_COUNT_OFFSET_X = 11f"))
        assertTrue(active.contains("private const val INVENTORY_COUNT_OFFSET_Y = 4f"))
        assertTrue(active.contains("private const val INVENTORY_COUNT_PIP_PADDING = 3f"))
        assertTrue(active.contains("private const val INVENTORY_COUNT_SHADOW_OFFSET = 2f"))
        assertTrue(active.contains("private const val INVENTORY_COUNT_ANCHOR_SAMPLE = \"64\""))
        assertTrue(active.contains("private const val INVENTORY_MINECRAFT_COUNT_OFFSET_RIGHT = 1f"))
        assertTrue(active.contains("private const val INVENTORY_MINECRAFT_COUNT_OFFSET_BOTTOM = 2f"))
        assertTrue(active.contains("val countFontSize = INVENTORY_ITEM_BASE_SIZE * INVENTORY_COUNT_FONT_SLOT_FACTOR / INVENTORY_ITEM_SCALE_FACTOR"))
        assertTrue(active.contains("val x = col * slotSize + (slotSize - 16 * itemScale) / 2f"))
        assertTrue(active.contains("val y = row * slotSize + (slotSize - 16 * itemScale) / 2f"))
        assertTrue(active.contains("if (inventoryHudMinecraftStackFont) {"))
        assertTrue(active.contains("drawInventoryMinecraftStackCount(count, x, y, itemScale, 0xFFFFFFFF.toInt())"))
        assertTrue(active.contains("private fun GuiGraphics.drawInventoryMinecraftStackCount("))
        assertTrue(active.contains("val textWidth = mc.font.width(count).toFloat()"))
        assertTrue(active.contains("val countX = INVENTORY_ITEM_BASE_SIZE - textWidth - INVENTORY_MINECRAFT_COUNT_OFFSET_RIGHT"))
        assertTrue(active.contains("val countY = INVENTORY_ITEM_BASE_SIZE - mc.font.lineHeight - INVENTORY_MINECRAFT_COUNT_OFFSET_BOTTOM"))
        assertTrue(active.contains("pose().translate(itemX, itemY)"))
        assertTrue(active.contains("drawString(mc.font, count, countX.roundToInt(), countY.roundToInt(), textColor, true)"))
        assertTrue(active.contains("val countWidth = inventoryCountTextWidth(count, countFontSize)"))
        assertTrue(active.contains("val countAnchorWidth = max(countWidth, inventoryCountTextWidth(INVENTORY_COUNT_ANCHOR_SAMPLE, countFontSize))"))
        assertTrue(active.contains("val countX = INVENTORY_ITEM_BASE_SIZE - countAnchorWidth + INVENTORY_COUNT_OFFSET_X"))
        assertTrue(active.contains("val countY = INVENTORY_ITEM_BASE_SIZE - countFontSize * INVENTORY_COUNT_VISUAL_HEIGHT_FACTOR + INVENTORY_COUNT_OFFSET_Y"))
        assertTrue(active.contains("drawInventoryStackCount(count, x, y, itemScale, countX, countY, countWidth, countAnchorWidth, countFontSize, 0xFFFFFFFF.toInt())"))
        assertTrue(active.contains("val pipWidth = ceil(countAnchorWidth + INVENTORY_COUNT_SHADOW_OFFSET + INVENTORY_COUNT_PIP_PADDING * 2f).toInt()"))
        assertTrue(active.contains("val pipHeight = ceil(inventoryCountTextHeight(countFontSize) + INVENTORY_COUNT_SHADOW_OFFSET + INVENTORY_COUNT_PIP_PADDING * 2f).toInt()"))
        assertTrue(active.contains("val countTextX = INVENTORY_COUNT_PIP_PADDING + (countAnchorWidth - countWidth)"))
        assertTrue(active.contains("itemX + (countX - INVENTORY_COUNT_PIP_PADDING) * itemScale"))
        assertTrue(active.contains("itemY + (countY - INVENTORY_COUNT_PIP_PADDING) * itemScale"))
        assertTrue(active.contains("pose().scale(itemScale, itemScale)"))
        assertTrue(active.contains("pipWidth"))
        assertTrue(active.contains("pipHeight"))
        assertTrue(active.contains("NVGRenderer.textShadow("))
        assertTrue(active.contains("textColor,"))
        assertTrue(active.contains("countTextX"))
        assertTrue(active.contains("NVGRenderer.defaultFont"))
        assertFalse(active.contains("val fontScale = inventoryCountMinecraftFontScale()"))
        assertFalse(active.contains("pose().translate(countTextX / fontScale, INVENTORY_COUNT_PIP_PADDING / fontScale)"))
        assertFalse(active.contains("drawString(mc.font, count, 0, 0, textColor, true)"))
        assertFalse(active.contains("val countX = INVENTORY_ITEM_BASE_SIZE - countWidth + INVENTORY_COUNT_OFFSET_X"))
        assertFalse(active.contains("val countX = (x + 16f * itemScale - mc.font.width(count) - 1f).roundToInt()"))
        assertFalse(active.contains("(INVENTORY_ITEM_BASE_SIZE + INVENTORY_COUNT_PIP_PADDING * 2f).toInt()"))
        assertFalse(active.contains("INVENTORY_COUNT_SCALE"))
        assertFalse(active.contains("pose().scale(countScale, countScale)"))
        assertFalse(active.contains("val x = (col * slotSize + (slotSize - 16 * itemScale) / 2f).roundToInt().toFloat()"))
        assertFalse(active.contains("val y = (row * slotSize + (slotSize - 16 * itemScale) / 2f).roundToInt().toFloat()"))
        assertFalse(active.contains("val slotSize = (18 * inventoryHudScale).toInt()"))
        assertTrue(itemRenderer.contains("renderFakeItem(item, x, y)"))
        assertFalse(itemRenderer.contains("renderItem(item, x, y)"))
        assertFalse(itemRenderer.contains("guiRenderState.submitPicturesInPictureState(state)"))
    }

    @Test
    fun `hud manager hover text renders to the left of hovered hud previews`() {
        val active = Files.readString(Path.of("").toAbsolutePath().resolve("src/main/kotlin/floydaddons/not/dogshit/client/clickgui/HudManager.kt"))

        assertTrue(active.contains("val tooltipWidth = maxOf(mc.font.width(hoveredHud.name).toFloat(), 150f) * 2f"))
        assertTrue(active.contains("(hoveredHud.value.screenX - tooltipWidth - 10f).coerceAtLeast(0f)"))
        assertTrue(active.contains("hoveredHud.value.screenY.toFloat()"))
        assertTrue(active.contains("guiGraphics.drawString(mc.font, hoveredHud.name, 0, 0, Colors.WHITE.rgba)"))
        assertTrue(active.contains("guiGraphics.drawWordWrap(mc.font, Component.literal(hoveredHud.description), 0, 10, 150, Colors.WHITE.rgba)"))
        assertTrue(active.contains("if (!shouldUpdateHudLayout()) {"))
        assertTrue(active.contains("if (!mc.isWindowActive) return false"))
        assertTrue(active.contains("return mc.window.screenWidth > 0 && mc.window.screenHeight > 0"))
        assertTrue(active.contains("val scale = FloydHud.scoreboardHudScale"))
        assertTrue(active.contains("(180 * scale).roundToInt() to (120 * scale).roundToInt()"))
        assertTrue(active.contains("private fun clearInteractionState() {"))
        assertTrue(active.contains("deltaX = 0f"))
        assertTrue(active.contains("deltaY = 0f"))
        assertTrue(active.contains("clearInteractionState()"))
    }

    @Test
    fun `legacy hud popup exposes minecraft font toggles under the correct entries`() {
        val legacy = Files.readString(Path.of("").toAbsolutePath().resolve("src/main/kotlin/floydaddons/not/dogshit/client/clickgui/LegacyFloydClickGUI.kt"))

        assertTrue(legacy.contains("\"Custom Scoreboard\" -> listOfNotNull(booleanSetting(FloydHud, \"Scoreboard HUD Minecraft Font\"))"))
        assertTrue(legacy.contains("LegacyModuleBrowserKind.RENDER_HUD ->"))
        assertTrue(legacy.contains("listOfNotNull(booleanSetting(FloydHud, \"Inventory HUD Minecraft Stack Font\"))"))
        assertTrue(legacy.contains("(module === FloydHud && setting.name in setOf(\"Inventory HUD Minecraft Stack Font\", \"Scoreboard HUD Minecraft Font\"))"))
        assertTrue(legacy.contains("\"Inventory HUD Minecraft Stack Font\","))
        assertTrue(legacy.contains("\"Scoreboard HUD Minecraft Font\" -> \"(Minecraft Font)\""))
    }

    @Test
    fun `click gui clears interaction state when window focus is lost`() {
        val clickGui = Files.readString(Path.of("").toAbsolutePath().resolve("src/main/kotlin/floydaddons/not/dogshit/client/clickgui/ClickGUI.kt"))
        val panel = Files.readString(Path.of("").toAbsolutePath().resolve("src/main/kotlin/floydaddons/not/dogshit/client/clickgui/Panel.kt"))
        val moduleButton = Files.readString(Path.of("").toAbsolutePath().resolve("src/main/kotlin/floydaddons/not/dogshit/client/clickgui/settings/ModuleButton.kt"))
        val renderable = Files.readString(Path.of("").toAbsolutePath().resolve("src/main/kotlin/floydaddons/not/dogshit/client/clickgui/settings/RenderableSetting.kt"))
        val colorSetting = Files.readString(Path.of("").toAbsolutePath().resolve("src/main/kotlin/floydaddons/not/dogshit/client/clickgui/settings/impl/ColorSetting.kt"))
        val stringSetting = Files.readString(Path.of("").toAbsolutePath().resolve("src/main/kotlin/floydaddons/not/dogshit/client/clickgui/settings/impl/StringSetting.kt"))

        assertTrue(clickGui.contains("if (!shouldUpdateGuiInteractions()) {"))
        assertTrue(clickGui.contains("private fun clearInteractionState() {"))
        assertTrue(clickGui.contains("SearchBar.mouseReleased()"))
        assertTrue(clickGui.contains("panels.forEach { it.clearInteractionState() }"))
        assertTrue(clickGui.contains("if (!mc.isWindowActive) return false"))
        assertTrue(panel.contains("fun clearInteractionState() {"))
        assertTrue(panel.contains("dragging = false"))
        assertTrue(panel.contains("moduleButtons.forEach { it.clearInteractionState() }"))
        assertTrue(moduleButton.contains("fun clearInteractionState() {"))
        assertTrue(moduleButton.contains("representableSettings.forEach { it.clearInteractionState() }"))
        assertTrue(renderable.contains("open fun clearInteractionState() {"))
        assertTrue(renderable.contains("listening = false"))
        assertTrue(colorSetting.contains("override fun clearInteractionState() {"))
        assertTrue(colorSetting.contains("textInputHandler.mouseReleased()"))
        assertTrue(colorSetting.contains("section = null"))
        assertTrue(stringSetting.contains("override fun clearInteractionState() {"))
        assertTrue(stringSetting.contains("textInputHandler.mouseReleased()"))
    }

    @Test
    fun `scoreboard text strips Hypixel legacy formatting codes`() {
        assertEquals("05/24/26 m80Svs8O8U", FloydHud.scoreboardPlainText(Component.literal("§705/24/26 §8m80Svs8O8U")))
    }

    @Test
    fun `scoreboard HUD renderer preserves Floyd sidebar ordering gate and footer`() {
        val root = Path.of("").toAbsolutePath()
        val floyd = Files.readString(root.resolve("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/features/hud/ScoreboardHudRenderer.java"))
        val active = Files.readString(root.resolve("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/render/FloydHud.kt"))
        val module = Files.readString(root.resolve("src/main/kotlin/floydaddons/not/dogshit/client/features/Module.kt"))
        val hudElement = Files.readString(root.resolve("src/main/kotlin/floydaddons/not/dogshit/client/clickgui/settings/impl/HUDElement.kt"))

        assertTrue(floyd.contains("public final class ScoreboardHudRenderer"))
        assertTrue(floyd.contains("Comparator.comparing(ScoreboardEntry::value).reversed()"))
        assertTrue(floyd.contains("if (!vanillaWouldRender) return;"))
        assertTrue(floyd.contains("ScoreboardDisplaySlot.fromFormatting(team.getColor())"))
        assertTrue(floyd.contains("if (lines.size() > 1)"))
        assertTrue(floyd.contains("String footerText = \"FloydAddons\""))

        assertTrue(active.contains("sortedWith(compareByDescending<PlayerScoreEntry> { it.value() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner() })"))
        assertTrue(active.contains("return if (consumeVanillaSignal) vanillaScoreboardWouldRender.getAndSet(false) else vanillaScoreboardWouldRender.get()"))
        assertTrue(active.contains("fun shouldCancelVanillaScoreboard("))
        assertTrue(active.contains("moduleEnabled && hudEnabled && customScoreboard && objectivePresent"))
        assertTrue(active.contains("DisplaySlot::teamColorToSlot"))
        assertTrue(active.contains("if (lines.size > 1) lines.removeAt(lines.lastIndex)"))
        assertTrue(active.contains("private const val SCOREBOARD_FONT_SIZE = 12f"))
        assertTrue(active.contains("private const val HUD_CHROMA_DURATION_MS = 4000L"))
        assertTrue(active.contains("private const val HUD_FADE_DURATION_MS = 8000L"))
        assertTrue(active.contains("NumberSetting(\"Scoreboard HUD Scale\", 1.0f"))
        assertTrue(active.contains("BooleanSetting(\"Scoreboard HUD Minecraft Font\", false"))
        assertTrue(active.contains("ColorSetting(\"Inventory HUD Color\""))
        assertTrue(active.contains("ColorSetting(\"Scoreboard HUD Color\""))
        assertTrue(active.contains("if (scoreboardHudMinecraftFont) mc.font.width(text) * scoreboardHudScale"))
        assertTrue(active.contains("else NVGRenderer.textWidth(text, scoreboardFontSize(), NVGRenderer.defaultFont)"))
        assertTrue(active.contains("val name = PlayerTeam.formatNameForTeam(team, entry.ownerName())"))
        assertTrue(active.contains("val score = entry.formatValue(objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT))"))
        assertTrue(active.contains("val nameText = styledText(FloydNickHider.replaceSequence(name.visualOrderText))"))
        assertTrue(active.contains("val scoreText = styledText(FloydNickHider.replaceSequence(score.visualOrderText))"))
        assertTrue(active.contains("ScoreLine(nameText, scoreText, textWidth(nameText), textWidth(scoreText))"))
        assertTrue(active.contains("return drawScoreboardBox(objective.displayName, lines, \"FloydAddons\")"))
        assertTrue(active.contains("return drawScoreboardBox(Component.literal(\"SKYBLOCK\"), lines, \"FloydAddons\")"))
        assertTrue(active.contains("INVENTORY_HUD_DEFAULT_SCALE, anchorRight = true"))
        assertTrue(active.contains("SCOREBOARD_HUD_DEFAULT_SCALE, anchorRight = true"))
        assertTrue(active.contains("private val SCOREBOARD_MIN_WIDTH_SAMPLES = listOf("))
        assertTrue(active.contains("\"Purse: 999,999,999\""))
        assertTrue(active.contains("val titleText = FloydNickHider.replaceSequence(title.visualOrderText)"))
        assertTrue(active.contains("val footerText = Component.literal(footer).visualOrderText"))
        assertTrue(active.contains("val styledTitleText = styledText(titleText)"))
        assertTrue(active.contains("val borderColors = scoreboardHudBorderColors()"))
        assertTrue(active.contains("val styledFooterText = styledFooterText(footerText)"))
        assertTrue(active.contains("val titleWidth = textWidth(styledTitleText)"))
        assertTrue(active.contains("val colonWidth = textWidth(\": \")"))
        assertTrue(active.contains("var maxLineWidth = max(max(titleWidth, footerWidth), scoreboardMinimumContentWidth())"))
        assertTrue(active.contains("val fontSize = scoreboardTextHeight()"))
        assertTrue(active.contains("val padding = ceil(6f * scoreboardHudScale).toInt()"))
        assertTrue(active.contains("val lineHeight = ceil(fontSize + 4f * scoreboardHudScale).toInt()"))
        assertTrue(active.contains("val titlePad = ceil(5f * scoreboardHudScale).toInt()"))
        assertTrue(active.contains("val textElements = mutableListOf("))
        assertTrue(active.contains("ScoreboardText(styledTitleText, (boxWidth - titleWidth) / 2f, titlePad.toFloat())"))
        assertTrue(active.contains("ScoreboardText(styledFooterText, (boxWidth - footerWidth) / 2f, (lineY + titlePad).toFloat())"))
        assertTrue(active.contains("val scoreRight = boxWidth - padding"))
        assertTrue(active.contains("ScoreboardText(line.name, padding.toFloat(), lineY.toFloat())"))
        assertTrue(active.contains("ScoreboardText(line.score, scoreRight - line.scoreWidth, lineY.toFloat())"))
        assertTrue(active.contains("drawScoreboardPanelAndText(boxWidth, boxHeight, textElements, borderColors)"))
        assertTrue(active.contains("NVGPIPRenderer.draw(this, 0, 0, boxWidth, boxHeight, renderScaleMultiplier = mc.window.guiScale.toFloat())"))
        assertTrue(active.contains("NVGRenderer.text(segment.text, segmentX, text.y, scoreboardFontSize(), segment.color, NVGRenderer.defaultFont)"))
        assertTrue(active.contains("private fun GuiGraphics.drawMinecraftScoreboardText(text: ScoreboardText) {"))
        assertTrue(active.contains("drawString(mc.font, segment.text, segmentX.roundToInt(), textY.roundToInt(), segment.color, false)"))
        assertTrue(active.contains("private fun styledText(text: FormattedCharSequence, forcedColor: Int? = null): StyledScoreboardText"))
        assertTrue(active.contains("private fun styledFooterText(text: FormattedCharSequence): StyledScoreboardText {"))
        assertTrue(active.contains("val baseOffset = hudRotationOffset(scoreboardHud.x, scoreboardHud.y, 0.38f)"))
        assertTrue(active.contains("val phase = if (chars.size == 1) baseOffset else offsetPhase(baseOffset, index.toFloat() / chars.size.toFloat())"))
        assertTrue(active.contains("accentColor(scoreboardHudColor, scoreboardHudChroma, scoreboardHudFade, scoreboardHudFadeColor, phase)"))
        assertTrue(active.contains("private fun scoreboardStyleColor(style: Style): Int"))
        assertTrue(active.contains("private fun inventoryHudAccentColor(): Int ="))
        assertTrue(active.contains("private fun inventoryHudBorderColors(): HudBorderColors ="))
        assertTrue(active.contains("hudRotationOffset(inventoryHud.x, inventoryHud.y, 0.08f)"))
        assertTrue(active.contains("private fun scoreboardHudAccentColor(): Int ="))
        assertTrue(active.contains("private fun scoreboardHudBorderColors(): HudBorderColors ="))
        assertTrue(active.contains("hudRotationOffset(scoreboardHud.x, scoreboardHud.y, 0.38f)"))
        assertTrue(active.contains("private fun accentColor(base: Color, chroma: Boolean, fade: Boolean, fadeColor: Color, offset: Float): Int"))
        assertTrue(active.contains("private fun circularBorderColors(base: Color, chroma: Boolean, fade: Boolean, fadeColor: Color, offset: Float): HudBorderColors ="))
        assertTrue(active.contains("topRight = accentColor(base, chroma, fade, fadeColor, offsetPhase(offset, 0.25f))"))
        assertTrue(active.contains("bottomRight = accentColor(base, chroma, fade, fadeColor, offsetPhase(offset, 0.5f))"))
        assertTrue(active.contains("bottomLeft = accentColor(base, chroma, fade, fadeColor, offsetPhase(offset, 0.75f))"))
        assertTrue(active.contains("private fun monochromeBorderColors(color: Int): HudBorderColors ="))
        assertTrue(active.contains("private fun hudRotationOffset(x: Int, y: Int, seed: Float): Float ="))
        assertTrue(active.contains("private fun offsetPhase(offset: Float, delta: Float): Float ="))
        assertTrue(active.contains("private fun fadeProgress(offset: Float): Float"))
        assertTrue(active.contains("val angle = animationPhase(HUD_FADE_DURATION_MS, offset) * (2f * PI.toFloat())"))
        assertTrue(active.contains("return ((sin(angle) + 1f) * 0.5f).coerceIn(0f, 1f)"))
        assertTrue(active.contains("private fun blendColors(start: Int, end: Int, progress: Float): Int"))
        assertTrue(active.contains("private fun scoreboardFontSize(): Float = SCOREBOARD_FONT_SIZE * scoreboardHudScale"))
        assertTrue(active.contains("private fun scoreboardTextHeight(): Float ="))
        assertTrue(active.contains("if (scoreboardHudMinecraftFont) mc.font.lineHeight * scoreboardHudScale"))
        assertTrue(active.contains("private fun inventoryCountTextWidth(text: String, countFontSize: Float): Float ="))
        assertTrue(active.contains("NVGRenderer.textWidth(text, countFontSize, NVGRenderer.defaultFont)"))
        assertTrue(active.contains("private fun inventoryCountTextHeight(countFontSize: Float): Float ="))
        assertTrue(active.contains("countFontSize"))
        assertTrue(active.contains("private fun scoreboardMinimumContentWidth(): Float ="))
        assertTrue(active.contains("private fun animationPhase(durationMs: Long, offset: Float): Float ="))
        assertTrue(active.contains("val hue = animationPhase(HUD_CHROMA_DURATION_MS, offset)"))
        assertTrue(active.contains("private data class ScoreLine(val name: StyledScoreboardText, val score: StyledScoreboardText, val nameWidth: Float, val scoreWidth: Float)"))
        assertTrue(active.contains("private data class ScoreboardText(val value: StyledScoreboardText, val x: Float, val y: Float)"))
        assertTrue(active.contains("private data class HudBorderColors(val topLeft: Int, val topRight: Int, val bottomRight: Int, val bottomLeft: Int)"))
        assertTrue(active.contains("private data class ScoreboardTextSegment(val text: String, val color: Int, val minecraftFont: Boolean = false)"))
        assertTrue(active.contains("fillPanel(boxWidth, boxHeight, borderColors)"))
        assertTrue(active.contains("private fun GuiGraphics.fillPanel(width: Int, height: Int, borderColors: HudBorderColors = monochromeBorderColors(chromaColor(0f)))"))
        assertTrue(active.contains("borderColors.topLeft, borderColors.topRight, borderColors.bottomRight, borderColors.bottomLeft, 2f"))
        assertTrue(module.contains("anchorRight: Boolean = false"))
        assertTrue(module.contains("HUDSetting(name, x, y, scale, toggleable, anchorRight, desc, this, block)"))
        assertTrue(hudElement.contains("val anchorRight: Boolean = false"))
        assertTrue(hudElement.contains("var screenX: Int = x"))
        assertTrue(hudElement.contains("var screenY: Int = y"))
        assertTrue(hudElement.contains("private var lastScreenWidth: Int = -1"))
        assertTrue(hudElement.contains("private var lastScreenHeight: Int = -1"))
        assertTrue(hudElement.contains("it.screenX = screenX"))
        assertTrue(hudElement.contains("it.screenY = screenY"))
        assertTrue(hudElement.contains("it.lastScreenWidth = lastScreenWidth"))
        assertTrue(hudElement.contains("it.lastScreenHeight = lastScreenHeight"))
        assertTrue(hudElement.contains("screenX = x"))
        assertTrue(hudElement.contains("screenY = y"))
        assertTrue(hudElement.contains("preserveScreenAnchor(screenWidth)"))
        assertTrue(hudElement.contains("clampToScreen(screenWidth, screenHeight, width, height)"))
        assertTrue(hudElement.contains("if (anchorRight && previousWidth > 0 && previousWidth != width)"))
        assertTrue(hudElement.contains("screenX += ((previousWidth - width) * scale).roundToInt()"))
        assertTrue(hudElement.contains("lastScreenWidth = screenWidth"))
        assertTrue(hudElement.contains("lastScreenHeight = screenHeight"))
        assertTrue(hudElement.contains("private fun preserveScreenAnchor(screenWidth: Int) {"))
        assertTrue(hudElement.contains("val preserveRight = anchorRight || x + scaledWidth / 2 >= lastScreenWidth / 2"))
        assertTrue(hudElement.contains("val rightMargin = (lastScreenWidth - (x + scaledWidth)).coerceAtLeast(0)"))
        assertTrue(hudElement.contains("screenWidth - scaledWidth - rightMargin"))
        assertTrue(hudElement.contains("screenY = y"))
        assertTrue(hudElement.contains("private fun clampToScreen(screenWidth: Int, screenHeight: Int, width: Int = this.width, height: Int = this.height) {"))
        assertTrue(hudElement.contains("screenX = screenX.coerceIn(0, (screenWidth - (width * scale).roundToInt()).coerceAtLeast(0))"))
        assertTrue(hudElement.contains("screenY = screenY.coerceIn(0, (screenHeight - (height * scale).roundToInt()).coerceAtLeast(0))"))
        assertFalse(hudElement.contains("val preserveBottom = y + scaledHeight / 2 >= lastScreenHeight / 2"))
        assertFalse(hudElement.contains("val bottomMargin = (lastScreenHeight - (y + scaledHeight)).coerceAtLeast(0)"))
        assertFalse(hudElement.contains("screenHeight - scaledHeight - bottomMargin"))
        assertTrue(hudElement.contains("fun isHovered(): Boolean = isAreaHovered(screenX.toFloat(), screenY.toFloat(), width * scale, height * scale)"))
        assertFalse(active.contains("PlayerTeam.formatNameForTeam(team, entry.ownerName()).string"))
        assertFalse(active.contains("entry.formatValue(objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT)).string"))
        assertFalse(active.contains("scoreboardPlainText(PlayerTeam.formatNameForTeam(team, entry.ownerName()))"))
        assertFalse(active.contains("scoreboardPlainText(entry.formatValue(objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT)))"))
        assertFalse(active.contains("return drawScoreboardBox(objective.displayName.string, lines, \"FloydAddons\")"))
        assertFalse(active.contains("val titleText = title.visualOrderText"))
        assertFalse(active.contains("drawString(\n                mc.font,\n                text.value,"))
        assertFalse(active.contains("ScoreboardText(line.score, padding.toFloat(), lineY.toFloat(), 0xFFFFFFFF.toInt(), shadow = false)"))
        assertFalse(active.contains("ScoreboardText(titleText, (boxWidth - titleWidth) / 2f, titlePad.toFloat(), 0xFFFFFFFF.toInt(), shadow = false)"))
        assertFalse(active.contains("ScoreboardText(title, (boxWidth - titleWidth) / 2f, titlePad.toFloat(), scoreboardChroma, shadow = false)"))
        assertFalse(active.contains("ScoreboardText(title, (boxWidth - titleWidth) / 2f, titlePad.toFloat(), chromaColor(0f), shadow = true)"))
        assertFalse(active.contains("val styledFooterText = styledText(footerText, forcedColor = scoreboardAccent)"))
        assertFalse(active.contains("ScoreboardText(footer, (boxWidth - footerWidth) / 2f, (lineY + titlePad).toFloat(), chromaColor(0.5f), shadow = true)"))
        assertFalse(active.contains("drawScoreboardText(boxWidth, boxHeight, textElements)"))
        assertFalse(active.contains("NVGRenderer.rect(0f, 0f, boxWidth.toFloat(), boxHeight.toFloat(), 0x40000000, radius)"))
    }
}
