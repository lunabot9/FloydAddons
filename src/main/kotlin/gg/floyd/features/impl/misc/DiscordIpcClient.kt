package gg.floyd.features.impl.misc

import com.google.gson.GsonBuilder
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
 * followed by a UTF-8 JSON payload, over the local `discord-ipc-N` AF_UNIX socket. Only what
 * presence needs is implemented: HANDSHAKE (op 0) then SET_ACTIVITY frames (op 1). One response
 * frame is read after each send so the socket receive buffer can never back up. Windows uses a
 * named pipe (`\\.\pipe\discord-ipc-0`) that the JDK cannot open as a SocketChannel, so this
 * fallback is Unix-only; Windows keeps using the working native dll.
 *
 * All socket I/O is blocking and is expected to run on a dedicated daemon thread, never the client
 * thread.
 */
object DiscordIpcClient {
    private const val OP_HANDSHAKE = 0
    private const val OP_FRAME = 1
    private const val OP_CLOSE = 2
    private const val MAX_FRAME = 1 shl 20

    private val gson = GsonBuilder().create()
    private var channel: SocketChannel? = null

    val isConnected: Boolean
        @Synchronized get() = channel?.isOpen == true

    /** Opens the IPC socket and performs the handshake. Returns false (and stays closed) on any failure. */
    @Synchronized
    fun connect(appId: String): Boolean {
        if (channel?.isOpen == true) return true
        val path = resolveSocketPath() ?: return false
        return try {
            channel = SocketChannel.open(UnixDomainSocketAddress.of(path))
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
        if (channel?.isOpen != true) return false
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
        if (channel?.isOpen == true) runCatching { send(OP_CLOSE, emptyMap<String, Any?>()) }
        closeQuietly()
    }

    private fun send(op: Int, payload: Map<String, Any?>) {
        val ch = channel ?: throw IllegalStateException("not_connected")
        val json = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(8 + json.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(op).putInt(json.size).put(json).flip()
        while (buf.hasRemaining()) ch.write(buf)
    }

    /** Reads and discards exactly one response frame so the receive buffer stays balanced. */
    private fun readFrame() {
        val ch = channel ?: return
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        if (!readFully(ch, header)) return
        header.flip()
        header.int // opcode (ignored)
        val len = header.int
        if (len in 1..MAX_FRAME) readFully(ch, ByteBuffer.allocate(len))
    }

    private fun readFully(ch: SocketChannel, buf: ByteBuffer): Boolean {
        while (buf.hasRemaining()) if (ch.read(buf) < 0) return false
        return true
    }

    private fun closeQuietly() {
        runCatching { channel?.close() }
        channel = null
    }

    /**
     * First existing `discord-ipc-{0..9}` socket under the platform runtime dir(s), or null on
     * Windows (named pipe, not a SocketChannel path) / when Discord is not running.
     */
    private fun resolveSocketPath(): Path? {
        if (System.getProperty("os.name", "").lowercase().contains("win")) return null
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
