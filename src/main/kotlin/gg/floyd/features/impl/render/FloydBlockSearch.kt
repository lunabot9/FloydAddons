package gg.floyd.features.impl.render

import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.ColorSetting
import gg.floyd.clickgui.settings.impl.ExtendedSearchableListSetting
import gg.floyd.clickgui.settings.impl.MapSetting
import gg.floyd.clickgui.settings.impl.SelectorSetting
import gg.floyd.events.RenderEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.utils.Colors
import gg.floyd.utils.Identifiers
import gg.floyd.utils.modMessage
import gg.floyd.utils.perf.FloydPerf
import gg.floyd.utils.perf.FloydPerfCounters
import gg.floyd.utils.render.BlockIconCache
import gg.floyd.utils.render.drawStyledBoxBatch
import gg.floyd.utils.render.drawTracerFan
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Locale

/**
 * Pure selection math for [FloydBlockSearch], split out for JVM-only unit tests
 * (the repo pattern: see FloydXrayAlpha / FloydLocalControlSettings).
 */
internal object FloydBlockSearchSelection {

    /**
     * Partial selection: after return, dist/pos[0, k) hold the k smallest distances (unordered).
     * Iterative Hoare quickselect with median-of-three pivots — the fill order is chunk-major
     * (long near-sorted runs), which degrades first-element pivots toward O(n²).
     */
    fun quickselectK(dist: LongArray, pos: LongArray, len: Int, k: Int) {
        if (k >= len) return
        var lo = 0
        var hi = len - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (dist[mid] < dist[lo]) swapAt(dist, pos, mid, lo)
            if (dist[hi] < dist[lo]) swapAt(dist, pos, hi, lo)
            if (dist[hi] < dist[mid]) swapAt(dist, pos, hi, mid)
            val pivot = dist[mid]
            var i = lo - 1
            var j = hi + 1
            while (true) {
                do i++ while (dist[i] < pivot)
                do j-- while (dist[j] > pivot)
                if (i >= j) break
                swapAt(dist, pos, i, j)
            }
            // Hoare invariant: [lo..j] <= pivot <= [j+1..hi].
            if (k <= j) hi = j else lo = j + 1
        }
    }

    private fun swapAt(dist: LongArray, pos: LongArray, a: Int, b: Int) {
        val d = dist[a]; dist[a] = dist[b]; dist[b] = d
        val p = pos[a]; pos[a] = pos[b]; pos[b] = p
    }
}

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
    private val tracers by BooleanSetting("Tracers", false, desc = "Draws a tracer line from your crosshair to each matched block (like Mob/Player ESP tracers).")

    private val blockSearchList by ExtendedSearchableListSetting(
        "Block List",
        optionsProvider = { allBlockIds },
        selectedProvider = { activeIds() },
        onToggle = { id ->
            if (id in activeIds()) searchBlocks.remove(id) else searchBlocks[id] = true
            reindexAll()
            ModuleManager.saveConfigurations()
        },
        desc = "Search all blocks; click to toggle highlighting.",
        displayNameProvider = Identifiers::friendlyName,
        iconProvider = BlockIconCache::get,
        showActionsRow = true,
        onClearAll = {
            searchBlocks.clear()
            reindexAll()
            ModuleManager.saveConfigurations()
        },
    )

    private val searchBlocks by MapSetting("Search Blocks", mutableMapOf<String, Boolean>()).hide()

    private val allBlockIds by lazy { BuiltInRegistries.BLOCK.keySet().map { it.toString() }.filterNot { it in BlockIconCache.invisibleBlockIds }.sorted() }

    // chunkKey (ChunkPos.asLong) -> matching block positions inside that chunk.
    private val matchedByChunk = HashMap<Long, MutableSet<BlockPos>>()

    // Running total of indexed matches across every chunk in [matchedByChunk]. Used to enforce
    // [MAX_INDEXED] without rescanning. Rare blocks never approach the cap, so their reach is unaffected.
    private var indexedCount = 0
    // True once [MAX_INDEXED] is hit and new positions are being dropped from the index.
    private var capped = false
    private var lastCapWarningMs = 0L

    // ---- Selection cache ----------------------------------------------------------------------
    // The drawn selection (pre-inflated AABBs) is rebuilt ONLY when the index actually mutates
    // (indexEpoch, bumped exclusively on real add/remove — no-op removeChunk calls from chunk
    // churn must NOT bump it or the cache thrashes while travelling) or, when the selection is
    // distance-capped at MAX_RENDERED, when the player has moved >= 2 blocks since the last
    // rebuild. Baseline measured the old per-frame re-sort at 5.2ms + 4.07MB allocated PER FRAME
    // (32k indexed diamond_ore); the cache amortizes that to a handful of rebuilds per second
    // worst-case. Each rebuild produces a FRESH list: a queued BoxBatchData may outlive a frame
    // when the flush skips, so a previously queued list is never mutated in place.
    private var indexEpoch = 0L
    private var cachedEpoch = -1L
    private var cachedSelection: ObjectArrayList<AABB> = ObjectArrayList()
    // Block centers parallel to [cachedSelection], for the tracer fan (built per rebuild, not per frame).
    private var cachedCenters: ObjectArrayList<Vec3> = ObjectArrayList()
    private var cachedSelectionCapped = false
    private var lastSelEyeX = 0
    private var lastSelEyeY = 0
    private var lastSelEyeZ = 0
    // ClientLevel identity of the indexed content — a dimension switch swaps mc.level without a
    // per-chunk CHUNK_UNLOAD, which used to leave ghost highlights at old-dimension coordinates.
    private var indexedLevel: Any? = null
    private const val RESELECT_DIST_SQ = 4L // 2 blocks of eye movement re-sorts a capped selection

    // Rebuild scratch (distances + packed BlockPos), retained between rebuilds, dropped by
    // clearIndex so a one-off common-block search doesn't pin ~1.6MB for the session.
    private var scratchDist = LongArray(0)
    private var scratchPos = LongArray(0)

    private fun bumpEpoch() {
        indexEpoch++
    }

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
            val level = mc.level ?: run { clearIndex(); return@on }
            // Dimension switch / reconnect: the index belongs to another level — drop it. New-level
            // chunks re-index via CHUNK_LOAD (which also updates indexedLevel, usually before the
            // first frame renders, so this guard is the late-arrival fallback).
            if (indexedLevel !== level) {
                clearIndex()
                indexedLevel = level
                return@on
            }
            if (matchedByChunk.isEmpty()) return@on
            val player = mc.player ?: return@on
            val eye = player.blockPosition()

            // Rebuild the cached selection only on real index mutation, or on >=2 blocks of eye
            // movement while distance-capped (under the cap the selection is the whole index and
            // is eye-independent).
            val moved = cachedSelectionCapped && run {
                val dx = (eye.x - lastSelEyeX).toLong()
                val dy = (eye.y - lastSelEyeY).toLong()
                val dz = (eye.z - lastSelEyeZ).toLong()
                dx * dx + dy * dy + dz * dz >= RESELECT_DIST_SQ
            }
            if (cachedEpoch != indexEpoch || moved) {
                FloydPerf.section("BlockSearch.reselect") { rebuildSelection(eye) }
            }
            if (cachedSelection.isEmpty) return@on

            val drawStyle = when (boxStyle) {
                1 -> 0 // Filled
                2 -> 2 // Both
                else -> 1 // Outline (wireframe)
            }
            // depth = false renders the highlight through occlusion (no depth test), like X-Ray's
            // through-walls pass. Color/style are read here per frame, so chroma keeps animating
            // and style switches apply instantly even while the cached AABBs are reused.
            drawStyledBoxBatch(cachedSelection, color, drawStyle, depth = false)
            if (tracers) drawTracerFan(cachedCenters, color, thickness = 2f, depth = false)

            // Warn once per interval that we are capped, so the user knows the highlight is partial.
            if (capped) {
                val now = System.currentTimeMillis()
                if (now - lastCapWarningMs >= CAP_WARNING_INTERVAL_MS) {
                    lastCapWarningMs = now
                    modMessage("Block Search: too many matches — showing nearest ${cachedSelection.size}")
                }
            }
        }

        // Incremental index: scan a chunk once when it loads, drop it when it unloads. Block edits are
        // patched in O(1) by BlockSearchChunkMixin -> handleClientBlockChange.
        ClientChunkEvents.CHUNK_LOAD.register { level, chunk ->
            if (indexedLevel !== level) {
                clearIndex()
                indexedLevel = level
            }
            if (enabled && activeIds().isNotEmpty()) indexChunk(chunk)
        }
        ClientChunkEvents.CHUNK_UNLOAD.register { _, chunk -> removeChunk(ChunkPos.asLong(chunk.pos.x, chunk.pos.z)) }

        // Fabric-level (fires regardless of module enabled state): the level-identity guard holds a
        // strong ClientLevel reference — without this, a disconnect leaves the entire dead level
        // (chunks, entity sections) reachable from indexedLevel until the next world's first
        // CHUNK_LOAD. Deliberately NOT nulled in clearIndex: reindexAll() clears then re-indexes
        // the SAME level, and a nulled identity there would make the next frame wipe the rebuild.
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            clearIndex()
            indexedLevel = null
        }
    }

    /**
     * Rebuilds [cachedSelection] (fresh list — never mutates a possibly-still-queued one). Over
     * the cap: nearest [MAX_RENDERED] via tandem quickselect on primitive arrays — no comparator,
     * no boxing, distances in integer math. Selection order is irrelevant (same-color translucent
     * lines blend commutatively).
     */
    private fun rebuildSelection(eye: BlockPos) {
        FloydPerfCounters.blockSearchReselects.increment()
        cachedEpoch = indexEpoch
        lastSelEyeX = eye.x
        lastSelEyeY = eye.y
        lastSelEyeZ = eye.z
        val total = indexedCount
        val out = ObjectArrayList<AABB>(minOf(total, MAX_RENDERED))
        val centers = ObjectArrayList<Vec3>(minOf(total, MAX_RENDERED))
        if (total <= MAX_RENDERED) {
            cachedSelectionCapped = false
            for (set in matchedByChunk.values) for (pos in set) {
                out.add(inflatedBox(pos.x, pos.y, pos.z))
                centers.add(Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5))
            }
        } else {
            cachedSelectionCapped = true
            if (scratchDist.size < total) {
                scratchDist = LongArray(total + total / 4)
                scratchPos = LongArray(total + total / 4)
            }
            val ex = eye.x; val ey = eye.y; val ez = eye.z
            var n = 0
            for (set in matchedByChunk.values) for (pos in set) {
                val dx = (pos.x - ex).toLong()
                val dy = (pos.y - ey).toLong()
                val dz = (pos.z - ez).toLong()
                scratchDist[n] = dx * dx + dy * dy + dz * dz
                scratchPos[n] = pos.asLong()
                n++
            }
            FloydBlockSearchSelection.quickselectK(scratchDist, scratchPos, n, MAX_RENDERED)
            for (i in 0 until MAX_RENDERED) {
                val packed = scratchPos[i]
                val x = BlockPos.getX(packed)
                val y = BlockPos.getY(packed)
                val z = BlockPos.getZ(packed)
                out.add(inflatedBox(x, y, z))
                centers.add(Vec3(x + 0.5, y + 0.5, z + 0.5))
            }
        }
        cachedSelection = out
        cachedCenters = centers
    }

    private fun inflatedBox(x: Int, y: Int, z: Int): AABB {
        val xd = x.toDouble()
        val yd = y.toDouble()
        val zd = z.toDouble()
        return AABB(
            xd - HIGHLIGHT_INFLATE, yd - HIGHLIGHT_INFLATE, zd - HIGHLIGHT_INFLATE,
            xd + 1.0 + HIGHLIGHT_INFLATE, yd + 1.0 + HIGHLIGHT_INFLATE, zd + 1.0 + HIGHLIGHT_INFLATE
        )
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
    private fun indexChunk(chunk: LevelChunk): Unit = FloydPerf.section("BlockSearch.indexChunk") {
        FloydPerfCounters.blockSearchChunkScans.increment()
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
        FloydPerfCounters.blockSearchBlockChanges.increment()
        val ids = activeIds()
        if (ids.isEmpty()) return
        val key = ChunkPos.asLong(pos)
        val id = BuiltInRegistries.BLOCK.getKey(state.block).toString()
        if (!state.isAir && id in ids) {
            if (indexedCount >= MAX_INDEXED) { capped = true; return }
            if (matchedByChunk.getOrPut(key) { LinkedHashSet() }.add(pos.immutable())) {
                indexedCount++
                bumpEpoch()
            }
        } else {
            val set = matchedByChunk[key] ?: return
            if (set.remove(pos)) {
                indexedCount--
                bumpEpoch()
            }
            if (set.isEmpty()) matchedByChunk.remove(key)
        }
    }

    /** Stores a chunk's matched positions, keeping [indexedCount] in sync (replaces any old entry). */
    private fun putChunk(key: Long, found: MutableSet<BlockPos>) {
        indexedCount += found.size - (matchedByChunk.put(key, found)?.size ?: 0)
        bumpEpoch()
    }

    /**
     * Drops a chunk's matched positions, keeping [indexedCount] in sync. Bumps the selection epoch
     * ONLY on a real removal — this is called for EVERY unloading chunk and every loaded chunk
     * with zero matches, and an unconditional bump would invalidate the cache continuously while
     * the player travels.
     */
    private fun removeChunk(key: Long) {
        val removed = matchedByChunk.remove(key) ?: return
        indexedCount -= removed.size
        bumpEpoch()
    }

    /** Clears the entire index, the cached selection, and the cap bookkeeping. */
    private fun clearIndex() {
        if (matchedByChunk.isNotEmpty()) bumpEpoch()
        matchedByChunk.clear()
        indexedCount = 0
        capped = false
        cachedEpoch = -1L
        cachedSelection = ObjectArrayList()
        cachedCenters = ObjectArrayList()
        cachedSelectionCapped = false
        scratchDist = LongArray(0)
        scratchPos = LongArray(0)
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
