package com.odtheking.odin.features.impl.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class FloydCapeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public FloydCapeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float limbAngle, float limbDistance) {
        if (!FloydCape.isActiveFor(state.id) || state.isInvisible) return;

        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        poseStack.translate(0.0F, 0.0F, 0.18F);

        float xRot = -(6.0F + state.capeLean / 2.0F + state.capeLean2);
        float zRot = -(state.capeFlap / 2.0F);
        float yRot = 180.0F + state.capeFlap / 2.0F;
        poseStack.mulPose(Axis.YN.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(xRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(zRot));
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));

        collector.submitCustomGeometry(
            poseStack,
            RenderTypes.entityCutoutNoCull(FloydCape.texture()),
            (entry, consumer) -> drawCape(entry, consumer, light)
        );
        poseStack.popPose();
    }

    private static void drawCape(PoseStack.Pose pose, VertexConsumer consumer, int light) {
        int overlay = OverlayTexture.NO_OVERLAY;
        float aspect = FloydCape.aspectRatio();
        float width = Math.max(0.65F, Math.min(0.80F, 0.8F * (aspect / 2.0F)));
        float height = 1.02F;
        float thickness = 0.08F;
        float x0 = -width / 2.0F;
        float x1 = width / 2.0F;
        float y0 = 0.0F;
        float y1 = height;
        float zFront = -thickness / 2.0F;
        float zBack = thickness / 2.0F;

        vertex(consumer, pose, x0, y0, zBack, 1.0F, 0.0F, overlay, light, 0, 0, 1);
        vertex(consumer, pose, x1, y0, zBack, 0.0F, 0.0F, overlay, light, 0, 0, 1);
        vertex(consumer, pose, x1, y1, zBack, 0.0F, 1.0F, overlay, light, 0, 0, 1);
        vertex(consumer, pose, x0, y1, zBack, 1.0F, 1.0F, overlay, light, 0, 0, 1);

        vertex(consumer, pose, x0, y1, zFront, 0.0F, 1.0F, overlay, light, 0, 0, -1);
        vertex(consumer, pose, x1, y1, zFront, 1.0F, 1.0F, overlay, light, 0, 0, -1);
        vertex(consumer, pose, x1, y0, zFront, 1.0F, 0.0F, overlay, light, 0, 0, -1);
        vertex(consumer, pose, x0, y0, zFront, 0.0F, 0.0F, overlay, light, 0, 0, -1);

        vertex(consumer, pose, x0, y1, zBack, 0.0F, 1.0F, overlay, light, -1, 0, 0);
        vertex(consumer, pose, x0, y1, zFront, 0.0F, 1.0F, overlay, light, -1, 0, 0);
        vertex(consumer, pose, x0, y0, zFront, 0.0F, 0.0F, overlay, light, -1, 0, 0);
        vertex(consumer, pose, x0, y0, zBack, 0.0F, 0.0F, overlay, light, -1, 0, 0);

        vertex(consumer, pose, x1, y1, zFront, 1.0F, 1.0F, overlay, light, 1, 0, 0);
        vertex(consumer, pose, x1, y1, zBack, 1.0F, 1.0F, overlay, light, 1, 0, 0);
        vertex(consumer, pose, x1, y0, zBack, 1.0F, 0.0F, overlay, light, 1, 0, 0);
        vertex(consumer, pose, x1, y0, zFront, 1.0F, 0.0F, overlay, light, 1, 0, 0);

        vertex(consumer, pose, x0, y0, zFront, 0.0F, 0.0F, overlay, light, 0, -1, 0);
        vertex(consumer, pose, x1, y0, zFront, 1.0F, 0.0F, overlay, light, 0, -1, 0);
        vertex(consumer, pose, x1, y0, zBack, 1.0F, 0.0F, overlay, light, 0, -1, 0);
        vertex(consumer, pose, x0, y0, zBack, 0.0F, 0.0F, overlay, light, 0, -1, 0);

        vertex(consumer, pose, x0, y1, zBack, 0.0F, 1.0F, overlay, light, 0, 1, 0);
        vertex(consumer, pose, x1, y1, zBack, 1.0F, 1.0F, overlay, light, 0, 1, 0);
        vertex(consumer, pose, x1, y1, zFront, 1.0F, 1.0F, overlay, light, 0, 1, 0);
        vertex(consumer, pose, x0, y1, zFront, 0.0F, 1.0F, overlay, light, 0, 1, 0);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        consumer.addVertex(pose, x, y, z)
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
    }
}
