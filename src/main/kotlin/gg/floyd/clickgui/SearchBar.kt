package gg.floyd.clickgui

import gg.floyd.Branding
import gg.floyd.features.impl.render.ClickGUIModule
import gg.floyd.utils.ChromaCache
import gg.floyd.utils.Colors
import gg.floyd.utils.font.FontEpochCache
import gg.floyd.utils.ui.TextInputHandler
import gg.floyd.utils.ui.isAreaHovered
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import java.awt.Desktop
import java.net.URI

object SearchBar {
    private const val BAR_WIDTH = 240f
    private const val BAR_HEIGHT = 22f
    private const val INNER_PADDING = 12f
    private const val BOTTOM_MARGIN = 64f
    private const val TITLE_GAP = 13f
    private const val BAR_TOP_GAP = 16f
    private const val COMMUNITY_GAP = 12f
    private const val LINKS_GAP = 10f
    private const val LINKS_SPACING = 10f
    private const val TITLE_TEXT = "FloydAddons"
    private const val COFFEE_TEXT = "Buy Me A Coffee!"
    private const val COMMUNITY_TEXT = Branding.COMMUNITY_HEADER
    private const val GITHUB_TEXT = "github"
    private const val DISCORD_TEXT = ".gg/FLOYD"
    private const val PLACEHOLDER_TEXT = "Search modules"
    private const val SEARCH_TEXT_SIZE = 11f
    private const val TITLE_SIZE = 13f
    private const val SUBTITLE_SIZE = 7.5f
    private const val COMMUNITY_SIZE = 7.5f
    private const val LINK_SIZE = 8f

    var currentSearch = ""
        private set(value) {
            if (value == field || value.length > 16) return
            field = value
            searchWidth.invalidate()
        }

    // Epoch-checked so the cached widths re-measure after a mid-session font reload.
    private val placeHolderWidth = FontEpochCache { NVGRenderer.textWidth(PLACEHOLDER_TEXT, SEARCH_TEXT_SIZE, NVGRenderer.defaultFont) }
    private val searchWidth = FontEpochCache { NVGRenderer.textWidth(currentSearch, SEARCH_TEXT_SIZE, NVGRenderer.defaultFont) }

    private val textInputHandler = TextInputHandler(
        textProvider = { currentSearch },
        textSetter = { currentSearch = it }
    ).apply {
        fontSizeOverride = SEARCH_TEXT_SIZE
        textPaddingX = 0f
        textPaddingY = 1f
    }

    private var githubBounds = LinkRect.ZERO
    private var discordBounds = LinkRect.ZERO
    private var coffeeBounds = LinkRect.ZERO

    fun draw(screenWidth: Float, screenHeight: Float, mouseX: Float, mouseY: Float) {
        val centerX = screenWidth / 2f
        val x = centerX - BAR_WIDTH / 2f
        val y = screenHeight - BOTTOM_MARGIN
        val titleWidth = NVGRenderer.textWidth(TITLE_TEXT, TITLE_SIZE, NVGRenderer.defaultFont)
        val coffeeWidth = NVGRenderer.textWidth(COFFEE_TEXT, SUBTITLE_SIZE, NVGRenderer.defaultFont)
        val communityWidth = NVGRenderer.textWidth(COMMUNITY_TEXT, COMMUNITY_SIZE, NVGRenderer.defaultFont)
        val githubWidth = NVGRenderer.textWidth(GITHUB_TEXT, LINK_SIZE, NVGRenderer.defaultFont)
        val discordWidth = NVGRenderer.textWidth(DISCORD_TEXT, LINK_SIZE, NVGRenderer.defaultFont)
        val coffeeLinkWidth = NVGRenderer.textWidth(COFFEE_TEXT, SUBTITLE_SIZE, NVGRenderer.defaultFont)
        val titleY = y - BAR_TOP_GAP - TITLE_GAP
        val coffeeY = titleY + TITLE_GAP
        val communityY = y + BAR_HEIGHT + COMMUNITY_GAP
        val linkY = communityY + LINKS_GAP
        val linksStartX = centerX - (githubWidth + LINKS_SPACING + discordWidth) / 2f

        NVGRenderer.text(TITLE_TEXT, centerX - titleWidth / 2f, titleY, TITLE_SIZE, chromaColor(0f), NVGRenderer.defaultFont)
        NVGRenderer.text(COFFEE_TEXT, centerX - coffeeWidth / 2f, coffeeY, SUBTITLE_SIZE, chromaColor(0.08f), NVGRenderer.defaultFont)

        ClickGUI.drawChrome(x, y, BAR_WIDTH, BAR_HEIGHT, 7f, hovered = true, accented = currentSearch.isNotEmpty())

        val inputHeight = SEARCH_TEXT_SIZE + 4f
        val inputY = y + (BAR_HEIGHT - inputHeight) / 2f
        val inputWidth = BAR_WIDTH
        val centeredTextWidth = if (currentSearch.isEmpty()) placeHolderWidth.get() else searchWidth.get()
        val centeredPadding = ((BAR_WIDTH - centeredTextWidth) / 2f).coerceAtLeast(INNER_PADDING)

        if (currentSearch.isEmpty()) {
            NVGRenderer.text(
                PLACEHOLDER_TEXT,
                x + centeredPadding,
                inputY + 1f,
                SEARCH_TEXT_SIZE,
                ClickGUI.oringoTextMuted.rgba,
                NVGRenderer.defaultFont
            )
        }
        textInputHandler.x = x
        textInputHandler.y = inputY
        textInputHandler.width = inputWidth
        textInputHandler.height = inputHeight
        textInputHandler.textPaddingX = centeredPadding
        textInputHandler.draw(mouseX, mouseY)

        NVGRenderer.text(COMMUNITY_TEXT, centerX - communityWidth / 2f, communityY, COMMUNITY_SIZE, Colors.WHITE.rgba, NVGRenderer.defaultFont)
        githubBounds = LinkRect(linksStartX, linkY, githubWidth, LINK_SIZE)
        discordBounds = LinkRect(linksStartX + githubWidth + LINKS_SPACING, linkY, discordWidth, LINK_SIZE)
        coffeeBounds = LinkRect(centerX - coffeeLinkWidth / 2f, coffeeY, coffeeLinkWidth, SUBTITLE_SIZE)
        val githubHovered = isAreaHovered(githubBounds.left.toFloat(), githubBounds.top.toFloat(), githubBounds.width.toFloat(), githubBounds.height.toFloat(), true)
        val discordHovered = isAreaHovered(discordBounds.left.toFloat(), discordBounds.top.toFloat(), discordBounds.width.toFloat(), discordBounds.height.toFloat(), true)
        val coffeeHovered = isAreaHovered(coffeeBounds.left.toFloat(), coffeeBounds.top.toFloat(), coffeeBounds.width.toFloat(), coffeeBounds.height.toFloat(), true)
        NVGRenderer.text(GITHUB_TEXT, linksStartX, linkY, LINK_SIZE, if (githubHovered) Colors.WHITE.rgba else chromaColor(0f), NVGRenderer.defaultFont)
        NVGRenderer.text(DISCORD_TEXT, linksStartX + githubWidth + LINKS_SPACING, linkY, LINK_SIZE, if (discordHovered) Colors.WHITE.rgba else chromaColor(0.12f), NVGRenderer.defaultFont)
        if (coffeeHovered) {
            NVGRenderer.text(COFFEE_TEXT, centerX - coffeeLinkWidth / 2f, coffeeY, SUBTITLE_SIZE, Colors.WHITE.rgba, NVGRenderer.defaultFont)
        }
    }

    fun predictedBounds(screenWidth: Float, screenHeight: Float): FloatArray {
        val x = screenWidth / 2f - BAR_WIDTH / 2f
        val y = screenHeight - BOTTOM_MARGIN
        val top = y - BAR_TOP_GAP - TITLE_GAP - 2f
        val bottom = y + BAR_HEIGHT + COMMUNITY_GAP + LINKS_GAP + LINK_SIZE + 4f
        return floatArrayOf(x, top, x + BAR_WIDTH, bottom)
    }

    fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (click.button() == 0) {
            when {
                githubBounds.contains(mouseX, mouseY) -> {
                    runCatching { Desktop.getDesktop().browse(URI(Branding.GITHUB_URL)) }
                    return true
                }
                discordBounds.contains(mouseX, mouseY) -> {
                    runCatching { Desktop.getDesktop().browse(URI(Branding.DISCORD_URL)) }
                    return true
                }
                coffeeBounds.contains(mouseX, mouseY) -> {
                    runCatching { Desktop.getDesktop().browse(URI(Branding.COFFEE_URL)) }
                    return true
                }
            }
        }
        return textInputHandler.mouseClicked(mouseX, mouseY, click)
    }

    fun mouseReleased() {
        textInputHandler.mouseReleased()
    }

    fun keyPressed(input: KeyEvent): Boolean {
        return textInputHandler.keyPressed(input)
    }

    fun keyTyped(input: CharacterEvent): Boolean {
        return textInputHandler.keyTyped(input)
    }

    private fun chromaColor(offset: Float): Int = 0xFF000000.toInt() or ChromaCache.rgbFor(offset)

    private data class LinkRect(val left: Float, val top: Float, val width: Float, val height: Float) {
        fun contains(x: Float, y: Float): Boolean = x >= left && x <= left + width && y >= top && y <= top + height

        companion object {
            val ZERO = LinkRect(0f, 0f, 0f, 0f)
        }
    }
}
