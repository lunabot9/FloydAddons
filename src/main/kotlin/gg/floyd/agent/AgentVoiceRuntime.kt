package gg.floyd.agent

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** A separate bounded CPU worker keeps model loading and synthesis off Minecraft's render thread. */
object AgentVoiceRuntime {
    private fun home() = File(System.getProperty("user.home"), ".floydaddons/agent-voice-hq")
    private fun python() = File(home(), if (System.getProperty("os.name").lowercase().contains("win")) "Scripts/python.exe" else "bin/python")
    private var process: Process? = null
    private val replies = LinkedBlockingQueue<String>()
    @Volatile private var generating = false
    @Volatile var warmingUp = false; private set
    @Volatile var processStarts = 0; private set
    @Volatile private var preparedProfile: String? = null
    @Volatile var status = "Not loaded"; private set

    fun install() {
        status = "Installing local voice runtime..."
        if (!python().isFile) {
            val setup = File(System.getProperty("user.home"), ".floydaddons/agent-setup")
            val win = System.getProperty("os.name").lowercase().contains("win")
            val bootstrap = File(setup, if (win) "Scripts/python.exe" else "bin/python")
            if (!bootstrap.isFile) run(listOf(if (win) "python" else "python3", "-m", "venv", setup.absolutePath), 120)
            run(listOf(bootstrap.absolutePath, "-m", "pip", "install", "uv"), 300)
            run(listOf(bootstrap.absolutePath, "-m", "uv", "venv", "--python", "3.12", "--seed", home().absolutePath), 300)
        }
        run(listOf(python().absolutePath, "-m", "pip", "install", "chatterbox-tts==0.1.7", "setuptools<81", "faster-whisper==1.2.1", "av>=11,<17"), 900)
        status = "Natural voice installed. Use Test Voice to load the model."
    }

    private fun run(command: List<String>, timeout: Long) {
        val child = ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        if (!child.waitFor(timeout, TimeUnit.SECONDS)) { child.destroyForcibly(); error("Voice setup timed out. Try again.") }
        check(child.exitValue() == 0) { "Voice setup failed. Install Python 3.10 or newer, then try again." }
    }

    fun prepare(directory: File, profile: String = "Verity") {
        if (!python().isFile || (process?.isAlive == true && status == "Ready (CPU)" && preparedProfile == profile)) return
        synthesize(directory, "", File(directory, "voice-warmup.wav"), warmup = true, profile = profile)
    }

    @Synchronized fun synthesize(directory: File, text: String, output: File, warmup: Boolean = false, profile: String = "Verity") {
        check(python().isFile) { "The local Natural voice runtime is not installed." }
        if (process?.isAlive != true) {
            val script = File(directory, "voice_worker.py")
            val reference = File(directory, "verity-reference.wav")
            val randyReference = File(directory, "randy-original-reference.wav")
            val tomReference = File(directory, "tom-pearl-reference.wav")
            copyResource("voice_worker.py", script); copyResource("verity-reference.wav", reference)
            copyResource("randy-original-reference.wav", randyReference)
            copyResource("tom-pearl-reference.wav", tomReference)
            replies.clear()
            val builder = ProcessBuilder(python().absolutePath, "-u", script.absolutePath, reference.absolutePath, randyReference.absolutePath, tomReference.absolutePath)
                .directory(directory).redirectError(ProcessBuilder.Redirect.DISCARD)
            builder.environment()["CUDA_VISIBLE_DEVICES"] = ""
            builder.environment()["OMP_NUM_THREADS"] = "4"
            builder.environment()["MKL_NUM_THREADS"] = "4"
            builder.environment()["HF_HUB_DISABLE_TELEMETRY"] = "1"
            val child = builder.start(); process = child; processStarts++; preparedProfile = null
            Thread({
                child.inputStream.bufferedReader().useLines { lines -> lines.forEach { if (process === child) replies.offer(it) } }
                if (process === child) replies.offer("{\"ok\":false}")
            }, "Floyd-Agent-Voice-Reader").apply { isDaemon = true; start() }
        }
        warmingUp = warmup; generating = true
        try {
            status = if (warmup) "Warming $profile voice on CPU..." else "Generating $profile voice on CPU..."
            val writer = checkNotNull(process).outputStream.bufferedWriter()
            writer.write(Gson().toJson(mapOf("text" to text, "output" to output.absolutePath, "warmup" to warmup, "profile" to profile))); writer.newLine(); writer.flush()
            val line = replies.poll(300, TimeUnit.SECONDS) ?: error("Natural voice timed out. Check the local voice setup.")
            val result = JsonParser.parseString(line).asJsonObject
            check(result["ok"]?.asBoolean == true) { result["error"]?.asString ?: "Local voice worker stopped." }
            preparedProfile = profile; status = "Ready (CPU)"
        } catch (e: Exception) { status = e.message ?: "Voice unavailable"; throw e }
        finally { generating = false; warmingUp = false }
    }

    fun cancelGeneration() {
        // Warmup belongs to the enabled companion, not an individual response.
        // Cancelling it on every intro/message forces the model to reload from disk.
        if (!generating || warmingUp) return
        val child = process; process = null; stopWorker(child)
        replies.offer("{\"ok\":false,\"error\":\"Voice generation stopped.\"}")
        status = "Stopped"
    }
    fun close() {
        val child = process; process = null; stopWorker(child)
        replies.offer("{\"ok\":false,\"error\":\"Voice worker stopped.\"}")
        status = "Not loaded"
    }
    private fun stopWorker(child: Process?) {
        if (child == null) return
        // Windows venv python.exe may launch the base interpreter as a child.
        child.toHandle().descendants().use { children -> children.forEach { it.destroyForcibly() } }
        child.destroy()
    }
    private fun copyResource(name: String, target: File) {
        AgentVoiceRuntime::class.java.getResourceAsStream("/assets/floydaddons/agent/$name").use { source ->
            checkNotNull(source) { "Bundled voice resource missing." }
            target.outputStream().use { source.copyTo(it) }
        }
    }
}
