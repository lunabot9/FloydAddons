package gg.floyd.utils.sound

import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import net.minecraft.client.sounds.AudioStream
import org.lwjgl.system.MemoryUtil
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.sampled.AudioFormat

/** Decodes a few MP3 frames at a time; Minecraft owns and frees each returned native PCM buffer. */
class Mp3AudioStream(path: Path, private val onRead: (Int) -> Unit = {}) : AudioStream {
    private val bits = Bitstream(Files.newInputStream(path).buffered())
    private val decoder = Decoder()
    private var samples = ShortArray(0)
    private var sampleIndex = 0
    private var sampleCount = 0
    private var rate = 0
    private var channels = 0
    private val format: AudioFormat

    init {
        try {
            if (!nextFrame()) throw IOException("MP3 contains no decodable audio")
            format = AudioFormat(rate.toFloat(), 16, channels, true, false)
        } catch (failure: Exception) {
            bits.close()
            throw IOException("Cannot decode MP3: ${path.fileName}", failure)
        }
    }

    private fun nextFrame(): Boolean {
        try {
            val header = bits.readFrame() ?: return false
            try {
                val decoded = decoder.decodeFrame(header, bits) as SampleBuffer
                if (rate != 0 && (rate != decoded.sampleFrequency || channels != decoded.channelCount))
                    throw IOException("MP3 changes audio format mid-stream")
                rate = decoded.sampleFrequency
                channels = decoded.channelCount
                samples = decoded.buffer
                sampleIndex = 0
                sampleCount = decoded.bufferLength
            } finally { bits.closeFrame() }
            return true
        } catch (failure: Exception) { throw IOException("MP3 frame decode failed", failure) }
    }

    override fun getFormat(): AudioFormat = format

    override fun read(size: Int): ByteBuffer {
        val pcm = MemoryUtil.memAlloc((size.coerceAtLeast(channels * 2) / (channels * 2)) * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
        try {
            while (pcm.remaining() >= 2) {
                if (sampleIndex >= sampleCount && !nextFrame()) break
                if (sampleCount == 0) continue
                pcm.putShort(samples[sampleIndex++])
            }
            onRead(pcm.position())
            return pcm.flip()
        } catch (failure: Exception) { MemoryUtil.memFree(pcm); throw failure }
    }

    override fun close() { bits.close() }
}
