package gg.floyd.features.impl.render

import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.clickgui.settings.impl.ColorSetting
import gg.floyd.clickgui.settings.impl.MapSetting
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.clickgui.settings.impl.SearchableListSetting
import gg.floyd.clickgui.settings.impl.SelectorSetting
import gg.floyd.clickgui.settings.impl.StringSetting
import gg.floyd.events.RenderEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.utils.Colors
import gg.floyd.utils.modMessage
import gg.floyd.utils.render.drawStyledBox
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.AABB
import java.util.Locale

/**
 * Highlights every nearby block whose id is in the search list with a (chroma) outline.
 * Driven by the GUI editor or the `/fa blocksearch <block>` command.
 */
object FloydBlockSearch : Module(
    name = "Block Search",
    category = Category.RENDER,
    description = "Outlines all nearby blocks matching the searched block IDs.",
) {
    private val color by ColorSetting("Color", Colors.ACCENT.copy().also { it.chroma = true }, desc = "Outline color (toggle chroma inside the picker).")
    private val boxStyle by SelectorSetting("Box Style", "Outline", listOf("Outline", "Filled", "Both"), desc = "How matched blocks are highlighted.")
    private val radius by NumberSetting("Radius", 16, 4, 24, 1, desc = "Scan radius around you, in blocks.")

    private var editorBlock by StringSetting("Block", "minecraft:diamond_ore", 96, desc = "Block ID edited by the buttons below.")
    private val addBlockAction by ActionSetting("Add Block", desc = "Adds the block ID above to the search list.") {
        val id = runCatching { validBlockId(editorBlock) }.getOrElse { modMessage(it.message ?: "Invalid block ID."); return@ActionSetting }
        addSearchBlock(id)
        ModuleManager.saveConfigurations()
        modMessage("Searching for block: $id")
    }
    private val removeBlockAction by ActionSetting("Remove Block", desc = "Removes the block ID above from the search list.") {
        val id = runCatching { validBlockId(editorBlock) }.getOrElse { modMessage(it.message ?: "Invalid block ID."); return@ActionSetting }
        val removed = searchBlocks.remove(id) != null
        forceRescan()
        ModuleManager.saveConfigurations()
        modMessage(if (removed) "Removed block search: $id" else "Block not in search list: $id")
    }
    private val listBlocksAction by ActionSetting("List Blocks", desc = "Lists the searched block IDs in chat.") {
        modMessage(blockListSummary())
    }
    private val clearBlocksAction by ActionSetting("Clear Blocks", desc = "Clears all searched block IDs.") {
        searchBlocks.clear()
        forceRescan()
        ModuleManager.saveConfigurations()
        modMessage("Cleared block search list.")
    }

    private val blockList by SearchableListSetting(
        "All Blocks",
        optionsProvider = { listOptions() },
        selectedProvider = { activeIds() },
        onToggle = { id ->
            if (id in activeIds()) searchBlocks.remove(id) else searchBlocks[id] = true
            forceRescan()
            ModuleManager.saveConfigurations()
        },
        desc = "Search nearby + all blocks; click to toggle highlighting."
    )

    private val searchBlocks by MapSetting("Search Blocks", mutableMapOf<String, Boolean>()).hide()

    private val allBlockIds by lazy { BuiltInRegistries.BLOCK.keySet().map { it.toString() }.sorted() }
    private val nearbyIds = linkedSetOf<String>()
    private val matched = ArrayList<BlockPos>()
    private var scanCooldown = 0
    private var lastScanCenter: BlockPos? = null

    // Pull the highlight faces a hair off the block surface so they never sit exactly coplanar
    // with the chunk geometry (which otherwise causes the highlight to half-clip / vanish).
    private const val HIGHLIGHT_INFLATE = 0.002

    // The GUI list (nearby + full registry) cached with a short TTL so the dropdown does not rebuild a ~1000-entry list every frame.
    private var cachedOptions: List<String> = emptyList()
    private var cachedOptionsMs = 0L
    private fun listOptions(): List<String> {
        val now = System.currentTimeMillis()
        if (cachedOptions.isEmpty() || now - cachedOptionsMs > 500L) {
            cachedOptions = (nearbyIds + allBlockIds).distinct()
            cachedOptionsMs = now
        }
        return cachedOptions
    }

    init {
        on<RenderEvent.Extract> {
            if (!enabled) return@on
            val level = mc.level ?: return@on
            val player = mc.player ?: return@on
            if (++scanCooldown >= 20) {
                scanCooldown = 0
                val center = player.blockPosition()
                if (center != lastScanCenter) {
                    lastScanCenter = center
                    rescan(level, center)
                }
            }
            if (matched.isEmpty()) return@on
            val drawStyle = when (boxStyle) {
                1 -> 0 // Filled
                2 -> 2 // Both
                else -> 1 // Outline (wireframe)
            }
            // depth = false renders the highlight through occlusion (no depth test), like X-Ray's
            // through-walls pass; the tiny inflate keeps every face off the exact block surface so the
            // highlight never z-fights / half-clips with the chunk geometry it is sitting on.
            for (pos in matched) drawStyledBox(AABB(pos).inflate(HIGHLIGHT_INFLATE), color, drawStyle, depth = false)
        }
    }

    override fun onEnable() {
        super.onEnable()
        // Disable section occlusion culling (see XrayOcclusionMixin) so a section the player can see
        // through never gets fully culled and drops the highlights of matching blocks behind it.
        FloydXray.rebuildChunks()
    }

    override fun onDisable() {
        FloydXray.rebuildChunks()
        super.onDisable()
    }

    /** True while highlights are being drawn; consumed by XrayOcclusionMixin to bypass occlusion culling. */
    @JvmStatic
    fun isActive(): Boolean = enabled

    private fun rescan(level: ClientLevel, center: BlockPos) {
        matched.clear()
        nearbyIds.clear()
        val ids = activeIds()
        val r = radius
        for (x in -r..r) for (y in -r..r) for (z in -r..r) {
            val pos = BlockPos(center.x + x, center.y + y, center.z + z)
            val state = level.getBlockState(pos)
            if (state.isAir) continue
            val id = BuiltInRegistries.BLOCK.getKey(state.block).toString()
            nearbyIds.add(id)
            if (id in ids) matched.add(pos)
        }
    }

    private fun forceRescan() {
        scanCooldown = 20
        lastScanCenter = null
        matched.clear()
    }

    private fun activeIds(): Set<String> = searchBlocks.filterValues { it }.keys

    /** Normalizes and validates a block id like "minecraft:diamond_ore". */
    fun validBlockId(id: String): String =
        Identifier.tryParse(id.trim().lowercase(Locale.ROOT))?.toString()
            ?: throw IllegalArgumentException("Invalid block ID: $id")

    fun addSearchBlock(id: String) {
        searchBlocks[id] = true
        forceRescan()
    }

    /** Entry point for the `/fa blocksearch <block>` command. */
    fun searchForBlock(rawId: String) {
        val id = validBlockId(rawId)
        addSearchBlock(id)
        if (!enabled) toggle()
        ModuleManager.saveConfigurations()
        modMessage("Searching for block: $id")
    }

    fun searchedBlockIds(): List<String> = activeIds().sorted()

    private fun blockListSummary(): String {
        val ids = searchedBlockIds()
        return if (ids.isEmpty()) "Block search list is empty." else "Block search (${ids.size}): ${ids.joinToString(", ")}"
    }
}
