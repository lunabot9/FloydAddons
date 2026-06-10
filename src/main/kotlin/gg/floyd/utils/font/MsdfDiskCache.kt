package gg.floyd.utils.font

import com.mojang.logging.LogUtils
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Comparator

/**
 * Per-glyph MSDF disk cache (design D12, normative). One directory per font keyed by
 * `sha256(fontBytes)` + [CACHE_VERSION] (binding version, edge-coloring angle, pxrange, scaling
 * mode, y-flip) + glyph cell px size, holding one validated binary entry per codepoint
 * (autoframe transform + encoded RGBA cell). Writes go through a temp file + ATOMIC_MOVE so two
 * concurrent clients can share the directory; entries are read-only after publish. Reads validate
 * header/length and treat anything corrupt or truncated as a miss (self-healing delete). Total
 * cache size is bounded by whole-font-directory LRU eviction ordered by directory mtime.
 *
 * Deliberately Minecraft-free so key derivation, round-trips, corruption handling and eviction are
 * unit testable with plain temp dirs.
 */
class MsdfDiskCache private constructor(
    private val directory: Path,
    private val cellSize: Int,
) {
    fun directory(): Path = directory

    /** Validated read; any missing/corrupt/truncated entry is a miss (corrupt files are deleted). */
    fun read(codepoint: Int): MsdfGlyphRaster? {
        val path = entryPath(codepoint)
        val bytes = try {
            if (!Files.isRegularFile(path)) return null
            Files.readAllBytes(path)
        } catch (e: IOException) {
            return null
        }
        val bitmapLength = cellSize * cellSize * 4
        if (bytes.size != HEADER_BYTES + bitmapLength) return miss(path)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if (buffer.int != MAGIC) return miss(path)
        if (buffer.int != FORMAT) return miss(path)
        if (buffer.int != codepoint) return miss(path)
        if (buffer.int != cellSize) return miss(path)
        val scale = buffer.double
        val translateX = buffer.double
        val translateY = buffer.double
        if (buffer.int != bitmapLength) return miss(path)
        val rgba = ByteArray(bitmapLength)
        buffer.get(rgba)
        if (!scale.isFinite() || scale <= 0.0 || !translateX.isFinite() || !translateY.isFinite()) return miss(path)
        return MsdfGlyphRaster(scale, translateX, translateY, rgba)
    }

    /** Temp-file + ATOMIC_MOVE publish; failures are swallowed (the cache is best-effort). */
    fun write(codepoint: Int, raster: MsdfGlyphRaster) {
        if (raster.rgba.size != cellSize * cellSize * 4) return
        val target = entryPath(codepoint)
        var temp: Path? = null
        try {
            temp = Files.createTempFile(directory, "entry", ".tmp")
            val buffer = ByteBuffer.allocate(HEADER_BYTES + raster.rgba.size).order(ByteOrder.BIG_ENDIAN)
            buffer.putInt(MAGIC).putInt(FORMAT).putInt(codepoint).putInt(cellSize)
            buffer.putDouble(raster.scale).putDouble(raster.translateX).putDouble(raster.translateY)
            buffer.putInt(raster.rgba.size).put(raster.rgba)
            Files.write(temp, buffer.array())
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
            temp = null
        } catch (e: IOException) {
            warnOnce("[FloydMSDF] disk cache write failed under {}", directory, e)
        } finally {
            temp?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }

    private fun entryPath(codepoint: Int): Path =
        directory.resolve(String.format("u%06x.bin", codepoint))

    private fun miss(path: Path): MsdfGlyphRaster? {
        runCatching { Files.deleteIfExists(path) }
        return null
    }

    companion object {
        private val logger = LogUtils.getLogger()

        /**
         * Everything that changes the generated bitmaps invalidates the cache: lwjgl-msdfgen
         * binding version (build.gradle.kts pins 3.3.4), edge-coloring angle (3.0), PX_RANGE (4),
         * EM_NORMALIZED scaling, and the top-down row flip applied at encode.
         */
        const val CACHE_VERSION = "msdfgen3.3.4-ec3.0-pxr4-emnorm-yflip-scc1"

        const val DEFAULT_MAX_CACHE_BYTES: Long = 64L * 1024L * 1024L

        private const val MAGIC = 0x464D5343 // "FMSC"
        private const val FORMAT = 1
        private const val HEADER_BYTES = 4 + 4 + 4 + 4 + 8 + 8 + 8 + 4

        @Volatile
        private var warnedWriteFailure = false

        /** sha256 hex of the font bytes (position/limit of the supplied buffer are untouched). */
        fun fontKey(fontBytes: ByteBuffer): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(fontBytes.duplicate())
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun directoryName(fontKey: String, cellSize: Int): String =
            "$fontKey-$CACHE_VERSION-c$cellSize"

        /**
         * Opens (creating if needed) the per-font cache dir under [root] and bumps its mtime so
         * LRU eviction sees it as fresh. Returns null when the directory cannot be created — the
         * caller then simply runs uncached.
         */
        fun open(root: Path, fontKey: String, cellSize: Int): MsdfDiskCache? = try {
            val directory = root.resolve(directoryName(fontKey, cellSize))
            Files.createDirectories(directory)
            runCatching { Files.setLastModifiedTime(directory, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis())) }
            MsdfDiskCache(directory, cellSize)
        } catch (e: IOException) {
            logger.warn("[FloydMSDF] disk cache unavailable under {}", root, e)
            null
        }

        /**
         * Bounded LRU eviction: while the total size of all font dirs under [root] exceeds
         * [maxBytes], delete whole font directories oldest-mtime-first. [keep] (the live font's
         * dir) is never evicted. Best-effort — every failure is swallowed; concurrent readers of
         * an evicted entry see a validated miss and regenerate.
         */
        fun evictLru(root: Path, maxBytes: Long = DEFAULT_MAX_CACHE_BYTES, keep: Path? = null) {
            try {
                if (!Files.isDirectory(root)) return
                val dirs = Files.list(root).use { stream ->
                    stream.filter { Files.isDirectory(it) }
                        .map { dir -> DirInfo(dir, dirSize(dir), lastModified(dir)) }
                        .toList()
                }
                var total = dirs.sumOf { it.size }
                if (total <= maxBytes) return
                for (dir in dirs.sortedBy { it.mtime }) {
                    if (total <= maxBytes) break
                    if (keep != null && dir.path == keep) continue
                    deleteRecursively(dir.path)
                    total -= dir.size
                }
            } catch (t: Throwable) {
                logger.warn("[FloydMSDF] disk cache eviction failed under {}", root, t)
            }
        }

        private fun dirSize(dir: Path): Long = try {
            Files.walk(dir).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .mapToLong { runCatching { Files.size(it) }.getOrDefault(0L) }
                    .sum()
            }
        } catch (e: IOException) {
            0L
        }

        private fun lastModified(dir: Path): Long =
            runCatching { Files.getLastModifiedTime(dir).toMillis() }.getOrDefault(0L)

        private fun deleteRecursively(dir: Path) {
            runCatching {
                Files.walk(dir).use { stream ->
                    stream.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.deleteIfExists(it) } }
                }
            }
        }

        private fun warnOnce(message: String, directory: Path, e: Exception) {
            if (!warnedWriteFailure) {
                warnedWriteFailure = true
                logger.warn(message, directory, e)
            }
        }
    }

    private class DirInfo(val path: Path, val size: Long, val mtime: Long)
}
