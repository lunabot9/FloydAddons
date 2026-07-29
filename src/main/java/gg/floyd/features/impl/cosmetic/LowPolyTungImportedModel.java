package gg.floyd.features.impl.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OBJ-backed low-poly Tung player model adapted from the public scoliossis Model Modifier example
 * pack. Only the asset parser and render math were ported; no executable gameplay/account code was
 * imported from that repository.
 */
public final class LowPolyTungImportedModel {
    private static final String MODEL_RESOURCE = "assets/floydaddons/player_models/low_poly_tung.obj";
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
        "floydaddons", "textures/entity/player_model/low_poly_tung.png"
    );
    private static final Vec3 HELD_ITEM_OFFSET = new Vec3(0.08, -0.15, -0.11);
    private static final Vector3f ENTITY_OFFSET = new Vector3f(0.0F, -1.501F, 0.0F);
    private static final String BODY = "body";
    private static final String RIGHT_ARM = "right_arm";
    private static final String LEFT_ARM = "left_arm";
    private static final String RIGHT_LEG = "right_leg";
    private static final String LEFT_LEG = "left_leg";
    private static final String BASE_ITEM = "baseitem";
    private static final Model MODEL = load();

    private LowPolyTungImportedModel() {}

    public static Vec3 heldItemOffset(HumanoidArm arm) {
        return arm == HumanoidArm.LEFT
            ? new Vec3(-HELD_ITEM_OFFSET.x, HELD_ITEM_OFFSET.y, HELD_ITEM_OFFSET.z)
            : HELD_ITEM_OFFSET;
    }

    static void render(PoseStack poseStack, SubmitNodeCollector collector, int light, PlayerModel model) {
        renderPart(poseStack, collector, light, model.body, BODY);
        renderPart(poseStack, collector, light, model.leftArm, LEFT_ARM);
        renderPart(poseStack, collector, light, model.rightArm, RIGHT_ARM);
        renderPart(poseStack, collector, light, model.rightArm, BASE_ITEM);
        renderPart(poseStack, collector, light, model.leftLeg, LEFT_LEG);
        renderPart(poseStack, collector, light, model.rightLeg, RIGHT_LEG);
    }

    public static void renderFirstPersonArm(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                            ModelPart arm, HumanoidArm armSide) {
        renderPart(poseStack, collector, light, arm, armSide == HumanoidArm.RIGHT ? RIGHT_ARM : LEFT_ARM);
        if (armSide == HumanoidArm.RIGHT) {
            renderPart(poseStack, collector, light, arm, BASE_ITEM);
        }
    }

    private static void renderPart(PoseStack stack, SubmitNodeCollector collector, int light, ModelPart part, String partName) {
        List<Face> faces = MODEL.faces.get(partName);
        if (faces == null || faces.isEmpty()) return;

        Vector3f origin = new Vector3f(part.x, part.y, part.z).div(16.0F);
        stack.pushPose();
        part.translateAndRotate(stack);
        collector.submitCustomGeometry(
            stack,
            RenderTypes.entityCutout(TEXTURE),
            (pose, consumer) -> emitFaces(pose, consumer, light, faces, origin)
        );
        stack.popPose();
    }

    private static void emitFaces(PoseStack.Pose pose, VertexConsumer consumer, int light, List<Face> faces, Vector3f origin) {
        for (Face face : faces) {
            Triangle positions = face.positions.negate().subtract(origin).subtract(ENTITY_OFFSET);
            emitVertex(pose, consumer, light, positions.a, face.uvs.a, face.normals.a);
            emitVertex(pose, consumer, light, positions.b, face.uvs.b, face.normals.b);
            emitVertex(pose, consumer, light, positions.c, face.uvs.c, face.normals.c);
            emitVertex(pose, consumer, light, positions.c, face.uvs.c, face.normals.c);
        }
    }

    private static void emitVertex(PoseStack.Pose pose, VertexConsumer consumer, int light,
                                   Vector3f position, Vector3f uv, Vector3f normal) {
        consumer.addVertex(pose, position.x, position.y, position.z)
            .setColor(0xFFFFFFFF)
            .setUv(uv.x, 1.0F - uv.y)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, normal.x, normal.y, normal.z);
    }

    private static Model load() {
        try (InputStream stream = LowPolyTungImportedModel.class.getClassLoader().getResourceAsStream(MODEL_RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing resource " + MODEL_RESOURCE);
            Model model = parse(stream);
            scale(model.faces, new Vec3(1.5, 2.0, 1.3));
            return model;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load Low Poly Tung model", exception);
        }
    }

    private static Model parse(InputStream stream) throws Exception {
        HashMap<String, List<Face>> faces = new HashMap<>();
        ArrayList<Vector3f> vertices = new ArrayList<>();
        ArrayList<Vector3f> uvs = new ArrayList<>();
        ArrayList<Vector3f> normals = new ArrayList<>();
        String currentPart = "";
        faces.put(currentPart, new ArrayList<>());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                line = line.replaceAll("\\s+", " ");

                String[] split = line.split(" ");
                switch (split[0]) {
                    case "o" -> {
                        currentPart = split.length > 1 ? split[1] : "";
                        faces.putIfAbsent(currentPart, new ArrayList<>());
                    }
                    case "v" -> vertices.add(parseVector(split));
                    case "vt" -> uvs.add(parseVector(split));
                    case "vn" -> normals.add(parseVector(split));
                    case "f" -> triangulateFace(split, vertices, uvs, normals, faces.get(currentPart));
                    default -> { }
                }
            }
        }

        return new Model(faces);
    }

    private static void triangulateFace(String[] split, List<Vector3f> vertices, List<Vector3f> uvs,
                                        List<Vector3f> normals, List<Face> faces) {
        for (int i = 2; i < split.length - 1; i++) {
            String[] triangleParts = new String[] {split[1], split[i], split[i + 1]};
            faces.add(new Face(
                triangle(vertices, triangleParts, 0),
                triangle(uvs, triangleParts, 1),
                triangle(normals, triangleParts, 2)
            ));
        }
    }

    private static Vector3f parseVector(String[] split) {
        return new Vector3f(
            Float.parseFloat(split[1]),
            Float.parseFloat(split[2]),
            split.length >= 4 ? Float.parseFloat(split[3]) : 0.0F
        );
    }

    private static Triangle triangle(List<Vector3f> values, String[] parts, int elementIndex) {
        if (values.isEmpty()) return Triangle.EMPTY;
        int a = faceIndex(parts[0], elementIndex);
        int b = faceIndex(parts[1], elementIndex);
        int c = faceIndex(parts[2], elementIndex);
        return new Triangle(
            values.get(Math.min(a, values.size() - 1)),
            values.get(Math.min(b, values.size() - 1)),
            values.get(Math.min(c, values.size() - 1))
        );
    }

    private static int faceIndex(String face, int elementIndex) {
        String[] elements = face.split("/");
        if (elementIndex >= elements.length || elements[elementIndex].isBlank()) return 0;
        return Math.max(0, Integer.parseInt(elements[elementIndex]) - 1);
    }

    private static void scale(Map<String, List<Face>> faces, Vec3 targetSize) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (List<Face> partFaces : faces.values()) {
            for (Face face : partFaces) {
                minX = Math.min(minX, face.positions.minX());
                minY = Math.min(minY, face.positions.minY());
                minZ = Math.min(minZ, face.positions.minZ());
                maxX = Math.max(maxX, face.positions.maxX());
                maxY = Math.max(maxY, face.positions.maxY());
                maxZ = Math.max(maxZ, face.positions.maxZ());
            }
        }

        float width = Math.max(0.0001F, maxX - minX);
        float height = Math.max(0.0001F, maxY - minY);
        float depth = Math.max(0.0001F, maxZ - minZ);
        Vec3 scale = new Vec3(targetSize.x / width, targetSize.y / height, targetSize.z / depth);

        for (List<Face> partFaces : faces.values()) {
            for (int i = 0; i < partFaces.size(); i++) {
                Face face = partFaces.get(i);
                partFaces.set(i, new Face(face.positions.multiply(scale), face.uvs, face.normals));
            }
        }
    }

    private record Model(Map<String, List<Face>> faces) {}

    private record Face(Triangle positions, Triangle uvs, Triangle normals) {}

    private record Triangle(Vector3f a, Vector3f b, Vector3f c) {
        private static final Triangle EMPTY = new Triangle(new Vector3f(), new Vector3f(), new Vector3f());

        private Triangle negate() {
            return new Triangle(a.negate(new Vector3f()), b.negate(new Vector3f()), c.negate(new Vector3f()));
        }

        private Triangle subtract(Vector3f value) {
            return new Triangle(a.sub(value, new Vector3f()), b.sub(value, new Vector3f()), c.sub(value, new Vector3f()));
        }

        private Triangle multiply(Vec3 scale) {
            return new Triangle(
                a.mul((float) scale.x, (float) scale.y, (float) scale.z, new Vector3f()),
                b.mul((float) scale.x, (float) scale.y, (float) scale.z, new Vector3f()),
                c.mul((float) scale.x, (float) scale.y, (float) scale.z, new Vector3f())
            );
        }

        private float minX() { return Math.min(a.x, Math.min(b.x, c.x)); }
        private float minY() { return Math.min(a.y, Math.min(b.y, c.y)); }
        private float minZ() { return Math.min(a.z, Math.min(b.z, c.z)); }
        private float maxX() { return Math.max(a.x, Math.max(b.x, c.x)); }
        private float maxY() { return Math.max(a.y, Math.max(b.y, c.y)); }
        private float maxZ() { return Math.max(a.z, Math.max(b.z, c.z)); }
    }
}
