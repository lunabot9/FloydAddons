package gg.floyd.features.impl.misc

import gg.floyd.utils.ui.rendering.NVGRenderer
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.events.ContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.options.OptionsSubScreen
import net.minecraft.client.gui.screens.packs.PackSelectionScreen
import java.lang.reflect.Field
import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object FloydMenuScreenStyling {
    private const val SODIUM_VIDEO_SETTINGS_SCREEN = "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen"
    private const val REESES_VIDEO_SETTINGS_SCREEN = "me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen"
    private const val SODIUM_GUI_PREFIX = "net.caffeinemc.mods.sodium.client.gui."
    private const val REESES_GUI_PREFIX = "me.flashyreese.mods.reeses_sodium_options.client.gui."
    private val pendingTextRuns = ArrayList<MenuTextRun>()

    /**
     * Whether Floyd should paint its media background behind [screen] instead of the vanilla
     * panorama / blurred world. Targets every "default background" screen — the whole title flow
     * out of game and the pause/options/settings flow in game.
     *
     * Uses only `is` type checks (NOT class-name strings): the shipped jar runs on intermediary
     * mappings where e.g. OptionsScreen is `net.minecraft.class_429`, so a package-name string match
     * silently fails at runtime — the bug that left the in-game options screen unstyled.
     *
     * Excluded: container/inventory and chat screens (they intentionally show the live world), and
     * Floyd's own screens (they draw their own background). The hook fires from
     * `Screen.renderBackground`, so screens that fully override it are unaffected regardless.
     */
    @JvmStatic
    fun shouldReplaceBackground(screen: Screen): Boolean {
        if (!FloydCompatibility.shouldUseCustomMainMenu()) return false
        if (screen is FloydMainMenuScreen) return false
        if (screen is TitleScreen) return false
        if (screen is AbstractContainerScreen<*>) return false
        if (screen is ChatScreen) return false
        if (usesShaderWrapper(screen)) return true
        if (isFloydScreen(screen)) return false
        return true
    }

    /**
     * @return true if the media background was drawn (so the caller cancels the vanilla one).
     */
    @JvmStatic
    fun renderBackground(context: GuiGraphics): Boolean {
        val width = context.guiWidth()
        val height = context.guiHeight()
        if (width > 0 && height > 0) {
            // Never expose a black frame while the shader pass or its backing target warms up.
            context.fill(0, 0, width, height, FloydCustomMainMenu.skyHorizonColor.rgba or (0xFF shl 24))
        }
        FloydMenuVideoBackground.render(context)
        return true
    }

    @JvmStatic
    fun renderOverlay(screen: Screen, context: GuiGraphics, partialTick: Float) {
        if (!usesShaderWrapper(screen) && !shouldUseQueuedCustomText(screen)) {
            pendingTextRuns.clear()
            return
        }

        val multiplier = Minecraft.getInstance().window.guiScale.toFloat() / NVGRenderer.devicePixelRatio()
        queueScreenTitle(screen)
        if (usesShaderWrapper(screen)) {
            widgetTree(screen)
                .forEach { (widget, clip) -> queueWidgetLabel(widget, clip) }
        }

        if (pendingTextRuns.isEmpty()) return

        val runs = ArrayList(pendingTextRuns)
        pendingTextRuns.clear()
        for (run in runs) {
            val textWidth = ceil(NVGRenderer.textWidth(run.text, run.size, NVGRenderer.defaultFont).toDouble()).toInt()
            if (textWidth <= 0) continue
            val left = run.slotLeft ?: floor(run.x.toDouble()).toInt() - 8
            val top = (run.slotTop?.minus(4)) ?: (floor(run.y.toDouble()).toInt() - 8)
            val width = run.slotWidth ?: (textWidth + 16)
            val height = (run.slotHeight?.plus(8)) ?: (ceil(run.size.toDouble()).toInt() + 16)
            if (width <= 0 || height <= 0) continue
            NVGPIPRenderer.draw(context, left, top, width, height, multiplier, localCoordinates = true) {
                NVGRenderer.textImmediate(run.text, run.x - left, run.y - top, run.size, run.color, NVGRenderer.defaultFont)
            }
        }
    }

    @JvmStatic
    fun shouldSuppressWidgetFont(screen: Screen): Boolean =
        usesShaderWrapper(screen) || shouldUseQueuedCustomText(screen)

    @JvmStatic
    fun shouldSuppressListBackground(screen: Screen): Boolean = usesShaderWrapper(screen)

    @JvmStatic
    fun shouldUseDirectBackground(screen: Screen?): Boolean =
        screen?.javaClass?.name == SODIUM_VIDEO_SETTINGS_SCREEN ||
            screen?.javaClass?.name == REESES_VIDEO_SETTINGS_SCREEN

    @JvmStatic
    fun shouldUseQueuedCustomText(screen: Screen?): Boolean =
        screen?.javaClass?.name == SODIUM_VIDEO_SETTINGS_SCREEN ||
            screen?.javaClass?.name == REESES_VIDEO_SETTINGS_SCREEN

    @JvmStatic
    fun softenCustomScreenFill(screen: Screen?, color: Int): Int {
        if (screen == null || !shouldReplaceBackground(screen)) return color
        if (!isSodiumFamilyScreen(screen)) return color

        val alpha = color ushr 24
        if (alpha <= 0x40) return color

        val red = (color ushr 16) and 0xFF
        val green = (color ushr 8) and 0xFF
        val blue = color and 0xFF
        val maxChannel = maxOf(red, green, blue)
        val minChannel = minOf(red, green, blue)
        if (maxChannel - minChannel > 24 || maxChannel > 96) return color

        val softenedAlpha = min(alpha, if (maxChannel < 32) 0x20 else 0x30)
        val liftedChannel = maxOf(maxChannel, 40)
        return (softenedAlpha shl 24) or (liftedChannel shl 16) or (liftedChannel shl 8) or liftedChannel
    }

    @JvmStatic
    @JvmOverloads
    fun queueText(screen: Screen?, text: String, x: Int, y: Int, color: Int, size: Float = 9f) {
        if (screen == null || (!usesShaderWrapper(screen) && !shouldUseQueuedCustomText(screen))) return
        val cleanText = sanitizeQueuedText(screen, text)
        if (cleanText.isBlank()) return
        pendingTextRuns.add(MenuTextRun(cleanText, x.toFloat(), y.toFloat(), color, size))
    }

    private fun isFloydScreen(screen: Screen): Boolean =
        screen.javaClass.name.startsWith("gg.floyd.")

    private fun usesShaderWrapper(screen: Screen): Boolean {
        if (
            screen is FloydJoinMultiplayerScreen ||
            screen is JoinMultiplayerScreen ||
            screen is FloydOptionsScreen ||
            screen is FloydSelectWorldScreen ||
            screen is OptionsSubScreen ||
            screen is PackSelectionScreen
        ) {
            return true
        }

        return screenLinkChain(screen).any { linked ->
            linked is FloydJoinMultiplayerScreen ||
                linked is JoinMultiplayerScreen ||
                linked is FloydOptionsScreen ||
                linked is FloydSelectWorldScreen
        }
    }

    private fun isSodiumFamilyScreen(screen: Screen): Boolean {
        val name = screen.javaClass.name
        return name.startsWith(SODIUM_GUI_PREFIX) || name.startsWith(REESES_GUI_PREFIX)
    }

    private fun queueScreenTitle(screen: Screen) {
        val title = sanitizeQueuedText(screen, screen.title.string)
        if (title.isBlank()) return

        val size = 11f
        val textWidth = NVGRenderer.textWidth(title, size, NVGRenderer.defaultFont)
        val x = (screen.width - textWidth) * 0.5f
        pendingTextRuns.add(
            MenuTextRun(
                text = title,
                x = x,
                y = 15f,
                color = 0xFFD8D8D8.toInt(),
                size = size,
                slotLeft = 0,
                slotTop = 0,
                slotWidth = screen.width,
                slotHeight = 28
            )
        )
    }

    /**
     * Vanilla submenu screens are not our subclasses, but they keep a backlink such as
     * `lastScreen`, `previousScreen`, or `parent`. Walking that chain lets the wrapper styling
     * cover the full options/menu subtree without hardcoding every screen class.
     */
    private fun screenLinkChain(screen: Screen): Sequence<Screen> = sequence {
        val seen = IdentityHashMap<Screen, Boolean>()
        val queue = ArrayDeque<Screen>()
        queue.add(screen)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (seen.put(current, true) != null) continue
            yield(current)
            linkedScreens(current).forEach(queue::addLast)
        }
    }

    private fun linkedScreens(screen: Screen): List<Screen> {
        val linked = ArrayList<Screen>(2)
        var type: Class<*>? = screen.javaClass
        while (type != null && Screen::class.java.isAssignableFrom(type)) {
            type.declaredFields
                .asSequence()
                .filter { it.type == Screen::class.java }
                .filter { field ->
                    val name = field.name.lowercase()
                    name.contains("last") || name.contains("previous") || name.contains("parent")
                }
                .mapNotNull { field -> readLinkedScreen(field, screen) }
                .forEach(linked::add)
            type = type.superclass
        }
        return linked
    }

    private fun readLinkedScreen(field: Field, instance: Screen): Screen? {
        return runCatching {
            field.isAccessible = true
            field.get(instance) as? Screen
        }.getOrNull()
    }

    private fun queueWidgetLabel(widget: AbstractWidget, clip: ClipRect) {
        if (widget is AbstractSliderButton) return
        if (!clip.contains(widget.x, widget.y, widget.width, widget.height)) return
        val text = widget.message.string
        if (text.isBlank()) return
        val size = when (widget) {
            is StringWidget -> 11f
            else -> 9f
        }
        val color = when (widget) {
            is Button -> {
                if (!widget.active) 0xA0B6B6B6.toInt()
                else if (widget.isHovered) 0xFFFFFFFF.toInt()
                else 0xE6ECECEC.toInt()
            }
            else -> 0xFFD8D8D8.toInt()
        }
        val textWidth = NVGRenderer.textWidth(text, size, NVGRenderer.defaultFont)
        val x = widget.x + (widget.width - textWidth) * 0.5f
        val y = widget.y + (widget.height - size) * 0.5f + 1f
        pendingTextRuns.add(
            MenuTextRun(
                text = text,
                x = x,
                y = y,
                color = color,
                size = size,
                slotLeft = widget.x,
                slotTop = widget.y,
                slotWidth = widget.width,
                slotHeight = widget.height
            )
        )
    }

    private fun widgetTree(screen: Screen): Sequence<QueuedWidget> = sequence {
        val seen = IdentityHashMap<GuiEventListener, Boolean>()
        val screenClip = ClipRect(0, 0, screen.width, screen.height)
        val queue = ArrayDeque<QueuedListener>()
        screen.children().forEach { queue.addLast(QueuedListener(it, screenClip)) }
        while (queue.isNotEmpty()) {
            val (current, clip) = queue.removeFirst()
            if (seen.put(current, true) != null) continue
            val nextClip = if (current is AbstractWidget) {
                clip.intersect(ClipRect(current.x, current.y, current.width, current.height))
            } else {
                clip
            }
            if (current is AbstractWidget) yield(QueuedWidget(current, clip))
            if (current is ContainerEventHandler) {
                current.children().forEach { queue.addLast(QueuedListener(it, nextClip)) }
            }
        }
    }

    private data class QueuedListener(
        val listener: GuiEventListener,
        val clip: ClipRect
    )

    private data class QueuedWidget(
        val widget: AbstractWidget,
        val clip: ClipRect
    )

    private data class ClipRect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) {
        fun contains(otherX: Int, otherY: Int, otherWidth: Int, otherHeight: Int): Boolean {
            val right = otherX + otherWidth
            val bottom = otherY + otherHeight
            return otherWidth > 0 &&
                otherHeight > 0 &&
                otherX >= x &&
                otherY >= y &&
                right <= x + width &&
                bottom <= y + height
        }

        fun intersect(other: ClipRect): ClipRect {
            val left = maxOf(x, other.x)
            val top = maxOf(y, other.y)
            val right = minOf(x + width, other.x + other.width)
            val bottom = minOf(y + height, other.y + other.height)
            return ClipRect(left, top, maxOf(0, right - left), maxOf(0, bottom - top))
        }
    }

    private data class MenuTextRun(
        val text: String,
        val x: Float,
        val y: Float,
        val color: Int,
        val size: Float,
        val slotLeft: Int? = null,
        val slotTop: Int? = null,
        val slotWidth: Int? = null,
        val slotHeight: Int? = null
    )

    private fun sanitizeQueuedText(screen: Screen, text: String): String {
        val withoutFormatting = stripFormattingCodes(text)
        return if (shouldUseQueuedCustomText(screen)) stripDecorativePrefix(withoutFormatting) else withoutFormatting
    }

    private fun stripFormattingCodes(text: String): String {
        if ('§' !in text) return text
        val cleaned = StringBuilder(text.length)
        var skip = false
        for (char in text) {
            if (skip) {
                skip = false
                continue
            }
            if (char == '§') {
                skip = true
                continue
            }
            cleaned.append(char)
        }
        return cleaned.toString()
    }

    private fun stripDecorativePrefix(text: String): String {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char.isWhitespace()) {
                index++
                continue
            }
            if (char.isLetterOrDigit() || char == '$' || char == '[' || char == '(') break
            index++
        }
        return text.substring(index).trimStart()
    }

    internal fun drawWord(text: String, x: Float, y: Float, size: Float, alpha: Float) {
        NVGRenderer.text(text, x, y, size, colorGray(210, alpha), NVGRenderer.defaultFont)
    }

    private fun colorGray(gray: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        val g = gray.coerceIn(0, 255)
        return (a shl 24) or (g shl 16) or (g shl 8) or g
    }
}
