package gg.floyd.utils.font

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.gui.font.TextRenderable
import java.util.concurrent.atomic.AtomicLong

/** Skyblocker selects world shaders itself, bypassing TextRenderable.renderType. */
object SkyblockerMsdfCompat {
    private val pipelineRewrites = AtomicLong()
    private val samplerRewrites = AtomicLong()

    @JvmStatic fun isMsdf(text: TextRenderable): Boolean =
        text is FloydMsdfRenderable || text is FloydGuiWorldRenderable

    @JvmStatic fun pipeline(original: RenderPipeline, throughWalls: Boolean, text: TextRenderable): RenderPipeline {
        if (!isMsdf(text)) return original
        pipelineRewrites.incrementAndGet()
        return if (throughWalls) MsdfPipelines.TEXT_SEE_THROUGH else MsdfPipelines.TEXT
    }

    @JvmStatic fun filter(original: FilterMode, text: TextRenderable): FilterMode {
        if (!isMsdf(text)) return original
        samplerRewrites.incrementAndGet()
        return FilterMode.LINEAR
    }

    fun state(): Map<String, Any> = mapOf(
        "pipelineRewrites" to pipelineRewrites.get(),
        "samplerRewrites" to samplerRewrites.get(),
    )
}
