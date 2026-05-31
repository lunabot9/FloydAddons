package gg.floyd.features.impl.render

import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.clickgui.settings.impl.ColorSetting
import gg.floyd.clickgui.settings.impl.MapSetting
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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.phys.AABB
import java.util.Locale

/**
 * Highlights every nearby block whose id is in the search list with a (chroma) outline.
 * Driven by the GUI editor or the `/fa blocksearch <block>` command.
 *
 * Matches are kept in a per-chunk index that is built incrementally rather than by scanning a
 * cube around the player every tick: each chunk is scanned ONCE when it loads (air-only sections
 * skipped), dropped when it unloads, and patched in O(1) on every block edit (see
 * [handleClientBlockChange], driven by `BlockSearchChunkMixin`). The search therefore reaches as
 * far as the world is loaded — i.e. the render distance — with no scan radius and no per-frame cost.
 */
object FloydBlockSearch : Module(
    name = "Block Search",
    category = Category.RENDER,
    description = "Outlines all loaded blocks matching the searched block IDs (reaches your full render distance).",
) {
    private val color by ColorSetting("Color", Colors.ACCENT.copy().also { it.chroma = true }, desc = "Outline color (toggle chroma inside the picker).")
    private val boxStyle by SelectorSetting("Box Style", "Outline", listOf("Outline", "Filled", "Both"), desc = "How matched blocks are highlighted.")

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
        reindexAll()
        ModuleManager.saveConfigurations()
        modMessage(if (removed) "Removed block search: $id" else "Block not in search list: $id")
    }
    private val listBlocksAction by ActionSetting("List Blocks", desc = "Lists the searched block IDs in chat.") {
        modMessage(blockListSummary())
    }
    private val clearBlocksAction by ActionSetting("Clear Blocks", desc = "Clears all searched block IDs.") {
        searchBlocks.clear()
        reindexAll()
        ModuleManager.saveConfigurations()
        modMessage("Cleared block search list.")
    }

    private val blockList by SearchableListSetting(
        "All Blocks",
        optionsProvider = { allBlockIds },
        selectedProvider = { activeIds() },
        onToggle = { id ->
            if (id in activeIds()) searchBlocks.remove(id) else searchBlocks[id] = true
            reindexAll()
            ModuleManager.saveConfigurations()
        },
        desc = "Search all blocks; click to toggle highlighting."
    )

    private val searchBlocks by MapSetting("Search Blocks", mutableMapOf<String, Boolean>()).hide()

    private val allBlockIds by lazy { BuiltInRegistries.BLOCK.keySet().map { it.toString() }.sorted() }

    // chunkKey (ChunkPos.asLong) -> matching block positions inside that chunk.
    private val matchedByChunk = HashMap<Long, MutableSet<BlockPos>>()

    // Running total of indexed matches across every chunk in [matchedByChunk]. Used to enforce
    // [MAX_INDEXED] without rescanning. Rare blocks never approach the cap, so their reach is unaffected.
    private var indexedCount = 0
    // True once [MAX_INDEXED] is hit and new positions are being dropped from the index.
    private var capped = false
    private var lastCapWarningMs = 0L

    // Pull the highlight faces a hair off the block surface so they never sit exactly coplanar
    // with the chunk geometry (which otherwise causes the highlight to half-clip / vanish).
    private const val HIGHLIGHT_INFLATE = 0.002

    // Hard cap on total indexed matches. Searching a common block (e.g. stone) across the whole
    // render distance would otherwise index millions of positions and exhaust memory. Once reached we
    // stop adding new positions. Far above what any ore/spawner/chest search produces, so rare blocks
    // are never affected.
    private const val MAX_INDEXED = 100_000
    // Hard cap on matches actually drawn per frame, nearest-first. drawStyledBox feeds a shared
    // BufferBuilder that overflows past ~16.7M vertices (24/box); 10k boxes is well under that ceiling.
    private const val MAX_RENDERED = 10_000
    private const val CAP_WARNING_INTERVAL_MS = 5_000L

    init {
        on<RenderEvent.Extract> {
            if (!enabled) return@on
            if (mc.level == null) { clearIndex(); return@on }
            if (matchedByChunk.isEmpty()) return@on
            val drawStyle = when (boxStyle) {
                1 -> 0 // Filled
                2 -> 2 // Both
                else -> 1 // Outline (wireframe)
            }
            val player = mc.player ?: return@on
            val eye = player.blockPosition()
            // The whole index draws when it is within the per-frame cap; only when it exceeds the cap do
            // we collect, sort by squared distance to the player, and draw the nearest MAX_RENDERED. This
            // keeps the vertex buffer from overflowing while leaving normal/rare searches untouched.
            val nearest: Collection<BlockPos> = if (indexedCount <= MAX_RENDERED) {
                matchedByChunk.values.flatten()
            } else {
                matchedByChunk.values.asSequence().flatten()
                    .sortedBy { it.distSqr(eye) }
                    .take(MAX_RENDERED)
                    .toList()
            }
            // depth = false renders the highlight through occlusion (no depth test), like X-Ray's
            // through-walls pass; the tiny inflate keeps every face off the exact block surface so the
            // highlight never z-fights / half-clips with the chunk geometry it is sitting on.
            for (pos in nearest) drawStyledBox(AABB(pos).inflate(HIGHLIGHT_INFLATE), color, drawStyle, depth = false)

            // Warn once per interval that we are capped, so the user knows the highlight is partial.
            if (capped) {
                val now = System.currentTimeMillis()
                if (now - lastCapWarningMs >= CAP_WARNING_INTERVAL_MS) {
                    lastCapWarningMs = now
                    modMessage("Block Search: too many matches — showing nearest ${nearest.size}")
                }
            }
        }

        // Incremental index: scan a chunk once when it loads, drop it when it unloads. Block edits are
        // patched in O(1) by BlockSearchChunkMixin -> handleClientBlockChange.
        ClientChunkEvents.CHUNK_LOAD.register { _, chunk -> if (enabled && activeIds().isNotEmpty()) indexChunk(chunk) }
        ClientChunkEvents.CHUNK_UNLOAD.register { _, chunk -> removeChunk(ChunkPos.asLong(chunk.pos.x, chunk.pos.z)) }
    }

    override fun onEnable() {
        super.onEnable()
        // Disable section occlusion culling (see XrayOcclusionMixin) so a section the player can see
        // through never gets fully culled and drops the highlights of matching blocks behind it.
        FloydXray.rebuildChunks()
        reindexAll()
    }

    override fun onDisable() {
        clearIndex()
        FloydXray.rebuildChunks()
        super.onDisable()
    }

    /** True while highlights are being drawn; consumed by XrayOcclusionMixin to bypass occlusion culling. */
    @JvmStatic
    fun isActive(): Boolean = enabled

    /** Scans a single chunk's non-air sections once and stores its matching positions. */
    private fun indexChunk(chunk: LevelChunk) {
        val ids = activeIds()
        val key = ChunkPos.asLong(chunk.pos.x, chunk.pos.z)
        if (ids.isEmpty()) { removeChunk(key); return }

        val sections = chunk.sections
        val minSectionY = chunk.minSectionY
        val baseX = chunk.pos.minBlockX
        val baseZ = chunk.pos.minBlockZ
        val found = LinkedHashSet<BlockPos>()
        sections@ for (i in sections.indices) {
            val section = sections[i]
            if (section.hasOnlyAir()) continue
            val baseY = SectionPos.sectionToBlockCoord(minSectionY + i)
            for (y in 0..15) for (x in 0..15) for (z in 0..15) {
                val state = section.getBlockState(x, y, z)
                if (state.isAir) continue
                val id = BuiltInRegistries.BLOCK.getKey(state.block).toString()
                if (id in ids) {
                    // Stop adding once the global cap is reached. Re-counting subtracts this chunk's old
                    // size first (below), so a chunk that already had a smaller entry can still grow.
                    if (indexedCount - (matchedByChunk[key]?.size ?: 0) + found.size >= MAX_INDEXED) {
                        capped = true
                        break@sections
                    }
                    found.add(BlockPos(baseX + x, baseY + y, baseZ + z))
                }
            }
        }
        if (found.isEmpty()) removeChunk(key) else putChunk(key, found)
    }

    /** Re-scans every currently-loaded chunk. Called when the active id set changes or on enable. */
    private fun reindexAll() {
        clearIndex()
        if (!enabled || activeIds().isEmpty()) return
        val level = mc.level ?: return
        val player = mc.player ?: return
        val cache = level.chunkSource
        val center = player.blockPosition()
        val pcx = SectionPos.blockToSectionCoord(center.x)
        val pcz = SectionPos.blockToSectionCoord(center.z)
        val r = mc.options.effectiveRenderDistance
        for (cx in pcx - r..pcx + r) for (cz in pcz - r..pcz + r) {
            val chunk = cache.getChunk(cx, cz, ChunkStatus.FULL, false) ?: continue
            indexChunk(chunk)
        }
    }

    /** O(1) index patch for a single client-side block change (from BlockSearchChunkMixin). */
    @JvmStatic
    fun handleClientBlockChange(pos: BlockPos, state: BlockState) {
        if (!enabled) return
        val ids = activeIds()
        if (ids.isEmpty()) return
        val key = ChunkPos.asLong(pos)
        val id = BuiltInRegistries.BLOCK.getKey(state.block).toString()
        if (!state.isAir && id in ids) {
            if (indexedCount >= MAX_INDEXED) { capped = true; return }
            if (matchedByChunk.getOrPut(key) { LinkedHashSet() }.add(pos.immutable())) indexedCount++
        } else {
            val set = matchedByChunk[key] ?: return
            if (set.remove(pos)) indexedCount--
            if (set.isEmpty()) matchedByChunk.remove(key)
        }
    }

    /** Stores a chunk's matched positions, keeping [indexedCount] in sync (replaces any old entry). */
    private fun putChunk(key: Long, found: MutableSet<BlockPos>) {
        indexedCount += found.size - (matchedByChunk.put(key, found)?.size ?: 0)
    }

    /** Drops a chunk's matched positions, keeping [indexedCount] in sync. */
    private fun removeChunk(key: Long) {
        indexedCount -= matchedByChunk.remove(key)?.size ?: 0
    }

    /** Clears the entire index and resets the cap bookkeeping. */
    private fun clearIndex() {
        matchedByChunk.clear()
        indexedCount = 0
        capped = false
    }

    private fun activeIds(): Set<String> = searchBlocks.filterValues { it }.keys

    /** Normalizes and validates a block id like "minecraft:diamond_ore". */
    fun validBlockId(id: String): String =
        Identifier.tryParse(id.trim().lowercase(Locale.ROOT))?.toString()
            ?: throw IllegalArgumentException("Invalid block ID: $id")

    fun addSearchBlock(id: String) {
        searchBlocks[id] = true
        reindexAll()
    }

    /** Entry point for the `/fa blocksearch <block>` command. */
    fun searchForBlock(rawId: String) {
        val id = validBlockId(rawId)
        searchBlocks[id] = true
        if (!enabled) toggle() else reindexAll()
        ModuleManager.saveConfigurations()
        modMessage("Searching for block: $id")
    }

    fun searchedBlockIds(): List<String> = activeIds().sorted()

    private fun blockListSummary(): String {
        val ids = searchedBlockIds()
        return if (ids.isEmpty()) "Block search list is empty." else "Block search (${ids.size}): ${ids.joinToString(", ")}"
    }
}
