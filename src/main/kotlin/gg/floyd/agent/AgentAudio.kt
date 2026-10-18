package gg.floyd.agent

import com.google.gson.Gson
import java.io.*
import javax.sound.sampled.*
import kotlin.math.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors

/** Explicit push-to-talk capture; Windows speech is local and receives only the selected clip. */
object AgentAudio {
    private val format = AudioFormat(16000f, 16, 1, true, false)
    @Volatile var recording = false; private set
    @Volatile var mouth = 0f; private set
    @Volatile var speaking = false; private set
    private var line: TargetDataLine? = null
    private var clip: Clip? = null
    private val playbackGeneration = AtomicLong()
    private val microphoneEpoch = AtomicLong()
    private val previews = Executors.newSingleThreadExecutor { r -> Thread(r, "Floyd-Agent-Live-Transcript").apply { isDaemon = true } }

    fun microphones(): List<String> = listOf("Default") + AudioSystem.getMixerInfo().filter {
        runCatching { AudioSystem.getMixer(it).targetLineInfo.any { line -> TargetDataLine::class.java.isAssignableFrom(line.lineClass) } }.getOrDefault(false)
    }.map { it.name }.distinct().sorted()

    fun record(directory: File, microphone: String, partial: (String) -> Unit = {}, done: (String?, String?) -> Unit) {
        if (recording) return
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val input = if (microphone == "Default") AudioSystem.getLine(info) as TargetDataLine else {
            val mixer = AudioSystem.getMixerInfo().firstOrNull { it.name == microphone }
                ?: error("Selected microphone is unavailable. Choose another input.")
            AudioSystem.getMixer(mixer).getLine(info) as TargetDataLine
        }
        input.open(format); input.start(); line = input; recording = true
        val session = microphoneEpoch.incrementAndGet(); val previewBusy = AtomicBoolean(false)
        Thread({
            val bytes = ByteArrayOutputStream()
            try {
                val buffer = ByteArray(2048)
                var previewAt = 0L
                while (session == microphoneEpoch.get() && recording && bytes.size() < 1_920_000) {
                    val count = try { input.read(buffer, 0, buffer.size) } catch (e: Exception) {
                        if (!recording) break else throw e
                    }
                    if (count > 0) bytes.write(buffer, 0, count)
                    val now = System.currentTimeMillis()
                    if (bytes.size() >= 11200 && now - previewAt >= 250 && previewBusy.compareAndSet(false, true)) {
                        previewAt = now; val snapshot = bytes.toByteArray()
                        previews.execute {
                            try {
                                if (session == microphoneEpoch.get()) {
                                    val draft = AgentTranscription.transcribe(directory, snapshot, partial = true)
                                    if (session == microphoneEpoch.get() && draft.isNotBlank()) partial(draft)
                                }
                            } catch (_: Exception) { /* Final transcription reports setup or audio failures. */ }
                            finally { previewBusy.set(false) }
                        }
                    }
                }
                if (session == microphoneEpoch.get()) recording = false
                input.close()
                if (session != microphoneEpoch.get()) return@Thread
                val audio = bytes.toByteArray()
                if (audio.size < 3200) error("Recording was too short. Hold your push-to-talk key longer.")
                val text = AgentTranscription.transcribe(directory, audio, partial = false).trim()
                if (session == microphoneEpoch.get()) done(text.takeIf { it.isNotBlank() }, if (text.isBlank()) "No clear speech recognized. Check your microphone and try again." else null)
            } catch (_: Exception) { if (session == microphoneEpoch.get()) done(null, "Could not transcribe the microphone. Check your input and install the local speech runtime.") }
            finally { input.close(); if (session == microphoneEpoch.get()) { recording = false; line = null } }
        }, "Floyd-Agent-Microphone").apply { isDaemon = true; start() }
    }

    fun stopRecording() { recording = false; line?.close() }
    fun stop() { microphoneEpoch.incrementAndGet(); playbackGeneration.incrementAndGet(); AgentVoiceRuntime.cancelGeneration(); stopRecording(); line?.close(); clip?.stop(); clip?.close(); clip = null; speaking = false; mouth = 0f }

    fun voices(directory: File): List<String> {
        val text = windows(directory, "voices", emptyMap())
        return listOf("Default") + text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    }

    fun speak(directory: File, text: String, voice: String, volume: Float, recordedResource: String? = null, started: () -> Unit = {}, progress: (Float) -> Unit = {}, done: () -> Unit) {
        val epoch = playbackGeneration.get()
        val wav = File.createTempFile("voice-", ".wav", directory)
        try {
            if (recordedResource != null) {
                checkNotNull(AgentAudio::class.java.getResourceAsStream("/assets/floydaddons/agent/$recordedResource")) {
                    "The bundled introduction is unavailable."
                }.use { source -> wav.outputStream().use { source.copyTo(it) } }
            } else if (voice in listOf("Randy", "Big D Randy") || voice.startsWith("Verity")) AgentVoiceRuntime.synthesize(directory, text, wav, profile = voice)
            else windows(directory, "speak", mapOf("text" to text.take(6000), "voice" to voice, "audio" to wav.absolutePath))
            if (epoch != playbackGeneration.get()) return
            AudioSystem.getAudioInputStream(wav).use { stream ->
                val bytes = stream.readBytes()
                val playback = AudioSystem.getClip()
                playback.open(stream.format, bytes, 0, bytes.size)
                if (epoch != playbackGeneration.get()) { playback.close(); return }
                clip = playback
                if (playback.isControlSupported(BooleanControl.Type.MUTE))
                    (playback.getControl(BooleanControl.Type.MUTE) as BooleanControl).value = volume <= 0f
                if (playback.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    val gain = playback.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                    gain.value = (if (volume <= 0f) gain.minimum else 20f * log10(volume)).coerceIn(gain.minimum, gain.maximum)
                }
                speaking = true; started(); playback.start()
                while (epoch == playbackGeneration.get() && playback.isOpen && (playback.isRunning || playback.framePosition == 0)) {
                    progress((playback.framePosition.toFloat() / playback.frameLength.coerceAtLeast(1)).coerceIn(0f, 1f))
                    val offset = playback.framePosition * stream.format.frameSize
                    var energy = 0.0; var count = 0
                    for (i in offset until minOf(bytes.size - 1, offset + 1024) step 2) {
                        val sample = ((bytes[i].toInt() and 255) or (bytes[i + 1].toInt() shl 8)).toShort() / 32768.0
                        energy += sample * sample; count++
                    }
                    mouth = if (count == 0) 0f else (sqrt(energy / count) * 8).toFloat().coerceIn(0f, 1f)
                    Thread.sleep(25)
                }
                if (epoch == playbackGeneration.get()) progress(1f)
                playback.close()
            }
        } finally {
            wav.delete()
            if (epoch == playbackGeneration.get()) { speaking = false; mouth = 0f; clip = null }
            done()
        }
    }

    private fun windows(directory: File, mode: String, data: Map<String, String>): String {
        check(System.getProperty("os.name").lowercase().contains("win")) { "Local speech currently requires Windows." }
        directory.mkdirs()
        val script = File(directory, "speech.ps1")
        script.writeText("""
            param([string]${'$'}Mode,[string]${'$'}Job)
            ${'$'}ErrorActionPreference='Stop'
            [Console]::OutputEncoding=[Text.UTF8Encoding]::new()
            Add-Type -AssemblyName System.Speech
            ${'$'}data=Get-Content -LiteralPath ${'$'}Job -Raw -Encoding UTF8 | ConvertFrom-Json
            if (${'$'}Mode -eq 'recognize') {
                ${'$'}r=[System.Speech.Recognition.SpeechRecognitionEngine]::new()
                try { ${'$'}r.LoadGrammar([System.Speech.Recognition.DictationGrammar]::new()); ${'$'}r.SetInputToWaveFile(${'$'}data.audio); ${'$'}result=${'$'}r.Recognize([TimeSpan]::FromSeconds(15)); if (${'$'}result) { ${'$'}result.Text } } finally { ${'$'}r.Dispose() }
            } else {
                ${'$'}s=[System.Speech.Synthesis.SpeechSynthesizer]::new()
                try { if (${'$'}Mode -eq 'voices') { ${'$'}s.GetInstalledVoices() | ForEach-Object { ${'$'}_.VoiceInfo.Name } } else { if (${'$'}data.voice -ne 'Default') { ${'$'}s.SelectVoice(${'$'}data.voice) }; ${'$'}s.SetOutputToWaveFile(${'$'}data.audio); ${'$'}s.Speak(${'$'}data.text) } } finally { ${'$'}s.Dispose() }
            }
        """.trimIndent())
        val job = File.createTempFile("speech-job-", ".json", directory)
        try {
            job.writeText(Gson().toJson(data))
            val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-File", script.absolutePath, mode, job.absolutePath).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val output = StringBuilder()
            val reader = Thread { process.inputStream.bufferedReader(Charsets.UTF_8).use { output.append(it.readText()) } }.apply { isDaemon = true; start() }
            if (!process.waitFor(90, TimeUnit.SECONDS)) { process.destroyForcibly(); error("Windows speech timed out.") }
            reader.join(2000)
            check(process.exitValue() == 0) { "Windows speech is unavailable." }
            return output.toString()
        } finally { job.delete() }
    }
}
