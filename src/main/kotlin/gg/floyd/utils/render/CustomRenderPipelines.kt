package gg.floyd.utils.render

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import gg.floyd.FloydAddonsMod
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

object CustomRenderPipelines {
    val LINES_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, true))
            .withLocation("${FloydAddonsMod.MOD_ID}/lines_esp")
            .build()
    )

    val LINES_TRANSLUCENT_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withLocation("${FloydAddonsMod.MOD_ID}/lines_translucent_esp")
            .build()
    )

    // Antialiased no-depth ESP lines: same screen-space line expansion as the vanilla LINES snippet, but
    // a custom shader pair computes true analytic coverage (fwidth-normalized SDF feather on a noperspective
    // pixel distance) so tracers/wireframes read as smooth thin strokes instead of hard, stair-stepped
    // pixels. The fragment outputs PREMULTIPLIED alpha (so overlaps don't double-darken into dark dots),
    // so the pipeline must use the premultiplied translucent blend to match.
    val LINES_AA_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withVertexShader(Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "core/lines_aa"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "core/lines_aa"))
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            // No backface culling: the screen-space line quad can wind either way depending on the
            // segment's direction; culling it drops some angles into gaps. (The shader also flips the
            // offset for winding consistency; this is belt-and-suspenders.)
            .withCull(false)
            .withLocation("${FloydAddonsMod.MOD_ID}/lines_aa_esp")
            .build()
    )

    val QUADS_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, true))
            .withLocation("${FloydAddonsMod.MOD_ID}/quads_esp")
            .build()
    )

    val PIPELINE_ROUND_RECT: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "pipeline/round_rect"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "core/round_rect"))
            .withVertexShader(Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "core/round_rect"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withUniform("u", UniformType.UNIFORM_BUFFER)
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .build()
    )

    /**
     * Per-panel frosted blur pipeline: a GUI round-rect that samples the main framebuffer ("In") and
     * blurs it inside the rounded mask. Mirrors [PIPELINE_ROUND_RECT] but adds the texture sampler.
     */
    val PIPELINE_PANEL_BLUR: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "pipeline/panel_blur"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "core/panel_blur"))
            .withVertexShader(Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "core/panel_blur"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withUniform("u", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .build()
    )
}
