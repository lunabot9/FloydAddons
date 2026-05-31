package gg.floyd.features.impl.misc

import club.minnced.discord.rpc.DiscordEventHandlers
import club.minnced.discord.rpc.DiscordRPC
import club.minnced.discord.rpc.DiscordRichPresence
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.events.TickEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import java.time.Instant

object FloydDiscordPresence : Module(
    name = "Discord Presence",
    category = Category.MISC,
    description = "Floyd Discord rich presence.",
    toggled = true,
) {
    private const val DEFAULT_APP_ID = "1471448522279747595"
    private const val DEFAULT_LARGE_IMAGE_KEY = "floydaddons_icon"
    private val appId = (System.getenv("FLOYDADDONS_DISCORD_APP_ID") ?: DEFAULT_APP_ID).trim()
    private val largeImageKey = (System.getenv("FLOYDADDONS_DISCORD_LARGE_IMAGE") ?: DEFAULT_LARGE_IMAGE_KEY).trim()
    private val sessionStart = Instant.now().epochSecond

    val presenceEnabled by BooleanSetting("Enabled", true, desc = "Enables Floyd Discord rich presence.")

    private var rpc: DiscordRPC? = null
    private var callbackThread: Thread? = null
    private var initialized = false
    private var failed = false
    private var lastState = ""
    private var lastShouldRun = false

    init {
        on<TickEvent.ClientEnd> {
            val shouldRun = enabled && presenceEnabled
            if (shouldRun != lastShouldRun) {
                if (shouldRun) start() else shutdown()
                lastShouldRun = shouldRun
            }
            if (shouldRun) tickPresence()
        }
    }

    override fun onDisable() {
        shutdown()
        lastShouldRun = false
        super.onDisable()
    }

    fun startIfEnabled() {
        val shouldRun = enabled && presenceEnabled
        if (shouldRun) start()
        lastShouldRun = shouldRun
    }

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "presenceEnabled" to presenceEnabled,
        "shouldRun" to (enabled && presenceEnabled),
        "initialized" to initialized,
        "failed" to failed,
        "lastState" to lastState,
        "lastShouldRun" to lastShouldRun,
        "appIdConfigured" to appId.isNotEmpty(),
        "largeImageKey" to largeImageKey,
        "callbackThreadAlive" to (callbackThread?.isAlive == true)
    )

    @Synchronized
    private fun start() {
        if (initialized || failed || appId.isEmpty()) return
        try {
            val currentRpc = DiscordRPC.INSTANCE
            rpc = currentRpc
            currentRpc.Discord_Initialize(appId, DiscordEventHandlers(), true, null)
            updatePresence("In menus")
            callbackThread = Thread({
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        currentRpc.Discord_RunCallbacks()
                        Thread.sleep(2000L)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    } catch (_: Throwable) {
                        failed = true
                        Thread.currentThread().interrupt()
                    }
                }
            }, "FloydAddons-DiscordRPC").apply {
                isDaemon = true
                start()
            }
            initialized = true
        } catch (_: Throwable) {
            failed = true
            rpc = null
        }
    }

    private fun tickPresence() {
        if (!initialized || failed) return
        val state = computeState()
        if (state != lastState) {
            lastState = state
            updatePresence(state)
        }
    }

    private fun computeState(): String {
        mc.currentServer?.let { server ->
            val name = server.name.takeIf { it.isNotBlank() } ?: server.ip
            return if (name.isNotBlank()) "Multiplayer: $name" else "Multiplayer"
        }
        if (mc.isSingleplayer) return "Singleplayer world"
        return "In menus"
    }

    private fun updatePresence(state: String) {
        val presence = DiscordRichPresence().apply {
            details = "Playing Floyd Addons"
            this.state = state
            largeImageKey = this@FloydDiscordPresence.largeImageKey
            largeImageText = "Floyd Addons"
            startTimestamp = sessionStart
        }
        rpc?.Discord_UpdatePresence(presence)
    }

    @Synchronized
    fun shutdown() {
        if (!initialized) return
        callbackThread?.interrupt()
        callbackThread = null
        try {
            rpc?.Discord_ClearPresence()
            rpc?.Discord_Shutdown()
        } catch (_: Throwable) {
        }
        initialized = false
    }
}
