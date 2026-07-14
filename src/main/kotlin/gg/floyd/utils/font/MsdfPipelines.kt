package gg.floyd.utils.font

import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import gg.floyd.FloydAddonsMod
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

/**
 * Static MSDF text pipelines mirroring vanilla TEXT/TEXT_SEE_THROUGH/TEXT_POLYGON_OFFSET/GUI_TEXT
 * with our median-of-RGB fragment shaders. Static instances only (the GlDevice pipeline cache is
 * an IdentityHashMap) and registered so resource reloads eagerly compile-check them.
 */
object MsdfPipelines {
    /**
     * Forces this object's classload (and thus the four `RenderPipelines.register` calls below)
     * during client init, BEFORE the first resource reload's `ShaderManager.apply` snapshots the
     * static-pipeline list for its eager compile check (D1(b)/D4). Without this the object is
     * first touched at the first glyph bake — after the boot reload — so a broken shipped MSDF
     * shader would fail silently at first draw instead of hard-failing the reload. Registration
     * is GPU-free (`RenderPipelines.register` only puts into a static map), so init-time is safe.
     */
    @JvmStatic
    fun bootstrap() {
        // Object initializer has run by the time this method is entered; nothing else to do.
    }

    private fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, path)

    val TEXT: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.TEXT_SNIPPET, RenderPipelines.FOG_SNIPPET)
            .withLocation(id("pipeline/msdf_text"))
            .withVertexShader(id("core/msdf_text"))
            .withFragmentShader(id("core/msdf_text"))
            .withSampler("Sampler0")
            .withSampler("Sampler2")
            .build()
    )

    val TEXT_SEE_THROUGH: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.TEXT_SNIPPET)
            .withLocation(id("pipeline/msdf_text_see_through"))
            .withVertexShader(id("core/msdf_text_see_through"))
            .withFragmentShader(id("core/msdf_text_see_through"))
            .withSampler("Sampler0")
            .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build()
    )

    val TEXT_POLYGON_OFFSET: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.TEXT_SNIPPET, RenderPipelines.FOG_SNIPPET)
            .withLocation(id("pipeline/msdf_text_polygon_offset"))
            .withVertexShader(id("core/msdf_text"))
            .withFragmentShader(id("core/msdf_text"))
            .withSampler("Sampler0")
            .withSampler("Sampler2")
            .withDepthStencilState(DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true, -1.0F, -10.0F))
            .build()
    )

    val GUI: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXT_SNIPPET, RenderPipelines.FOG_SNIPPET)
            .withLocation(id("pipeline/msdf_text_gui"))
            .withVertexShader(id("core/msdf_text"))
            .withFragmentShader(id("core/msdf_text"))
            .withSampler("Sampler0")
            .withSampler("Sampler2")
            .build()
    )
}
