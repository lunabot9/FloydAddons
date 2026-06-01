package gg.floyd.features.impl.misc

import club.minnced.discord.rpc.DiscordEventHandlers
import club.minnced.discord.rpc.DiscordRPC
import club.minnced.discord.rpc.DiscordRichPresence
import gg.floyd.FloydAddonsMod
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

    // Pure-Java IPC fallback (used when the native discord-rpc lib is unavailable, e.g. macOS arm64).
    private var usingIpc = false
    private var ipcThread: Thread? = null
    @Volatile private var desiredState = ""
    private val pid by lazy { ProcessHandle.current().pid() }

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
        "usingIpc" to usingIpc,
        "callbackThreadAlive" to (callbackThread?.isAlive == true),
        "ipcThreadAlive" to (ipcThread?.isAlive == true)
    )

    @Synchronized
    private fun start() {
        if (initialized || failed || appId.isEmpty()) return
        // Native RPC first; on any failure (notably UnsatisfiedLinkError on macOS arm64) fall back
        // to the pure-Java IPC socket client so presence works on every platform Discord runs on.
        if (startNative() || startIpc()) {
            initialized = true
        } else {
            failed = true
        }
    }

    private fun startNative(): Boolean = try {
        val currentRpc = DiscordRPC.INSTANCE
        currentRpc.Discord_Initialize(appId, DiscordEventHandlers(), true, null)
        rpc = currentRpc
        updatePresenceNative("In menus")
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
        true
    } catch (_: Throwable) {
        rpc = null
        false
    }

    private fun startIpc(): Boolean = try {
        // Spawn the IPC thread and let the BLOCKING connect/handshake happen there, never on the
        // client thread (this runs from TickEvent.ClientEnd) — an unresponsive Discord socket must
        // not be able to stall the game.
        usingIpc = true
        desiredState = "In menus"
        ipcThread = Thread(::runIpcLoop, "FloydAddons-DiscordIPC").apply {
            isDaemon = true
            start()
        }
        true
    } catch (_: Throwable) {
        usingIpc = false
        false
    }

    /** IPC fallback on its own daemon thread: connects, then pushes [desiredState]. Never the client thread. */
    private fun runIpcLoop() {
        if (!DiscordIpcClient.connect(appId)) {
            failed = true
            usingIpc = false
            return
        }
        FloydAddonsMod.logger.info("Discord presence using pure-Java IPC fallback (native lib unavailable)")
        var lastSent: String? = null
        while (!Thread.currentThread().isInterrupted) {
            val target = desiredState
            if (target != lastSent) {
                if (DiscordIpcClient.setActivity(pid, buildActivity(target))) {
                    lastSent = target
                } else {
                    failed = true
                    break
                }
            }
            try {
                Thread.sleep(2000L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun buildActivity(state: String): Map<String, Any?> = mapOf(
        "details" to "Playing Floyd Addons",
        "state" to state,
        "assets" to mapOf("large_image" to largeImageKey, "large_text" to "Floyd Addons"),
        "timestamps" to mapOf("start" to sessionStart)
    )

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
        if (usingIpc) desiredState = state else updatePresenceNative(state)
    }

    private fun updatePresenceNative(state: String) {
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
        if (usingIpc) {
            ipcThread?.interrupt()
            ipcThread = null
            DiscordIpcClient.close()
            usingIpc = false
        } else {
            callbackThread?.interrupt()
            callbackThread = null
            try {
                rpc?.Discord_ClearPresence()
                rpc?.Discord_Shutdown()
            } catch (_: Throwable) {
            }
        }
        initialized = false
    }
}
