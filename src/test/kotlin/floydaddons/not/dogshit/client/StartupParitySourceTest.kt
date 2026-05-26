package floydaddons.not.dogshit.client

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartupParitySourceTest {
    private val root = Path.of("").toAbsolutePath()

    @Test
    fun `active initializer preserves Floyd startup surfaces through Odin scaffold`() {
        val active = source("src/main/kotlin/floydaddons/not/dogshit/client/FloydAddonsMod.kt")
        val moduleManager = source("src/main/kotlin/floydaddons/not/dogshit/client/features/ModuleManager.kt")
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/FloydAddonsClient.java")

        for (expected in listOf(
            "FloydAddonsConfig.load()",
            "FloydAddonsCommand.register()",
            "DiscordPresenceManager.start()",
            "LocalMinecraftControlServer.start()",
            "UpdateChecker.init()",
            "SkinManager.extractDefaultSkin(client)",
            "ClientLifecycleEvents.CLIENT_STOPPING.register",
            "FloydAddonsConfig.save()",
        )) {
            assertTrue(floyd.contains(expected), "Vendored Floyd source no longer contains expected startup token: $expected")
        }

        for (expected in listOf(
            "ClientCommandRegistrationCallback.EVENT.register",
            "mainCommand",
            "stalkCommand",
            "EventBus.subscribe(it)",
            "EventDispatcher",
            "ModuleManager",
            "SpecialGuiElementRegistry.register",
            "ClientLifecycleEvents.CLIENT_STOPPING.register",
            "FloydLocalControl.stop()",
            "FloydDiscordPresence.shutdown()",
            "ModuleManager.saveConfigurations()",
        )) {
            assertTrue(active.contains(expected), "Active initializer missing Odin/Floyd startup token: $expected")
        }

        for (expected in listOf(
            "config.load()",
            "FloydSidecarConfig.loadExistingSidecars()",
            "FloydDiscordPresence.startIfEnabled()",
            "FloydLocalControl.startIfEnabled()",
            "config.save()",
            "FloydSidecarConfig.saveSidecars()",
            "FloydSkin",
            "HudElementRegistry.attachElementBefore",
            "VanillaHudElements.SLEEP",
            "ModuleManager::render",
        )) {
            assertTrue(moduleManager.contains(expected), "ModuleManager missing startup/config token: $expected")
        }

        assertFalse(active.contains("ClickGuiScreen"), "Do not register Floyd's old ClickGuiScreen startup path")
        assertFalse(active.contains("FloydAddonsV2Screen"), "Do not register Floyd's old V2 GUI startup path")
        assertFalse(active.contains("KeyBindingHelper.registerKeyBinding"), "Use Odin keybind settings instead of Floyd raw keybindings")
    }

    @Test
    fun `event dispatcher exposes title-screen ticks and render hooks needed by Floyd modules`() {
        val dispatcher = source("src/main/kotlin/floydaddons/not/dogshit/client/events/EventDispatcher.kt")
        val eventBus = source("src/main/kotlin/floydaddons/not/dogshit/client/events/core/EventBus.kt")
        val renderUtils = source("src/main/kotlin/floydaddons/not/dogshit/client/utils/render/RenderUtils.kt")
        val compatibility = source("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/misc/FloydCompatibility.kt")
        val discord = source("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/misc/FloydDiscordPresence.kt")
        val localControl = source("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/misc/FloydLocalControl.kt")
        val skin = source("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/cosmetic/FloydSkin.kt")
        val mobEsp = source("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/render/FloydMobEsp.kt")
        val render = source("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/render/FloydRender.kt")

        for (expected in listOf(
            "ClientTickEvents.START_CLIENT_TICK.register",
            "ClientTickEvents.END_CLIENT_TICK.register",
            "ClientTickEvents.START_WORLD_TICK.register",
            "ClientTickEvents.END_WORLD_TICK.register",
            "WorldRenderEvents.END_EXTRACTION.register",
            "WorldRenderEvents.END_MAIN.register",
            "ScreenEvents.AFTER_INIT.register",
            "ScreenEvents.BEFORE_INIT.register",
            "ClientReceiveMessageEvents.ALLOW_GAME.register",
            "onReceive<ClientboundSystemChatPacket>",
        )) {
            assertTrue(dispatcher.contains(expected), "EventDispatcher missing active Odin event bridge: $expected")
        }
        for (expected in listOf(
            "fun state(): Map<String, Any?>",
            "\"subscriberCount\" to activeSubscribers.size",
            "\"listenerEventCount\" to listenerArrays.size",
            "\"invokerCount\" to invokers.size",
            "\"subscribers\" to activeSubscribers.map",
            "\"listeners\" to listenerArrays.entries",
        )) {
            assertTrue(eventBus.contains(expected), "EventBus state missing token: $expected")
        }

        for ((sourceName, moduleSource) in mapOf(
            "FloydCompatibility" to compatibility,
            "FloydDiscordPresence" to discord,
            "FloydLocalControl" to localControl,
            "FloydRender" to render,
            "FloydSkin" to skin,
        )) {
            assertTrue(moduleSource.contains("on<TickEvent.ClientEnd>"), "$sourceName must tick on title screen")
            assertTrue(moduleSource.contains("toggled = true"), "$sourceName must start enabled like Floyd's startup services")
        }

        assertTrue(render.contains("if (window.isFullscreen) {\n                window.toggleFullScreen()"), "Borderless window startup must exit exclusive fullscreen before applying borderless mode")

        assertTrue(mobEsp.contains("@AlwaysActive"), "Mob ESP stalk/render hooks must remain subscribed while the feature toggle is off")
        assertTrue(mobEsp.contains("on<TickEvent.ClientEnd>"), "Mob ESP must keep a client-tick cache cleanup hook")
        assertTrue(mobEsp.contains("on<TickEvent.End>"), "Mob ESP world scan tick hook missing")
        assertTrue(mobEsp.contains("on<RenderEvent.Extract>"), "Mob ESP render extraction hook missing")
        assertTrue(compatibility.contains("FloydUpdateChecker.init()"), "Compatibility startup must initialize Floyd update metadata")
        for (expected in listOf(
            "fun state(): Map<String, Any?>",
            "\"lines\" to renderConsumer.lines.size",
            "\"filledBoxes\" to renderConsumer.filledBoxes.size",
            "\"wireBoxes\" to renderConsumer.wireBoxes.size",
            "\"texts\" to renderConsumer.texts.size",
            "\"texturedQuads\" to renderConsumer.texturedQuads.size",
        )) {
            assertTrue(renderUtils.contains(expected), "RenderBatchManager state missing token: $expected")
        }
    }

    @Test
    fun `runtime feature keybind toggles do not persist config like Floyd raw keybindings`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/FloydAddonsClient.java")
        val xray = source("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/render/FloydXray.kt")
        val mobEsp = source("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/render/FloydMobEsp.kt")
        val camera = source("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/camera/FloydCamera.kt")

        for (expected in listOf(
            "while (xrayToggleKey.wasPressed()) {\n                RenderConfig.toggleXray();\n            }",
            "while (mobEspToggleKey.wasPressed()) {\n                RenderConfig.toggleMobEsp();\n            }",
            "while (freecamToggleKey.wasPressed()) {\n                CameraConfig.toggleFreecam();\n            }",
            "while (freelookToggleKey.wasPressed()) {\n                CameraConfig.toggleFreelook();\n            }",
        )) {
            assertTrue(floyd.contains(expected), "Vendored Floyd keybind handler missing runtime-only toggle: $expected")
        }

        assertTrue(xray.contains("private val toggleKey by KeybindSetting(\"Toggle X-Ray\""))
        assertTrue(xray.contains("val active = toggleXray()"))
        assertTrue(!xray.contains("val active = toggleXray()\n        ModuleManager.saveConfigurations()"))

        assertTrue(mobEsp.contains("private val toggleKey by KeybindSetting(\"Toggle Mob ESP\""))
        assertTrue(mobEsp.contains("toggle()\n        modMessage(toggleSummary())"))
        assertTrue(!mobEsp.contains("toggle()\n        ModuleManager.saveConfigurations()\n        modMessage(toggleSummary())"))

        assertTrue(camera.contains("private val freecamKey by KeybindSetting(\"Toggle Freecam\""))
        assertTrue(camera.contains("private val freelookKey by KeybindSetting(\"Toggle Freelook\""))
        assertTrue(camera.contains("toggleFreecam()\n        modMessage(if (freecamActive())"))
        assertTrue(camera.contains("toggleFreelook()\n        modMessage(if (freelookActive())"))
        assertTrue(!camera.contains("toggleFreecam()\n        ModuleManager.saveConfigurations()"))
        assertTrue(!camera.contains("toggleFreelook()\n        ModuleManager.saveConfigurations()"))
    }

    @Test
    fun `mob esp clears runtime caches when enabled filters leave world`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/esp/MobEspManager.java")
        val active = source("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/render/FloydMobEsp.kt")

        assertTrue(floyd.contains("public static void tickScan()"), "Vendored Floyd MobEspManager tick scan missing")
        assertTrue(floyd.contains("if (mc == null || mc.world == null || mc.player == null) {"))
        assertTrue(floyd.contains("clearCaches();"))
        assertTrue(active.contains("on<TickEvent.ClientEnd>"), "Active Mob ESP missing client tick cleanup")
        assertTrue(active.contains("if (enabled && hasFilters() && (mc.level == null || mc.player == null)) reloadRuntimeCaches()"))
    }

    @Test
    fun `mob esp npc scan only runs when enabled filters exist`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/features/misc/NpcTracker.java")
        val active = source("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/render/FloydMobEsp.kt")

        assertTrue(floyd.contains("if (!MobEspManager.isEnabled() || !MobEspManager.hasFilters()) return;"))
        assertTrue(active.contains("if (enabled && hasFilters()) scanNpcNames()"))
    }

    @Test
    fun `update checker waits for a player before checking or delivering messages`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/features/misc/UpdateChecker.java")
        val active = source("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/misc/FloydUpdateChecker.kt")

        assertTrue(floyd.contains("if (client.player == null) return;"), "Vendored Floyd update checker no longer gates on player presence")
        assertTrue(floyd.contains("try (InputStream is = UpdateChecker.class.getResourceAsStream(\"/fabric.mod.json\"))"))
        assertTrue(floyd.contains("if (!\"floydaddons\".equals(mod.get(\"id\").getAsString()))"))
        assertTrue(floyd.contains("if (currentSemver == null || tagSuffix == null) return;"))
        assertTrue(active.contains("fun init()"))
        assertTrue(active.contains("val version = FloydAddonsMod.MOD_VERSION"), "Active update checker must use FloydAddonsMod constants so loader-hiding self lookups cannot suppress metadata")
        assertFalse(active.contains("getResourceAsStream(\"/fabric.mod.json\")"), "Active update checker must not parse raw fabric.mod.json resources because dev runs can expose unexpanded placeholders")
        assertFalse(active.contains("getModContainer(FloydAddonsMod.MOD_ID)"), "Active update checker must not depend on public self-lookups that Floyd's loader-hiding mixin can suppress")
        assertFalse(active.contains("wrong mod id, skipping"), "Active update checker must not reject the loader-owned floydaddons container")
        assertTrue(active.contains("val currentSemver = currentSemver ?: return"))
        assertTrue(active.contains("val tagSuffix = tagSuffix ?: return"))
        assertTrue(floyd.indexOf("if (client.player == null) return;") < floyd.indexOf("Text msg = pendingMessage;"))
        assertTrue(floyd.contains("client.player.sendMessage(msg, false);"))
        assertTrue(floyd.contains("Text.literal(\"[FloydAddons] \")"))
        assertTrue(active.contains("if (FloydAddonsMod.mc.player == null) return"), "Active update checker must gate on player presence")
        assertTrue(active.indexOf("if (FloydAddonsMod.mc.player == null) return") < active.indexOf("pendingMessage?.let"))
        assertTrue(active.contains("FloydAddonsMod.mc.gui.chat.addMessage(it)"))
        assertTrue(active.contains("Component.literal(\"[FloydAddons] \")"))
        assertTrue(active.contains("fun state(): Map<String, Any?> = mapOf("))
        assertTrue(active.contains("\"currentSemver\" to currentSemver"))
        assertTrue(active.contains("\"tagSuffix\" to tagSuffix"))
        assertTrue(active.contains("\"checked\" to checked.get()"))
        assertTrue(active.contains("\"pendingMessage\" to (pendingMessage != null)"))
        assertTrue(floyd.contains("Integer.compare(av[i], bv[i])"))
        assertTrue(floyd.contains("try { result[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) {}"))
        assertTrue(active.contains("return l[i].compareTo(r[i])"))
        assertTrue(active.contains("part.toIntOrNull() ?: 0"))
    }

    @Test
    fun `taskbar icon preserves Floyd resource loading and failure semantics`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/util/TaskbarIconManager.java")
        val active = source("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/misc/FloydTaskbarIcon.kt")

        for (icon in listOf(
            "icons/taskbar_icon_16x16.png",
            "icons/taskbar_icon_32x32.png",
            "icons/taskbar_icon_48x48.png",
            "icons/taskbar_icon_128x128.png",
        )) {
            assertTrue(floyd.contains(icon), "Vendored Floyd taskbar source missing icon: $icon")
            assertTrue(active.contains(icon), "Active taskbar source missing icon: $icon")
        }

        for (expected in listOf(
            "NativeImage.read",
            "MemoryUtil.memAlloc(pixels",
            "buffer.put(pixel.toByte())",
            "buffer.put((pixel shr 8).toByte())",
            "buffer.put((pixel shr 16).toByte())",
            "buffer.put((pixel shr 24).toByte())",
            "GLFW.glfwSetWindowIcon",
            "loaded.forEach { MemoryUtil.memFree(it.pixels) }",
        )) {
            assertTrue(active.contains(expected), "Active taskbar source missing Floyd icon-loading token: $expected")
        }

        assertTrue(floyd.contains("if (loaded.isEmpty()) {"), "Vendored Floyd taskbar source must keep missing-icon fallback")
        assertTrue(active.contains("if (loaded.isEmpty()) return"), "Active taskbar source must leave the default icon when resources are missing")
        assertTrue(
            active.indexOf("if (loaded.isEmpty()) return") < active.indexOf("applied = true", active.indexOf("val loaded")),
            "Active taskbar source must not mark the icon applied before resources load, so startup ticks can retry"
        )
        assertTrue(floyd.contains("catch (Exception e)"), "Vendored Floyd taskbar source must tolerate GLFW failures")
        assertTrue(active.contains("catch (_: Exception)"), "Active taskbar source must tolerate GLFW failures")
    }

    @Test
    fun `discord presence preserves Floyd app defaults callback loop and state text`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/features/misc/DiscordPresenceManager.java")
        val active = source("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/misc/FloydDiscordPresence.kt")

        for (expected in listOf(
            "\"1471448522279747595\"",
            "\"floydaddons_icon\"",
            "\"FLOYDADDONS_DISCORD_APP_ID\"",
            "\"FLOYDADDONS_DISCORD_LARGE_IMAGE\"",
            "Instant.now().getEpochSecond()",
            "Discord_Initialize(APP_ID, handlers, true, null)",
            "updatePresence(\"In menus\")",
            "Discord_RunCallbacks()",
            "Thread.sleep(2000L)",
            "\"FloydAddons-DiscordRPC\"",
            "callbackThread.setDaemon(true)",
            "presence.details = \"Playing Floyd Addons\"",
            "presence.largeImageText = \"Floyd Addons\"",
            "presence.startTimestamp = SESSION_START",
            "\"Multiplayer: \" + name",
            "\"Singleplayer world\"",
            "\"In menus\"",
            "Discord_ClearPresence()",
            "Discord_Shutdown()",
        )) {
            assertTrue(floyd.contains(expected), "Vendored Floyd Discord source missing token: $expected")
        }

        for (expected in listOf(
            "\"1471448522279747595\"",
            "\"floydaddons_icon\"",
            "\"FLOYDADDONS_DISCORD_APP_ID\"",
            "\"FLOYDADDONS_DISCORD_LARGE_IMAGE\"",
            "Instant.now().epochSecond",
            "currentRpc.Discord_Initialize(appId, DiscordEventHandlers(), true, null)",
            "updatePresence(\"In menus\")",
            "currentRpc.Discord_RunCallbacks()",
            "Thread.sleep(2000L)",
            "\"FloydAddons-DiscordRPC\"",
            "isDaemon = true",
            "details = \"Playing Floyd Addons\"",
            "largeImageText = \"Floyd Addons\"",
            "startTimestamp = sessionStart",
            "\"Multiplayer: \$name\"",
            "\"Singleplayer world\"",
            "\"In menus\"",
            "rpc?.Discord_ClearPresence()",
            "rpc?.Discord_Shutdown()",
        )) {
            assertTrue(active.contains(expected), "Active Discord presence source missing Floyd token: $expected")
        }

        assertTrue(active.contains("val shouldRun = enabled"), "Active Discord presence must remain controlled by the Odin module enabled state")
        assertTrue(active.contains("fun startIfEnabled()"), "Active Discord presence must start during ModuleManager initialization like Floyd startup")
        assertTrue(active.contains("on<TickEvent.ClientEnd>"), "Active Discord presence must keep title-screen tick updates")
        assertTrue(active.contains("\"lastState\" to lastState"), "Active Discord state must expose the last presence string for runtime smoke proof")
        assertTrue(active.contains("\"callbackThreadAlive\" to (callbackThread?.isAlive == true)"), "Active Discord state must expose callback thread liveness")
    }

    private fun source(path: String): String = Files.readString(root.resolve(path)).replace("\r\n", "\n")
}
