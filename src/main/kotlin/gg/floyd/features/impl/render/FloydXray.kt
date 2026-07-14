package gg.floyd.features.impl.render

import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.ExtendedSearchableListSetting
import gg.floyd.clickgui.settings.impl.KeybindSetting
import gg.floyd.clickgui.settings.impl.MapSetting
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.events.TickEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.utils.Identifiers
import gg.floyd.utils.moduleToggle
import gg.floyd.utils.perf.FloydPerfCounters
import gg.floyd.utils.render.BlockIconCache
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.state.BlockState
import org.lwjgl.glfw.GLFW

internal object FloydXrayIds {
    fun validOpaqueBlockId(id: String): String {
        val trimmed = id.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("Invalid block ID: $id")
        val normalized = trimmed.lowercase()
        return Identifier.tryParse(normalized)?.toString()
            ?: throw IllegalArgumentException("Invalid block ID: $id")
    }
}

internal object FloydXrayAlpha {
    fun alpha(opacity: Float): Int = (opacity * 255).toInt().coerceIn(0, 255)
}

internal object FloydXrayOpaqueLists {
    fun activeIds(blocks: Map<String, Boolean>): Set<String> =
        blocks.filterValues { it }.keys.toCollection(LinkedHashSet())

    fun defaultIds(): Set<String> = defaultBlocks().toCollection(LinkedHashSet())

    fun summary(blocks: Collection<String>): String =
        "--- Xray Opaque Blocks (${blocks.size}) ---" +
            blocks.joinToString(separator = "\n", prefix = "\n").takeIf { blocks.isNotEmpty() }.orEmpty()

    fun defaultBlocks(): List<String> {
        val colors = listOf(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
        )
        return buildList {
            add("minecraft:glass")
            add("minecraft:tinted_glass")
            add("minecraft:glass_pane")
            colors.forEach {
                add("minecraft:${it}_stained_glass")
                add("minecraft:${it}_stained_glass_pane")
            }
        }
    }
}

object FloydXray : Module(
    name = "X-Ray",
    category = Category.RENDER,
    description = "Floyd x-ray opacity and opaque block filtering.",
    toggled = false,
) {
    private val renderingBlockState: ThreadLocal<BlockState?> = ThreadLocal()

    val opacity by NumberSetting("Opacity", 0.3f, 0.05f, 1f, 0.05f, desc = "Opacity for non-opaque x-ray blocks.")
    private val toggleKey by KeybindSetting("Toggle X-Ray", GLFW.GLFW_KEY_UNKNOWN, desc = "Floyd X-Ray toggle key.").onPress {
        val active = toggleXray()
        if (ClickGUIModule.enableNotification) moduleToggle(name, active)
    }
    private val opaqueBlockList by ExtendedSearchableListSetting(
        "Opaque Block List",
        optionsProvider = { allBlockIds },
        selectedProvider = { opaqueBlockIds() },
        onToggle = { id ->
            if (id in opaqueBlockIds()) removeOpaqueBlock(id) else addOpaqueBlock(id)
            ModuleManager.saveConfigurations()
        },
        desc = "Search all blocks; click to toggle whether x-ray treats the block as opaque.",
        displayNameProvider = Identifiers::friendlyName,
        iconProvider = BlockIconCache::get,
        showActionsRow = true,
        onClearAll = {
            clearOpaqueBlocks()
            ModuleManager.saveConfigurations()
        },
    )
    // Backing storage for persistence; kept hidden. Name unchanged so saved configs load as-is.
    private val opaqueBlocks by MapSetting("Opaque Blocks", defaultOpaqueBlocks().associateWith { true }.toMutableMap()).hide()

    private val allBlockIds by lazy { BuiltInRegistries.BLOCK.keySet().map { it.toString() }.filterNot { it in BlockIconCache.invisibleBlockIds }.sorted() }

    private var lastActive = false

    init {
        on<TickEvent.End> {
            val active = isActive()
            if (active != lastActive) rebuildChunks()
            lastActive = active
        }
    }

    override fun onEnable() {
        super.onEnable()
        rebuildChunks()
    }

    override fun onDisable() {
        rebuildChunks()
        super.onDisable()
    }

    @JvmStatic
    fun isActive(): Boolean = enabled

    @JvmStatic
    fun alpha(): Int = FloydXrayAlpha.alpha(opacity)

    @JvmStatic
    fun isOpaque(state: BlockState): Boolean {
        // Counter, not a nanoTime section: this runs per-BlockState on Sodium's section-compile
        // worker threads — millions of calls during a rebuild burst; timing each would distort.
        FloydPerfCounters.xrayIsOpaqueCalls.increment()
        val id = BuiltInRegistries.BLOCK.getKey(state.block).toString()
        return opaqueBlocks[id] == true
    }

    @JvmStatic
    fun beginBlockTessellation(state: BlockState) {
        renderingBlockState.set(state)
    }

    @JvmStatic
    fun endBlockTessellation() {
        renderingBlockState.remove()
    }

    @JvmStatic
    fun shouldForceTranslucentLayer(): Boolean {
        val state = renderingBlockState.get() ?: return false
        return isActive() && !isOpaque(state)
    }

    fun opaqueBlockIds(): Set<String> = FloydXrayOpaqueLists.activeIds(opaqueBlocks)

    fun defaultOpaqueBlockIds(): Set<String> = FloydXrayOpaqueLists.defaultIds()

    fun opaqueBlockListSummary(): String {
        val blocks = opaqueBlockIds()
        return FloydXrayOpaqueLists.summary(blocks)
    }

    fun addOpaqueBlockMessage(blockId: String, added: Boolean): String =
        if (added) "Added xray opaque block: $blockId" else "Block already in opaque list: $blockId"

    fun removeOpaqueBlockMessage(blockId: String, removed: Boolean): String =
        if (removed) "Removed xray opaque block: $blockId" else "Block not in opaque list: $blockId"

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "active" to isActive(),
        "opacity" to opacity,
        "alpha" to alpha(),
        "opaqueBlockCount" to opaqueBlockIds().size,
        "opaqueBlocks" to opaqueBlockIds().sorted(),
        "sodiumLoaded" to isSodiumLoaded()
    )

    fun addOpaqueBlock(id: String): Boolean {
        val normalized = id.lowercase()
        val added = opaqueBlocks[normalized] != true
        opaqueBlocks[normalized] = true
        rebuildChunks()
        return added
    }

    fun loadSidecarOpaqueBlocks(ids: List<String>) {
        opaqueBlocks.clear()
        for (id in ids) opaqueBlocks[id] = true
        rebuildChunks()
    }

    fun addUserOpaqueBlock(id: String): Boolean =
        addOpaqueBlock(validOpaqueBlockId(id))

    fun removeUserOpaqueBlock(id: String): Boolean =
        removeOpaqueBlock(validOpaqueBlockId(id))

    fun validOpaqueBlockId(id: String): String =
        FloydXrayIds.validOpaqueBlockId(id)

    fun removeOpaqueBlock(id: String): Boolean {
        val removed = opaqueBlocks.remove(id.lowercase()) != null
        if (removed) rebuildChunks()
        return removed
    }

    fun clearOpaqueBlocks() {
        opaqueBlocks.clear()
        rebuildChunks()
    }

    fun toggleXray(): Boolean {
        // The module's own on/off is now the single x-ray switch; onEnable/onDisable rebuild chunks.
        toggle()
        return isActive()
    }

    @JvmStatic
    fun rebuildChunks() {
        try {
            //? if >=26.2 {
            /*mc.levelRenderer.invalidateCompiledGeometry(mc.level!!, mc.options, mc.gameRenderer.mainCamera(), mc.blockColors)
            *///?} else {
            mc.levelRenderer.allChanged()
            //?}
        } catch (_: NullPointerException) {
            return
        }
        try {
            val renderer = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer")
            val instance = renderer.getMethod("instance").invoke(null)
            renderer.getMethod("scheduleRebuildForAllChunks", Boolean::class.javaPrimitiveType).invoke(instance, true)
        } catch (_: ReflectiveOperationException) {
        }
    }

    private fun isSodiumLoaded(): Boolean = try {
        Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer")
        true
    } catch (_: ClassNotFoundException) {
        false
    }

    private fun defaultOpaqueBlocks(): List<String> = FloydXrayOpaqueLists.defaultBlocks()
}
