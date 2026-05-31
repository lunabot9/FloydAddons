package gg.floyd.features.impl.misc

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import gg.floyd.FloydAddonsMod
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.ClickGUI
import gg.floyd.clickgui.HudManager
import gg.floyd.clickgui.LegacyFloydClickGUI
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.config.FloydSidecarConfig
import gg.floyd.events.core.EventBus
import gg.floyd.features.ModuleManager
import gg.floyd.events.TickEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.impl.camera.FloydCamera
import gg.floyd.features.impl.hiders.FloydHiders
import gg.floyd.features.impl.render.FloydHud
import gg.floyd.features.impl.render.FloydMobEsp
import gg.floyd.features.impl.render.FloydRender
import gg.floyd.features.impl.cosmetic.FloydCape
import gg.floyd.features.impl.cosmetic.FloydConeHat
import gg.floyd.features.impl.player.FloydNickHider
import gg.floyd.features.impl.player.FloydPlayerSize
import gg.floyd.features.impl.cosmetic.FloydSkin
import gg.floyd.features.impl.render.ClickGUIModule
import gg.floyd.features.impl.render.FloydAnimations
import gg.floyd.features.impl.render.FloydXray
import gg.floyd.utils.render.RenderBatchManager
import gg.floyd.utils.ui.clearMouseOverride
import gg.floyd.utils.ui.setMouseOverride
import gg.floyd.utils.ui.withMouseOverride
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.CameraType
import net.minecraft.client.KeyMapping
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.exists
import kotlin.math.max
import org.lwjgl.glfw.GLFW

internal object FloydLocalControlFutures {
    fun <T> joinClientFuture(future: CompletableFuture<T>): T {
        try {
            return future.join()
        } catch (e: CompletionException) {
            val cause = e.cause
            if (cause is Exception) throw cause
            if (cause is Error) throw cause
            throw e
        }
    }
}

internal object FloydLocalControlSettings {
    const val DEFAULT_PORT = 38769

    fun normalizePort(port: Int): Int =
        if (port <= 0) DEFAULT_PORT else port

    fun newSettingsPort(): Int = DEFAULT_PORT
}

internal object FloydLocalControlJson {
    fun requiredString(body: JsonObject, key: String): String {
        val value = body[key]?.takeIf { !it.isJsonNull }?.asString
            ?: throw IllegalArgumentException("missing_$key")
        if (value.isBlank()) throw IllegalArgumentException("blank_$key")
        return value
    }
}

object FloydLocalControl : Module(
    name = "Local Control",
    category = Category.MISC,
    description = "Floyd local Minecraft control bridge.",
    toggled = true,
) {
    private val gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()
    private val settingsPath: Path = FloydAddonsMod.configFile.toPath().resolve("control-bridge.json")
    private const val maxBodyBytes = 8192
    private val advertisedEndpoints = listOf("/state", "/chat", "/look", "/hotbar", "/key", "/action", "/screen", "/mouse", "/type", "/replace-text", "/screenshot")

    val bridgeEnabled by BooleanSetting("Enabled", true, desc = "Starts the loopback-only local control bridge.")
    private val port by NumberSetting("Port", FloydLocalControlSettings.DEFAULT_PORT, 1024, 65535, 1, desc = "Local control bridge port.")

    private var server: HttpServer? = null
    private var scheduler: ScheduledExecutorService? = null
    private var bridgeSettings: Settings? = null
    private var lastShouldRun = false

    init {
        on<TickEvent.ClientEnd> {
            val shouldRun = enabled && bridgeEnabled
            if (shouldRun != lastShouldRun) {
                if (shouldRun) start() else stop()
                lastShouldRun = shouldRun
            }
        }
    }

    override fun onDisable() {
        stop()
        lastShouldRun = false
        super.onDisable()
    }

    @JvmStatic
    fun isRunning(): Boolean = server != null

    fun state(): Map<String, Any?> {
        val loaded = bridgeSettings
        return mapOf(
            "enabled" to enabled,
            "bridgeEnabled" to bridgeEnabled,
            "shouldRun" to (enabled && bridgeEnabled),
            "running" to isRunning(),
            "lastShouldRun" to lastShouldRun,
            "configuredPort" to port,
            "settingsPort" to (loaded?.port ?: port),
            "settingsEnabled" to loaded?.enabled,
            "tokenConfigured" to (loaded?.token?.isNotBlank() == true),
            "settingsPath" to settingsPath.toString(),
            "endpoints" to advertisedEndpoints
        )
    }

    fun startIfEnabled() {
        val shouldRun = enabled && bridgeEnabled
        if (shouldRun) start()
        lastShouldRun = shouldRun
    }

    @Synchronized
    fun start() {
        if (server != null) return
        try {
            val loaded = loadSettings()
            bridgeSettings = loaded
            if (!loaded.enabled) {
                FloydAddonsMod.logger.info("Local control bridge disabled in {}", settingsPath)
                return
            }
            val controlServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), loaded.port), 0)
            scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "FloydAddons-Control-Scheduler").apply { isDaemon = true }
            }
            controlServer.executor = Executors.newFixedThreadPool(2) { runnable ->
                Thread(runnable, "FloydAddons-Control-HTTP").apply { isDaemon = true }
            }
            controlServer.createContext("/") { exchange -> handle(exchange) }
            controlServer.start()
            server = controlServer
            FloydAddonsMod.logger.info("Local control bridge listening on http://127.0.0.1:${loaded.port}")
            FloydAddonsMod.logger.info("Control bridge token/config: {}", settingsPath)
        } catch (e: IOException) {
            FloydAddonsMod.logger.warn("Failed to start local control bridge", e)
            server = null
            scheduler?.shutdownNow()
            scheduler = null
        }
    }

    @Synchronized
    fun stop() {
        server?.stop(0)
        server = null
        scheduler?.shutdownNow()
        scheduler = null
    }

    private fun loadSettings(): Settings {
        Files.createDirectories(settingsPath.parent)
        val fromFile = if (settingsPath.exists()) {
            runCatching { gson.fromJson(Files.readString(settingsPath), Settings::class.java) }.getOrNull()
        } else null
        val resolved = fromFile?.takeIf { it.token.isNotBlank() }
            ?: Settings(token = generateToken(), port = FloydLocalControlSettings.newSettingsPort())
        // The in-GUI "Port" NumberSetting is authoritative for the bind address; the JSON only
        // persists the auth token. (Module config is loaded before start(), so `port` is set here.)
        val normalized = resolved.copy(port = FloydLocalControlSettings.normalizePort(port))
        Files.writeString(settingsPath, gson.toJson(normalized), StandardCharsets.UTF_8)
        return normalized
    }

    private fun handle(exchange: HttpExchange) {
        try {
            if (!isLoopback(exchange)) return send(exchange, 403, mapOf("ok" to false, "error" to "loopback_only"))
            val path = exchange.requestURI.path
            if (path == "/" || path == "/health") {
                return send(exchange, 200, healthPayload())
            }
            if (!isAuthorized(exchange)) return send(exchange, 401, mapOf("ok" to false, "error" to "unauthorized"))
            when (path) {
                "/state" -> requireMethod(exchange, "GET") { send(exchange, 200, statePayload()) }
                "/chat" -> requireMethod(exchange, "POST") { handleChat(exchange) }
                "/look" -> requireMethod(exchange, "POST") { handleLook(exchange) }
                "/hotbar" -> requireMethod(exchange, "POST") { handleHotbar(exchange) }
                "/key" -> requireMethod(exchange, "POST") { handleKey(exchange) }
                "/action" -> requireMethod(exchange, "POST") { handleAction(exchange) }
                "/screen" -> requireMethod(exchange, "POST") { handleScreen(exchange) }
                "/mouse" -> requireMethod(exchange, "POST") { handleMouse(exchange) }
                "/type" -> requireMethod(exchange, "POST") { handleType(exchange) }
                "/replace-text" -> requireMethod(exchange, "POST") { handleReplaceText(exchange) }
                "/screenshot" -> requireMethod(exchange, "POST") { handleScreenshot(exchange) }
                else -> send(exchange, 404, mapOf("ok" to false, "error" to "not_found"))
            }
        } catch (e: IllegalArgumentException) {
            send(exchange, 400, mapOf("ok" to false, "error" to (e.message ?: "bad_request")))
        } catch (t: Throwable) {
            FloydAddonsMod.logger.warn("Control bridge request failed", t)
            send(exchange, 500, mapOf("ok" to false, "error" to t.javaClass.simpleName))
        } finally {
            exchange.close()
        }
    }

    private fun healthPayload(): Map<String, Any?> = mapOf(
        "ok" to true,
        "service" to "floydaddons-control",
        "port" to (bridgeSettings?.port ?: port),
        "auth" to "Authorization: Bearer <token>",
        "settings" to settingsPath.toString(),
        "endpoints" to advertisedEndpoints
    )

    private fun statePayload(): Map<String, Any?> = callClient {
        val root = linkedMapOf<String, Any?>()
        root["ok"] = true
        root["connected"] = mc.player != null && mc.level != null
        root["screen"] = mc.screen?.javaClass?.name
        root["screenTitle"] = mc.screen?.title?.string
        root["scaffold"] = scaffoldPayload()
        root["modules"] = ModuleManager.state()
        root["configs"] = mapOf(
            "floydSidecars" to FloydSidecarConfig.state()
        )
        root["eventBus"] = EventBus.state()
        root["window"] = mapOf(
            "fullscreen" to mc.window.isFullscreen,
            "borderlessWindowed" to (FloydRender.enabled && FloydRender.borderlessWindowed),
            "width" to mc.window.width,
            "height" to mc.window.height,
            "scaledWidth" to mc.window.guiScaledWidth,
            "scaledHeight" to mc.window.guiScaledHeight
        )
        root["server"] = serverPayload()
        root["camera"] = mapOf(
            "type" to mc.options.cameraType.name,
            "firstPerson" to mc.options.cameraType.isFirstPerson,
            "features" to FloydCamera.state()
        )
        root["render"] = mapOf(
            "batch" to RenderBatchManager.state(),
            "core" to FloydRender.state(),
            "xray" to FloydXray.state(),
            "animations" to FloydAnimations.state(),
            "hiders" to FloydHiders.state(),
            "mobEsp" to FloydMobEsp.state(),
            "hud" to FloydHud.state()
        )
        root["qol"] = emptyMap<String, Any?>()
        root["cosmetics"] = mapOf(
            "cape" to FloydCape.state(),
            "coneHat" to FloydConeHat.state(),
            "skin" to FloydSkin.state()
        )
        root["playerFeatures"] = mapOf(
            "nickHider" to FloydNickHider.state(),
            "playerSize" to FloydPlayerSize.state()
        )
        root["misc"] = mapOf(
            "localControl" to state(),
            "discordPresence" to FloydDiscordPresence.state(),
            "compatibility" to FloydCompatibility.state(),
            "clickGui" to ClickGUIModule.state()
        )
        root["legacyGui"] = LegacyFloydClickGUI.debugState()

        val player = mc.player ?: return@callClient root
        val level = mc.level ?: return@callClient root
        root["player"] = mapOf(
            "name" to player.name.string,
            "x" to player.x,
            "y" to player.y,
            "z" to player.z,
            "yaw" to player.yRot,
            "pitch" to player.xRot,
            "health" to player.health,
            "maxHealth" to player.maxHealth,
            "food" to player.foodData.foodLevel,
            "onGround" to player.onGround()
        )
        root["world"] = mapOf(
            "dimension" to level.dimension().identifier().toString(),
            "time" to level.gameTime,
            "timeOfDay" to level.dayTime
        )
        root["crosshair"] = describeHit()
        root["hotbar"] = describeHotbar(player.inventory)
        root["nearbyEntities"] = level.entitiesForRendering()
            .filter { it !== player && it.distanceToSqr(player) <= 256.0 }
            .take(24)
            .map(::describeEntity)
        root
    }

    private fun scaffoldPayload(): Map<String, Any?> {
        val loader = FabricLoader.getInstance()
        val minecraftMetadata = loader.getModContainer("minecraft").orElse(null)?.metadata
        return mapOf(
            "modId" to FloydAddonsMod.MOD_ID,
            "modName" to FloydAddonsMod.MOD_NAME,
            "version" to FloydAddonsMod.MOD_VERSION,
            "minecraftVersion" to minecraftMetadata?.version?.friendlyString,
            "entrypoint" to "gg.floyd.FloydAddonsMod",
            "mixinConfig" to "floydaddons.mixins.json",
            "resourceNamespace" to FloydAddonsMod.MOD_ID,
            "activeScaffold" to "Floyd Fabric module/config/event/ClickGUI",
            "vendoredBehaviorSource" to "vendor/floydaddons-fabric"
        )
    }

    private fun serverPayload(): Map<String, Any?> {
        val connected = mc.player != null && mc.level != null
        val currentServer = mc.currentServer
        val connectionServer = mc.connection?.serverData
        val server = currentServer ?: connectionServer
        return mapOf(
            "connected" to connected,
            "multiplayer" to (connected && server != null),
            "localServer" to mc.isLocalServer,
            "singleplayer" to mc.hasSingleplayerServer(),
            "name" to server?.name,
            "address" to server?.ip,
            "type" to server?.type()?.name,
            "lan" to server?.isLan,
            "realm" to server?.isRealm,
            "current" to describeServerData(currentServer),
            "connection" to describeServerData(connectionServer),
        )
    }

    private fun describeServerData(server: ServerData?): Map<String, Any?>? =
        server?.let {
            mapOf(
                "name" to it.name,
                "address" to it.ip,
                "type" to it.type().name,
                "lan" to it.isLan,
                "realm" to it.isRealm,
            )
        }

    private fun handleChat(exchange: HttpExchange) {
        val body = readJson(exchange)
        val message = requiredString(body, "message")
        callClient {
            val connection = mc.connection ?: throw IllegalArgumentException("not_connected")
            if (message.startsWith("/")) connection.sendCommand(message.drop(1))
            else connection.sendChat(message)
        }
        send(exchange, 200, mapOf("ok" to true))
    }

    private fun handleLook(exchange: HttpExchange) {
        val body = readJson(exchange)
        val result = callClient {
            val player = mc.player ?: throw IllegalArgumentException("not_connected")
            val yaw = (number(body, "yaw")?.toFloat() ?: player.yRot) + (number(body, "deltaYaw")?.toFloat() ?: 0f)
            val pitch = ((number(body, "pitch")?.toFloat() ?: player.xRot) + (number(body, "deltaPitch")?.toFloat() ?: 0f)).coerceIn(-90f, 90f)
            player.yRot = yaw
            player.xRot = pitch
            player.yHeadRot = yaw
            mapOf("ok" to true, "yaw" to yaw, "pitch" to pitch)
        }
        send(exchange, 200, result)
    }

    private fun handleHotbar(exchange: HttpExchange) {
        val slot = requiredInt(readJson(exchange), "slot")
        if (slot !in 0..8) throw IllegalArgumentException("slot_must_be_0_to_8")
        callClient {
            val player = mc.player ?: throw IllegalArgumentException("not_connected")
            player.inventory.setSelectedSlot(slot)
            mc.connection?.send(ServerboundSetCarriedItemPacket(slot))
        }
        send(exchange, 200, mapOf("ok" to true, "slot" to slot))
    }

    private fun handleKey(exchange: HttpExchange) {
        val body = readJson(exchange)
        val keyName = requiredString(body, "key")
        val pressed = body["pressed"]?.takeIf { !it.isJsonNull }?.asBoolean ?: true
        val durationMs = max(0L, number(body, "durationMs")?.toLong() ?: 0L)
        callClient {
            val key = keyByName(keyName)
            key.isDown = pressed
            if (pressed && durationMs > 0) {
                scheduler?.schedule({ mc.execute { key.isDown = false } }, durationMs, TimeUnit.MILLISECONDS)
            }
        }
        send(exchange, 200, mapOf("ok" to true, "key" to keyName, "pressed" to pressed))
    }

    private fun handleAction(exchange: HttpExchange) {
        val body = readJson(exchange)
        val action = requiredString(body, "action")
        callClient {
            when (action) {
                "attack" -> tapKey(mc.options.keyAttack, body)
                "use" -> tapKey(mc.options.keyUse, body)
                "swing", "swingMainHand" -> {
                    val player = mc.player ?: throw IllegalArgumentException("not_connected")
                    player.swing(InteractionHand.MAIN_HAND, true)
                }
                "jump" -> tapKey(mc.options.keyJump, body)
                "sneak" -> tapKey(mc.options.keyShift, body)
                "sprint" -> tapKey(mc.options.keySprint, body)
                "closeScreen" -> mc.screen?.onClose() ?: mc.setScreen(null)
                "camera", "perspective" -> {
                    val requested = body["type"]?.takeIf { !it.isJsonNull }?.asString
                    val target = when (requested?.lowercase()) {
                        null, "", "cycle", "next" -> mc.options.cameraType.cycle()
                        "first", "first_person", "first-person" -> CameraType.FIRST_PERSON
                        "back", "third_back", "third-person-back", "third_person_back" -> CameraType.THIRD_PERSON_BACK
                        "front", "third_front", "third-person-front", "third_person_front" -> CameraType.THIRD_PERSON_FRONT
                        else -> throw IllegalArgumentException("unknown_camera_type")
                    }
                    mc.options.cameraType = target
                }
                "fullscreen" -> {
                    val enabled = body["enabled"]?.takeIf { !it.isJsonNull }?.asBoolean ?: true
                    if (mc.window.isFullscreen != enabled) mc.window.toggleFullScreen()
                }
                "connect" -> {
                    val addressText = requiredString(body, "address")
                    val address = ServerAddress.parseString(addressText)
                    if (!ServerAddress.isValidAddress(address.host)) throw IllegalArgumentException("invalid_address")
                    val server = ServerData(addressText, addressText, ServerData.Type.OTHER)
                    ConnectScreen.startConnecting(mc.screen ?: TitleScreen(), mc, address, server, false, null)
                }
                "openWorld", "loadWorld", "singleplayer" -> {
                    val levelId = body["world"]?.takeIf { !it.isJsonNull }?.asString
                        ?: body["level"]?.takeIf { !it.isJsonNull }?.asString
                        ?: body["name"]?.takeIf { !it.isJsonNull }?.asString
                        ?: "New World"
                    if (levelId.isBlank() || levelId.contains("/") || levelId.contains("\\") || levelId.contains("..")) {
                        throw IllegalArgumentException("invalid_world")
                    }
                    if (!mc.levelSource.levelExists(levelId)) throw IllegalArgumentException("world_not_found")
                    mc.createWorldOpenFlows().openWorld(levelId) {}
                }
                "windowedFullscreen", "borderlessWindowed" -> {
                    val enabled = body["enabled"]?.takeIf { !it.isJsonNull }?.asBoolean ?: true
                    if (enabled && mc.window.isFullscreen) mc.window.toggleFullScreen()
                    FloydRender.setBorderlessWindowed(enabled, force = true)
                }
                "reloadConfig" -> ModuleManager.loadConfigurations()
                "reloadMod", "reloadResources" -> {
                    ModuleManager.loadConfigurations()
                    FloydSkin.reload()
                    FloydCape.reload()
                    FloydConeHat.reload()
                    FloydXray.rebuildChunks()
                    mc.reloadResourcePacks()
                }
                else -> throw IllegalArgumentException("unknown_action")
            }
        }
        send(exchange, 200, mapOf("ok" to true, "action" to action))
    }

    private fun handleScreen(exchange: HttpExchange) {
        val screen = requiredString(readJson(exchange), "screen")
        callClient {
            when (screen) {
                "floyd", "floydaddons", "legacy", "legacygui", "oldgui" -> mc.setScreen(LegacyFloydClickGUI.openHub())
                "v2", "clickgui", "xrayEditor", "xrayBlocks", "mobEspEditor", "mobEspFilters" -> mc.setScreen(ClickGUI)
                "hud", "edithud" -> mc.setScreen(HudManager)
                "close", "none" -> mc.setScreen(null)
                else -> throw IllegalArgumentException("unknown_screen")
            }
        }
        send(exchange, 200, mapOf("ok" to true, "screen" to screen))
    }

    private fun handleMouse(exchange: HttpExchange) {
        val body = readJson(exchange)
        val event = body["event"]?.takeIf { !it.isJsonNull }?.asString ?: "click"
        val rawX = requiredNumber(body, "x")
        val rawY = requiredNumber(body, "y")
        val (x, y) = controlMousePoint(rawX, rawY, body)
        val button = number(body, "button")?.toInt() ?: 0
        val horizontalAmount = number(body, "horizontalAmount")?.toDouble() ?: 0.0
        val verticalAmount = number(body, "verticalAmount")?.toDouble() ?: number(body, "amount")?.toDouble() ?: 0.0
        val dragX = number(body, "deltaX")?.toDouble() ?: 0.0
        val dragY = number(body, "deltaY")?.toDouble() ?: 0.0
        val result = callClient {
            val currentScreen = mc.screen ?: throw IllegalArgumentException("no_screen")
            val click = MouseButtonEvent(x, y, MouseButtonInfo(button, 0))
            val handled = when (event) {
                "clear" -> {
                    clearMouseOverride()
                    true
                }
                else -> {
                    setMouseOverride(x, y)
                    withMouseOverride(x, y) {
                        when (event) {
                            "move" -> {
                                currentScreen.mouseMoved(x, y)
                                true
                            }
                            "click" -> {
                                val down = currentScreen.mouseClicked(click, false)
                                val up = currentScreen.mouseReleased(click)
                                down || up
                            }
                            "down", "press" -> currentScreen.mouseClicked(click, false)
                            "up", "release" -> currentScreen.mouseReleased(click)
                            "drag" -> currentScreen.mouseDragged(click, dragX, dragY)
                            "scroll", "wheel" -> currentScreen.mouseScrolled(x, y, horizontalAmount, verticalAmount)
                            else -> throw IllegalArgumentException("unknown_mouse_event")
                        }
                    }
                }
            }
            currentScreen.afterMouseAction()
            mapOf(
                "ok" to true,
                "event" to event,
                "handled" to handled,
                "rawX" to rawX,
                "rawY" to rawY,
                "x" to x,
                "y" to y,
                "button" to button,
                "horizontalAmount" to horizontalAmount,
                "verticalAmount" to verticalAmount
            )
        }
        send(exchange, 200, result)
    }

    private fun controlMousePoint(rawX: Double, rawY: Double, body: JsonObject): Pair<Double, Double> {
        val coordinateSpace = body["coordinateSpace"]?.takeIf { !it.isJsonNull }?.asString
            ?: body["space"]?.takeIf { !it.isJsonNull }?.asString
        if (coordinateSpace.equals("gui", ignoreCase = true) || coordinateSpace.equals("scaled", ignoreCase = true)) {
            return rawX to rawY
        }

        val window = mc.window
        if (window.width == window.guiScaledWidth && window.height == window.guiScaledHeight) return rawX to rawY
        return rawX * window.guiScaledWidth / window.width to rawY * window.guiScaledHeight / window.height
    }

    private fun handleType(exchange: HttpExchange) {
        val body = readJson(exchange)
        val text = body["text"]?.takeIf { !it.isJsonNull }?.asString ?: ""
        val clear = body["clear"]?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        val submit = body["submit"]?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        val result = callClient {
            val currentScreen = mc.screen ?: throw IllegalArgumentException("no_screen")
            var handled = false
            if (clear) {
                handled = currentScreen.keyPressed(KeyEvent(GLFW.GLFW_KEY_DELETE, 0, 0)) || handled
            }
            text.codePoints().forEach { codepoint ->
                handled = currentScreen.charTyped(CharacterEvent(codepoint, 0)) || handled
            }
            if (submit) {
                handled = currentScreen.keyPressed(KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)) || handled
            }
            currentScreen.afterKeyboardAction()
            mapOf("ok" to true, "typed" to text.length, "cleared" to clear, "submitted" to submit, "handled" to handled)
        }
        send(exchange, 200, result)
    }

    private fun handleScreenshot(exchange: HttpExchange) {
        val body = readJson(exchange)
        val fileName = body["fileName"]?.takeIf { !it.isJsonNull }?.asString ?: "floyd-control.png"
        if (!fileName.endsWith(".png") || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw IllegalArgumentException("invalid_fileName")
        }

        val future = CompletableFuture<Map<String, Any?>>()
        mc.execute {
            try {
                val screenshotsDir = mc.gameDirectory.toPath().resolve("screenshots")
                val output = screenshotsDir.resolve(fileName)
                Screenshot.grab(mc.gameDirectory, fileName, mc.mainRenderTarget, 1) { message ->
                    future.complete(
                        mapOf(
                            "ok" to true,
                            "path" to output.toString(),
                            "message" to message.string,
                            "screen" to mc.screen?.javaClass?.name
                        )
                    )
                }
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }

        try {
            send(exchange, 200, future.get(5, TimeUnit.SECONDS))
        } catch (_: TimeoutException) {
            throw IllegalArgumentException("screenshot_timeout")
        }
    }

    private fun handleReplaceText(exchange: HttpExchange) {
        val body = readJson(exchange)
        val text = requiredString(body, "text")
        val scanText = body["scanText"]?.takeIf { !it.isJsonNull }?.asString
        val scoreboard = body["scoreboard"]?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        val result = callClient {
            val scanned = scanText?.let { FloydNickHider.debugScanServerIdText(it, scoreboard) }
            val replaced = FloydNickHider.replaceString(text)
            mapOf(
                "ok" to true,
                "text" to text,
                "replaced" to replaced,
                "changed" to (text != replaced),
                "scanned" to scanned,
                "nickHider" to FloydNickHider.state()
            )
        }
        send(exchange, 200, result)
    }

    private fun tapKey(key: KeyMapping, body: JsonObject) {
        val durationMs = max(0L, number(body, "durationMs")?.toLong() ?: 120L)
        key.isDown = true
        scheduler?.schedule({ mc.execute { key.isDown = false } }, durationMs, TimeUnit.MILLISECONDS)
    }

    private fun keyByName(name: String): KeyMapping = when (name) {
        "forward", "w" -> mc.options.keyUp
        "back", "s" -> mc.options.keyDown
        "left", "a" -> mc.options.keyLeft
        "right", "d" -> mc.options.keyRight
        "jump", "space" -> mc.options.keyJump
        "sneak", "shift" -> mc.options.keyShift
        "sprint", "ctrl" -> mc.options.keySprint
        "attack", "mouse1" -> mc.options.keyAttack
        "use", "mouse2" -> mc.options.keyUse
        "tab", "playerList", "player_list" -> mc.options.keyPlayerList
        else -> throw IllegalArgumentException("unknown_key")
    }

    private fun describeHit(): Map<String, Any?> {
        val hit = mc.hitResult ?: return mapOf("type" to "NONE")
        val result = linkedMapOf<String, Any?>(
            "type" to hit.type.name,
            "x" to hit.location.x,
            "y" to hit.location.y,
            "z" to hit.location.z
        )
        when (hit) {
            is BlockHitResult -> {
                val pos = hit.blockPos
                result["blockPos"] = mapOf("x" to pos.x, "y" to pos.y, "z" to pos.z)
                result["side"] = hit.direction.name.lowercase()
                result["block"] = mc.level?.getBlockState(pos)?.block?.let { BuiltInRegistries.BLOCK.getKey(it).toString() }
            }
            is EntityHitResult -> result["entity"] = describeEntity(hit.entity)
        }
        return result
    }

    private fun describeHotbar(inventory: Inventory): List<Map<String, Any?>> =
        (0..8).map { slot ->
            val stack = inventory.getItem(slot)
            mapOf(
                "slot" to slot,
                "selected" to (slot == inventory.selectedSlot),
                "item" to if (stack.isEmpty) mapOf("empty" to true) else mapOf(
                    "empty" to false,
                    "id" to BuiltInRegistries.ITEM.getKey(stack.item).toString(),
                    "name" to stack.hoverName.string,
                    "count" to stack.count
                )
            )
        }

    private fun describeEntity(entity: Entity): Map<String, Any?> = mapOf(
        "id" to entity.id,
        "type" to BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString(),
        "name" to entity.name.string,
        "x" to entity.x,
        "y" to entity.y,
        "z" to entity.z
    )

    private fun requireMethod(exchange: HttpExchange, method: String, block: () -> Unit) {
        if (exchange.requestMethod != method) return send(exchange, 405, mapOf("ok" to false, "error" to "method_not_allowed"))
        block()
    }

    private fun isLoopback(exchange: HttpExchange): Boolean =
        exchange.remoteAddress.address.isLoopbackAddress

    private fun isAuthorized(exchange: HttpExchange): Boolean {
        val expected = bridgeSettings?.token ?: return false
        val header = exchange.requestHeaders.getFirst("Authorization")
        if (header == "Bearer $expected") return true

        val tokenHeader = exchange.requestHeaders.getFirst("X-FloydAddons-Token")
        if (tokenHeader == expected) return true

        return queryParams(exchange)["token"] == expected
    }

    private fun queryParams(exchange: HttpExchange): Map<String, String> {
        val query = exchange.requestURI.rawQuery?.takeIf { it.isNotBlank() } ?: return emptyMap()
        return buildMap {
            for (part in query.split("&")) {
                val index = part.indexOf('=')
                if (index <= 0) continue
                val key = URLDecoder.decode(part.substring(0, index), StandardCharsets.UTF_8)
                val value = URLDecoder.decode(part.substring(index + 1), StandardCharsets.UTF_8)
                put(key, value)
            }
        }
    }

    private fun readJson(exchange: HttpExchange): JsonObject {
        val bytes = exchange.requestBody.use { it.readNBytes(maxBodyBytes + 1) }
        if (bytes.size > maxBodyBytes) throw IllegalArgumentException("body_too_large")
        if (bytes.isEmpty()) return JsonObject()
        return JsonParser.parseString(String(bytes, StandardCharsets.UTF_8)).asJsonObject
    }

    private fun requiredString(body: JsonObject, key: String): String =
        FloydLocalControlJson.requiredString(body, key)

    private fun requiredInt(body: JsonObject, key: String): Int =
        number(body, key)?.toInt() ?: throw IllegalArgumentException("missing_$key")

    private fun requiredNumber(body: JsonObject, key: String): Double =
        number(body, key)?.toDouble() ?: throw IllegalArgumentException("missing_$key")

    private fun number(body: JsonObject, key: String): Number? =
        body[key]?.takeIf { !it.isJsonNull && it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asNumber

    private fun send(exchange: HttpExchange, status: Int, payload: Any?) {
        val bytes = gson.toJson(payload).toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun <T> callClient(callable: Callable<T>): T {
        if (mc.isSameThread) return callable.call()
        val future = CompletableFuture<T>()
        mc.execute {
            try {
                future.complete(callable.call())
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }
        return FloydLocalControlFutures.joinClientFuture(future)
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private data class Settings(
        val enabled: Boolean = true,
        val port: Int = 38765,
        val token: String = ""
    )
}
