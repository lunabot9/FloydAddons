package gg.floyd.features.impl.misc

import com.google.gson.GsonBuilder
import gg.floyd.utils.FloydPlatform
import java.io.RandomAccessFile
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Minimal pure-Java Discord IPC client (no native library) used as a cross-platform fallback for
 * rich presence. The bundled `club.minnced:discord-rpc` JNI ships no macOS-arm64 binary, so
 * `Discord_Initialize` throws `UnsatisfiedLinkError` there; this speaks the IPC protocol directly.
 *
 * Framing per the Discord IPC spec: a **little-endian** `{opcode:int32, length:int32}` header
 * followed by a UTF-8 JSON payload. Only what presence needs is implemented: HANDSHAKE (op 0)
 * then SET_ACTIVITY frames (op 1). One response frame is read after each send so the receive
 * buffer can never back up.
 *
 * Transport per [FloydPlatform]: AF_UNIX `discord-ipc-N` sockets on macOS/Linux (SocketChannel),
 * and the `\\.\pipe\discord-ipc-N` named pipe on Windows — the JDK cannot open that as a
 * SocketChannel, but a plain [RandomAccessFile] in `rw` mode speaks it fine (the standard
 * pure-Java trick), so the fallback now covers every platform if the native dll fails too.
 *
 * All I/O is blocking and is expected to run on a dedicated daemon thread, never the client
 * thread.
 */
object DiscordIpcClient {
    private const val OP_HANDSHAKE = 0
    private const val OP_FRAME = 1
    private const val OP_CLOSE = 2
    private const val MAX_FRAME = 1 shl 20

    private val gson = GsonBuilder().create()
    private var conduit: IpcConduit? = null

    val isConnected: Boolean
        @Synchronized get() = conduit?.isOpen == true

    /** Opens the IPC transport and performs the handshake. Returns false (and stays closed) on any failure. */
    @Synchronized
    fun connect(appId: String): Boolean {
        if (conduit?.isOpen == true) return true
        return try {
            conduit = openConduit() ?: return false
            send(OP_HANDSHAKE, mapOf("v" to 1, "client_id" to appId))
            readFrame()
            true
        } catch (_: Throwable) {
            closeQuietly()
            false
        }
    }

    /** Sends a SET_ACTIVITY frame. Returns false (and closes) on any failure. */
    @Synchronized
    fun setActivity(pid: Long, activity: Map<String, Any?>): Boolean {
        if (conduit?.isOpen != true) return false
        return try {
            send(OP_FRAME, mapOf(
                "cmd" to "SET_ACTIVITY",
                "nonce" to UUID.randomUUID().toString(),
                "args" to mapOf("pid" to pid, "activity" to activity)
            ))
            readFrame()
            true
        } catch (_: Throwable) {
            closeQuietly()
            false
        }
    }

    @Synchronized
    fun close() {
        if (conduit?.isOpen == true) runCatching { send(OP_CLOSE, emptyMap<String, Any?>()) }
        closeQuietly()
    }

    private fun send(op: Int, payload: Map<String, Any?>) {
        val ch = conduit ?: throw IllegalStateException("not_connected")
        val json = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(8 + json.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(op).putInt(json.size).put(json).flip()
        ch.writeFully(buf)
    }

    /** Reads and discards exactly one response frame so the receive buffer stays balanced. */
    private fun readFrame() {
        val ch = conduit ?: return
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        if (!ch.readFully(header)) return
        header.flip()
        header.int // opcode (ignored)
        val len = header.int
        if (len in 1..MAX_FRAME) ch.readFully(ByteBuffer.allocate(len))
    }

    private fun closeQuietly() {
        runCatching { conduit?.close() }
        conduit = null
    }

    // ---- Transport ------------------------------------------------------------------------------

    /** One blocking IPC transport: an AF_UNIX socket (macOS/Linux) or a Windows named pipe. */
    private interface IpcConduit : AutoCloseable {
        val isOpen: Boolean
        fun writeFully(buf: ByteBuffer)

        /** Fills [buf] completely; false on orderly EOF. */
        fun readFully(buf: ByteBuffer): Boolean
    }

    private class SocketConduit(private val channel: SocketChannel) : IpcConduit {
        override val isOpen: Boolean get() = channel.isOpen
        override fun writeFully(buf: ByteBuffer) {
            while (buf.hasRemaining()) channel.write(buf)
        }

        override fun readFully(buf: ByteBuffer): Boolean {
            while (buf.hasRemaining()) if (channel.read(buf) < 0) return false
            return true
        }

        override fun close() = channel.close()
    }

    private class PipeConduit(private val pipe: RandomAccessFile) : IpcConduit {
        @Volatile
        private var open = true
        override val isOpen: Boolean get() = open
        override fun writeFully(buf: ByteBuffer) {
            val bytes = ByteArray(buf.remaining())
            buf.get(bytes)
            pipe.write(bytes)
        }

        override fun readFully(buf: ByteBuffer): Boolean {
            val bytes = ByteArray(buf.remaining())
            return try {
                pipe.readFully(bytes)
                buf.put(bytes)
                true
            } catch (_: java.io.EOFException) {
                false
            }
        }

        override fun close() {
            open = false
            pipe.close()
        }
    }

    private fun openConduit(): IpcConduit? = if (FloydPlatform.isWindows) {
        // Windows: \\.\pipe\discord-ipc-{0..9}; RandomAccessFile("rw") opens the duplex pipe.
        (0..9).firstNotNullOfOrNull { i ->
            runCatching { PipeConduit(RandomAccessFile("""\\.\pipe\discord-ipc-$i""", "rw")) }.getOrNull()
        }
    } else {
        resolveSocketPath()?.let { path ->
            runCatching { SocketConduit(SocketChannel.open(UnixDomainSocketAddress.of(path))) }.getOrNull()
        }
    }

    /**
     * First existing `discord-ipc-{0..9}` socket under the platform runtime dir(s), or null when
     * Discord is not running. macOS/Linux only — Windows goes through the named pipe instead.
     */
    private fun resolveSocketPath(): Path? {
        val bases = buildList {
            System.getenv("XDG_RUNTIME_DIR")?.let { add(it) }
            System.getenv("TMPDIR")?.let { add(it.trimEnd('/')) }
            add("/tmp")
        }
        // Discord under flatpak/snap nests the socket in an app subdir.
        val subdirs = listOf("", "app/com.discordapp.Discord/", "snap.discord/")
        for (base in bases) for (sub in subdirs) for (i in 0..9) {
            val candidate = Path.of(base, "$sub" + "discord-ipc-$i")
            if (Files.exists(candidate)) return candidate
        }
        return null
    }
}
