package gg.floyd.utils.sound

import gg.floyd.FloydAddonsMod
import net.minecraft.client.resources.sounds.AbstractSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.AudioStream
import net.minecraft.client.sounds.SoundBufferLibrary
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundSource
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

class LocalMp3Sound(val file: Path, volume: Float, private val onFailure: (Throwable) -> Unit) :
    AbstractSoundInstance(Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "mining_ready_mp3"), SoundSource.UI, SoundInstance.createUnseededRandom()) {
    val decodedBytes = java.util.concurrent.atomic.AtomicLong()

    init {
        this.volume = volume
        pitch = 1f
        relative = true
        attenuation = SoundInstance.Attenuation.NONE
    }

    override fun getAudioStream(library: SoundBufferLibrary, id: Identifier, repeatInstantly: Boolean): CompletableFuture<AudioStream> =
        CompletableFuture.supplyAsync<AudioStream> { Mp3AudioStream(file) { decodedBytes.addAndGet(it.toLong()) } }.whenComplete { _, error ->
            if (error != null) onFailure(error)
        }
}
