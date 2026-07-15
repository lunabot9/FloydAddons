package gg.floyd.utils.media

import com.google.gson.Gson
import com.google.gson.JsonObject
import gg.floyd.FloydAddonsMod
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference

data class MediaSnapshot(
    val available: Boolean = false,
    val source: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val positionMs: Long = 0L,
    val reportedPositionMs: Long = positionMs,
    val durationMs: Long = 0L,
    val playing: Boolean = false,
    val artworkKey: String = "",
    val artworkBase64: String? = null,
    val sampledAtMs: Long = System.currentTimeMillis(),
) {
    fun displayedPositionMs(nowMs: Long = System.currentTimeMillis()): Long {
        val advanced = if (playing) positionMs + (nowMs - sampledAtMs).coerceAtLeast(0L) else positionMs
        return if (durationMs > 0L) advanced.coerceIn(0L, durationMs) else advanced.coerceAtLeast(0L)
    }
}

/** Persistent, low-frequency bridge to Windows' Global System Media Transport Controls session. */
object WindowsMediaSession {
    private val gson = Gson()
    private val current = AtomicReference(MediaSnapshot())
    private val lock = Any()
    @Volatile private var process: Process? = null
    @Volatile private var readerThread: Thread? = null

    init {
        runCatching {
            Runtime.getRuntime().addShutdownHook(Thread({ stop() }, "Floyd-MediaSession-Shutdown"))
        }
    }

    fun snapshot(): MediaSnapshot = current.get()

    fun start() {
        if (!isWindows()) return
        synchronized(lock) {
            if (process?.isAlive == true) return
            val script = extractScript() ?: return
            runCatching {
                val child = ProcessBuilder(
                    "powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", script.toString()
                ).start()
                process = child
                Thread({ readLoop(child) }, "Floyd-MediaSession").also {
                    it.isDaemon = true
                    readerThread = it
                    it.start()
                }
                Thread({ child.errorStream.bufferedReader().use { it.readText() } }, "Floyd-MediaSession-Stderr").also {
                    it.isDaemon = true
                    it.start()
                }
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            process?.destroy()
            process = null
            readerThread?.interrupt()
            readerThread = null
            current.set(MediaSnapshot())
        }
    }

    internal fun parse(line: String, sampledAtMs: Long = System.currentTimeMillis()): MediaSnapshot? {
        val json = runCatching { gson.fromJson(line, JsonObject::class.java) }.getOrNull() ?: return null
        if (!json.bool("available")) return MediaSnapshot(sampledAtMs = sampledAtMs)
        return MediaSnapshot(
            available = true,
            source = json.string("source"),
            title = json.string("title"),
            artist = json.string("artist"),
            album = json.string("album"),
            positionMs = json.long("positionMs"),
            reportedPositionMs = json.long("positionMs"),
            durationMs = json.long("durationMs"),
            playing = json.bool("playing"),
            artworkKey = json.string("artworkKey"),
            artworkBase64 = json.get("artwork")?.takeUnless { it.isJsonNull }?.asString,
            sampledAtMs = sampledAtMs,
        )
    }

    internal fun reconcile(previous: MediaSnapshot, next: MediaSnapshot): MediaSnapshot {
        if (!previous.available || !next.available || previous.artworkKey != next.artworkKey) return next

        val retainedArtwork = next.artworkBase64 ?: previous.artworkBase64
        if (!previous.playing || !next.playing) return next.copy(artworkBase64 = retainedArtwork)

        val projected = previous.displayedPositionMs(next.sampledAtMs)
        val reconciledPosition = when {
            next.reportedPositionMs == previous.reportedPositionMs -> projected
            next.reportedPositionMs < previous.reportedPositionMs - SEEK_BACK_THRESHOLD_MS -> next.positionMs
            else -> maxOf(next.positionMs, projected)
        }
        return next.copy(positionMs = reconciledPosition, artworkBase64 = retainedArtwork)
    }

    private fun readLoop(child: Process) {
        try {
            child.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    parse(line)?.let { next -> current.updateAndGet { previous -> reconcile(previous, next) } }
                }
            }
        } catch (_: Exception) {
        } finally {
            if (process === child) process = null
        }
    }

    private fun extractScript() = runCatching {
        val directory = FloydAddonsMod.configFile.toPath().resolve("cache")
        Files.createDirectories(directory)
        val target = directory.resolve("windows-media-session.ps1")
        WindowsMediaSession::class.java.getResourceAsStream("/assets/floydaddons/media/windows-media-session.ps1")!!.use { input ->
            Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        target
    }.getOrNull()

    private fun isWindows(): Boolean = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)

    private fun JsonObject.string(name: String): String = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    private fun JsonObject.long(name: String): Long = get(name)?.takeUnless { it.isJsonNull }?.asLong ?: 0L
    private fun JsonObject.bool(name: String): Boolean = get(name)?.takeUnless { it.isJsonNull }?.asBoolean ?: false

    private const val SEEK_BACK_THRESHOLD_MS = 2_000L
}
