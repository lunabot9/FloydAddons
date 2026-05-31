package gg.floyd.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import gg.floyd.FloydAddonsMod
import gg.floyd.clickgui.Panel
import gg.floyd.clickgui.settings.Saving
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.features.impl.render.ClickGUIModule
import gg.floyd.features.impl.player.FloydNickHider
import gg.floyd.features.impl.render.FloydMobEsp
import gg.floyd.features.impl.render.FloydXray
import gg.floyd.utils.Color
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object FloydSidecarConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var activePaths: SidecarPaths? = null

    private val stringMapType = object : TypeToken<Map<String, String>>() {}.type
    private val stringListType = object : TypeToken<List<String>>() {}.type
    private val mobEspEntriesType = object : TypeToken<List<Map<String, String>>>() {}.type
    private var preserveFreshNameTemplate = false
    private var preserveFreshMobEspTemplate = false

    internal fun withConfigDirForTest(configDir: Path, block: () -> Unit) {
        val previousPaths = activePaths
        val previousNameTemplate = preserveFreshNameTemplate
        val previousMobEspTemplate = preserveFreshMobEspTemplate
        activePaths = SidecarPaths(configDir)
        preserveFreshNameTemplate = false
        preserveFreshMobEspTemplate = false
        try {
            block()
        } finally {
            activePaths = previousPaths
            preserveFreshNameTemplate = previousNameTemplate
            preserveFreshMobEspTemplate = previousMobEspTemplate
        }
    }

    private fun paths(): SidecarPaths =
        activePaths ?: SidecarPaths(FloydAddonsMod.configFile.toPath()).also { activePaths = it }

    fun loadExistingSidecars() {
        loadLegacyMainConfigIfFresh()
        loadNameMappings()
        loadXrayOpaqueBlocks()
        loadMobEspEntries(clearWhenMissing = false)
    }

    fun reloadMobEspEntries() {
        loadMobEspEntries(clearWhenMissing = true)
        FloydMobEsp.reloadRuntimeCaches()
    }

    fun state(): Map<String, Any?> {
        val paths = paths()
        return mapOf(
            "configDir" to paths.configDir.toString(),
            "preserveFreshNameTemplate" to preserveFreshNameTemplate,
            "preserveFreshMobEspTemplate" to preserveFreshMobEspTemplate,
            "legacyMain" to fileState(paths.legacyMainPath),
            "floydMain" to fileState(paths.floydMainPath),
            "nameMappings" to fileState(paths.namesPath),
            "xrayOpaque" to fileState(paths.xrayOpaquePath),
            "mobEsp" to fileState(paths.mobEspPath)
        )
    }

    fun saveSidecars() {
        Files.createDirectories(paths().configDir)
        val nameMappings = FloydNickHider.nameMappings
        if (preserveFreshNameTemplate && nameMappings.isEmpty() && Files.isRegularFile(paths().namesPath)) {
            preserveFreshNameTemplate = false
        } else {
            Files.writeString(paths().namesPath, gson.toJson(nameMappings), StandardCharsets.UTF_8)
        }
        Files.writeString(paths().xrayOpaquePath, gson.toJson(FloydXray.opaqueBlockIds().toList()), StandardCharsets.UTF_8)
        val mobEspEntries = FloydMobEsp.sidecarEntries()
        if (preserveFreshMobEspTemplate && mobEspEntries.isEmpty() && Files.isRegularFile(paths().mobEspPath)) {
            preserveFreshMobEspTemplate = false
        } else {
            Files.writeString(paths().mobEspPath, gson.toJson(mobEspEntries), StandardCharsets.UTF_8)
        }
    }

    private fun loadNameMappings() {
        if (!Files.isRegularFile(paths().namesPath)) {
            Files.createDirectories(paths().configDir)
            Files.writeString(paths().namesPath, gson.toJson(nameMappingsTemplate()), StandardCharsets.UTF_8)
            preserveFreshNameTemplate = true
            FloydNickHider.clearNameMappings()
            return
        }
        FloydNickHider.clearNameMappings()
        val mappings = readJson<Map<String, String>>(paths().namesPath, stringMapType) ?: return
        for ((realName, fakeName) in mappings) {
            FloydNickHider.addNameMapping(realName, fakeName)
        }
    }

    private fun loadXrayOpaqueBlocks() {
        if (!Files.isRegularFile(paths().xrayOpaquePath)) {
            Files.createDirectories(paths().configDir)
            Files.writeString(paths().xrayOpaquePath, gson.toJson(FloydXray.defaultOpaqueBlockIds().toList()), StandardCharsets.UTF_8)
            return
        }
        val blocks = readJson<List<String>>(paths().xrayOpaquePath, stringListType) ?: return
        FloydXray.loadSidecarOpaqueBlocks(blocks)
    }

    private fun loadMobEspEntries(clearWhenMissing: Boolean) {
        if (!Files.isRegularFile(paths().mobEspPath)) {
            Files.createDirectories(paths().configDir)
            Files.writeString(paths().mobEspPath, gson.toJson(mobEspTemplate()), StandardCharsets.UTF_8)
            preserveFreshMobEspTemplate = true
            if (clearWhenMissing) FloydMobEsp.loadSidecarEntries(emptyList())
            return
        }
        val entries = readJson<List<Map<String, String>>>(paths().mobEspPath, mobEspEntriesType) ?: emptyList()
        FloydMobEsp.loadSidecarEntries(entries)
    }

    private fun nameMappingsTemplate(): Map<String, String> =
        linkedMapOf("ExampleIGN" to "NewDisplayName")

    private fun mobEspTemplate(): List<Map<String, String>> = listOf(
        mapOf("name" to "Vanquisher"),
        mapOf("mob" to "minecraft:ghast")
    )

    private fun loadLegacyMainConfigIfFresh() {
        if (!Files.isRegularFile(paths().legacyMainPath)) return
        if (Files.isRegularFile(paths().floydMainPath) && Files.size(paths().floydMainPath) > 0L) return
        val data = readJson<JsonObject>(paths().legacyMainPath, JsonObject::class.java) ?: return

        setModuleEnabled("Animations", data.bool("animEnabled"))
        setModuleEnabled("Mob ESP", data.bool("mobEspEnabled"))

        set("Nick Hider", "Enabled", data.primitive("nickHiderEnabled"))
        set("Nick Hider", "Default Nick", data.nonEmptyStringPrimitive("nickname"))
        set("Hiders", "Server ID Hider", data.primitive("serverIdHiderEnabled"))
        set("Hiders", "Profile ID Hider", data.boolOrDefault("profileIdHiderEnabled", true))

        set("Custom Skin", "Custom Skin", data.boolOrDefault("skinCustomEnabled", true))
        set("Custom Skin", "Self", data.primitive("skinSelfEnabled"))
        set("Custom Skin", "Others", data.primitive("skinOthersEnabled"))
        set("Custom Skin", "Skin", data.primitive("selectedSkin"))
        val legacyScale = data.floatValue("playerScale")?.takeIf { it != 1.0f && it > 0.0f }
        val scaleX = data.primitive("playerScaleX")
        val scaleY = data.primitive("playerScaleY")
        val scaleZ = data.primitive("playerScaleZ")
        val useLegacyUniformScale = legacyScale != null &&
            listOf(scaleX, scaleY, scaleZ).all { it == null || it.asFloat == 1.0f }
        set("Player Size", "X", if (useLegacyUniformScale) JsonPrimitive(legacyScale) else scaleX)
        set("Player Size", "Y", if (useLegacyUniformScale) JsonPrimitive(legacyScale) else scaleY)
        set("Player Size", "Z", if (useLegacyUniformScale) JsonPrimitive(legacyScale) else scaleZ)
        set("Player Size", "Target", data.string("playerSizeTarget")?.let(::playerSizeTarget)?.let(::JsonPrimitive))

        setModuleEnabled("Time Changer", data.bool("customTimeEnabled"))
        set("Time Changer", "Time", data.primitive("customTimeValue"))
        setModuleEnabled("Custom Scoreboard", data.bool("customScoreboardEnabled"))
        set("General", "Borderless Window", data.primitive("borderlessWindowed"))
        set("General", "Instance Title", data.trimmedStringPrimitive("windowTitle"))

        set("X-Ray", "Opacity", data.positivePrimitive("xrayOpacity"))

        set("Hiders", "No Hurt Camera", data.primitive("hiderNoHurtCamera"))
        set("Hiders", "Remove Fire Overlay", data.primitive("hiderRemoveFireOverlay"))
        set("Hiders", "Disable Hunger Bar", data.primitive("hiderDisableHungerBar"))
        set("Hiders", "Hide Potion Effects", data.primitive("hiderHidePotionEffects"))
        set("Hiders", "3rd Person Crosshair", data.primitive("hiderThirdPersonCrosshair"))
        set("Hiders", "Hide Entity Fire", data.primitive("hiderHideEntityFire"))
        set("Hiders", "Disable Arrows", data.primitive("hiderDisableAttachedArrows"))
        set("Hiders", "Remove Falling Blocks", data.primitive("hiderRemoveFallingBlocks"))
        set("Hiders", "No Explosion Particles", data.primitive("hiderRemoveExplosionParticles"))
        set("Hiders", "Remove Tab Ping", data.primitive("hiderRemoveTabPing"))
        set("Hiders", "Target", data.string("hiderNoArmorMode")?.let(::noArmorMode)?.let(::JsonPrimitive))

        set("Camera", "Speed", data.positivePrimitive("freecamSpeed"))
        set("Camera", "Distance", data.positivePrimitive("freelookDistance"))
        set("Camera", "Disable Front Cam", data.primitive("f5DisableFront"))
        set("Camera", "Disable Back Cam", data.primitive("f5DisableBack"))
        set("Camera", "No Third-Person Clipping", data.primitive("f5NoClip"))
        set("Camera", "Scrolling Changes Distance", data.primitive("f5ScrollEnabled"))
        set("Camera", "Reset F5 Scrolling", data.primitive("f5ResetOnToggle"))
        set("Camera", "Camera Distance", data.positivePrimitive("f5CameraDistance"))

        set("Animations", "Pos X", data.primitive("animPosX"))
        set("Animations", "Pos Y", data.primitive("animPosY"))
        set("Animations", "Pos Z", data.primitive("animPosZ"))
        set("Animations", "Rot X", data.primitive("animRotX"))
        set("Animations", "Rot Y", data.primitive("animRotY"))
        set("Animations", "Rot Z", data.primitive("animRotZ"))
        set("Animations", "Scale", data.positivePrimitive("animScale"))
        set("Animations", "Swing Duration", data.positivePrimitive("animSwingDuration"))
        set("Animations", "Cancel Re-Equip", data.primitive("animCancelReEquip"))
        set("Animations", "Hide Hand", data.primitive("animHidePlayerHand"))
        set("Animations", "Classic Click", data.primitive("animClassicClick"))

        set("Custom Cape", "Enabled", data.primitive("capeEnabled"))
        set("Custom Cape", "Image", data.primitive("selectedCapeImage"))
        set("Cone Hat", "Enabled", data.primitive("floydHatEnabled"))
        set("Cone Hat", "Image", data.primitive("selectedConeImage"))
        set("Cone Hat", "Height", data.positivePrimitive("coneHatHeight"))
        set("Cone Hat", "Radius", data.positivePrimitive("coneHatRadius"))
        set("Cone Hat", "Y Offset", data.nonZeroPrimitive("coneHatYOffset"))
        set("Cone Hat", "Rotation", data.floatValue("coneHatRotation")?.let(::coneHatRotation)?.let(::JsonPrimitive))
        set("Cone Hat", "Spin Speed", data.primitive("coneHatRotationSpeed"))

        set("HUD", "Inventory HUD Scale", data.numberOrDefault("inventoryHudScale", 0.0f))
        set("General", "Panel Corner Radius", data.primitive("hudCornerRadius"))
        set("HUD", "Inventory HUD", hudSetting(data, "inventoryHudX", "inventoryHudY", "inventoryHudScale", "inventoryHudEnabled", 0, 0, 0.5f))
        set("HUD", "Scoreboard HUD", hudSetting(data, "customScoreboardX", "customScoreboardY", null, "customScoreboardEnabled", 0, 0, 1f))

        set("Mob ESP", "Tracers", data.boolOrDefault("mobEspTracers", true))
        set("Mob ESP", "Hitboxes", data.boolOrDefault("mobEspHitboxes", true))
        set("Mob ESP", "Star Mobs", data.boolOrDefault("mobEspStarMobs", true))
        set("Mob ESP", "Default ESP Color", data.argbColor("defaultEspColor", data.bool("defaultEspChromaEnabled") ?: true))
        set("Mob ESP", "Tracer Color", data.argbColor("stalkTracerColor", data.bool("stalkTracerChromaEnabled") ?: true))

        importLegacyClickGuiPanels(data)
    }

    private fun setModuleEnabled(moduleName: String, enabled: Boolean?) {
        if (enabled == null) return
        val module = ModuleManager.modules[moduleName.lowercase()] ?: return
        if (module.enabled != enabled) module.toggle()
    }

    private fun set(moduleName: String, settingName: String, value: JsonElement?) {
        if (value == null || value.isJsonNull) return
        val module = ModuleManager.modules[moduleName.lowercase()] ?: return
        module.set(settingName, value)
    }

    private fun Module.set(settingName: String, value: JsonElement) {
        runCatching {
            (settings[settingName] as? Saving)?.read(value, gson)
        }.onFailure { throwable ->
            FloydAddonsMod.logger.warn("Failed to import legacy Floyd setting {}.{}", name, settingName, throwable)
        }
    }

    private fun noArmorMode(value: String): String? = when (value.trim().uppercase()) {
        "OFF" -> "Off"
        "SELF" -> "Self"
        "OTHERS" -> "Others"
        "ALL" -> "All"
        else -> null
    }

    private fun coneHatRotation(value: Float): Float =
        ((value % 360f) + 360f) % 360f

    private fun playerSizeTarget(value: String): String? = when (value) {
        "Self", "Real Players", "All" -> value
        else -> null
    }

    private fun hudSetting(
        data: JsonObject,
        xKey: String,
        yKey: String,
        scaleKey: String?,
        enabledKey: String,
        defaultX: Int,
        defaultY: Int,
        defaultScale: Float,
    ): JsonObject = JsonObject().apply {
        addProperty("x", data.int(xKey) ?: defaultX)
        addProperty("y", data.int(yKey) ?: defaultY)
        addProperty("scale", scaleKey?.let { data.floatValue(it) } ?: defaultScale)
        addProperty("enabled", data.bool(enabledKey) ?: false)
    }

    private fun importLegacyClickGuiPanels(data: JsonObject) {
        val panels = data.get("clickGuiPanels")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        seedMissingPanelDefaults()
        val legacyNames = mapOf(
            "RENDER" to Category.RENDER.name,
            "HIDERS" to Category.HIDERS.name,
            "PLAYER" to Category.PLAYER.name,
            "CAMERA" to Category.CAMERA.name,
        )
        for ((legacyName, categoryName) in legacyNames) {
            val values = panels.get(legacyName)?.takeIf { it.isJsonArray }?.asJsonArray ?: continue
            if (values.size() < 3) continue
            val setting = ClickGUIModule.panelSetting.getOrPut(categoryName) { ClickGUIModule.PanelData() }
            setting.x = values[0].asFloat
            setting.y = values[1].asFloat
            setting.extended = values[2].asInt == 0
        }
    }

    private fun seedMissingPanelDefaults() {
        val activeCategories = Category.categories.values.toList()
        val gap = 20f
        activeCategories.forEachIndexed { index, category ->
            ClickGUIModule.panelSetting.getOrPut(category.name) {
                ClickGUIModule.PanelData(
                    x = 10f + (Panel.WIDTH + gap) * index,
                    y = 10f,
                    extended = true,
                )
            }
        }
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? =
        get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asJsonPrimitive

    private fun JsonObject.bool(key: String): Boolean? =
        primitive(key)?.takeIf { it.isBoolean }?.asBoolean

    private fun JsonObject.boolOrDefault(key: String, default: Boolean): JsonPrimitive =
        JsonPrimitive(bool(key) ?: default)

    private fun JsonObject.string(key: String): String? =
        primitive(key)?.takeIf { it.isString }?.asString

    private fun JsonObject.nonEmptyStringPrimitive(key: String): JsonPrimitive? =
        primitive(key)?.takeIf { it.isString && it.asString.isNotEmpty() }

    private fun JsonObject.trimmedStringPrimitive(key: String): JsonPrimitive? =
        primitive(key)?.takeIf { it.isString }?.asString?.trim()?.let(::JsonPrimitive)

    private fun JsonObject.int(key: String): Int? =
        primitive(key)?.takeIf { it.isNumber }?.asInt

    private fun JsonObject.floatValue(key: String): Float? =
        primitive(key)?.takeIf { it.isNumber }?.asFloat

    private fun JsonObject.numberOrDefault(key: String, default: Number): JsonPrimitive =
        primitive(key)?.takeIf { it.isNumber } ?: JsonPrimitive(default)

    private fun JsonObject.positivePrimitive(key: String): JsonPrimitive? =
        primitive(key)?.takeIf { it.isNumber && it.asFloat > 0.0f }

    private fun JsonObject.nonZeroPrimitive(key: String): JsonPrimitive? =
        primitive(key)?.takeIf { it.isNumber && it.asFloat != 0.0f }

    private fun JsonObject.argbColor(key: String, chroma: Boolean = false): JsonElement? {
        val argb = int(key) ?: return null
        val alpha = argb ushr 24 and 0xFF
        val red = argb ushr 16 and 0xFF
        val green = argb ushr 8 and 0xFF
        val blue = argb and 0xFF
        return gson.toJsonTree(Color(red, green, blue, alpha / 255f).also { it.chroma = chroma }, Color::class.java)
    }

    private fun <T> readJson(path: Path, type: java.lang.reflect.Type): T? =
        runCatching {
            Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
                gson.fromJson<T>(reader, type)
            }
        }.getOrNull()

    private fun fileState(path: Path): Map<String, Any?> = mapOf(
        "path" to path.toString(),
        "exists" to Files.isRegularFile(path),
        "sizeBytes" to if (Files.isRegularFile(path)) Files.size(path) else null
    )

    private data class SidecarPaths(val configDir: Path) {
        val namesPath: Path = configDir.resolve("name-mappings.json")
        val xrayOpaquePath: Path = configDir.resolve("xray-opaque.json")
        val mobEspPath: Path = configDir.resolve("mob-esp.json")
        val legacyMainPath: Path = configDir.resolve("config.json")
        val floydMainPath: Path = configDir.resolve("floydaddons-config.json")
    }
}
