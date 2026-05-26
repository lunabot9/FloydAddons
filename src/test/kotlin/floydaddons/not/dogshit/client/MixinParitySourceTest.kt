package floydaddons.not.dogshit.client

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MixinParitySourceTest {
    private val root = Path.of("").toAbsolutePath()

    @Test
    fun `active mixin config only references existing Odin-mapped source files`() {
        val mixin = JsonParser.parseString(source("src/main/resources/floydaddons.mixins.json")).asJsonObject
        val activeEntries = listOf("client", "mixins")
            .flatMap { section -> mixin.getAsJsonArray(section).map { it.asString } }

        val missing = activeEntries.map { root.resolve("src/main/java/floydaddons/not/dogshit/mixin/${it.replace('.', '/')}.java") }
            .filterNot { Files.isRegularFile(it) }
            .map { root.relativize(it).toString() }

        assertEquals(emptyList(), missing)
        assertTrue(activeEntries.contains("mixins.FontMixin"))
        assertTrue(activeEntries.contains("mixins.GuiMixin"))
        assertTrue(activeEntries.contains("mixins.ChatComponentMixin"))
        assertTrue(activeEntries.contains("mixins.FloydTimeUpdateMixin"))
        assertTrue(activeEntries.contains("mixins.FloydWatchdogMessageMixin"))
        assertTrue(activeEntries.contains("mixins.XraySodiumAlphaMixin"))
    }

    @Test
    fun `Floyd behavior mixins are represented by active Odin-mapped mixins`() {
        val pairs = mapOf(
            "NickHiderTextRendererMixin.java" to "FontMixin.java",
            "PlayerEntityRendererMixin.java" to "AvatarRendererMixin.java",
            "PlayerSizeMixin.java" to "AvatarRendererMixin.java",
            "BrandSpoofMixin.java" to "FloydBrandSpoofMixin.java",
            "FabricLoaderImplMixin.java" to "FloydFabricLoaderMixin.java",
            "ScoreboardSidebarMixin.java" to "GuiMixin.java",
            "XrayRenderLayersMixin.java" to "XrayRenderLayersMixin.java",
            "XrayIndigoMixin.java" to "XrayIndigoAlphaMixin.java",
            "XraySodiumAlphaMixin.java" to "XraySodiumAlphaMixin.java",
            "XraySodiumFaceCullMixin.java" to "XraySodiumFaceCullMixin.java",
            "XraySodiumFluidAlphaMixin.java" to "XraySodiumFluidAlphaMixin.java",
            "XrayChunkOcclusionMixin.java" to "XrayOcclusionMixin.java",
            "XrayLightmapMixin.java" to "XrayLightTextureMixin.java",
            "TitleScreenBackgroundMixin.java" to "FloydTitleScreenBackgroundMixin.java",
            "HiderOverlayMixin.java" to "ScreenEffectRendererMixin.java",
            "HiderGameRendererMixin.java" to "GameRendererMixin.java",
            "HiderInGameHudMixin.java" to "GuiMixin.java",
            "HiderFallingBlockMixin.java" to "HiderFallingBlockRendererMixin.java",
            "HiderParticleMixin.java" to "ParticleEngineMixin.java",
            "HiderPlayerListMixin.java" to "PlayerTabOverlayMixin.java",
            "HiderArmorMixin.java" to "HiderArmorLayerMixin.java",
            "HiderHeadMixin.java" to "HiderHeadLayerMixin.java",
            "HiderArrowMixin.java" to "HiderArrowLayerMixin.java",
            "HiderFireEntityMixin.java" to "EntityMixin.java",
            "CameraAccessor.java" to "CameraAccessor.java",
            "CameraMixin.java" to "CameraMixin.java",
            "CameraMouseMixin.java" to "CameraMouseMixin.java",
            "CameraMovementMixin.java" to "CameraLocalPlayerInputMixin.java",
            "CameraClientMixin.java" to "CameraClientControlMixin.java",
            "CameraPerspectiveMixin.java" to "CameraTypeMixin.java",
            "ItemInHandRendererMixin.java" to "AnimationItemInHandRendererMixin.java",
            "SwingDurationMixin.java" to "AnimationLivingEntityMixin.java",
            "ClassicClickMixin.java" to "AnimationToggleKeyMappingMixin.java",
            "TimeUpdateMixin.java" to "FloydTimeUpdateMixin.java",
            "WatchdogMessageHiderMixin.java" to "FloydWatchdogMessageMixin.java",
        )

        for ((floydMixin, activeMixin) in pairs) {
            assertTrue(
                Files.isRegularFile(root.resolve("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/mixin/$floydMixin")),
                "Missing vendored Floyd mixin $floydMixin"
            )
            assertTrue(
                Files.isRegularFile(root.resolve("src/main/java/floydaddons/not/dogshit/mixin/mixins/$activeMixin")) ||
                    Files.isRegularFile(root.resolve("src/main/java/floydaddons/not/dogshit/mixin/accessors/$activeMixin")),
                "Missing active Odin-mapped mixin $activeMixin for $floydMixin"
            )
        }
    }

    @Test
    fun `Floyd accessor and lifecycle-only mixins are represented by Odin scaffold paths`() {
        val clientWorldProperties = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/mixin/ClientWorldPropertiesAccessor.java")
        val floydRenderConfig = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/config/RenderConfig.java")
        val render = source("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/render/FloydRender.kt")
        val activeTimeMixin = activeMixin("FloydTimeUpdateMixin.java")

        assertContains(clientWorldProperties, "@Accessor(\"time\") void floydaddons\$setTime(long time);")
        assertContains(clientWorldProperties, "@Accessor(\"timeOfDay\") void floydaddons\$setTimeOfDay(long timeOfDay);")
        assertContains(floydRenderConfig, "acc.floydaddons\$setTime(")
        assertContains(floydRenderConfig, "acc.floydaddons\$setTimeOfDay(")
        assertContains(activeTimeMixin, "FloydRender.applyCustomTimeOverride()")
        assertContains(render, "val levelData = mc.level?.levelData ?: return")
        assertContains(render, "levelData.setDayTime(ticks)")
        assertContains(render, "levelData.setGameTime(ticks)")

        val worldRendererAccessor = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/mixin/WorldRendererAccessor.java")
        val mobRenderer = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/esp/MobEspRenderer.java")
        val stalkRenderer = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/esp/StalkRenderer.java")
        val mobEsp = source("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/render/FloydMobEsp.kt")
        val renderUtils = source("src/main/kotlin/floydaddons/not/dogshit/client/utils/render/RenderUtils.kt")
        val dispatcher = source("src/main/kotlin/floydaddons/not/dogshit/client/events/EventDispatcher.kt")

        assertContains(worldRendererAccessor, "@Accessor")
        assertContains(worldRendererAccessor, "BufferBuilderStorage getBufferBuilders();")
        assertContains(mobRenderer, "((WorldRendererAccessor) mc.worldRenderer).getBufferBuilders()")
        assertContains(stalkRenderer, "((WorldRendererAccessor) mc.worldRenderer).getBufferBuilders()")
        assertContains(dispatcher, "WorldRenderEvents.END_EXTRACTION.register")
        assertContains(dispatcher, "RenderBatchManager.renderConsumer")
        assertContains(mobEsp, "on<RenderEvent.Extract>")
        assertContains(mobEsp, "drawWireFrameBox(")
        assertContains(mobEsp, "drawTracer(")
        assertContains(renderUtils, "val bufferSource = context.consumers() as? MultiBufferSource.BufferSource ?: return@on")
        assertContains(renderUtils, "matrix.renderQueuedLinesAndWireBoxes(renderConsumer.lines, renderConsumer.wireBoxes, bufferSource)")

        val skinConfigSaver = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/mixin/SkinConfigSaverMixin.java")
        val floydClient = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/FloydAddonsClient.java")
        val activeInitializer = source("src/main/kotlin/floydaddons/not/dogshit/client/FloydAddonsMod.kt")

        assertContains(skinConfigSaver, "Dummy mixin to ensure SkinConfig.save() is called on client stop")
        assertContains(floydClient, "ClientLifecycleEvents.CLIENT_STOPPING.register")
        assertContains(floydClient, "FloydAddonsConfig.save()")
        assertContains(activeInitializer, "ClientLifecycleEvents.CLIENT_STOPPING.register")
        assertContains(activeInitializer, "onClientStopping()")
        assertContains(activeInitializer, "ModuleManager.saveConfigurations()")
    }

    @Test
    fun `active Odin-mapped mixins call the Floyd feature modules they implement`() {
        assertContains(activeMixin("FontMixin.java"), "CustomNameReplacer")
        assertContains(activeMixin("FontMixin.java"), "ChatChroma.INSTANCE.transform")
        assertContains(activeMixin("ChatComponentMixin.java"), "ChatChroma.INSTANCE.beginRender()")
        assertContains(activeMixin("ChatComponentMixin.java"), "ChatChroma.INSTANCE.endRender()")
        assertContains(activeMixin("AvatarRendererMixin.java"), "FloydSkin")
        assertContains(activeMixin("FloydBrandSpoofMixin.java"), "FloydCompatibility.shouldSpoofClientBrand()")
        assertContains(activeMixin("FloydFabricLoaderMixin.java"), "FloydCompatibility.shouldHideLoaderEntry()")
        assertContains(activeMixin("GuiMixin.java"), "FloydHud.shouldCancelVanillaScoreboard")
        assertContains(activeMixin("GuiMixin.java"), "FloydHud.markVanillaScoreboardWouldRender()")
        assertContains(activeMixin("GuiMixin.java"), "FloydHiders.shouldDisableHungerBar()")
        assertContains(activeMixin("FloydTimeUpdateMixin.java"), "FloydRender.shouldUseCustomTime()")
        assertContains(activeMixin("FloydWatchdogMessageMixin.java"), "FloydCompatibility.shouldHideWatchdogMessages()")
        assertContains(activeMixin("FloydTitleScreenBackgroundMixin.java"), "FloydCompatibility.shouldUseCustomMainMenu()")

        for (mixin in listOf(
            "XrayRenderLayersMixin.java",
            "XrayIndigoAlphaMixin.java",
            "XraySodiumAlphaMixin.java",
            "XraySodiumFaceCullMixin.java",
            "XraySodiumFluidAlphaMixin.java",
            "XrayOcclusionMixin.java",
            "XrayLightTextureMixin.java",
        )) {
            assertContains(activeMixin(mixin), "FloydXray")
        }

        for (mixin in listOf(
            "ScreenEffectRendererMixin.java",
            "GameRendererMixin.java",
            "HiderFallingBlockRendererMixin.java",
            "ParticleEngineMixin.java",
            "PlayerTabOverlayMixin.java",
            "HiderArmorLayerMixin.java",
            "HiderHeadLayerMixin.java",
            "HiderArrowLayerMixin.java",
            "EntityMixin.java",
        )) {
            assertContains(activeMixin(mixin), "FloydHiders")
        }

        for (mixin in listOf(
            "CameraMixin.java",
            "CameraMouseMixin.java",
            "CameraLocalPlayerInputMixin.java",
            "CameraClientControlMixin.java",
            "CameraTypeMixin.java",
        )) {
            assertContains(activeMixin(mixin), "FloydCamera")
        }

        for (mixin in listOf(
            "AnimationItemInHandRendererMixin.java",
            "AnimationLivingEntityMixin.java",
            "AnimationToggleKeyMappingMixin.java",
        )) {
            assertContains(activeMixin(mixin), "FloydAnimations")
        }
    }

    @Test
    fun `Watchdog message hider preserves Floyd suppression phrases on current chat packet paths`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/mixin/WatchdogMessageHiderMixin.java")
        val active = activeMixin("FloydWatchdogMessageMixin.java")

        assertContains(floyd, "onGameMessage")
        assertContains(active, "handleSystemChat")
        assertContains(active, "handleDisguisedChat")
        assertContains(active, "FloydCompatibility.shouldHideWatchdogMessages()")

        for (phrase in listOf(
            "[watchdog announcement]",
            "watchdog has banned",
            "staff have banned an additional",
            "blacklisted modifications are a bannable offense"
        )) {
            assertContains(floyd, phrase)
            assertContains(active, phrase)
        }
    }

    @Test
    fun `title screen background mixin preserves Floyd custom mainmenu load and render path`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/mixin/TitleScreenBackgroundMixin.java")
        val active = activeMixin("FloydTitleScreenBackgroundMixin.java")

        for (expected in listOf(
            "renderBackground",
            "cancellable = true",
            "floydaddons\$customBgId",
            "floydaddons\$triedLoad",
            "if (floydaddons\$customBgId != null) return true;",
            "if (floydaddons\$triedLoad) return false;",
            "floydaddons\$triedLoad = true;",
            "mainmenu.png",
            "floydaddons_custom_mainmenu",
            "custom_mainmenu",
            "ci.cancel()",
        )) {
            assertContains(floyd, expected)
            assertContains(active, expected)
        }

        assertContains(floyd, "FloydAddonsConfig.getConfigDir().resolve(\"mainmenu.png\")")
        assertContains(active, "FloydCompatibility.configPath(\"mainmenu.png\")")
        assertContains(floyd, "NativeImage.read(Files.newInputStream(path))")
        assertContains(active, "NativeImage.read(Files.newInputStream(path))")
        assertContains(floyd, "MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex)")
        assertContains(active, "Minecraft.getInstance().getTextureManager().register(id, texture)")
        assertContains(active, "FloydCompatibility.shouldUseCustomMainMenu()")
    }

    private fun source(path: String): String = Files.readString(root.resolve(path)).replace("\r\n", "\n")

    private fun activeMixin(fileName: String): String {
        val mixin = root.resolve("src/main/java/floydaddons/not/dogshit/mixin/mixins/$fileName")
        val accessor = root.resolve("src/main/java/floydaddons/not/dogshit/mixin/accessors/$fileName")
        return Files.readString(if (Files.isRegularFile(mixin)) mixin else accessor)
    }

    private fun assertContains(source: String, expected: String) {
        assertTrue(source.contains(expected), "Expected source to contain: $expected")
    }
}
