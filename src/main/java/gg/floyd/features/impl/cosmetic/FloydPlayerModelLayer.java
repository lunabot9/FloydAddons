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
import net.minecraft.resources.Identifier;

/**
 * Renders Floyd's bundled custom player models. Tung Tung Sahur is an original low-poly
 * recreation based on rocklee.ff's CC BY 4.0 Sketchfab reference model; the others follow the
 * same client-side-only replacement path so crouching and limb motion still come from vanilla.
 */
public final class FloydPlayerModelLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier WHITE_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/block/white_concrete.png");
    private static final int WOOD = 0xFFDE7A20;
    private static final int WOOD_DARK = 0xFF8B3E0D;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF17100B;
    private static final int SKIN = 0xFF704733;
    private static final int SKIN_LIGHT = 0xFF8A5B42;
    private static final int HAIR = 0xFF241A16;
    private static final int SWEATER = 0xFF171719;
    private static final int SWEATER_STRIPE = 0xFF77777B;
    private static final int PANTS = 0xFF09090B;
    private static final int JENNY_SKIN = 0xFFF8CAA7;
    private static final int JENNY_SHIRT = 0xFF322640;
    private static final int JENNY_TRIM = 0xFF171719;
    private static final int JENNY_PANTS = 0xFF161616;
    private static final int JENNY_SHOE = 0xFF161616;
    private static final int JENNY_SHADOW = 0xFF241D2D;

    public FloydPlayerModelLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float limbAngle, float limbDistance) {
        if (!FloydPlayerModel.isActiveFor(state.id) || state.isInvisible) return;

        if (FloydPlayerModel.isGeorgeFloydModel()) {
            submitGeorgeFloyd(poseStack, collector, light);
            return;
        }

        if (FloydPlayerModel.isJennyModel()) {
            submitJenny(poseStack, collector, light);
            return;
        }

        submitTungTung(poseStack, collector, light);
    }

    private void submitJenny(PoseStack poseStack, SubmitNodeCollector collector, int light) {
        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        JennyImportedModel.renderHead(poseStack, collector, light);
        poseStack.popPose();

        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        JennyImportedModel.renderBody(poseStack, collector, light);
        renderJennyBust(poseStack, collector, light);
        renderJennyBackside(poseStack, collector, light);
        poseStack.popPose();

        poseStack.pushPose();
        getParentModel().rightArm.translateAndRotate(poseStack);
        poseStack.translate(0.032F, -0.082F, 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(15.0F));
        JennyImportedModel.renderRightArm(poseStack, collector, light);
        poseStack.popPose();

        poseStack.pushPose();
        getParentModel().leftArm.translateAndRotate(poseStack);
        poseStack.translate(-0.032F, -0.082F, 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-15.0F));
        JennyImportedModel.renderLeftArm(poseStack, collector, light);
        poseStack.popPose();

        poseStack.pushPose();
        getParentModel().rightLeg.translateAndRotate(poseStack);
        JennyImportedModel.renderRightLeg(poseStack, collector, light);
        poseStack.popPose();

        poseStack.pushPose();
        getParentModel().leftLeg.translateAndRotate(poseStack);
        JennyImportedModel.renderLeftLeg(poseStack, collector, light);
        poseStack.popPose();
    }

    private void renderJennyBackside(PoseStack poseStack, SubmitNodeCollector collector, int light) {
        ellipsoid(poseStack, collector, light, -0.095F, 0.680F, 0.192F, 0.242F, 0.198F, 0.205F, JENNY_PANTS);
        ellipsoid(poseStack, collector, light, 0.095F, 0.680F, 0.192F, 0.242F, 0.198F, 0.205F, JENNY_PANTS);
    }

    private void renderJennyBust(PoseStack poseStack, SubmitNodeCollector collector, int light) {
        // Keep the bust part of Jenny's purple top while giving the otherwise flat imported
        // torso a rounded silhouette. The ellipsoids overlap the torso so they remain joined
        // during vanilla body animation instead of reading as separate floating pieces.
        ellipsoid(poseStack, collector, light, -0.106F, 0.192F, -0.132F, 0.159F, 0.135F, 0.146F, JENNY_SHIRT);
        ellipsoid(poseStack, collector, light, 0.106F, 0.192F, -0.132F, 0.159F, 0.135F, 0.146F, JENNY_SHIRT);

        // Two smaller exposed shapes sit above the larger clothed pair. They use Jenny's
        // existing skin tone and leave the center open instead of painting cleavage in front.
        ellipsoid(poseStack, collector, light, -0.057F, 0.133F, -0.137F, 0.101F, 0.086F, 0.107F, JENNY_SKIN);
        ellipsoid(poseStack, collector, light, 0.057F, 0.133F, -0.137F, 0.101F, 0.086F, 0.107F, JENNY_SKIN);
    }

    private void submitTungTung(PoseStack poseStack, SubmitNodeCollector collector, int light) {
        renderTungHead(poseStack, collector, light);

        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        // Use a much squarer torso profile so the body reads as a simple block under the head.
        profiledTube(poseStack, collector, light,
            new float[] {-0.07F, 0.02F, 0.20F, 0.38F, 0.58F},
            new float[] {0.22F, 0.23F, 0.23F, 0.23F, 0.22F},
            new float[] {0.16F, 0.17F, 0.17F, 0.17F, 0.16F},
            new float[] {0, 0, 0, 0, 0, 0}, WOOD);
        poseStack.popPose();

        renderArm(poseStack, collector, light, getParentModel().rightArm, true);
        renderArm(poseStack, collector, light, getParentModel().leftArm, false);
        renderLeg(poseStack, collector, light, getParentModel().rightLeg);
        renderLeg(poseStack, collector, light, getParentModel().leftLeg);
    }

    private void renderTungHead(PoseStack poseStack, SubmitNodeCollector collector, int light) {
        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        // Match the torso's squarer look so the head reads more like a block than a rounded log.
        profiledTube(poseStack, collector, light,
            new float[] {-0.59F, -0.52F, -0.36F, -0.20F, -0.07F},
            new float[] {0.16F, 0.22F, 0.22F, 0.22F, 0.18F},
            new float[] {0.12F, 0.17F, 0.17F, 0.17F, 0.13F},
            new float[] {0, 0, 0, 0, 0}, WOOD);

        // Expressive face on the front (-Z), based on the supplied reference.
        // Deep sockets and large circular eyes replace the previous eyebrow/cartoon-eye treatment.
        ellipsoid(poseStack, collector, light, -0.095F, -0.30F, -0.181F, 0.105F, 0.125F, 0.032F, WOOD_DARK);
        ellipsoid(poseStack, collector, light, 0.095F, -0.30F, -0.181F, 0.105F, 0.125F, 0.032F, WOOD_DARK);
        ellipsoid(poseStack, collector, light, -0.095F, -0.30F, -0.208F, 0.076F, 0.090F, 0.026F, WHITE);
        ellipsoid(poseStack, collector, light, 0.095F, -0.30F, -0.208F, 0.076F, 0.090F, 0.026F, WHITE);
        ellipsoid(poseStack, collector, light, -0.078F, -0.295F, -0.232F, 0.039F, 0.049F, 0.018F, BLACK);
        ellipsoid(poseStack, collector, light, 0.078F, -0.295F, -0.232F, 0.039F, 0.049F, 0.018F, BLACK);
        ellipsoid(poseStack, collector, light, -0.090F, -0.315F, -0.249F, 0.011F, 0.014F, 0.007F, WHITE);
        ellipsoid(poseStack, collector, light, 0.066F, -0.315F, -0.249F, 0.011F, 0.014F, 0.007F, WHITE);
        ellipsoid(poseStack, collector, light, 0.0F, -0.15F, -0.225F, 0.052F, 0.082F, 0.045F, WOOD);
        // Simple black smile: the middle sits lower than the corners in model-space (+Y is down).
        for (int i = -3; i <= 3; i++) {
            float x = i * 0.026F;
            float y = 0.052F - (i * i) * 0.005F;
            ellipsoid(poseStack, collector, light, x, y, -0.231F, 0.020F, 0.017F, 0.012F, BLACK);
        }
        poseStack.popPose();
    }

    private void submitGeorgeFloyd(PoseStack poseStack, SubmitNodeCollector collector, int light) {
        renderGeorgeHead(poseStack, collector, light);

        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        // Dark hood and a broad sweater torso.
        ellipsoid(poseStack, collector, light, 0.0F, -0.015F, 0.045F, 0.205F, 0.13F, 0.16F, SWEATER);
        profiledTube(poseStack, collector, light,
            new float[] {-0.02F, 0.08F, 0.58F, 0.66F},
            new float[] {0.24F, 0.28F, 0.25F, 0.22F},
            new float[] {0.145F, 0.16F, 0.15F, 0.135F},
            new float[] {0, 0, 0, 0}, SWEATER);
        // Repeating horizontal knit stripes continue from the chest to the sweater hem.
        sweaterBand(poseStack, collector, light, 0.13F, 0.20F, 0.279F, 0.161F);
        sweaterBand(poseStack, collector, light, 0.29F, 0.36F, 0.271F, 0.158F);
        sweaterBand(poseStack, collector, light, 0.45F, 0.52F, 0.260F, 0.154F);
        sweaterBand(poseStack, collector, light, 0.59F, 0.645F, 0.232F, 0.143F);
        poseStack.popPose();

        renderGeorgeArm(poseStack, collector, light, getParentModel().rightArm);
        renderGeorgeArm(poseStack, collector, light, getParentModel().leftArm);
        renderGeorgeLeg(poseStack, collector, light, getParentModel().rightLeg);
        renderGeorgeLeg(poseStack, collector, light, getParentModel().leftLeg);
    }

    private void renderGeorgeHead(PoseStack poseStack, SubmitNodeCollector collector, int light) {
        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        ellipsoid(poseStack, collector, light, 0.0F, -0.28F, 0.0F, 0.205F, 0.25F, 0.19F, SKIN);
        // One thin curved shell follows the head ellipsoid from crown to hairline. Avoid separate
        // disks or spheres here: from the front those read as a hat and oversized eyebrows.
        ellipsoidCap(poseStack, collector, light, 0.0F, -0.28F, 0.0F, 0.208F, 0.253F, 0.193F, HAIR);
        ellipsoid(poseStack, collector, light, -0.205F, -0.28F, 0.0F, 0.038F, 0.065F, 0.035F, SKIN);
        ellipsoid(poseStack, collector, light, 0.205F, -0.28F, 0.0F, 0.038F, 0.065F, 0.035F, SKIN);
        // Eyes, nose and mouth use restrained proportions based on the supplied portrait.
        ellipsoid(poseStack, collector, light, -0.074F, -0.31F, -0.181F, 0.047F, 0.034F, 0.018F, WHITE);
        ellipsoid(poseStack, collector, light, 0.074F, -0.31F, -0.181F, 0.047F, 0.034F, 0.018F, WHITE);
        ellipsoid(poseStack, collector, light, -0.064F, -0.308F, -0.198F, 0.018F, 0.021F, 0.010F, BLACK);
        ellipsoid(poseStack, collector, light, 0.064F, -0.308F, -0.198F, 0.018F, 0.021F, 0.010F, BLACK);
        ellipsoid(poseStack, collector, light, 0.0F, -0.215F, -0.198F, 0.045F, 0.070F, 0.040F, SKIN_LIGHT);
        // Separate upper and lower lips use the face's skin tone; their projection and the narrow
        // gap between them provide definition without the old black circular mouth.
        ellipsoid(poseStack, collector, light, 0.0F, -0.116F, -0.184F, 0.064F, 0.014F, 0.018F, SKIN);
        ellipsoid(poseStack, collector, light, 0.0F, -0.084F, -0.186F, 0.071F, 0.016F, 0.020F, SKIN);
        poseStack.popPose();
    }

    private static void sweaterBand(PoseStack poseStack, SubmitNodeCollector collector, int light, float top, float bottom, float radiusX, float radiusZ) {
        profiledTube(poseStack, collector, light,
            new float[] {top, bottom},
            new float[] {radiusX, radiusX},
            new float[] {radiusZ, radiusZ},
            new float[] {0, 0}, SWEATER_STRIPE);
    }

    private static void renderGeorgeArm(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm) {
        poseStack.pushPose();
        arm.translateAndRotate(poseStack);
        profiledTube(poseStack, collector, light,
            new float[] {-0.045F, 0.08F, 0.58F, 0.66F},
            new float[] {0.115F, 0.110F, 0.087F, 0.078F},
            new float[] {0.105F, 0.100F, 0.080F, 0.070F},
            new float[] {0, 0, 0, 0}, SWEATER);
        // Sleeve stripes align roughly with the torso bands and continue toward the cuff.
        armBand(poseStack, collector, light, 0.16F, 0.23F, 0.105F);
        armBand(poseStack, collector, light, 0.32F, 0.39F, 0.097F);
        armBand(poseStack, collector, light, 0.48F, 0.55F, 0.089F);
        ellipsoid(poseStack, collector, light, 0.0F, 0.715F, 0.0F, 0.088F, 0.105F, 0.082F, SKIN);
        poseStack.popPose();
    }

    private static void armBand(PoseStack poseStack, SubmitNodeCollector collector, int light, float top, float bottom, float radius) {
        profiledTube(poseStack, collector, light,
            new float[] {top, bottom},
            new float[] {radius, radius},
            new float[] {radius * 0.92F, radius * 0.92F},
            new float[] {0, 0}, SWEATER_STRIPE);
    }

    private static void renderGeorgeLeg(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart leg) {
        poseStack.pushPose();
        leg.translateAndRotate(poseStack);
        // Keep the vanilla leg pivot unchanged: its 0.75-block pivot plus this model's
        // 0.75-block leg/foot reaches the standard 1.5-block player ground plane.
        profiledTube(poseStack, collector, light,
            new float[] {-0.04F, 0.12F, 0.62F, 0.70F},
            new float[] {0.105F, 0.105F, 0.085F, 0.0F},
            new float[] {0.095F, 0.095F, 0.080F, 0.0F},
            new float[] {0, 0, 0, -0.08F}, PANTS);
        ellipsoid(poseStack, collector, light, 0.0F, 0.68F, -0.09F, 0.135F, 0.075F, 0.205F, PANTS);
        poseStack.popPose();
    }

    private static void renderArm(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm, boolean rightArm) {
        poseStack.pushPose();
        arm.translateAndRotate(poseStack);
        poseStack.translate(rightArm ? 0.035F : -0.035F, -0.09F, 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(rightArm ? 16.0F : -16.0F));
        // Shoulder, tapered forearm and rounded hand are one mesh, rather than stacked shapes.
        profiledTube(poseStack, collector, light,
            new float[] {-0.02F, 0.10F, 0.39F, 0.66F, 0.78F, 0.84F},
            new float[] {0.085F, 0.080F, 0.058F, 0.047F, 0.060F, 0.0F},
            new float[] {0.080F, 0.075F, 0.055F, 0.044F, 0.055F, 0.0F},
            new float[] {0, 0, 0, 0, 0, 0}, WOOD);
        // One rounded hand sphere, deliberately wider than the 0.047-wide forearm.
        ellipsoid(poseStack, collector, light, 0.0F, 0.80F, -0.004F, 0.082F, 0.092F, 0.082F, WOOD);
        poseStack.popPose();
    }

    private static void renderLeg(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart leg) {
        poseStack.pushPose();
        leg.translateAndRotate(poseStack);
        // Thigh, knee, shin, ankle and broad forward foot remain a single continuous mesh.
        profiledTube(poseStack, collector, light,
            new float[] {-0.19F, -0.08F, 0.25F, 0.34F, 0.55F, 0.695F, 0.765F},
            new float[] {0.078F, 0.074F, 0.070F, 0.082F, 0.055F, 0.145F, 0.0F},
            new float[] {0.075F, 0.071F, 0.068F, 0.078F, 0.055F, 0.190F, 0.0F},
            new float[] {0, 0, 0, 0, -0.015F, -0.105F, -0.19F}, WOOD);
        // Three forward-facing rounded toes, attached to the broad integrated foot.
        ellipsoid(poseStack, collector, light, -0.075F, 0.710F, -0.245F, 0.035F, 0.032F, 0.105F, WOOD);
        ellipsoid(poseStack, collector, light, 0.0F, 0.717F, -0.270F, 0.038F, 0.033F, 0.120F, WOOD);
        ellipsoid(poseStack, collector, light, 0.075F, 0.710F, -0.245F, 0.035F, 0.032F, 0.105F, WOOD);
        poseStack.popPose();
    }

    private static void profiledTube(PoseStack stack, SubmitNodeCollector collector, int light, float[] ys, float[] radiiX, float[] radiiZ, float[] centersZ, int color) {
        collector.submitCustomGeometry(stack, RenderTypes.entityCutout(WHITE_TEXTURE),
            (pose, consumer) -> drawProfiledTube(pose, consumer, light, ys, radiiX, radiiZ, centersZ, color));
    }

    private static void ellipsoid(PoseStack stack, SubmitNodeCollector collector, int light, float x, float y, float z, float sx, float sy, float sz, int color) {
        stack.pushPose();
        stack.translate(x, y, z);
        stack.scale(sx, sy, sz);
        collector.submitCustomGeometry(stack, RenderTypes.entityCutout(WHITE_TEXTURE),
            (pose, consumer) -> drawSphere(pose, consumer, light, color));
        stack.popPose();
    }

    private static void ellipsoidCap(PoseStack stack, SubmitNodeCollector collector, int light, float x, float y, float z, float sx, float sy, float sz, int color) {
        stack.pushPose();
        stack.translate(x, y, z);
        stack.scale(sx, sy, sz);
        collector.submitCustomGeometry(stack, RenderTypes.entityCutout(WHITE_TEXTURE),
            (pose, consumer) -> drawSphereCap(pose, consumer, light, color));
        stack.popPose();
    }

    private static void box(PoseStack stack, SubmitNodeCollector collector, int light, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int color) {
        collector.submitCustomGeometry(stack, RenderTypes.entityCutout(WHITE_TEXTURE),
            (pose, consumer) -> drawBox(pose, consumer, light, minX, minY, minZ, maxX, maxY, maxZ, color));
    }

    private static void drawSphere(PoseStack.Pose pose, VertexConsumer consumer, int light, int color) {
        int latitudes = 8;
        int longitudes = 16;
        for (int lat = 0; lat < latitudes; lat++) {
            float a0 = (float) (-Math.PI / 2.0 + Math.PI * lat / latitudes);
            float a1 = (float) (-Math.PI / 2.0 + Math.PI * (lat + 1) / latitudes);
            float y0 = (float) Math.sin(a0), y1 = (float) Math.sin(a1);
            float r0 = (float) Math.cos(a0), r1 = (float) Math.cos(a1);
            for (int lon = 0; lon < longitudes; lon++) {
                float b0 = (float) (2.0 * Math.PI * lon / longitudes);
                float b1 = (float) (2.0 * Math.PI * (lon + 1) / longitudes);
                float x00 = r0 * (float) Math.cos(b0), z00 = r0 * (float) Math.sin(b0);
                float x01 = r0 * (float) Math.cos(b1), z01 = r0 * (float) Math.sin(b1);
                float x10 = r1 * (float) Math.cos(b0), z10 = r1 * (float) Math.sin(b0);
                float x11 = r1 * (float) Math.cos(b1), z11 = r1 * (float) Math.sin(b1);
                vertex(consumer, pose, x00, y0, z00, 0, 0, light, color, x00, y0, z00);
                vertex(consumer, pose, x01, y0, z01, 1, 0, light, color, x01, y0, z01);
                vertex(consumer, pose, x11, y1, z11, 1, 1, light, color, x11, y1, z11);
                vertex(consumer, pose, x10, y1, z10, 0, 1, light, color, x10, y1, z10);
            }
        }
    }

    private static void drawSphereCap(PoseStack.Pose pose, VertexConsumer consumer, int light, int color) {
        int latitudes = 5;
        int longitudes = 16;
        float crown = (float) (-Math.PI / 2.0);
        float hairline = (float) Math.asin(-0.5);
        for (int lat = 0; lat < latitudes; lat++) {
            float a0 = crown + (hairline - crown) * lat / latitudes;
            float a1 = crown + (hairline - crown) * (lat + 1) / latitudes;
            float y0 = (float) Math.sin(a0), y1 = (float) Math.sin(a1);
            float r0 = (float) Math.cos(a0), r1 = (float) Math.cos(a1);
            for (int lon = 0; lon < longitudes; lon++) {
                float b0 = (float) (2.0 * Math.PI * lon / longitudes);
                float b1 = (float) (2.0 * Math.PI * (lon + 1) / longitudes);
                float x00 = r0 * (float) Math.cos(b0), z00 = r0 * (float) Math.sin(b0);
                float x01 = r0 * (float) Math.cos(b1), z01 = r0 * (float) Math.sin(b1);
                float x10 = r1 * (float) Math.cos(b0), z10 = r1 * (float) Math.sin(b0);
                float x11 = r1 * (float) Math.cos(b1), z11 = r1 * (float) Math.sin(b1);
                vertex(consumer, pose, x00, y0, z00, 0, 0, light, color, x00, y0, z00);
                vertex(consumer, pose, x01, y0, z01, 1, 0, light, color, x01, y0, z01);
                vertex(consumer, pose, x11, y1, z11, 1, 1, light, color, x11, y1, z11);
                vertex(consumer, pose, x10, y1, z10, 0, 1, light, color, x10, y1, z10);
            }
        }
    }

    private static void drawProfiledTube(PoseStack.Pose pose, VertexConsumer consumer, int light, float[] ys, float[] radiiX, float[] radiiZ, float[] centersZ, int color) {
        int segments = 16;
        for (int r = 0; r < ys.length - 1; r++) {
            for (int i = 0; i < segments; i++) {
                float a0 = (float) (2.0 * Math.PI * i / segments);
                float a1 = (float) (2.0 * Math.PI * (i + 1) / segments);
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                vertex(consumer, pose, radiiX[r] * c0, ys[r], centersZ[r] + radiiZ[r] * s0, 0, 0, light, color, c0, 0, s0);
                vertex(consumer, pose, radiiX[r] * c1, ys[r], centersZ[r] + radiiZ[r] * s1, 1, 0, light, color, c1, 0, s1);
                vertex(consumer, pose, radiiX[r + 1] * c1, ys[r + 1], centersZ[r + 1] + radiiZ[r + 1] * s1, 1, 1, light, color, c1, 0, s1);
                vertex(consumer, pose, radiiX[r + 1] * c0, ys[r + 1], centersZ[r + 1] + radiiZ[r + 1] * s0, 0, 1, light, color, c0, 0, s0);
            }
        }

        // Non-zero end rings are capped as part of this same mesh. This gives the trunk a flat
        // underside that overlaps the thighs instead of tapering to a point above them.
        capRing(pose, consumer, light, color, ys[0], radiiX[0], radiiZ[0], centersZ[0], -1.0F, segments);
        int last = ys.length - 1;
        capRing(pose, consumer, light, color, ys[last], radiiX[last], radiiZ[last], centersZ[last], 1.0F, segments);
    }

    private static void drawBox(PoseStack.Pose pose, VertexConsumer consumer, int light, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int color) {
        quad(consumer, pose, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, light, color, 0, 0, -1);
        quad(consumer, pose, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, light, color, 0, 0, 1);
        quad(consumer, pose, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, light, color, -1, 0, 0);
        quad(consumer, pose, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, light, color, 1, 0, 0);
        quad(consumer, pose, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ, light, color, 0, -1, 0);
        quad(consumer, pose, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, light, color, 0, 1, 0);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             int light, int color, float nx, float ny, float nz) {
        vertex(consumer, pose, x0, y0, z0, 0, 0, light, color, nx, ny, nz);
        vertex(consumer, pose, x1, y1, z1, 1, 0, light, color, nx, ny, nz);
        vertex(consumer, pose, x2, y2, z2, 1, 1, light, color, nx, ny, nz);
        vertex(consumer, pose, x3, y3, z3, 0, 1, light, color, nx, ny, nz);
    }

    private static void capRing(PoseStack.Pose pose, VertexConsumer consumer, int light, int color, float y, float radiusX, float radiusZ, float centerZ, float normalY, int segments) {
        if (radiusX <= 0.0F || radiusZ <= 0.0F) return;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (2.0 * Math.PI * i / segments);
            float a1 = (float) (2.0 * Math.PI * (i + 1) / segments);
            vertex(consumer, pose, 0, y, centerZ, 0.5F, 0.5F, light, color, 0, normalY, 0);
            vertex(consumer, pose, radiusX * (float) Math.cos(a0), y, centerZ + radiusZ * (float) Math.sin(a0), 0, 1, light, color, 0, normalY, 0);
            vertex(consumer, pose, radiusX * (float) Math.cos(a1), y, centerZ + radiusZ * (float) Math.sin(a1), 1, 1, light, color, 0, normalY, 0);
            vertex(consumer, pose, 0, y, centerZ, 0.5F, 0.5F, light, color, 0, normalY, 0);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light, int color, float nx, float ny, float nz) {
        consumer.addVertex(pose, x, y, z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
    }
}
