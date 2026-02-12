package floydaddons.not.dogshit.client.esp;
import floydaddons.not.dogshit.client.*;
import floydaddons.not.dogshit.client.config.*;
import floydaddons.not.dogshit.client.gui.*;
import floydaddons.not.dogshit.client.features.hud.*;
import floydaddons.not.dogshit.client.features.visual.*;
import floydaddons.not.dogshit.client.features.cosmetic.*;
import floydaddons.not.dogshit.client.features.misc.*;
import floydaddons.not.dogshit.client.esp.*;
import floydaddons.not.dogshit.client.skin.*;
import floydaddons.not.dogshit.client.util.*;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import floydaddons.not.dogshit.mixin.WorldRendererAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.OptionalDouble;

/**
 * Renders a chroma tracer line from the crosshair to the stalked player.
 * Shows through walls (no depth test). Approach based on SkyHanni's line renderer.
 */
public final class StalkRenderer {

    // Custom pipeline: LINES shaders with NO depth test (renders through walls)
    private static final RenderPipeline XRAY_LINES_PIPELINE = RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of("floydaddons", "pipeline/tracer_lines"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build();

    // Custom render layer: xray lines with configurable width
    private static final RenderLayer TRACER_LAYER = RenderLayer.of(
            "floydaddons_tracer",
            1536,
            XRAY_LINES_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(3.0)))
                    .layering(RenderPhase.NO_LAYERING)
                    .build(false)
    );

    private StalkRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(StalkRenderer::onAfterEntities);
    }

    private static void onAfterEntities(WorldRenderContext context) {
        if (!StalkManager.isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null || mc.worldRenderer == null) return;

        String targetName = StalkManager.getTargetName();
        if (targetName.isEmpty()) return;

        // Find the target player in the loaded world (case-insensitive IGN match)
        PlayerEntity target = null;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.getName().getString().equalsIgnoreCase(targetName)) {
                target = player;
                break;
            }
        }
        if (target == null) return;

        float tickDelta = mc.getRenderTickCounter().getTickProgress(false);

        // Camera position (smoothly interpolated by the renderer)
        Vec3d cameraPos = context.worldState().cameraRenderState.pos;

        // All coordinates are camera-relative (camera = origin).
        // This avoids jitter from mismatch between camera pos and player entity pos.

        // Start: 2 blocks in front of camera along look direction (anchored to crosshair)
        Vec3d lookVec = mc.player.getRotationVec(tickDelta);
        float sx = (float) (lookVec.x * 2.0);
        float sy = (float) (lookVec.y * 2.0);
        float sz = (float) (lookVec.z * 2.0);

        // End: target player center, relative to camera
        Vec3d targetPos = target.getLerpedPos(tickDelta);
        float ex = (float) (targetPos.x - cameraPos.x);
        float ey = (float) (targetPos.y + target.getHeight() / 2.0 - cameraPos.y);
        float ez = (float) (targetPos.z - cameraPos.z);

        // Normal = normalized line direction (required by LINES vertex format)
        float dx = ex - sx, dy = ey - sy, dz = ez - sz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001f) return;
        float nx = dx / len, ny = dy / len, nz = dz / len;

        // Stalk tracer color: chroma or static
        int rgb;
        if (RenderConfig.isStalkTracerChromaEnabled()) {
            float hue = (float) ((System.currentTimeMillis() % 4000) / 4000.0);
            rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        } else {
            rgb = RenderConfig.getStalkTracerColor();
        }
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;

        // Get the entity vertex consumers directly from WorldRenderer's buffer storage
        BufferBuilderStorage storage = ((WorldRendererAccessor) mc.worldRenderer).getBufferBuilders();
        VertexConsumerProvider.Immediate consumers = storage.getEntityVertexConsumers();
        VertexConsumer buf = consumers.getBuffer(TRACER_LAYER);

        // No matrix translation needed — coordinates are already camera-relative
        MatrixStack.Entry entry = context.matrices().peek();

        // Draw the line: two vertices with position, color, and normal
        buf.vertex(entry, sx, sy, sz).color(r, g, b, 1.0f).normal(entry, nx, ny, nz);
        buf.vertex(entry, ex, ey, ez).color(r, g, b, 1.0f).normal(entry, nx, ny, nz);

        // Flush our render layer immediately
        consumers.draw(TRACER_LAYER);
    }
}
