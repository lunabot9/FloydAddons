package com.odtheking.odin

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyConfigImportParitySourceTest {
    private val root = Path.of("").toAbsolutePath()

    @Test
    fun `legacy Floyd config data fields are imported or intentionally retired with old Floyd GUI`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/FloydAddonsConfig.java")
        val active = source("src/main/kotlin/com/odtheking/odin/config/FloydSidecarConfig.kt")

        val dataClass = Regex("private static class Data \\{(?<body>[\\s\\S]*?)\\n    \\}")
            .find(floyd)
            ?.groups
            ?.get("body")
            ?.value
            ?: error("Could not find vendored FloydAddonsConfig.Data body")
        val persistedFields = Regex("\\b(?:boolean|int|float|String|Map<String, int\\[]>)\\s+(\\w+)")
            .findAll(dataClass)
            .map { it.groupValues[1] }
            .toSortedSet()

        val retiredOldGuiFields = sortedSetOf(
            "buttonBorderChromaEnabled",
            "buttonBorderColor",
            "buttonBorderFadeColor",
            "buttonBorderFadeEnabled",
            "buttonTextChromaEnabled",
            "buttonTextColor",
            "buttonTextFadeColor",
            "buttonTextFadeEnabled",
            "guiBorderChromaEnabled",
            "guiBorderColor",
            "guiBorderFadeColor",
            "guiBorderFadeEnabled",
        )

        val unmapped = persistedFields
            .filterNot { field -> active.contains("\"$field\"") || active.contains(field) }
            .filterNot { it in retiredOldGuiFields }

        assertEquals(
            emptyList(),
            unmapped,
            "Every vendored Floyd persisted field must be imported into Odin or explicitly retired with the old Floyd GUI"
        )
    }

    @Test
    fun `legacy numeric config import preserves Floyd positive-value guards`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/FloydAddonsConfig.java")
        val active = source("src/main/kotlin/com/odtheking/odin/config/FloydSidecarConfig.kt")
        val renderConfig = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/config/RenderConfig.java")

        for (expected in listOf(
            "if (data.coneHatHeight > 0) RenderConfig.setConeHatHeight(data.coneHatHeight);",
            "if (data.coneHatRadius > 0) RenderConfig.setConeHatRadius(data.coneHatRadius);",
            "if (data.coneHatYOffset != 0) RenderConfig.setConeHatYOffset(data.coneHatYOffset);",
            "if (data.xrayOpacity > 0) RenderConfig.setXrayOpacity(data.xrayOpacity);",
            "if (data.animScale > 0) AnimationConfig.setScale(data.animScale);",
            "if (data.animSwingDuration > 0) AnimationConfig.setSwingDuration(data.animSwingDuration);",
            "if (data.freecamSpeed > 0) CameraConfig.setFreecamSpeed(data.freecamSpeed);",
            "if (data.freelookDistance > 0) CameraConfig.setFreelookDistance(data.freelookDistance);",
            "if (data.f5CameraDistance > 0) CameraConfig.setF5CameraDistance(data.f5CameraDistance);",
        )) {
            assertTrue(floyd.contains(expected), "Vendored Floyd loader no longer contains guard: $expected")
        }

        for (expected in listOf(
            "set(\"Cone Hat\", \"Height\", data.positivePrimitive(\"coneHatHeight\"))",
            "set(\"Cone Hat\", \"Radius\", data.positivePrimitive(\"coneHatRadius\"))",
            "set(\"Cone Hat\", \"Y Offset\", data.nonZeroPrimitive(\"coneHatYOffset\"))",
            "set(\"X-Ray\", \"Opacity\", data.positivePrimitive(\"xrayOpacity\"))",
            "set(\"Animations\", \"Scale\", data.positivePrimitive(\"animScale\"))",
            "set(\"Animations\", \"Swing Duration\", data.positivePrimitive(\"animSwingDuration\"))",
            "set(\"Camera\", \"Speed\", data.positivePrimitive(\"freecamSpeed\"))",
            "set(\"Camera\", \"Distance\", data.positivePrimitive(\"freelookDistance\"))",
            "set(\"Camera\", \"Camera Distance\", data.positivePrimitive(\"f5CameraDistance\"))",
        )) {
            assertTrue(active.contains(expected), "Active importer missing Floyd guard equivalent: $expected")
        }

        assertTrue(floyd.contains("RenderConfig.setConeHatRotation(data.coneHatRotation);"))
        assertTrue(renderConfig.contains("coneHatRotation = ((v % 360f) + 360f) % 360f;"))
        assertTrue(active.contains("set(\"Cone Hat\", \"Rotation\", data.floatValue(\"coneHatRotation\")?.let(::coneHatRotation)?.let(::JsonPrimitive))"))
        assertTrue(active.contains("private fun coneHatRotation(value: Float): Float =\n        ((value % 360f) + 360f) % 360f"))
    }

    @Test
    fun `legacy HUD numeric import preserves Floyd omitted-field defaults`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/FloydAddonsConfig.java")
        val active = source("src/main/kotlin/com/odtheking/odin/config/FloydSidecarConfig.kt")

        assertTrue(floyd.contains("RenderConfig.setInventoryHudX(data.inventoryHudX);"))
        assertTrue(floyd.contains("RenderConfig.setInventoryHudY(data.inventoryHudY);"))
        assertTrue(floyd.contains("RenderConfig.setInventoryHudScale(data.inventoryHudScale);"))
        assertTrue(floyd.contains("RenderConfig.setCustomScoreboardX(data.customScoreboardX);"))
        assertTrue(floyd.contains("RenderConfig.setCustomScoreboardY(data.customScoreboardY);"))
        assertTrue(floyd.contains("float inventoryHudScale;"))
        assertTrue(floyd.contains("int customScoreboardX;"))

        assertTrue(active.contains("set(\"HUD\", \"Inventory HUD Scale\", data.numberOrDefault(\"inventoryHudScale\", 0.0f))"))
        assertTrue(active.contains("hudSetting(data, \"inventoryHudX\", \"inventoryHudY\", \"inventoryHudScale\", \"inventoryHudEnabled\", 0, 0, 0.5f)"))
        assertTrue(active.contains("hudSetting(data, \"customScoreboardX\", \"customScoreboardY\", null, \"customScoreboardEnabled\", 0, 0, 1f)"))
    }

    @Test
    fun `legacy boolean import preserves Floyd omitted-field defaults`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/FloydAddonsConfig.java")
        val active = source("src/main/kotlin/com/odtheking/odin/config/FloydSidecarConfig.kt")

        for (expected in listOf(
            "boolean skinCustomEnabled = true;",
            "boolean profileIdHiderEnabled = true;",
            "boolean mobEspTracers = true;",
            "boolean mobEspHitboxes = true;",
            "boolean mobEspStarMobs = true;",
            "boolean defaultEspChromaEnabled = true;",
            "boolean stalkTracerChromaEnabled = true;",
        )) {
            assertTrue(floyd.contains(expected), "Vendored Floyd Data default changed: $expected")
        }

        for (expected in listOf(
            "set(\"Custom Skin\", \"Custom Skin\", data.boolOrDefault(\"skinCustomEnabled\", true))",
            "set(\"Hiders\", \"Profile ID Hider\", data.boolOrDefault(\"profileIdHiderEnabled\", true))",
            "set(\"Mob ESP\", \"Tracers\", data.boolOrDefault(\"mobEspTracers\", true))",
            "set(\"Mob ESP\", \"Hitboxes\", data.boolOrDefault(\"mobEspHitboxes\", true))",
            "set(\"Mob ESP\", \"Star Mobs\", data.boolOrDefault(\"mobEspStarMobs\", true))",
            "set(\"Mob ESP\", \"Default Chroma\", data.boolOrDefault(\"defaultEspChromaEnabled\", true))",
            "set(\"Mob ESP\", \"Stalk Chroma\", data.boolOrDefault(\"stalkTracerChromaEnabled\", true))",
        )) {
            assertTrue(active.contains(expected), "Active importer missing Floyd Data default equivalent: $expected")
        }
    }

    @Test
    fun `legacy nickname import preserves Floyd blank-string guard`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/FloydAddonsConfig.java")
        val active = source("src/main/kotlin/com/odtheking/odin/config/FloydSidecarConfig.kt")

        assertTrue(floyd.contains("if (data.nickname != null && !data.nickname.isEmpty()) NickHiderConfig.setNickname(data.nickname);"))
        assertTrue(active.contains("set(\"Neck Hider\", \"Default Nick\", data.nonEmptyStringPrimitive(\"nickname\"))"))
    }

    @Test
    fun `runtime nickname setter preserves Floyd empty-string guard`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/config/NickHiderConfig.java")
        val active = source("src/main/kotlin/com/odtheking/odin/features/impl/player/FloydNickHider.kt")

        assertTrue(floyd.contains("if (nick != null && !nick.isEmpty()) nickname = nick;"))
        assertTrue(active.contains("if (fakeName.isNotEmpty()) nickname = fakeName"))
    }

    @Test
    fun `runtime nick replacement gate preserves Floyd toggle-only behavior`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/util/NickTextUtil.java")
        val active = source("src/main/kotlin/com/odtheking/odin/features/impl/player/FloydNickHider.kt")
        val activeAccumulator = source("src/main/kotlin/com/odtheking/odin/features/impl/player/FloydServerIdAccumulator.kt")

        assertTrue(floyd.contains("if (!nickEnabled && !serverIdEnabled && !profileIdEnabled) return text;"))
        assertTrue(active.contains("fun hasReplacements(): Boolean =\n        enabled || FloydHiders.serverIdHider || FloydHiders.profileIdHider"))
        assertTrue(floyd.contains("return text.substring(0, idStart) + SERVER_ID_REPLACEMENT + text.substring(idEnd);"))
        assertTrue(activeAccumulator.contains("return text.substring(0, range.first) + replacement + text.substring(range.last + 1)"))
        assertTrue(floyd.contains("result = caseInsensitiveReplace(result, entry.getKey(), entry.getValue());"))
        assertTrue(active.contains("if (find.isNotEmpty())"))
    }

    @Test
    fun `legacy window title import preserves Floyd trim behavior`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/config/RenderConfig.java")
        val active = source("src/main/kotlin/com/odtheking/odin/config/FloydSidecarConfig.kt")

        assertTrue(floyd.contains("windowTitle = title != null ? title.trim() : \"\";"))
        assertTrue(active.contains("set(\"Render\", \"Instance Title\", data.trimmedStringPrimitive(\"windowTitle\"))"))
    }

    @Test
    fun `persisted Floyd strings are not dropped by Odin UI length limits`() {
        val renderConfig = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/config/RenderConfig.java")
        val nickConfig = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/config/NickHiderConfig.java")
        val skinConfig = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/config/SkinConfig.java")
        val stringSetting = source("src/main/kotlin/com/odtheking/odin/clickgui/settings/impl/StringSetting.kt")

        assertTrue(renderConfig.contains("public static void setWindowTitle(String title)"))
        assertTrue(renderConfig.contains("windowTitle = title != null ? title.trim() : \"\";"))
        assertTrue(nickConfig.contains("public static void setNickname(String nick) { if (nick != null && !nick.isEmpty()) nickname = nick; }"))
        assertTrue(skinConfig.contains("public static void setSelectedSkin(String name) { selectedSkin = name != null ? name : \"\"; }"))

        assertTrue(stringSetting.contains("if (it.length > length) length = it.length"))
        assertTrue(stringSetting.contains("value = it"))
    }

    @Test
    fun `legacy player size target import preserves Floyd exact selector validation`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/config/SkinConfig.java")
        val active = source("src/main/kotlin/com/odtheking/odin/config/FloydSidecarConfig.kt")

        assertTrue(floyd.contains("if (\"Self\".equals(v) || \"Real Players\".equals(v) || \"All\".equals(v)) playerSizeTarget = v;"))
        assertTrue(active.contains("set(\"Player Size\", \"Target\", data.string(\"playerSizeTarget\")?.let(::playerSizeTarget)?.let(::JsonPrimitive))"))
        assertTrue(active.contains("set(\"Player Size\", \"X\", if (useLegacyUniformScale) JsonPrimitive(legacyScale) else scaleX)"))
        assertTrue(active.contains("private fun playerSizeTarget(value: String): String? = when (value)"))
    }

    @Test
    fun `Mob ESP sidecar color import preserves Floyd invalid-color fallback`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/esp/MobEspManager.java")
        val active = source("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydMobEsp.kt")

        assertTrue(floyd.contains("if (s.length() != 6) return 0xFFFFFFFF;"))
        assertTrue(floyd.contains("} catch (NumberFormatException e) {\n            return 0xFFFFFFFF;"))
        assertTrue(active.contains("private fun sidecarEncodedColor(hexColor: String, chroma: Boolean): String ="))
        assertTrue(active.contains("parseHexColor(hexColor) ?: \"FFFFFF\""))
        assertTrue(floyd.contains("String s = hex.trim();"))
        assertTrue(active.contains("val hex = raw.trim().removePrefix(\"#\")"))
    }

    @Test
    fun `Mob ESP filter removal clears color metadata like Floyd raw entries`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/esp/MobEspManager.java")
        val active = source("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydMobEsp.kt")

        assertTrue(floyd.contains("rawEntries.removeIf(e ->"))
        assertTrue(floyd.contains("if (removed) reparse();"))
        assertTrue(active.contains("val removed = rawEntries.removeIf { entry ->"))
        assertTrue(active.contains("if (removed) reparseRawEntries()"))
    }

    @Test
    fun `Mob ESP name filters preserve Floyd raw entry casing`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/esp/MobEspManager.java")
        val active = source("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydMobEsp.kt")

        assertTrue(floyd.contains("rawEntries.add(new LinkedHashMap<>(Map.of(\"name\", name)));"))
        assertTrue(active.contains("rawEntries.add(linkedMapOf(\"name\" to name))"))
        assertTrue(active.contains("rawEntries.map { LinkedHashMap(it) }"))
        assertTrue(active.contains("nameFilters.filterValues { it }.keys.toSortedSet(String.CASE_INSENSITIVE_ORDER)"))
    }

    @Test
    fun `Mob ESP star mobs do not directly match arbitrary non armor stand stars`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/esp/MobEspManager.java")
        val active = source("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydMobEsp.kt")

        assertTrue(floyd.contains("if (entity instanceof PlayerEntity && RenderConfig.isMobEspStarMobs())"))
        assertTrue(floyd.contains("if (PLAYER_MOB_NAMES.contains(name)) return true;"))
        assertTrue(active.contains("if (entity is Player && starMobs && playerMobNames.contains"))
        assertFalse(active.contains("|| (starMobs && hasStar(entity.displayName.string))"))
    }

    @Test
    fun `Mob ESP star mob marker matches Floyd single glyph`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/esp/MobEspManager.java")
        val active = source("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydMobEsp.kt")

        assertTrue(floyd.contains("private static final String STAR_CHAR = \"\\u272F\";"))
        assertTrue(floyd.contains("if (starMobsEnabled && stripped.contains(STAR_CHAR)) matched = true;"))
        assertTrue(active.contains("private const val STAR_MOB_MARKER = \"✯\""))
        assertTrue(active.contains("private fun hasStar(text: String): Boolean = text.contains(STAR_MOB_MARKER)"))
        assertFalse(active.contains("text.contains(\"✪\")"))
    }

    @Test
    fun `Mob ESP real-player detection uses Floyd tab-list name cache`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/esp/MobEspManager.java")
        val active = source("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydMobEsp.kt")

        assertTrue(floyd.contains("private static volatile Set<String> tabListNames = Collections.emptySet();"))
        assertTrue(floyd.contains("private static final long TAB_LIST_CACHE_MS = 1_000L;"))
        assertTrue(floyd.contains("refreshTabListNames();"))
        assertTrue(floyd.contains("return tabListNames.contains(name);"))
        assertTrue(active.contains("@Volatile private var tabListNames: Set<String> = emptySet()"))
        assertTrue(active.contains("private const val TAB_LIST_CACHE_MS = 1_000L"))
        assertTrue(active.contains("refreshTabListNames()"))
        assertTrue(active.contains("return tabListNames.contains(name)"))
        assertFalse(active.contains("return mc.connection?.getPlayerInfo(name) != null"))
    }

    @Test
    fun `Mob ESP editor name filter input preserves Floyd GUI blank guard`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/gui/ClickGuiScreen.java")
        val active = source("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydMobEsp.kt")

        assertTrue(floyd.contains("String query = filterSearchQuery.trim();"))
        assertTrue(floyd.contains("if (query.isEmpty()) return;"))
        assertTrue(active.contains("fun validUserNameFilter(name: String): String"))
        assertTrue(active.contains("if (trimmed.isEmpty()) throw IllegalArgumentException(\"Name filter cannot be blank.\")"))
    }

    @Test
    fun `name mapping sidecar import preserves Floyd key casing and case-insensitive removal`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/config/NickHiderConfig.java")
        val active = source("src/main/kotlin/com/odtheking/odin/features/impl/player/FloydNickHider.kt")

        assertTrue(floyd.contains("mutable.put(ign, fakeName);"))
        assertTrue(floyd.contains("if (key.equalsIgnoreCase(ign)) {"))
        assertTrue(active.contains("nameMappings[realName] = fakeName"))
        assertTrue(active.contains("it.equals(realName, ignoreCase = true)"))
    }

    @Test
    fun `name mapping sidecar import preserves Floyd raw map values`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/FloydAddonsConfig.java")
        val active = source("src/main/kotlin/com/odtheking/odin/config/FloydSidecarConfig.kt")

        assertTrue(floyd.contains("NickHiderConfig.setNameMappingsRaw(loaded);"))
        assertTrue(active.contains("for ((realName, fakeName) in mappings) {\n            FloydNickHider.addNameMapping(realName, fakeName)\n        }"))
    }

    @Test
    fun `xray sidecar import creates Floyd default opaque list when missing`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/FloydAddonsConfig.java")
        val active = source("src/main/kotlin/com/odtheking/odin/config/FloydSidecarConfig.kt")

        assertTrue(floyd.contains("GSON.toJson(RenderConfig.defaultXrayOpaqueBlocks(), w);"))
        assertTrue(active.contains("gson.toJson(FloydXray.defaultOpaqueBlockIds().toList())"))
    }

    @Test
    fun `xray sidecar import preserves Floyd raw loaded set`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/FloydAddonsConfig.java")
        val floydRender = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/config/RenderConfig.java")
        val activeConfig = source("src/main/kotlin/com/odtheking/odin/config/FloydSidecarConfig.kt")
        val activeXray = source("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydXray.kt")

        assertTrue(floyd.contains("RenderConfig.setXrayOpaqueBlocks(Collections.unmodifiableSet(new LinkedHashSet<>(loaded)));"))
        assertTrue(floyd.contains("GSON.toJson(new java.util.ArrayList<>(RenderConfig.getXrayOpaqueBlocks()), w);"))
        assertTrue(floydRender.contains("Set<String> mutable = new LinkedHashSet<>(xrayOpaqueBlocks);"))
        assertTrue(activeConfig.contains("FloydXray.loadSidecarOpaqueBlocks(blocks)"))
        assertTrue(activeXray.contains("for (id in ids) opaqueBlocks[id] = true"))
        assertTrue(activeXray.contains("keys.toCollection(LinkedHashSet())"))
        assertTrue(activeXray.contains("fun defaultIds(): Set<String> = defaultBlocks().toCollection(LinkedHashSet())"))
    }

    @Test
    fun `xray editor block input preserves Floyd GUI trim guard`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/gui/ClickGuiScreen.java")
        val active = source("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydXray.kt")

        assertTrue(floyd.contains("String query = filterSearchQuery.trim();"))
        assertTrue(floyd.contains("if (query.isEmpty()) return;"))
        assertTrue(active.contains("val trimmed = id.trim()"))
        assertTrue(active.contains("if (trimmed.isEmpty()) throw IllegalArgumentException(\"Invalid block ID: \$id\")"))
    }

    private fun source(path: String): String = Files.readString(root.resolve(path)).replace("\r\n", "\n")
}
