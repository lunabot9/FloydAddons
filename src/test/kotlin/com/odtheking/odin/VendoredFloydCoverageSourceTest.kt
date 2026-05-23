package com.odtheking.odin

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals

class VendoredFloydCoverageSourceTest {
    private val root = Path.of("").toAbsolutePath()
    private val vendorRoot = root.resolve("vendor/floydaddons-fabric/app/src/main/java")
    private val vendorResourcesRoot = root.resolve("vendor/floydaddons-fabric/app/src/main/resources")

    @Test
    fun `every vendored Floyd source file is ported or intentionally retired`() {
        val vendoredFiles = Files.walk(vendorRoot).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .map { vendorRoot.relativize(it).toString().replace('\\', '/') }
                .sorted()
                .toList()
                .toSet()
        }

        assertEquals(
            activeBehaviorFiles + retiredOldGuiFiles + retiredGeneratedSampleFiles,
            vendoredFiles,
            "Every vendored Floyd source must be explicitly classified as active Odin behavior/provenance or intentionally retired old GUI/sample code."
        )
    }

    @Test
    fun `every vendored Floyd resource is packaged or intentionally retired`() {
        val vendoredResources = Files.walk(vendorResourcesRoot).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .map { vendorResourcesRoot.relativize(it).toString().replace('\\', '/') }
                .sorted()
                .toList()
                .toSet()
        }

        assertEquals(
            packagedFloydResources + retiredOldGuiResources + retiredFloydMetadataResources,
            vendoredResources,
            "Every vendored Floyd resource must be explicitly classified as packaged in the Odin scaffold or intentionally retired."
        )
    }

    @Test
    fun `packaged Floyd runtime resources match vendored bytes`() {
        val mismatched = packagedFloydResources
            .filterNot { relativePath ->
                Files.readAllBytes(root.resolve("src/main/resources").resolve(relativePath))
                    .contentEquals(Files.readAllBytes(vendorResourcesRoot.resolve(relativePath)))
            }

        assertEquals(
            emptyList(),
            mismatched,
            "Packaged Floyd runtime assets must remain byte-for-byte copies of vendored Floyd resources."
        )
    }

    @Test
    fun `active vendored behavior files are referenced outside the coverage ledger`() {
        val sourceRoots = listOf(
            root.resolve("src/main/kotlin"),
            root.resolve("src/main/java"),
            root.resolve("src/test/kotlin"),
        )
        val activeText = sourceRoots
            .flatMap(::sourceFiles)
            .filterNot { it.name == "VendoredFloydCoverageSourceTest.kt" }
            .joinToString("\n") { Files.readString(it) }

        val unreferenced = activeBehaviorFiles
            .filterNot { relativePath -> activeText.contains(Path.of(relativePath).fileName.toString().removeSuffix(".java")) }

        assertEquals(
            emptyList(),
            unreferenced,
            "Every active vendored Floyd behavior file must be named or pinned by active source/tests outside the coverage ledger."
        )
    }

    private val activeBehaviorFiles = setOf(
        "floydaddons/not/dogshit/client/FloydAddonsClient.java",
        "floydaddons/not/dogshit/client/FloydAddonsCommand.java",
        "floydaddons/not/dogshit/client/FloydAddonsConfig.java",
        "floydaddons/not/dogshit/client/config/AnimationConfig.java",
        "floydaddons/not/dogshit/client/config/CameraConfig.java",
        "floydaddons/not/dogshit/client/config/HidersConfig.java",
        "floydaddons/not/dogshit/client/config/NickHiderConfig.java",
        "floydaddons/not/dogshit/client/config/RenderConfig.java",
        "floydaddons/not/dogshit/client/config/SkinConfig.java",
        "floydaddons/not/dogshit/client/control/LocalMinecraftControlServer.java",
        "floydaddons/not/dogshit/client/esp/MobEspManager.java",
        "floydaddons/not/dogshit/client/esp/MobEspRenderer.java",
        "floydaddons/not/dogshit/client/esp/StalkManager.java",
        "floydaddons/not/dogshit/client/esp/StalkRenderer.java",
        "floydaddons/not/dogshit/client/features/cosmetic/CapeFeatureRenderer.java",
        "floydaddons/not/dogshit/client/features/cosmetic/CapeManager.java",
        "floydaddons/not/dogshit/client/features/cosmetic/ConeFeatureRenderer.java",
        "floydaddons/not/dogshit/client/features/cosmetic/ConeHatManager.java",
        "floydaddons/not/dogshit/client/features/hud/InventoryHudRenderer.java",
        "floydaddons/not/dogshit/client/features/hud/ScoreboardHudRenderer.java",
        "floydaddons/not/dogshit/client/features/misc/DiscordPresenceManager.java",
        "floydaddons/not/dogshit/client/features/misc/NpcTracker.java",
        "floydaddons/not/dogshit/client/features/misc/ServerIdTracker.java",
        "floydaddons/not/dogshit/client/features/misc/UpdateChecker.java",
        "floydaddons/not/dogshit/client/skin/SkinManager.java",
        "floydaddons/not/dogshit/client/util/NickTextUtil.java",
        "floydaddons/not/dogshit/client/util/RenderCompat.java",
        "floydaddons/not/dogshit/client/util/TaskbarIconManager.java",
        "floydaddons/not/dogshit/mixin/BrandSpoofMixin.java",
        "floydaddons/not/dogshit/mixin/CameraAccessor.java",
        "floydaddons/not/dogshit/mixin/CameraClientMixin.java",
        "floydaddons/not/dogshit/mixin/CameraMixin.java",
        "floydaddons/not/dogshit/mixin/CameraMouseMixin.java",
        "floydaddons/not/dogshit/mixin/CameraMovementMixin.java",
        "floydaddons/not/dogshit/mixin/CameraPerspectiveMixin.java",
        "floydaddons/not/dogshit/mixin/ClassicClickMixin.java",
        "floydaddons/not/dogshit/mixin/ClientWorldPropertiesAccessor.java",
        "floydaddons/not/dogshit/mixin/FabricLoaderImplMixin.java",
        "floydaddons/not/dogshit/mixin/HiderArmorMixin.java",
        "floydaddons/not/dogshit/mixin/HiderArrowMixin.java",
        "floydaddons/not/dogshit/mixin/HiderFallingBlockMixin.java",
        "floydaddons/not/dogshit/mixin/HiderFireEntityMixin.java",
        "floydaddons/not/dogshit/mixin/HiderGameRendererMixin.java",
        "floydaddons/not/dogshit/mixin/HiderHeadMixin.java",
        "floydaddons/not/dogshit/mixin/HiderInGameHudMixin.java",
        "floydaddons/not/dogshit/mixin/HiderOverlayMixin.java",
        "floydaddons/not/dogshit/mixin/HiderParticleMixin.java",
        "floydaddons/not/dogshit/mixin/HiderPlayerListMixin.java",
        "floydaddons/not/dogshit/mixin/ItemInHandRendererMixin.java",
        "floydaddons/not/dogshit/mixin/NickHiderTextRendererMixin.java",
        "floydaddons/not/dogshit/mixin/PlayerEntityRendererMixin.java",
        "floydaddons/not/dogshit/mixin/PlayerSizeMixin.java",
        "floydaddons/not/dogshit/mixin/ScoreboardSidebarMixin.java",
        "floydaddons/not/dogshit/mixin/SkinConfigSaverMixin.java",
        "floydaddons/not/dogshit/mixin/SwingDurationMixin.java",
        "floydaddons/not/dogshit/mixin/TimeUpdateMixin.java",
        "floydaddons/not/dogshit/mixin/TitleScreenBackgroundMixin.java",
        "floydaddons/not/dogshit/mixin/WatchdogMessageHiderMixin.java",
        "floydaddons/not/dogshit/mixin/WorldRendererAccessor.java",
        "floydaddons/not/dogshit/mixin/XrayChunkOcclusionMixin.java",
        "floydaddons/not/dogshit/mixin/XrayIndigoMixin.java",
        "floydaddons/not/dogshit/mixin/XrayLightmapMixin.java",
        "floydaddons/not/dogshit/mixin/XrayRenderLayersMixin.java",
        "floydaddons/not/dogshit/mixin/XraySodiumAlphaMixin.java",
        "floydaddons/not/dogshit/mixin/XraySodiumFaceCullMixin.java",
        "floydaddons/not/dogshit/mixin/XraySodiumFluidAlphaMixin.java",
    )

    private val retiredOldGuiFiles = setOf(
        "floydaddons/not/dogshit/client/features/cosmetic/CapeScreen.java",
        "floydaddons/not/dogshit/client/features/cosmetic/ConeHatScreen.java",
        "floydaddons/not/dogshit/client/features/hud/HudScreen.java",
        "floydaddons/not/dogshit/client/features/hud/MoveHudScreen.java",
        "floydaddons/not/dogshit/client/features/hud/MoveInventoryScreen.java",
        "floydaddons/not/dogshit/client/features/hud/MoveScoreboardScreen.java",
        "floydaddons/not/dogshit/client/features/visual/TimeSliderWidget.java",
        "floydaddons/not/dogshit/client/gui/AnimationsScreen.java",
        "floydaddons/not/dogshit/client/gui/CameraScreen.java",
        "floydaddons/not/dogshit/client/gui/ClickGuiConfig.java",
        "floydaddons/not/dogshit/client/gui/ClickGuiScreen.java",
        "floydaddons/not/dogshit/client/gui/ColorPickerScreen.java",
        "floydaddons/not/dogshit/client/gui/GuiStyleScreen.java",
        "floydaddons/not/dogshit/client/gui/HidersScreen.java",
        "floydaddons/not/dogshit/client/gui/MobEspEditorScreen.java",
        "floydaddons/not/dogshit/client/gui/MobEspScreen.java",
        "floydaddons/not/dogshit/client/gui/ModuleCategory.java",
        "floydaddons/not/dogshit/client/gui/ModuleEntry.java",
        "floydaddons/not/dogshit/client/gui/NameMappingsEditorScreen.java",
        "floydaddons/not/dogshit/client/gui/NickHiderScreen.java",
        "floydaddons/not/dogshit/client/gui/RenderScreen.java",
        "floydaddons/not/dogshit/client/gui/SkinScreen.java",
        "floydaddons/not/dogshit/client/gui/SkinSettingsScreen.java",
        "floydaddons/not/dogshit/client/gui/XrayEditorScreen.java",
        "floydaddons/not/dogshit/client/gui/v2/FloydAddonsV2Screen.java",
        "floydaddons/not/dogshit/client/gui/v2/row/AnimationsRow.java",
        "floydaddons/not/dogshit/client/gui/v2/row/EspRow.java",
        "floydaddons/not/dogshit/client/gui/v2/row/HidersRow.java",
        "floydaddons/not/dogshit/client/gui/v2/row/VisualRow.java",
        "floydaddons/not/dogshit/client/gui/v2/row/XrayRow.java",
        "floydaddons/not/dogshit/client/gui/v2/tab/CosmeticTab.java",
        "floydaddons/not/dogshit/client/gui/v2/tab/NeckHiderTab.java",
        "floydaddons/not/dogshit/client/gui/v2/tab/QolTab.java",
        "floydaddons/not/dogshit/client/gui/v2/tab/RenderTab.java",
        "floydaddons/not/dogshit/client/gui/v2/tab/V2Tab.java",
        "floydaddons/not/dogshit/client/gui/v2/widget/AccordionRow.java",
        "floydaddons/not/dogshit/client/gui/v2/widget/ContentPane.java",
        "floydaddons/not/dogshit/client/gui/v2/widget/InlineColorPicker.java",
        "floydaddons/not/dogshit/client/gui/v2/widget/LabeledDropdown.java",
        "floydaddons/not/dogshit/client/gui/v2/widget/MetallicButton.java",
        "floydaddons/not/dogshit/client/gui/v2/widget/NvgRoundedTextureRenderer.java",
        "floydaddons/not/dogshit/client/gui/v2/widget/SidebarTab.java",
        "floydaddons/not/dogshit/client/gui/v2/widget/Slider.java",
        "floydaddons/not/dogshit/client/gui/v2/widget/ToggleSwitch.java",
        "floydaddons/not/dogshit/client/gui/v2/widget/V2Theme.java",
    )

    private val retiredGeneratedSampleFiles = setOf(
        "floydaddons/not/dogshit/App.java",
    )

    private val packagedFloydResources = setOf(
        "assets/floydaddons/fonts/inter_regular.ttf",
        "assets/floydaddons/fonts/inter_semibold.ttf",
        "assets/floydaddons/icons/taskbar_icon_128x128.png",
        "assets/floydaddons/icons/taskbar_icon_16x16.png",
        "assets/floydaddons/icons/taskbar_icon_32x32.png",
        "assets/floydaddons/icons/taskbar_icon_48x48.png",
        "assets/floydaddons/lang/en_us.json",
        "assets/floydaddons/textures/cape/default_cape.png",
        "assets/floydaddons/textures/entity/cone.png",
        "assets/floydaddons/textures/skin/custom.png",
    )

    private val retiredOldGuiResources = setOf(
        "assets/floydaddons/textures/gui/figma/cosmetic_cape_hi.png",
        "assets/floydaddons/textures/gui/figma/cosmetic_cape_popup_hi.png",
        "assets/floydaddons/textures/gui/figma/cosmetic_cone_dropdown_hi.png",
        "assets/floydaddons/textures/gui/figma/cosmetic_cone_hi.png",
        "assets/floydaddons/textures/gui/figma/cosmetic_hi.png",
        "assets/floydaddons/textures/gui/figma/cosmetic_skin_hi.png",
        "assets/floydaddons/textures/gui/figma/mob_esp_editor_hi.png",
        "assets/floydaddons/textures/gui/figma/neck_hider_hi.png",
        "assets/floydaddons/textures/gui/figma/qol_hi.png",
        "assets/floydaddons/textures/gui/figma/render_hiders_hi.png",
        "assets/floydaddons/textures/gui/figma/render_xray_hi.png",
        "assets/floydaddons/textures/gui/figma/xray_editor_hi.png",
        "assets/floydaddons/textures/gui/floyd_screen.png",
        "assets/floydaddons/textures/gui/floyd_screen_v2.png",
        "assets/floydaddons/textures/gui/floyd_user.png",
        "assets/floydaddons/textures/gui/style_icon.png",
    )

    private val retiredFloydMetadataResources = setOf(
        "fabric.mod.json",
        "floydaddons.mixins.json",
    )

    private fun sourceFiles(directory: Path): List<Path> =
        Files.walk(directory).use { stream ->
            stream
                .filter { it.isRegularFile() }
                .filter { path -> path.name.endsWith(".kt") || path.name.endsWith(".java") }
                .toList()
        }
}
