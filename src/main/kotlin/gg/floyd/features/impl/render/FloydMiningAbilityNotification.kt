package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.HudSizeRegistry
import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.clickgui.settings.impl.StringSetting
import gg.floyd.features.ModuleManager
import gg.floyd.FloydAddonsMod
import gg.floyd.utils.modMessage
import gg.floyd.utils.openDirectory
import gg.floyd.utils.sound.LocalMp3Catalog
import gg.floyd.utils.sound.LocalMp3Sound
import net.minecraft.client.resources.sounds.SoundInstance
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.clickgui.settings.impl.BooleanSetting
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import gg.floyd.events.ChatPacketEvent
import gg.floyd.events.TickEvent
import gg.floyd.events.WorldEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.drawString
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

object FloydMiningAbilityNotification : Module(
    name = "Mining Ability Ready",
    category = Category.RENDER,
    description = "Fades a yellow Mining Ability Ready! alert onto the screen when chat or the tab list reports readiness.",
    toggled = false,
) {
    private const val LABEL = "Mining Ability Ready!"
    private const val COLOR = 0xFFFF55
    private const val DURATION_MS = 3_000L
    private const val FADE_IN_MS = 250L
    private const val FADE_OUT_MS = 750L
    private const val HUD_NAME = "Mining Ability Ready HUD"
    private val readChat by BooleanSetting("Read Chat", true, desc = "Detect readiness messages for all supported mining abilities.")
    private val readTab by BooleanSetting("Read Tab List", true, desc = "Detect when the selected pickaxe ability becomes ready in the tab list.")
    private val dingVolume by NumberSetting("Ding Volume", 50.0, 0.0, 100.0, 1.0,
        desc = "Volume of the readiness ding. 0% mutes it; Minecraft's Master and UI volume also apply.", unit = "%")
    private val testDing by ActionSetting("Test Sound", desc = "Play the readiness ding at the selected volume, even when the module is off.") {
        playDing("test")
    }
    private val soundCatalog by lazy { LocalMp3Catalog(FloydAddonsMod.configFile.toPath().resolve("sounds").resolve("mining-ability")) }
    private var availableSounds = listOf(LocalMp3Catalog.DEFAULT)
    private var soundsScanned = false
    private var selectedSound by StringSetting("Sound", LocalMp3Catalog.DEFAULT, 160,
        desc = "Default keeps the original ding. Select a .mp3 filename with Previous/Next Sound.")
    private val previousSound by ActionSetting("Previous Sound", desc = "Select the previous available sound, including Default.") { cycleSound(-1) }
    private val nextSound by ActionSetting("Next Sound", desc = "Select the next available sound, including Default.") { cycleSound(1) }
    private val openSounds by ActionSetting("Open Sound Folder", desc = "Open the folder for custom readiness .mp3 files.") {
        reloadSounds()
        if (!openDirectory(soundCatalog.directory)) modMessage("Sound folder: ${soundCatalog.directory}")
    }
    private val reloadSoundFiles by ActionSetting("Reload Sounds", desc = "Refresh the available .mp3 files. No resource reload needed.") {
        reloadSounds()
        modMessage("Available readiness sounds: ${availableSounds.joinToString()}")
    }
    private var soundError: String? = null
    private var soundGeneration = 0L
    private var lastDing: SoundInstance? = null
    private var lastDingResult: String? = null
    private var lastDingSource: String? = null
    private var lastDingVolume = 0f
    private val dingCount = AtomicLong()
    private val tracker = MiningAbilityReadyTracker()
    private data class Alert(val signal: MiningAbilityReadyTracker.Signal, val startedMs: Long)
    @Volatile private var alert: Alert? = null
    private val triggerCount = AtomicLong()
    private var ticks = 0
    private val notificationHud by HUD(HUD_NAME, "Move and resize the mining ability alert.", false, -1, -1, 3f) { example ->
        drawNotification(example)
    }

    init {
        HudSizeRegistry.register(HUD_NAME) { 160 to 9 }
        on<ChatPacketEvent> {
            if (readChat) tracker.chat(value, nowMs())?.let(::show)
        }
        on<TickEvent.ClientEnd> {
            if (!readTab || ++ticks % 5 != 0 || mc.level == null) return@on
            val lines = mc.connection?.listedOnlinePlayers.orEmpty().mapNotNull { it.tabListDisplayName?.string }
            tracker.tab(lines, nowMs()).lastOrNull()?.let(::show)
        }
        on<WorldEvent.Unload> { clear() }
    }

    private fun show(signal: MiningAbilityReadyTracker.Signal) {
        alert = Alert(signal, nowMs())
        triggerCount.incrementAndGet()
        playDing("ready")
    }

    private fun reloadSounds() {
        availableSounds = runCatching { soundCatalog.choices() }.getOrElse {
            soundError = it.message
            listOf(LocalMp3Catalog.DEFAULT)
        }
        soundsScanned = true
    }

    private fun cycleSound(direction: Int) {
        reloadSounds()
        val index = availableSounds.indexOf(selectedSound).coerceAtLeast(0)
        selectedSound = availableSounds[Math.floorMod(index + direction, availableSounds.size)]
        ModuleManager.saveConfigurations()
    }

    private fun playDefaultDing(volume: Float) {
        val sound = SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, volume)
        startSound(sound)
    }

    private fun startSound(sound: SoundInstance) {
        lastDing?.let { mc.soundManager.stop(it) }
        lastDing = sound
        val result = mc.soundManager.play(sound)
        lastDingResult = result.name
        if (result != net.minecraft.client.sounds.SoundEngine.PlayResult.NOT_STARTED) dingCount.incrementAndGet()
    }

    private fun playDing(source: String) {
        val generation = ++soundGeneration
        val volume = (dingVolume / 100.0).coerceIn(0.0, 1.0).toFloat()
        lastDingSource = source
        lastDingVolume = volume
        soundError = null
        if (volume <= 0f) {
            lastDing?.let { mc.soundManager.stop(it) }
            lastDing = null
            lastDingResult = "MUTED"
            return
        }
        val file = runCatching { soundCatalog.resolve(selectedSound) }.getOrNull()
        if (file == null) {
            if (selectedSound != LocalMp3Catalog.DEFAULT && selectedSound.isNotBlank()) soundError = "Selected MP3 is missing; using Default"
            if (soundError != null && source == "test") modMessage(checkNotNull(soundError))
            playDefaultDing(volume)
            return
        }
        val sound = LocalMp3Sound(file, volume) { error ->
            mc.execute {
                // A new preview or readiness event may have replaced this failed stream already.
                if (generation == soundGeneration) {
                    soundError = "Cannot decode ${file.fileName}; using Default: ${error.cause?.message ?: error.message}"
                    if (source == "test") modMessage("Could not play ${file.fileName}; using Default ding.")
                    playDefaultDing(volume)
                    lastDingResult = "DEFAULT_FALLBACK"
                }
            }
        }
        startSound(sound)
    }

    private fun clear() { alert = null; tracker.reset(); ticks = 0 }
    override fun onDisable() { clear(); super.onDisable() }

    private fun GuiGraphics.drawNotification(example: Boolean): Pair<Int, Int> {
        val font = mc.font
        val width = font.width(LABEL)
        val height = font.lineHeight
        if (notificationHud.x < 0 || notificationHud.y < 0) {
            val (x, y) = FloydFellNotification.defaultHudPosition(mc.window.width, mc.window.height, width, height, notificationHud.scale)
            if (notificationHud.x < 0) notificationHud.x = x
            if (notificationHud.y < 0) notificationHud.y = y
        }
        val alpha = if (example) 255 else alert?.let { opacity(nowMs() - it.startedMs) } ?: 0
        if (alpha > 0) drawString(font, LABEL, 0, 0, (alpha shl 24) or COLOR, true)
        return width to height
    }

    fun state(): Map<String, Any?> {
        if (!soundsScanned) reloadSounds()
        val current = alert
        val elapsed = current?.let { nowMs() - it.startedMs }
        return mapOf(
            "enabled" to enabled, "active" to (elapsed != null && elapsed in 0 until DURATION_MS),
            "text" to LABEL, "color" to "#FFFF55", "ability" to current?.signal?.ability,
            "source" to current?.signal?.source, "alpha" to (elapsed?.let(::opacity) ?: 0),
            "triggerCount" to triggerCount.get(), "readChat" to readChat, "readTabList" to readTab,
            "sound" to mapOf("volumePercent" to dingVolume, "lastVolume" to lastDingVolume,
                "selected" to selectedSound, "available" to availableSounds,
                "directory" to soundCatalog.directory.toAbsolutePath().toString(), "error" to soundError,
                "lastResult" to lastDingResult, "lastSource" to lastDingSource, "playCount" to dingCount.get(),
                "decodedPcmBytes" to ((lastDing as? LocalMp3Sound)?.decodedBytes?.get() ?: 0L),
                "playing" to (lastDing?.let { mc.soundManager.isActive(it) } ?: false)),
            "supportedAbilities" to MiningAbilityReadyTracker.abilities,
            "hud" to mapOf("x" to notificationHud.x, "y" to notificationHud.y, "scale" to notificationHud.scale),
        )
    }

    private fun nowMs() = System.nanoTime() / 1_000_000L
    internal fun opacity(elapsed: Long): Int = when {
        elapsed < 0 || elapsed >= DURATION_MS -> 0
        elapsed < FADE_IN_MS -> (255.0 * elapsed / FADE_IN_MS).roundToInt()
        elapsed <= DURATION_MS - FADE_OUT_MS -> 255
        else -> (255.0 * (DURATION_MS - elapsed) / FADE_OUT_MS).roundToInt()
    }
}
