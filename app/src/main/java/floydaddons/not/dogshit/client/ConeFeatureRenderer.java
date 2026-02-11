package floydaddons.not.dogshit.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

public class ConeFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private static final int SEGMENTS = 64;

    public ConeFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (!RenderConfig.isFloydHatEnabled()) return;
        if (state.invisible) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || state.id != mc.player.getId()) return;

        float height = RenderConfig.getConeHatHeight();
        float radius = RenderConfig.getConeHatRadius();
        float yOffset = RenderConfig.getConeHatYOffset();

        float rotation = RenderConfig.getConeHatRotation();

        matrices.push();
        getContextModel().head.applyTransform(matrices);
        matrices.translate(0.0f, yOffset, 0.0f);
        if (rotation != 0) {
            matrices.multiply(new Quaternionf().rotateY((float) Math.toRadians(rotation)));
        }

        Identifier texture = ConeHatManager.getTexture(mc);
        RenderLayer renderLayer = RenderLayer.getEntityCutoutNoCull(texture);

        queue.submitCustom(matrices, renderLayer, (entry, consumer) -> {
            drawCone(entry, consumer, light, height, radius);
        });

        matrices.pop();
    }

    private static void drawCone(MatrixStack.Entry entry, VertexConsumer consumer, int light,
                                 float height, float radius) {
        float apexY = -height;
        int overlay = OverlayTexture.DEFAULT_UV;
        float invSlant = 1.0f / (float) Math.sqrt(height * height + radius * radius);
        float horizScale = height * invSlant;
        float vertScale = radius * invSlant;

        for (int i = 0; i < SEGMENTS; i++) {
            float angle1 = (float) (2 * Math.PI * i / SEGMENTS);
            float angle2 = (float) (2 * Math.PI * (i + 1) / SEGMENTS);

            float x1 = (float) (radius * Math.cos(angle1));
            float z1 = (float) (radius * Math.sin(angle1));
            float x2 = (float) (radius * Math.cos(angle2));
            float z2 = (float) (radius * Math.sin(angle2));

            float u1 = (float) i / SEGMENTS;
            float u2 = (float) (i + 1) / SEGMENTS;

            float midAngle = (angle1 + angle2) / 2.0f;
            float nx = (float) Math.cos(midAngle) * horizScale;
            float ny = vertScale;
            float nz = (float) Math.sin(midAngle) * horizScale;

            consumer.vertex(entry, 0, apexY, 0)
                    .color(255, 255, 255, 255)
                    .texture(u1, 0)
                    .overlay(overlay)
                    .light(light)
                    .normal(entry, nx, ny, nz);

            consumer.vertex(entry, 0, apexY, 0)
                    .color(255, 255, 255, 255)
                    .texture(u2, 0)
                    .overlay(overlay)
                    .light(light)
                    .normal(entry, nx, ny, nz);

            consumer.vertex(entry, x2, 0, z2)
                    .color(255, 255, 255, 255)
                    .texture(u2, 1)
                    .overlay(overlay)
                    .light(light)
                    .normal(entry, nx, ny, nz);

            consumer.vertex(entry, x1, 0, z1)
                    .color(255, 255, 255, 255)
                    .texture(u1, 1)
                    .overlay(overlay)
                    .light(light)
                    .normal(entry, nx, ny, nz);
        }
    }
}
