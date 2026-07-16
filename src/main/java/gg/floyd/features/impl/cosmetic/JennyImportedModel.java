package gg.floyd.features.impl.cosmetic;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

final class JennyImportedModel {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("floydaddons", "textures/entity/player_model/jenny_dressed.png");
    private static final String MODEL_RESOURCE = "assets/floydaddons/player_models/jenny_dressed.json";
    private static final Gson GSON = new Gson();
    private static final ModelData MODEL = load();
    private static final float TEXTURE_SIZE = 64.0F;
    private static final BoxData[] RIGHT_ARM = new BoxData[] {
        new BoxData(-0.125F, 0.125F, -0.125F, 0.25F, -0.125F, 0.125F,
            uv(36, 52, 4, 6), uv(32, 52, 4, 6), uv(44, 52, 4, 6), uv(40, 52, 4, 6), uv(40, 48, -4, 4), uv(44, 52, -4, -4)),
        new BoxData(-0.125F, 0.125F, 0.25F, 0.625F, -0.125F, 0.125F,
            uv(40, 58, -4, 6), uv(40, 58, 4, 6), uv(48, 58, -4, 6), uv(32, 58, 4, 6), uv(36, 48, 4, 4), uv(40, 52, 4, -4))
    };
    private static final BoxData[] LEFT_ARM = new BoxData[] {
        new BoxData(-0.125F, 0.125F, -0.125F, 0.25F, -0.125F, 0.125F,
            uv(36, 52, 4, 6), uv(36, 52, -4, 6), uv(44, 52, 4, 6), uv(40, 52, 4, 6), uv(40, 48, -4, 4), uv(44, 52, -4, -4)),
        new BoxData(-0.125F, 0.125F, 0.25F, 0.625F, -0.125F, 0.125F,
            uv(36, 58, 4, 6), uv(36, 58, -4, 6), uv(44, 58, 4, 6), uv(44, 58, -4, 6), uv(40, 48, -4, 4), uv(44, 52, -4, -4))
    };
    private static final BoxData[] RIGHT_LEG = new BoxData[] {
        new BoxData(-0.125F, 0.125F, 0.0F, 0.375F, -0.125F, 0.125F,
            uv(4, 21, 4, 5), uv(0, 21, 4, 5), uv(12, 21, 4, 5), uv(8, 21, 4, 5), uv(4, 17, 4, 4), null),
        new BoxData(-0.125F, 0.125F, 0.375F, 0.75F, -0.125F, 0.125F,
            uv(4, 26, 4, 6), uv(0, 26, 4, 6), uv(12, 26, 4, 6), uv(8, 26, 4, 6), uv(4, 17, 4, 4), uv(8, 21, 4, -4))
    };
    private static final BoxData[] LEFT_LEG = new BoxData[] {
        new BoxData(-0.125F, 0.125F, 0.0F, 0.375F, -0.125F, 0.125F,
            uv(8, 21, -4, 5), uv(12, 21, -4, 5), uv(16, 21, -4, 5), uv(4, 21, -4, 5), uv(8, 17, -4, 4), null),
        new BoxData(-0.125F, 0.125F, 0.375F, 0.75F, -0.125F, 0.125F,
            uv(8, 26, -4, 6), uv(12, 26, -4, 6), uv(16, 26, -4, 6), uv(4, 26, -4, 6), uv(8, 17, -4, 4), uv(12, 21, -4, -4))
    };

    private JennyImportedModel() {}

    static void renderHead(PoseStack stack, SubmitNodeCollector collector, int light) {
        renderSegment("head", stack, collector, light);
    }

    static void renderBody(PoseStack stack, SubmitNodeCollector collector, int light) {
        renderSegment("body", stack, collector, light);
    }

    static void renderRightArm(PoseStack stack, SubmitNodeCollector collector, int light) {
        renderSegment("right_arm", stack, collector, light);
    }

    static void renderLeftArm(PoseStack stack, SubmitNodeCollector collector, int light) {
        renderMirroredSegment("right_arm", stack, collector, light);
    }

    static void renderRightLeg(PoseStack stack, SubmitNodeCollector collector, int light) {
        renderSegment("right_leg", stack, collector, light);
    }

    static void renderLeftLeg(PoseStack stack, SubmitNodeCollector collector, int light) {
        renderSegment("left_leg", stack, collector, light);
    }

    private static void renderSegment(String name, PoseStack stack, SubmitNodeCollector collector, int light) {
        List<QuadData> quads = MODEL.segments.get(name);
        if (quads == null || quads.isEmpty()) return;
        collector.submitCustomGeometry(stack, RenderTypes.entityCutout(TEXTURE), (pose, consumer) -> drawSegment(pose, consumer, light, quads));
    }

    private static void renderMirroredSegment(String name, PoseStack stack, SubmitNodeCollector collector, int light) {
        stack.pushPose();
        stack.scale(-1.0F, 1.0F, 1.0F);
        renderSegment(name, stack, collector, light);
        stack.popPose();
    }

    private static void drawSegment(PoseStack.Pose pose, VertexConsumer consumer, int light, List<QuadData> quads) {
        for (QuadData quad : quads) {
            if (quad.positions == null || quad.positions.length != 12 || quad.uv == null || quad.uv.length != 8 || quad.normal == null || quad.normal.length != 3) {
                continue;
            }
            emitQuad(pose, consumer, light, quad, 0, 1, 2, 3, quad.normal[0], quad.normal[1], quad.normal[2]);
            emitQuad(pose, consumer, light, quad, 3, 2, 1, 0, -quad.normal[0], -quad.normal[1], -quad.normal[2]);
        }
    }

    private static void renderBoxes(PoseStack stack, SubmitNodeCollector collector, int light, BoxData[] boxes) {
        collector.submitCustomGeometry(stack, RenderTypes.entityCutout(TEXTURE), (pose, consumer) -> {
            for (BoxData box : boxes) {
                drawBox(pose, consumer, light, box);
            }
        });
    }

    private static void drawBox(PoseStack.Pose pose, VertexConsumer consumer, int light, BoxData box) {
        quad(pose, consumer, light,
            box.minX, box.minY, box.minZ,
            box.maxX, box.minY, box.minZ,
            box.maxX, box.maxY, box.minZ,
            box.minX, box.maxY, box.minZ,
            box.north, 0.0F, 0.0F, -1.0F);
        quad(pose, consumer, light,
            box.maxX, box.minY, box.maxZ,
            box.minX, box.minY, box.maxZ,
            box.minX, box.maxY, box.maxZ,
            box.maxX, box.maxY, box.maxZ,
            box.south, 0.0F, 0.0F, 1.0F);
        quad(pose, consumer, light,
            box.minX, box.minY, box.maxZ,
            box.minX, box.minY, box.minZ,
            box.minX, box.maxY, box.minZ,
            box.minX, box.maxY, box.maxZ,
            box.east, -1.0F, 0.0F, 0.0F);
        quad(pose, consumer, light,
            box.maxX, box.minY, box.minZ,
            box.maxX, box.minY, box.maxZ,
            box.maxX, box.maxY, box.maxZ,
            box.maxX, box.maxY, box.minZ,
            box.west, 1.0F, 0.0F, 0.0F);
        quad(pose, consumer, light,
            box.minX, box.minY, box.maxZ,
            box.maxX, box.minY, box.maxZ,
            box.maxX, box.minY, box.minZ,
            box.minX, box.minY, box.minZ,
            box.up, 0.0F, -1.0F, 0.0F);
        quad(pose, consumer, light,
            box.minX, box.maxY, box.minZ,
            box.maxX, box.maxY, box.minZ,
            box.maxX, box.maxY, box.maxZ,
            box.minX, box.maxY, box.maxZ,
            box.down, 0.0F, 1.0F, 0.0F);
    }

    private static void quad(
        PoseStack.Pose pose,
        VertexConsumer consumer,
        int light,
        float x0, float y0, float z0,
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        float x3, float y3, float z3,
        FaceUv uv,
        float nx, float ny, float nz
    ) {
        if (uv == null) {
            return;
        }
        emit(pose, consumer, light, x0, y0, z0, uv.u0, uv.v0, nx, ny, nz);
        emit(pose, consumer, light, x1, y1, z1, uv.u1, uv.v0, nx, ny, nz);
        emit(pose, consumer, light, x2, y2, z2, uv.u1, uv.v1, nx, ny, nz);
        emit(pose, consumer, light, x3, y3, z3, uv.u0, uv.v1, nx, ny, nz);
        emit(pose, consumer, light, x3, y3, z3, uv.u0, uv.v1, -nx, -ny, -nz);
        emit(pose, consumer, light, x2, y2, z2, uv.u1, uv.v1, -nx, -ny, -nz);
        emit(pose, consumer, light, x1, y1, z1, uv.u1, uv.v0, -nx, -ny, -nz);
        emit(pose, consumer, light, x0, y0, z0, uv.u0, uv.v0, -nx, -ny, -nz);
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer consumer, int light, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        consumer.addVertex(pose, x, y, z)
            .setColor(0xFFFFFFFF)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
    }

    private static FaceUv uv(float u, float v, float width, float height) {
        return new FaceUv(u / TEXTURE_SIZE, v / TEXTURE_SIZE, (u + width) / TEXTURE_SIZE, (v + height) / TEXTURE_SIZE);
    }

    private static void emitQuad(PoseStack.Pose pose, VertexConsumer consumer, int light, QuadData quad, int i0, int i1, int i2, int i3, float nx, float ny, float nz) {
        emitVertex(pose, consumer, light, quad, i0, nx, ny, nz);
        emitVertex(pose, consumer, light, quad, i1, nx, ny, nz);
        emitVertex(pose, consumer, light, quad, i2, nx, ny, nz);
        emitVertex(pose, consumer, light, quad, i3, nx, ny, nz);
    }

    private static void emitVertex(PoseStack.Pose pose, VertexConsumer consumer, int light, QuadData quad, int index, float nx, float ny, float nz) {
        int positionIndex = index * 3;
        int uvIndex = index * 2;
        consumer.addVertex(pose, quad.positions[positionIndex], quad.positions[positionIndex + 1], quad.positions[positionIndex + 2])
            .setColor(0xFFFFFFFF)
            .setUv(quad.uv[uvIndex], quad.uv[uvIndex + 1])
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
    }

    private static ModelData load() {
        InputStream stream = JennyImportedModel.class.getClassLoader().getResourceAsStream(MODEL_RESOURCE);
        if (stream == null) throw new IllegalStateException("Missing resource " + MODEL_RESOURCE);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            ModelData model = GSON.fromJson(reader, ModelData.class);
            if (model == null || model.segments == null) throw new IllegalStateException("Invalid Jenny model resource");
            return model;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load Jenny model resource", exception);
        }
    }

    private static final class ModelData {
        Map<String, List<QuadData>> segments;
    }

    private static final class QuadData {
        float[] positions;
        float[] uv;
        float[] normal;
    }

    private static final class BoxData {
        final float minX;
        final float maxX;
        final float minY;
        final float maxY;
        final float minZ;
        final float maxZ;
        final FaceUv north;
        final FaceUv east;
        final FaceUv south;
        final FaceUv west;
        final FaceUv up;
        final FaceUv down;

        private BoxData(float minX, float maxX, float minY, float maxY, float minZ, float maxZ, FaceUv north, FaceUv east, FaceUv south, FaceUv west, FaceUv up, FaceUv down) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.north = north;
            this.east = east;
            this.south = south;
            this.west = west;
            this.up = up;
            this.down = down;
        }
    }

    private static final class FaceUv {
        final float u0;
        final float v0;
        final float u1;
        final float v1;

        private FaceUv(float u0, float v0, float u1, float v1) {
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
        }
    }
}
