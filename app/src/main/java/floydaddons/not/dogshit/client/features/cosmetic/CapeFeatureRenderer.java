package floydaddons.not.dogshit.client.features.cosmetic;
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

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.Identifier;

/**
 * Custom cape renderer that shows the full PNG on both front and back without
 * the vanilla texture layout that slices the image. Uses simple waving physics
 * for a smooth look.
 */
public class CapeFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    public CapeFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (!RenderConfig.isCapeEnabled()) return;
        if (state.invisible) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || state.id != mc.player.getId()) return;

        Identifier tex = CapeManager.getTexture(mc);
        RenderLayer layer = RenderLayer.getEntityCutoutNoCull(tex);

        matrices.push();
        // anchor to body
        var body = getContextModel().body;
        body.applyTransform(matrices);
        // Anchor like vanilla/OptiFine: sit on the upper back rather than the head.
        // Pull slightly farther off the back so armor doesn’t occlude it.
        matrices.translate(0.0f, 0.0f, 0.18f);

        // Use vanilla cape swing (matches OptiFine behavior).
        applyVanillaCapeRotation(matrices, state);

        queue.submitCustom(matrices, layer, (entry, consumer) -> drawSlabCape(entry, consumer, light));

        matrices.pop();
    }

    private static void drawSlabCape(MatrixStack.Entry entry, VertexConsumer consumer, int light) {
        final int overlay = OverlayTexture.DEFAULT_UV;
        final float height = 1.02f; // 0.03 shorter

        float aspect = CapeManager.getAspectRatio();
        // Wider cape span across the back.
        float width = 0.8f * (aspect / 2f);
        width = Math.max(0.65f, Math.min(0.80f, width));
        float thickness = 0.08f; // thicker per user request

        float x0 = -width / 2f;
        float x1 = width / 2f;
        float y0 = 0f;
        float y1 = height;
        float zFront = -thickness / 2f;
        float zBack = thickness / 2f;

        // Use edge rows of the texture for top/bottom so the PNG "wraps" across all faces
        float vTop = 0f;
        float vBottom = 1f;

        // back face (full image, U flipped so PNG reads correctly from behind)
        consumer.vertex(entry, x0, y0, zBack).color(255,255,255,255).texture(1f, 0f).overlay(overlay).light(light).normal(entry, 0, 0, 1);
        consumer.vertex(entry, x1, y0, zBack).color(255,255,255,255).texture(0f, 0f).overlay(overlay).light(light).normal(entry, 0, 0, 1);
        consumer.vertex(entry, x1, y1, zBack).color(255,255,255,255).texture(0f, 1f).overlay(overlay).light(light).normal(entry, 0, 0, 1);
        consumer.vertex(entry, x0, y1, zBack).color(255,255,255,255).texture(1f, 1f).overlay(overlay).light(light).normal(entry, 0, 0, 1);

        // front face (repeat image)
        consumer.vertex(entry, x0, y1, zFront).color(255,255,255,255).texture(0f, 1f).overlay(overlay).light(light).normal(entry, 0, 0, -1);
        consumer.vertex(entry, x1, y1, zFront).color(255,255,255,255).texture(1f, 1f).overlay(overlay).light(light).normal(entry, 0, 0, -1);
        consumer.vertex(entry, x1, y0, zFront).color(255,255,255,255).texture(1f, 0f).overlay(overlay).light(light).normal(entry, 0, 0, -1);
        consumer.vertex(entry, x0, y0, zFront).color(255,255,255,255).texture(0f, 0f).overlay(overlay).light(light).normal(entry, 0, 0, -1);

        // left edge
        consumer.vertex(entry, x0, y1, zBack).color(255,255,255,255).texture(0f, 1f).overlay(overlay).light(light).normal(entry, -1, 0, 0);
        consumer.vertex(entry, x0, y1, zFront).color(255,255,255,255).texture(0f, 1f).overlay(overlay).light(light).normal(entry, -1, 0, 0);
        consumer.vertex(entry, x0, y0, zFront).color(255,255,255,255).texture(0f, 0f).overlay(overlay).light(light).normal(entry, -1, 0, 0);
        consumer.vertex(entry, x0, y0, zBack).color(255,255,255,255).texture(0f, 0f).overlay(overlay).light(light).normal(entry, -1, 0, 0);

        // right edge
        consumer.vertex(entry, x1, y1, zFront).color(255,255,255,255).texture(1f, 1f).overlay(overlay).light(light).normal(entry, 1, 0, 0);
        consumer.vertex(entry, x1, y1, zBack).color(255,255,255,255).texture(1f, 1f).overlay(overlay).light(light).normal(entry, 1, 0, 0);
        consumer.vertex(entry, x1, y0, zBack).color(255,255,255,255).texture(1f, 0f).overlay(overlay).light(light).normal(entry, 1, 0, 0);
        consumer.vertex(entry, x1, y0, zFront).color(255,255,255,255).texture(1f, 0f).overlay(overlay).light(light).normal(entry, 1, 0, 0);

        // top edge (sample top row of PNG)
        consumer.vertex(entry, x0, y0, zFront).color(255,255,255,255).texture(0f, vTop).overlay(overlay).light(light).normal(entry, 0, -1, 0);
        consumer.vertex(entry, x1, y0, zFront).color(255,255,255,255).texture(1f, vTop).overlay(overlay).light(light).normal(entry, 0, -1, 0);
        consumer.vertex(entry, x1, y0, zBack).color(255,255,255,255).texture(1f, vTop).overlay(overlay).light(light).normal(entry, 0, -1, 0);
        consumer.vertex(entry, x0, y0, zBack).color(255,255,255,255).texture(0f, vTop).overlay(overlay).light(light).normal(entry, 0, -1, 0);

        // bottom edge (sample bottom row of PNG)
        consumer.vertex(entry, x0, y1, zBack).color(255,255,255,255).texture(0f, vBottom).overlay(overlay).light(light).normal(entry, 0, 1, 0);
        consumer.vertex(entry, x1, y1, zBack).color(255,255,255,255).texture(1f, vBottom).overlay(overlay).light(light).normal(entry, 0, 1, 0);
        consumer.vertex(entry, x1, y1, zFront).color(255,255,255,255).texture(1f, vBottom).overlay(overlay).light(light).normal(entry, 0, 1, 0);
        consumer.vertex(entry, x0, y1, zFront).color(255,255,255,255).texture(0f, vBottom).overlay(overlay).light(light).normal(entry, 0, 1, 0);
    }

    private static void applyVanillaCapeRotation(MatrixStack matrices, PlayerEntityRenderState state) {
        // Flip the X tilt so it swings backward instead of forward, while keeping vanilla sway.
        float xRotDeg = -(6.0f + (state.field_53537 / 2.0f) + state.field_53536);
        float zRotDeg = -(state.field_53538 / 2.0f);
        float yRotDeg = 180.0f + (state.field_53538 / 2.0f);

        matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(180f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(xRotDeg));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(zRotDeg));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yRotDeg));
    }
}
