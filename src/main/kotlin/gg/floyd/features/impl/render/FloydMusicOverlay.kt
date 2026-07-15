package gg.floyd.features.impl.render

import com.mojang.blaze3d.platform.NativeImage
import gg.floyd.FloydAddonsMod
import gg.floyd.clickgui.HudSizeRegistry
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.KeybindSetting
import gg.floyd.events.TickEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.media.MediaSnapshot
import gg.floyd.utils.media.WindowsMediaKeys
import gg.floyd.utils.media.WindowsMediaSession
import gg.floyd.utils.font.ClickGuiFont
import gg.floyd.utils.render.HudPanel
import net.minecraft.client.gui.*
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
import java.io.ByteArrayInputStream
import java.util.Base64
import kotlin.math.roundToInt

/** Windows now-playing HUD backed by the shared system media session used by Spotify and browsers. */
object FloydMusicOverlay : Module(
    name = "Music Overlay",
    category = Category.RENDER,
    description = "Displays the current Spotify or YouTube Music song, artwork and playback time in a movable HUD.",
    toggled = false,
) {
    private const val WIDTH = 250
    private const val CONTENT_HEIGHT = 58
    private const val ART_SIZE = 54
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
    private const val MUTED_TEXT_COLOR = 0xFFAAAAAA.toInt()
    private const val TRACK_COLOR = 0x553A3A3A
    private val artworkId = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "music_overlay_artwork")

    private val showOverlay by BooleanSetting(
        "Show Overlay",
        true,
        desc = "Shows the now-playing HUD. Media keybinds keep working while this is off.",
    )

    private val overlayHud by HUD(
        "Music Overlay",
        "Displays the active Spotify or YouTube Music session with artwork and playback progress.",
        false,
        24,
        240,
        1f,
    ) { example -> drawOverlay(example) }

    private val volumeUp by KeybindSetting("Volume Up", GLFW.GLFW_KEY_UNKNOWN, "Raises Windows media volume.").onPress {
        WindowsMediaKeys.send(WindowsMediaKeys.Action.VOLUME_UP)
    }
    private val volumeDown by KeybindSetting("Volume Down", GLFW.GLFW_KEY_UNKNOWN, "Lowers Windows media volume.").onPress {
        WindowsMediaKeys.send(WindowsMediaKeys.Action.VOLUME_DOWN)
    }
    private val nextSong by KeybindSetting("Next Song", GLFW.GLFW_KEY_UNKNOWN, "Skips to the next song.").onPress {
        WindowsMediaKeys.send(WindowsMediaKeys.Action.NEXT)
    }
    private val previousSong by KeybindSetting("Previous Song", GLFW.GLFW_KEY_UNKNOWN, "Returns to the previous song.").onPress {
        WindowsMediaKeys.send(WindowsMediaKeys.Action.PREVIOUS)
    }
    private val playPause by KeybindSetting("Play/Pause", GLFW.GLFW_KEY_UNKNOWN, "Pauses or resumes the active song.").onPress {
        WindowsMediaKeys.send(WindowsMediaKeys.Action.PLAY_PAUSE)
    }

    private var artworkTexture: DynamicTexture? = null
    private var uploadedArtworkKey = ""

    init {
        HudSizeRegistry.register("Music Overlay") { overlaySize() }
        on<TickEvent.ClientEnd> { syncArtwork(WindowsMediaSession.snapshot()) }
    }

    override fun onEnable() {
        super.onEnable()
        WindowsMediaSession.start()
    }

    override fun onDisable() {
        WindowsMediaSession.stop()
        clearArtwork()
        super.onDisable()
    }

    fun state(): Map<String, Any?> {
        val media = WindowsMediaSession.snapshot()
        return mapOf(
            "enabled" to enabled,
            "showOverlay" to showOverlay,
            "available" to media.available,
            "title" to media.title,
            "artist" to media.artist,
            "positionMs" to media.displayedPositionMs(),
            "durationMs" to media.durationMs,
            "playing" to media.playing,
            "hud" to mapOf("x" to overlayHud.x, "y" to overlayHud.y, "scale" to overlayHud.scale),
        )
    }

    private fun overlaySize(): Pair<Int, Int> {
        val padding = FloydPanelStyle.paddingFor(FloydPanelStyle.PanelTarget.MUSIC_OVERLAY).coerceAtLeast(0)
        return WIDTH to (CONTENT_HEIGHT + padding * 2)
    }

    private fun GuiGraphics.drawOverlay(example: Boolean): Pair<Int, Int> {
        if (!showOverlay) return 0 to 0
        val media = WindowsMediaSession.snapshot().takeIf { it.available } ?: if (example) exampleMedia() else return 0 to 0
        val font = ClickGuiFont.font
        val (width, height) = overlaySize()
        val target = FloydPanelStyle.PanelTarget.MUSIC_OVERLAY
        val padding = FloydPanelStyle.paddingFor(target).coerceAtLeast(0)
        HudPanel.fillPanel(this, 0, 0, width, height, target, HudPanel.panelBorderColors(target, overlayHud.x, overlayHud.y))

        val artY = padding + (CONTENT_HEIGHT - ART_SIZE) / 2
        val texture = artworkTexture
        if (!example && texture != null) {
            blit(RenderPipelines.GUI_TEXTURED, artworkId, padding, artY, 0f, 0f, ART_SIZE, ART_SIZE, ART_SIZE, ART_SIZE)
        } else {
            fill(padding, artY, padding + ART_SIZE, artY + ART_SIZE, 0xFF282828.toInt())
            val note = "♪"
            drawString(font, note, padding + (ART_SIZE - font.width(note)) / 2, artY + (ART_SIZE - font.lineHeight) / 2, MUTED_TEXT_COLOR, false)
        }

        val textX = padding + ART_SIZE + 8
        val textWidth = (width - textX - padding).coerceAtLeast(20)
        val title = font.plainSubstrByWidth(media.title.ifBlank { "Unknown song" }, textWidth)
        val artist = font.plainSubstrByWidth(media.artist.ifBlank { sourceName(media.source) }, textWidth)
        drawString(font, title, textX, padding + 3, TEXT_COLOR, false)
        drawString(font, artist, textX, padding + 16, MUTED_TEXT_COLOR, false)

        val progressY = padding + 34
        val progressWidth = textWidth
        fill(textX, progressY, textX + progressWidth, progressY + 3, TRACK_COLOR)
        val position = media.displayedPositionMs()
        val progress = if (media.durationMs > 0L) (position.toDouble() / media.durationMs).coerceIn(0.0, 1.0) else 0.0
        val filled = (progressWidth * progress).roundToInt()
        if (filled > 0) {
            val accent = HudPanel.accentColor(FloydPanelStyle.borderColorFor(target), HudPanel.hudRotationOffset(overlayHud.x, overlayHud.y, 0.62f))
            fill(textX, progressY, textX + filled, progressY + 3, accent)
        }

        val currentTime = formatTime(position)
        val duration = formatTime(media.durationMs)
        drawString(font, currentTime, textX, padding + 43, MUTED_TEXT_COLOR, false)
        drawString(font, duration, textX + progressWidth - font.width(duration), padding + 43, MUTED_TEXT_COLOR, false)
        drawPlaybackIcon(media.playing, textX + progressWidth / 2, padding + 47, MUTED_TEXT_COLOR)
        return width to height
    }

    private fun GuiGraphics.drawPlaybackIcon(playing: Boolean, centerX: Int, centerY: Int, color: Int) {
        if (playing) {
            // A row-built triangle keeps a straight left edge and a clean one-pixel right tip.
            for (row in -4..4) {
                val width = (4 - kotlin.math.abs(row)) * 3 / 2 + 1
                fill(centerX - 3, centerY + row, centerX - 3 + width, centerY + row + 1, color)
            }
        } else {
            fill(centerX - 3, centerY - 3, centerX - 1, centerY + 4, color)
            fill(centerX + 1, centerY - 3, centerX + 3, centerY + 4, color)
        }
    }

    private fun syncArtwork(media: MediaSnapshot) {
        if (!media.available) {
            if (uploadedArtworkKey.isNotEmpty()) clearArtwork()
            return
        }
        if (media.artworkKey == uploadedArtworkKey) return
        clearArtwork()
        uploadedArtworkKey = media.artworkKey
        val encoded = media.artworkBase64 ?: return
        val bytes = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: return
        if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) return
        val image = runCatching { ByteArrayInputStream(bytes).use(NativeImage::read) }.getOrNull() ?: return
        val texture = DynamicTexture({ "floydaddons_music_overlay_artwork" }, image)
        artworkTexture = texture
        mc.textureManager.register(artworkId, texture)
    }

    private fun clearArtwork() {
        artworkTexture?.close()
        artworkTexture = null
        uploadedArtworkKey = ""
    }

    internal fun formatTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
        return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
    }

    private fun sourceName(source: String): String = when {
        source.contains("spotify", ignoreCase = true) -> "Spotify"
        source.contains("chrome", ignoreCase = true) || source.contains("edge", ignoreCase = true) -> "YouTube Music"
        else -> "Now Playing"
    }

    private fun exampleMedia() = MediaSnapshot(
        available = true,
        source = "Spotify.exe",
        title = "Current Song",
        artist = "Artist Name",
        positionMs = 83_000L,
        durationMs = 214_000L,
        playing = true,
        sampledAtMs = System.currentTimeMillis(),
    )
}
