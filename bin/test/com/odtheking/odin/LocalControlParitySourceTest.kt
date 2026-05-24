package com.odtheking.odin

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class LocalControlParitySourceTest {
    private val root = Path.of("").toAbsolutePath()

    @Test
    fun `active local-control bridge preserves Floyd endpoint and auth contract`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/control/LocalMinecraftControlServer.java")
        val active = source("src/main/kotlin/com/odtheking/odin/features/impl/misc/FloydLocalControl.kt")

        assertContains(floyd, "server.createContext(\"/\"")
        assertContains(active, "controlServer.createContext(\"/\")")
        for (endpoint in listOf(
            "/state",
            "/chat",
            "/look",
            "/hotbar",
            "/key",
            "/action",
            "/screen",
            "/mouse",
            "/screenshot",
        )) {
            assertContains(floyd, "\"$endpoint\"")
            assertContains(active, "\"$endpoint\"")
        }
        assertContains(active, "\"/replace-text\"")
        assertContains(active, "\"/type\"")

        assertContains(floyd, "\"Authorization: Bearer <token>\"")
        assertContains(active, "\"Authorization: Bearer <token>\"")
        assertContains(floyd, "\"X-FloydAddons-Token\"")
        assertContains(active, "\"X-FloydAddons-Token\"")
        assertContains(floyd, "queryParams(exchange).get(\"token\")")
        assertContains(active, "queryParams(exchange)[\"token\"]")
        assertContains(floyd, "\"loopback_only\"")
        assertContains(active, "\"loopback_only\"")
        assertContains(floyd, "\"body_too_large\"")
        assertContains(active, "\"body_too_large\"")
        assertContains(floyd, "loaded.port = loaded.port <= 0 ? DEFAULT_PORT : loaded.port;")
        assertContains(active, "if (port <= 0) DEFAULT_PORT else port")
        assertContains(floyd, "created.port = DEFAULT_PORT;")
        assertContains(active, "fun newSettingsPort(): Int = DEFAULT_PORT")
        assertContains(floyd, "\"Cache-Control\", \"no-store\"")
        assertContains(active, "\"Cache-Control\", \"no-store\"")
        assertContains(floyd, "throw new IllegalArgumentException(\"blank_\" + key);")
        assertContains(active, "throw IllegalArgumentException(\"blank_\$key\")")
        assertContains(active, "private val advertisedEndpoints = listOf(")
        assertContains(active, "\"endpoints\" to advertisedEndpoints")
        assertContains(floyd, "try {\n            settings = loadSettings();")
        assertContains(active, "try {\n            val loaded = loadSettings()")
        assertContains(floyd, "} catch (IOException e) {")
        assertContains(floyd, "LOGGER.warn(\"Failed to start local Minecraft control bridge\", e);")
        assertContains(floyd, "scheduler.shutdownNow();")
        assertContains(active, "} catch (e: IOException) {")
        assertContains(active, "FloydAddonsMod.logger.warn(\"Failed to start local control bridge\", e)")
        assertContains(active, "scheduler?.shutdownNow()")
        assertContains(floyd, "if (cause instanceof Exception exception) throw exception;")
        assertContains(active, "if (cause is Exception) throw cause")
    }

    @Test
    fun `active local-control actions preserve Floyd controls and add Odin runtime smokes`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/control/LocalMinecraftControlServer.java")
        val active = source("src/main/kotlin/com/odtheking/odin/features/impl/misc/FloydLocalControl.kt")

        for (action in listOf(
            "attack",
            "use",
            "closeScreen",
            "jump",
            "sneak",
            "sprint",
            "fullscreen",
            "windowedFullscreen",
            "borderlessWindowed",
            "reloadMod",
            "reloadResources",
        )) {
            assertContains(floyd, "\"$action\"")
            assertContains(active, "\"$action\"")
        }

        for (extension in listOf(
            "\"swing\", \"swingMainHand\"",
            "\"camera\", \"perspective\"",
            "\"connect\"",
            "\"openWorld\", \"loadWorld\", \"singleplayer\"",
            "\"reloadConfig\"",
        )) {
            assertContains(active, extension)
        }

        assertContains(active, "FloydRender.setBorderlessWindowed(enabled, force = true)")
        assertContains(active, "\"closeScreen\" -> mc.screen?.onClose() ?: mc.setScreen(null)")
        assertContains(floyd, "case \"reloadMod\", \"reloadResources\" -> {")
        assertContains(active, "\"reloadMod\", \"reloadResources\" -> {\n                    ModuleManager.loadConfigurations()")
        assertContains(active, "FloydSkin.reload()")
        assertContains(active, "FloydCape.reload()")
        assertContains(active, "FloydConeHat.reload()")
        assertContains(active, "FloydXray.rebuildChunks()")
    }

    @Test
    fun `active local-control screen mouse key and state surfaces preserve Floyd bridge shape`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/control/LocalMinecraftControlServer.java")
        val active = source("src/main/kotlin/com/odtheking/odin/features/impl/misc/FloydLocalControl.kt")
        val legacyGui = source("src/main/kotlin/com/odtheking/odin/clickgui/LegacyFloydClickGUI.kt")

        for (screen in listOf(
            "floyd",
            "floydaddons",
            "v2",
            "xrayEditor",
            "xrayBlocks",
            "mobEspEditor",
            "mobEspFilters",
            "close",
            "none",
        )) {
            assertContains(floyd, "\"$screen\"")
            assertContains(active, "\"$screen\"")
        }
        assertContains(active, "\"clickgui\"")
        assertContains(active, "\"hud\", \"edithud\"")
        assertContains(active, "mc.setScreen(ClickGUI)")
        assertContains(active, "mc.setScreen(HudManager)")

        for (event in listOf("click", "down", "press", "up", "release", "scroll", "wheel")) {
            assertContains(floyd, "\"$event\"")
            assertContains(active, "\"$event\"")
        }
        assertContains(active, "\"move\"")
        assertContains(active, "\"drag\"")
        assertContains(active, "\"clear\"")
        assertContains(active, "withMouseOverride")
        assertContains(active, "setMouseOverride(x, y)")
        assertContains(legacyGui, "private fun rawToGuiScaled(x: Double, y: Double): Pair<Double, Double>")
        assertContains(legacyGui, "x * mc.window.guiScaledWidth / mc.window.width to y * mc.window.guiScaledHeight / mc.window.height")
        assertContains(legacyGui, "x > mc.window.width || y > mc.window.height")
        assertContains(legacyGui, "private fun rememberEventMouse(x: Double, y: Double)")
        assertContains(legacyGui, "private fun activeEventMousePoint(): Pair<Double, Double>?")
        assertContains(legacyGui, "?: activeEventMousePoint()\n            ?: bestGuiMousePoint(mouseX.toDouble(), mouseY.toDouble())")
        assertContains(legacyGui, "rememberEventMouse(point.first, point.second)")
        assertContains(legacyGui, "rememberEventMouse(mx, my)")
        assertContains(legacyGui, "private fun bestGuiMousePoint(x: Double, y: Double)")
        assertContains(legacyGui, "private fun guiMouseCandidates(x: Double, y: Double)")
        assertContains(legacyGui, "private fun currentMouseHitRects(): List<Rect>")
        assertContains(legacyGui, "private fun drawModulePopupActionInput(")
        assertContains(legacyGui, "ModulePopupExtraKind.XRAY_ADD_BLOCK -> {")
        assertContains(legacyGui, "submitModulePopupActionInput(ModulePopupExtraKind.XRAY_ADD_BLOCK)")
        assertContains(legacyGui, "submitModulePopupActionInput(ModulePopupExtraKind.MOB_ADD_NAME)")
        assertContains(legacyGui, "submitModulePopupActionInput(ModulePopupExtraKind.STALK_TARGET)")
        assertTrue(!legacyGui.contains("eventX / guiScale to eventY / guiScale"), "Legacy GUI must not blindly divide already-scaled screen click coordinates")
        assertTrue(!legacyGui.contains("overrideX / guiScale to overrideY / guiScale"), "Legacy GUI must not blindly divide already-scaled control click coordinates")
        assertContains(legacyGui, "points += bestGuiMousePoint(eventX, eventY)")
        assertContains(legacyGui, "points += guiMouseCandidates(eventX, eventY)")
        assertContains(legacyGui, "points += hoverX to hoverY")
        assertContains(legacyGui, "if (mouseOverride != null)")
        assertContains(legacyGui, "points += mouseOverride.first.toDouble() to mouseOverride.second.toDouble()")
        assertContains(legacyGui, "} else {\n            points += bestGuiMousePoint(eventX, eventY)")
        assertTrue(
            legacyGui.indexOf("points += bestGuiMousePoint(eventX, eventY)") < legacyGui.indexOf("physicalMouseGuiPoint()?.let { points += it }"),
            "Legacy GUI must try event/control click coordinates before the physical mouse fallback"
        )
        assertContains(active, "private fun handleType(exchange: HttpExchange)")
        assertContains(active, "currentScreen.keyPressed(KeyEvent(GLFW.GLFW_KEY_DELETE, 0, 0))")
        assertContains(active, "currentScreen.charTyped(CharacterEvent(codepoint, 0))")
        assertContains(active, "currentScreen.keyPressed(KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0))")

        for (key in listOf("forward", "back", "left", "right", "jump", "sneak", "sprint", "attack", "use")) {
            assertContains(floyd, "\"$key\"")
            assertContains(active, "\"$key\"")
        }
        assertContains(active, "\"tab\", \"playerList\", \"player_list\"")

        for (stateKey in listOf(
            "\"window\"",
            "\"player\"",
            "\"world\"",
            "\"crosshair\"",
            "\"hotbar\"",
            "\"nearbyEntities\"",
        )) {
            assertContains(floyd, stateKey)
            assertContains(active, stateKey)
        }
        for (odinStateKey in listOf("\"scaffold\"", "\"server\"", "\"configs\"", "\"eventBus\"", "\"render\"", "\"qol\"", "\"cosmetics\"", "\"playerFeatures\"", "\"misc\"")) {
            assertContains(active, odinStateKey)
        }
        assertContains(active, "root[\"scaffold\"] = scaffoldPayload()")
        for (scaffoldStateKey in listOf(
            "\"modId\" to FloydAddonsMod.MOD_ID",
            "\"modName\" to FloydAddonsMod.MOD_NAME",
            "\"version\" to FloydAddonsMod.MOD_VERSION",
            "\"minecraftVersion\" to minecraftMetadata?.version?.friendlyString",
            "\"entrypoint\" to \"com.odtheking.odin.FloydAddonsMod\"",
            "\"mixinConfig\" to \"floydaddons.mixins.json\"",
            "\"resourceNamespace\" to FloydAddonsMod.MOD_ID",
            "\"activeScaffold\" to \"Odin Fabric module/config/event/ClickGUI\"",
            "\"vendoredBehaviorSource\" to \"vendor/floydaddons-fabric\"",
        )) {
            assertContains(active, scaffoldStateKey)
        }
        assertTrue(!active.contains("getModContainer(FloydAddonsMod.MOD_ID)"), "Scaffold state must not depend on public self-lookups hidden by FloydFabricLoaderMixin")
        assertContains(active, "root[\"modules\"] = ModuleManager.state()")
        assertContains(active, "\"floydSidecars\" to FloydSidecarConfig.state()")
        assertContains(active, "root[\"eventBus\"] = EventBus.state()")
        assertContains(active, "\"batch\" to RenderBatchManager.state()")
        assertContains(active, "\"core\" to FloydRender.state()")
        assertContains(active, "\"mobEsp\" to FloydMobEsp.state()")
        assertContains(active, "\"hud\" to FloydHud.state()")
        assertContains(active, "root[\"qol\"] = emptyMap<String, Any?>()")
        assertContains(active, "\"localControl\" to state()")
        for (localControlStateKey in listOf(
            "\"shouldRun\"",
            "\"running\"",
            "\"lastShouldRun\"",
            "\"configuredPort\"",
            "\"settingsPort\"",
            "\"settingsEnabled\"",
            "\"tokenConfigured\"",
            "\"settingsPath\"",
            "\"endpoints\"",
        )) {
            assertContains(active, localControlStateKey)
        }
        assertContains(active, "\"discordPresence\" to FloydDiscordPresence.state()")
        assertContains(active, "\"compatibility\" to FloydCompatibility.state()")
        assertContains(active, "\"clickGui\" to ClickGUIModule.state()")
        assertContains(active, "root[\"legacyGui\"] = LegacyFloydClickGUI.debugState()")
        assertContains(active, "\"features\" to FloydCamera.state()")
        assertContains(source("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydMobEsp.kt"), "\"stalkEnabled\" to stalkEnabled()")
    }

    private fun source(path: String): String = Files.readString(root.resolve(path)).replace("\r\n", "\n")

    private fun assertContains(source: String, expected: String) {
        assertTrue(source.contains(expected), "Expected source to contain: $expected")
    }
}
