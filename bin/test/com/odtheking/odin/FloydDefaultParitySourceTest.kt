package com.odtheking.odin

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class FloydDefaultParitySourceTest {
    private val root = Path.of("").toAbsolutePath()

    @Test
    fun `render and mob esp defaults match vendored Floyd config`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/config/RenderConfig.java")
        val render = source("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydRender.kt")
        val xray = source("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydXray.kt")
        val floydXrayIndigo = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/mixin/XrayIndigoMixin.java")
        val floydXraySodium = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/mixin/XraySodiumAlphaMixin.java")
        val hud = source("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydHud.kt")
        val mobEsp = source("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydMobEsp.kt")

        assertContains(floyd, "private static boolean customTimeEnabled = false;")
        assertContains(render, "BooleanSetting(\"Time Changer\", false")
        assertContains(floyd, "private static float customTimeValue = 50f;")
        assertContains(render, "NumberSetting(\"Time\", 50f")
        assertContains(floyd, "Math.round((customTimeValue / 100f) * 23999L)")
        assertContains(render, "Math.round((value.coerceIn(0f, 100f) / 100f) * 23999L)")
        assertContains(floyd, "private static boolean customScoreboardEnabled = false;")
        assertContains(render, "BooleanSetting(\"Custom Scoreboard\", false")
        assertContains(floyd, "private static boolean borderlessWindowed = false;")
        assertContains(render, "BooleanSetting(\"Borderless Window\", false")
        assertContains(floyd, "private static String windowTitle = \"\";")
        assertContains(render, "StringSetting(\"Instance Title\", \"\"")
        assertContains(floyd, "private static float xrayOpacity = 0.3f;")
        assertContains(xray, "NumberSetting(\"Opacity\", 0.3f")
        assertContains(floydXrayIndigo, "(int) (RenderConfig.getXrayOpacity() * 255)")
        assertContains(floydXraySodium, "(int) (RenderConfig.getXrayOpacity() * 255)")
        assertContains(xray, "(opacity * 255).toInt()")
        assertContains(floyd, "private static float inventoryHudScale = 1.1f;")
        assertContains(hud, "NumberSetting(\"Inventory HUD Scale\", 1.1f")

        assertContains(floyd, "private static volatile boolean mobEspEnabled = false;")
        assertContains(mobEsp, "object FloydMobEsp : Module(")
        assertContains(floyd, "private static boolean mobEspTracers = false;")
        assertContains(mobEsp, "BooleanSetting(\"Tracers\", false")
        assertContains(floyd, "private static boolean mobEspHitboxes = false;")
        assertContains(mobEsp, "BooleanSetting(\"Hitboxes\", false")
        assertContains(floyd, "private static boolean mobEspStarMobs = false;")
        assertContains(mobEsp, "BooleanSetting(\"Star Mobs\", false")
        assertContains(floyd, "private static boolean defaultEspChromaEnabled = false;")
        assertContains(mobEsp, "BooleanSetting(\"Default Chroma\", false")
        assertContains(mobEsp, "ColorSetting(\"Default ESP Color\"")
        assertContains(floyd, "private static boolean stalkTracerChromaEnabled = false;")
        assertContains(mobEsp, "BooleanSetting(\"Stalk Chroma\", false")
        assertContains(mobEsp, "ColorSetting(\"Tracer Color\"")
    }

    @Test
    fun `player camera animation hider and cosmetic defaults match vendored Floyd config`() {
        val skinConfig = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/config/SkinConfig.java")
        val nickConfig = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/config/NickHiderConfig.java")
        val cameraConfig = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/config/CameraConfig.java")
        val animationConfig = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/config/AnimationConfig.java")
        val hidersConfig = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/config/HidersConfig.java")
        val renderConfig = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/config/RenderConfig.java")

        val skin = source("src/main/kotlin/com/odtheking/odin/features/impl/cosmetic/FloydSkin.kt")
        val nick = source("src/main/kotlin/com/odtheking/odin/features/impl/player/FloydNickHider.kt")
        val camera = source("src/main/kotlin/com/odtheking/odin/features/impl/camera/FloydCamera.kt")
        val animations = source("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydAnimations.kt")
        val hiders = source("src/main/kotlin/com/odtheking/odin/features/impl/hiders/FloydHiders.kt")
        val cape = source("src/main/kotlin/com/odtheking/odin/features/impl/cosmetic/FloydCape.kt")
        val cone = source("src/main/kotlin/com/odtheking/odin/features/impl/cosmetic/FloydConeHat.kt")
        val playerSize = source("src/main/kotlin/com/odtheking/odin/features/impl/player/FloydPlayerSize.kt")

        assertContains(nickConfig, "private static String nickname = \"George Floyd\";")
        assertContains(nick, "StringSetting(\"Default Nick\", \"George Floyd\"")
        assertContains(nickConfig, "private static boolean enabled = false;")
        assertContains(nick, "fun hasReplacements(): Boolean =")
        assertContains(renderConfig, "private static boolean profileIdHiderEnabled = true;")
        assertContains(hiders, "BooleanSetting(\"Profile ID Hider\", true")
        assertContains(hiders, "BooleanSetting(\"Server ID Hider\", false")

        assertContains(skinConfig, "private static boolean customEnabled = false;")
        assertContains(skin, "fun shouldUseCustomSkin(id: Int): Boolean {")
        assertContains(skin, "if (!enabled) return false")
        assertContains(skinConfig, "private static boolean selfEnabled = false;")
        assertContains(skin, "BooleanSetting(\"Self\", false")
        assertContains(skinConfig, "private static boolean othersEnabled = false;")
        assertContains(skin, "BooleanSetting(\"Others\", false")
        assertContains(skinConfig, "private static String selectedSkin = \"george-floyd.png\";")
        assertContains(skin, "StringSetting(\"Skin\", \"george-floyd.png\"")
        assertContains(skinConfig, "private static float playerScaleX = 1.0f;")
        assertContains(playerSize, "NumberSetting(\"X\", 1.0f")

        assertContains(cameraConfig, "private static float freecamSpeed = 1.0f;")
        assertContains(camera, "NumberSetting(\"Speed\", 1.0f")
        assertContains(cameraConfig, "private static float freelookDistance = 4.0f;")
        assertContains(camera, "NumberSetting(\"Distance\", 4.0f")
        assertContains(cameraConfig, "private static boolean f5ScrollEnabled = false;")
        assertContains(camera, "BooleanSetting(\"Scrolling Changes Distance\", false")
        assertContains(cameraConfig, "private static boolean f5NoClip = false;")
        assertContains(camera, "BooleanSetting(\"No Third-Person Clipping\", false")

        assertContains(animationConfig, "private static boolean enabled;")
        assertContains(animations, "toggled = false")
        assertContains(animationConfig, "private static float scale = 1.0f;")
        assertContains(animations, "NumberSetting(\"Scale\", 1.0f")
        assertContains(animationConfig, "private static int swingDuration = 6;")
        assertContains(animations, "NumberSetting(\"Swing Duration\", 6")
        assertContains(animationConfig, "private static boolean classicClick;")
        assertContains(animations, "BooleanSetting(\"Classic Click\", false")
        assertContains(animations, "NumberSetting(\"Pos X\", 0")
        assertContains(animations, "NumberSetting(\"Rot X\", 0")
        assertContains(animations, "BooleanSetting(\"Hide Hand\", false")

        assertContains(hidersConfig, "private static boolean removeTabPing;")
        assertContains(hiders, "BooleanSetting(\"3rd Person Crosshair\", false")
        assertContains(hiders, "BooleanSetting(\"No Explosion Particles\", false")
        assertContains(hiders, "BooleanSetting(\"Remove Tab Ping\", false")
        assertContains(hidersConfig, "private static String noArmorMode = \"OFF\";")
        assertContains(hiders, "SelectorSetting(\"Target\", \"Off\"")

        assertContains(cape, "fun isActive(): Boolean = enabled")
        assertContains(renderConfig, "private static String selectedCapeImage = \"\";")
        assertContains(cape, "StringSetting(\"Image\", \"\"")
        assertContains(cone, "fun isActive(): Boolean = enabled")
        assertContains(renderConfig, "private static String selectedConeImage = \"\";")
        assertContains(cone, "StringSetting(\"Image\", \"\"")
        assertContains(renderConfig, "private static float coneHatHeight = 0.45f;")
        assertContains(cone, "NumberSetting(\"Height\", 0.45f")
        assertContains(cone, "NumberSetting(\"Spin Speed\", 0.0f")
    }

    @Test
    fun `skin and cone default asset extraction follows Floyd manager lifecycle`() {
        val skinManager = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/skin/SkinManager.java")
        val coneManager = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/features/cosmetic/ConeHatManager.java")
        val skin = source("src/main/kotlin/com/odtheking/odin/features/impl/cosmetic/FloydSkin.kt")
        val cone = source("src/main/kotlin/com/odtheking/odin/features/impl/cosmetic/FloydConeHat.kt")

        assertContains(skinManager, "private static boolean defaultExtracted = false;")
        assertContains(skinManager, "if (defaultExtracted) return;")
        assertContains(skinManager, "defaultExtracted = true;")
        assertContains(skinManager, "extractDefaultSkin(mc);")
        assertContains(skin, "private var defaultExtracted = false")
        assertContains(skin, "if (defaultExtracted) return")
        assertContains(skin, "defaultExtracted = true")
        assertContains(skin, "on<TickEvent.ClientEnd>")
        assertContains(skin, "extractDefaultSkin()")

        assertContains(coneManager, "private static boolean defaultExtracted = false;")
        assertContains(coneManager, "if (defaultExtracted) return;")
        assertContains(coneManager, "defaultExtracted = true;")
        assertContains(coneManager, "extractDefault(mc);")
        assertContains(cone, "private var defaultExtracted = false")
        assertContains(cone, "if (defaultExtracted) return")
        assertContains(cone, "defaultExtracted = true")
        assertContains(cone, "extractDefault()")
    }

    @Test
    fun `cape texture filtering preserves Floyd high resolution cape smoothing`() {
        val renderCompat = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/util/RenderCompat.java")
        val capeManager = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/features/cosmetic/CapeManager.java")
        val cape = source("src/main/kotlin/com/odtheking/odin/features/impl/cosmetic/FloydCape.kt")

        assertContains(renderCompat, "enableLinearFiltering(NativeImageBackedTexture texture)")
        assertContains(capeManager, "RenderCompat.enableLinearFiltering(tex);")
        assertContains(capeManager, "Use linear filtering + mipmaps so higher-res capes stay smooth")
        assertContains(cape, "private class LinearCapeTexture")
        assertContains(cape, "sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)")
        assertContains(cape, "LinearCapeTexture({ \"floydaddons_cape\" }, resolvedImage)")
        assertContains(cape, "LinearCapeTexture({ \"floydaddons_cape_gif\" }, working)")
    }

    @Test
    fun `cosmetic render layers preserve Floyd local-player-only gate`() {
        val floydCapeLayer = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/features/cosmetic/CapeFeatureRenderer.java")
        val floydConeLayer = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/features/cosmetic/ConeFeatureRenderer.java")
        val cape = source("src/main/kotlin/com/odtheking/odin/features/impl/cosmetic/FloydCape.kt")
        val cone = source("src/main/kotlin/com/odtheking/odin/features/impl/cosmetic/FloydConeHat.kt")
        val activeCapeLayer = source("src/main/java/com/odtheking/odin/features/impl/cosmetic/FloydCapeLayer.java")
        val activeConeLayer = source("src/main/java/com/odtheking/odin/features/impl/cosmetic/FloydConeHatLayer.java")

        assertContains(floydCapeLayer, "if (mc.player == null || state.id != mc.player.getId()) return;")
        assertContains(floydConeLayer, "if (mc.player == null || state.id != mc.player.getId()) return;")
        assertContains(cape, "fun isActiveFor(id: Int): Boolean = isActive() && FloydAddonsMod.mc.player?.id == id")
        assertContains(cone, "fun isActiveFor(id: Int): Boolean = isActive() && FloydAddonsMod.mc.player?.id == id")
        assertContains(activeCapeLayer, "if (!FloydCape.isActiveFor(state.id) || state.isInvisible) return;")
        assertContains(activeConeLayer, "if (!FloydConeHat.isActiveFor(state.id) || state.isInvisible) return;")
    }

    private fun source(path: String): String = Files.readString(root.resolve(path)).replace("\r\n", "\n")

    private fun assertContains(source: String, expected: String) {
        assertTrue(source.contains(expected), "Expected source to contain: $expected")
    }
}
