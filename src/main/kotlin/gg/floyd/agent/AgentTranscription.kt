package gg.floyd.agent

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.sound.sampled.*

/** A persistent local Whisper process. Only model weights are downloaded; audio stays local. */
object AgentTranscription {
    private val python = File(System.getProperty("user.home"), ".floydaddons/agent-voice-hq/" + if (System.getProperty("os.name").lowercase().contains("win")) "Scripts/python.exe" else "bin/python")
    private var process: Process? = null
    private val replies = LinkedBlockingQueue<String>()
    @Volatile var status = "Not loaded"; private set
    private val format = AudioFormat(16000f, 16, 1, true, false)

    fun prepare(directory: File) { if (python.isFile) request(directory, mapOf("warmup" to true)) }

    fun transcribe(directory: File, pcm: ByteArray, partial: Boolean): String {
        val wav = File.createTempFile("transcription-", ".wav", directory)
        try {
            AudioInputStream(ByteArrayInputStream(pcm), format, pcm.size.toLong() / 2).use { AudioSystem.write(it, AudioFileFormat.Type.WAVE, wav) }
            return request(directory, mapOf("audio" to wav.absolutePath, "partial" to partial))
        } finally { wav.delete() }
    }

    @Synchronized private fun request(directory: File, job: Map<String, Any>): String {
        check(python.isFile) { "Use Install Voice Runtime to install local speech recognition." }
        if (process?.isAlive != true) {
            directory.mkdirs()
            val script = File(directory, "transcription_worker.py")
            AgentTranscription::class.java.getResourceAsStream("/assets/floydaddons/agent/transcription_worker.py").use { source ->
                checkNotNull(source); script.outputStream().use { source.copyTo(it) }
            }
            replies.clear()
            val builder = ProcessBuilder(python.absolutePath, "-u", script.absolutePath).directory(directory).redirectError(ProcessBuilder.Redirect.DISCARD)
            builder.environment()["CUDA_VISIBLE_DEVICES"] = ""
            builder.environment()["OMP_NUM_THREADS"] = "2"
            builder.environment()["HF_HUB_DISABLE_TELEMETRY"] = "1"
            val child = builder.start(); process = child
            Thread({
                child.inputStream.bufferedReader().useLines { lines -> lines.forEach { if (process === child) replies.offer(it) } }
                if (process === child) replies.offer("{\"ok\":false}")
            }, "Floyd-Agent-Transcription-Reader").apply { isDaemon = true; start() }
        }
        try {
            status = if (job["warmup"] == true) "Preparing Whisper..." else "Transcribing locally..."
            val writer = checkNotNull(process).outputStream.bufferedWriter()
            writer.write(Gson().toJson(job)); writer.newLine(); writer.flush()
            val result = JsonParser.parseString(replies.poll(300, TimeUnit.SECONDS) ?: error("Speech recognition timed out.")).asJsonObject
            check(result["ok"]?.asBoolean == true) { "Local speech recognition is unavailable. Use Install Voice Runtime to finish setup." }
            status = "Ready (CPU)"
            return result["text"]?.asString ?: ""
        } catch (e: Exception) { status = e.message ?: "Speech recognition unavailable"; throw e }
    }

    fun close() {
        val child = process; process = null
        child?.toHandle()?.descendants()?.use { children -> children.forEach { it.destroyForcibly() } }
        child?.destroy()
        replies.offer("{\"ok\":false}"); status = "Not loaded"
    }
}
