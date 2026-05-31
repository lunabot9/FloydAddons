package gg.floyd.features.impl.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class FloydConeHatLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final int SEGMENTS = 64;

    public FloydConeHatLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float limbAngle, float limbDistance) {
        if (!FloydConeHat.isActiveFor(state.id) || state.isInvisible) return;

        poseStack.pushPose();
        ModelPart head = getParentModel().head;
        head.translateAndRotate(poseStack);
        poseStack.mulPose(Axis.ZP.rotation(-head.zRot));
        poseStack.mulPose(Axis.XP.rotation(-head.xRot));
        poseStack.translate(0.0F, FloydConeHat.yOffset(), 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(FloydConeHat.currentRotation()));

        collector.submitCustomGeometry(
            poseStack,
            RenderTypes.entityCutoutNoCull(FloydConeHat.texture()),
            (entry, consumer) -> drawCone(entry, consumer, light, FloydConeHat.height(), FloydConeHat.radius())
        );
        poseStack.popPose();
    }

    private static void drawCone(PoseStack.Pose pose, VertexConsumer consumer, int light, float height, float radius) {
        int overlay = OverlayTexture.NO_OVERLAY;
        float apexY = -height;
        float invSlant = 1.0F / (float) Math.sqrt(height * height + radius * radius);
        float horizScale = height * invSlant;
        float vertScale = radius * invSlant;

        for (int i = 0; i < SEGMENTS; i++) {
            float angle1 = (float) (2.0 * Math.PI * i / SEGMENTS);
            float angle2 = (float) (2.0 * Math.PI * (i + 1) / SEGMENTS);
            float x1 = (float) (radius * Math.cos(angle1));
            float z1 = (float) (radius * Math.sin(angle1));
            float x2 = (float) (radius * Math.cos(angle2));
            float z2 = (float) (radius * Math.sin(angle2));
            float u1 = (float) i / SEGMENTS;
            float u2 = (float) (i + 1) / SEGMENTS;
            float midAngle = (angle1 + angle2) / 2.0F;
            float nx = (float) Math.cos(midAngle) * horizScale;
            float ny = vertScale;
            float nz = (float) Math.sin(midAngle) * horizScale;

            vertex(consumer, pose, 0.0F, apexY, 0.0F, u1, 0.0F, overlay, light, nx, ny, nz);
            vertex(consumer, pose, 0.0F, apexY, 0.0F, u2, 0.0F, overlay, light, nx, ny, nz);
            vertex(consumer, pose, x2, 0.0F, z2, u2, 1.0F, overlay, light, nx, ny, nz);
            vertex(consumer, pose, x1, 0.0F, z1, u1, 1.0F, overlay, light, nx, ny, nz);
        }
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
