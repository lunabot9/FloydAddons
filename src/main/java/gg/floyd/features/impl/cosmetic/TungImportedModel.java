package gg.floyd.features.impl.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and renders the rigged Tung Tung Sahur GLB.
 * Model and texture created and provided by ImJoyler, used with the owner's permission.
 */
final class TungImportedModel {
    private static final String MODEL_RESOURCE = "assets/floydaddons/player_models/tung_tung_sahur.glb";
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
        "floydaddons", "textures/entity/player_model/tung_tung_sahur.png"
    );
    private static final float MODEL_SCALE = 0.60F;
    private static final Model MODEL = load();

    private TungImportedModel() {}

    static void render(PoseStack stack, SubmitNodeCollector collector, int light, float movementSpeed, float attackTime) {
        Map<Integer, AnimatedTrs> animated = new HashMap<>();
        // Render states retain an animation position while idle, so gate the GLB run cycle on the
        // current movement speed. This leaves a stationary player in the authored base pose.
        if (MODEL.runAnimation != null && movementSpeed > 0.01F) {
            float duration = MODEL.runAnimation.duration;
            float time = duration > 0.0F ? clockSeconds() % duration : 0.0F;
            applyAnimation(MODEL.runAnimation, time, Math.min(1.0F, movementSpeed * 4.0F), animated);
        }
        if (MODEL.hitAnimation != null && attackTime > 0.0F) {
            float progress = Math.min(1.0F, attackTime);
            float weight = progress < 0.1F ? progress / 0.1F : progress > 0.9F ? (1.0F - progress) / 0.1F : 1.0F;
            applyAnimation(MODEL.hitAnimation, progress * MODEL.hitAnimation.duration, weight, animated);
        }

        stack.pushPose();
        // The GLB is Y-up and 2.5 units tall. Floyd's avatar model uses Y-down with its feet at 1.5.
        stack.translate(0.0F, 1.5F, 0.0F);
        stack.mulPose(Axis.YP.rotationDegrees(180.0F));
        stack.scale(-MODEL_SCALE, -MODEL_SCALE, MODEL_SCALE);
        for (int root : MODEL.sceneRoots) {
            renderNode(root, stack, collector, light, animated);
        }
        stack.popPose();
    }

    private static float clockSeconds() {
        return (System.nanoTime() % 60_000_000_000L) / 1_000_000_000.0F;
    }

    private static void renderNode(int nodeIndex, PoseStack stack, SubmitNodeCollector collector, int light,
                                   Map<Integer, AnimatedTrs> animated) {
        Node node = MODEL.nodes[nodeIndex];
        AnimatedTrs pose = animated.get(nodeIndex);
        float[] translation = pose == null || pose.translation == null ? node.translation : pose.translation;
        float[] rotation = pose == null || pose.rotation == null ? node.rotation : pose.rotation;
        float[] scale = pose == null || pose.scale == null ? node.scale : pose.scale;

        stack.pushPose();
        stack.translate(translation[0], translation[1], translation[2]);
        stack.mulPose(new Quaternionf(rotation[0], rotation[1], rotation[2], rotation[3]));
        stack.scale(scale[0], scale[1], scale[2]);
        if (node.mesh >= 0) {
            Mesh mesh = MODEL.meshes[node.mesh];
            collector.submitCustomGeometry(stack, RenderTypes.entityCutout(TEXTURE),
                (renderPose, consumer) -> emitMesh(renderPose, consumer, light, mesh));
        }
        for (int child : node.children) {
            renderNode(child, stack, collector, light, animated);
        }
        stack.popPose();
    }

    private static void emitMesh(PoseStack.Pose pose, VertexConsumer consumer, int light, Mesh mesh) {
        for (int i = 0; i + 2 < mesh.indices.length; i += 3) {
            emitVertex(pose, consumer, light, mesh, mesh.indices[i]);
            emitVertex(pose, consumer, light, mesh, mesh.indices[i + 1]);
            emitVertex(pose, consumer, light, mesh, mesh.indices[i + 2]);
            // Entity cutout batches quads; duplicating the last corner preserves each GLB triangle.
            emitVertex(pose, consumer, light, mesh, mesh.indices[i + 2]);
        }
    }

    private static void emitVertex(PoseStack.Pose pose, VertexConsumer consumer, int light, Mesh mesh, int index) {
        int p = index * 3;
        int uv = index * 2;
        consumer.addVertex(pose, mesh.positions[p], mesh.positions[p + 1], mesh.positions[p + 2])
            .setColor(0xFFFFFFFF)
            .setUv(mesh.uvs[uv], mesh.uvs[uv + 1])
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, mesh.normals[p], mesh.normals[p + 1], mesh.normals[p + 2]);
    }

    private static void applyAnimation(Animation animation, float time, float weight,
                                       Map<Integer, AnimatedTrs> animated) {
        if (weight <= 0.0F) return;
        for (Channel channel : animation.channels) {
            Node node = MODEL.nodes[channel.node];
            AnimatedTrs target = animated.computeIfAbsent(channel.node, ignored -> new AnimatedTrs());
            float[] sampled = sample(channel, time);
            switch (channel.path) {
                case "translation" -> target.translation = lerp(
                    target.translation == null ? node.translation : target.translation, sampled, weight
                );
                case "scale" -> target.scale = lerp(target.scale == null ? node.scale : target.scale, sampled, weight);
                case "rotation" -> {
                    float[] current = target.rotation == null ? node.rotation : target.rotation;
                    Quaternionf blended = new Quaternionf(current[0], current[1], current[2], current[3])
                        .slerp(new Quaternionf(sampled[0], sampled[1], sampled[2], sampled[3]), weight);
                    target.rotation = new float[] {blended.x, blended.y, blended.z, blended.w};
                }
                default -> { }
            }
        }
    }

    private static float[] sample(Channel channel, float time) {
        float[] timestamps = channel.timestamps;
        int last = timestamps.length - 1;
        if (last <= 0 || time <= timestamps[0]) return channel.value(0);
        if (time >= timestamps[last]) return channel.value(last);
        int index = 0;
        while (index + 1 < timestamps.length && timestamps[index + 1] < time) index++;
        float span = timestamps[index + 1] - timestamps[index];
        float alpha = span <= 0.0F ? 0.0F : (time - timestamps[index]) / span;
        float[] a = channel.value(index);
        float[] b = channel.value(index + 1);
        if (channel.path.equals("rotation")) {
            Quaternionf result = new Quaternionf(a[0], a[1], a[2], a[3])
                .slerp(new Quaternionf(b[0], b[1], b[2], b[3]), alpha);
            return new float[] {result.x, result.y, result.z, result.w};
        }
        return lerp(a, b, alpha);
    }

    private static float[] lerp(float[] a, float[] b, float alpha) {
        float[] result = new float[Math.min(a.length, b.length)];
        for (int i = 0; i < result.length; i++) result[i] = a[i] + (b[i] - a[i]) * alpha;
        return result;
    }

    private static Model load() {
        try (InputStream stream = TungImportedModel.class.getClassLoader().getResourceAsStream(MODEL_RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing resource " + MODEL_RESOURCE);
            byte[] bytes = stream.readAllBytes();
            ByteBuffer file = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (file.getInt() != 0x46546C67 || file.getInt() != 2) throw new IllegalStateException("Invalid GLB header");
            int totalLength = file.getInt();
            String json = null;
            byte[] binary = null;
            while (file.position() < totalLength) {
                int length = file.getInt();
                int type = file.getInt();
                byte[] chunk = new byte[length];
                file.get(chunk);
                if (type == 0x4E4F534A) json = new String(chunk, StandardCharsets.UTF_8).trim();
                if (type == 0x004E4942) binary = chunk;
            }
            if (json == null || binary == null) throw new IllegalStateException("GLB is missing JSON or binary data");
            return parse(JsonParser.parseString(json).getAsJsonObject(), binary);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load Tung Tung Sahur model", exception);
        }
    }

    private static Model parse(JsonObject root, byte[] binary) {
        JsonArray nodeJson = root.getAsJsonArray("nodes");
        Node[] nodes = new Node[nodeJson.size()];
        for (int i = 0; i < nodes.length; i++) {
            JsonObject value = nodeJson.get(i).getAsJsonObject();
            nodes[i] = new Node(
                value.has("mesh") ? value.get("mesh").getAsInt() : -1,
                ints(value.getAsJsonArray("children")),
                floats(value.getAsJsonArray("translation"), new float[] {0, 0, 0}),
                floats(value.getAsJsonArray("rotation"), new float[] {0, 0, 0, 1}),
                floats(value.getAsJsonArray("scale"), new float[] {1, 1, 1})
            );
        }

        JsonArray meshJson = root.getAsJsonArray("meshes");
        Mesh[] meshes = new Mesh[meshJson.size()];
        for (int i = 0; i < meshes.length; i++) {
            JsonObject primitive = meshJson.get(i).getAsJsonObject().getAsJsonArray("primitives").get(0).getAsJsonObject();
            JsonObject attributes = primitive.getAsJsonObject("attributes");
            meshes[i] = new Mesh(
                readFloats(root, binary, attributes.get("POSITION").getAsInt(), 3),
                readFloats(root, binary, attributes.get("NORMAL").getAsInt(), 3),
                readFloats(root, binary, attributes.get("TEXCOORD_0").getAsInt(), 2),
                readIndices(root, binary, primitive.get("indices").getAsInt())
            );
        }

        JsonObject scene = root.getAsJsonArray("scenes").get(root.has("scene") ? root.get("scene").getAsInt() : 0).getAsJsonObject();
        Animation run = null;
        Animation hit = null;
        if (root.has("animations")) {
            for (var element : root.getAsJsonArray("animations")) {
                Animation animation = parseAnimation(root, binary, element.getAsJsonObject());
                String name = element.getAsJsonObject().has("name") ? element.getAsJsonObject().get("name").getAsString().toLowerCase() : "";
                if (name.contains("run") || name.contains("walk")) run = animation;
                if (name.contains("hit") || name.contains("attack")) hit = animation;
            }
        }
        return new Model(nodes, meshes, ints(scene.getAsJsonArray("nodes")), run, hit);
    }

    private static Animation parseAnimation(JsonObject root, byte[] binary, JsonObject value) {
        JsonArray samplers = value.getAsJsonArray("samplers");
        JsonArray channels = value.getAsJsonArray("channels");
        Channel[] result = new Channel[channels.size()];
        float duration = 0.0F;
        for (int i = 0; i < channels.size(); i++) {
            JsonObject channel = channels.get(i).getAsJsonObject();
            JsonObject target = channel.getAsJsonObject("target");
            String path = target.get("path").getAsString();
            int width = path.equals("rotation") ? 4 : 3;
            JsonObject sampler = samplers.get(channel.get("sampler").getAsInt()).getAsJsonObject();
            float[] times = readFloats(root, binary, sampler.get("input").getAsInt(), 1);
            float[] values = readFloats(root, binary, sampler.get("output").getAsInt(), width);
            if (times.length > 0) duration = Math.max(duration, times[times.length - 1]);
            result[i] = new Channel(target.get("node").getAsInt(), path, times, values, width);
        }
        return new Animation(result, duration);
    }

    private static float[] readFloats(JsonObject root, byte[] binary, int accessorIndex, int width) {
        JsonObject accessor = root.getAsJsonArray("accessors").get(accessorIndex).getAsJsonObject();
        if (accessor.get("componentType").getAsInt() != 5126) throw new IllegalStateException("Expected float accessor");
        JsonObject view = root.getAsJsonArray("bufferViews").get(accessor.get("bufferView").getAsInt()).getAsJsonObject();
        int count = accessor.get("count").getAsInt();
        int offset = (view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0)
            + (accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0);
        int stride = view.has("byteStride") ? view.get("byteStride").getAsInt() : width * Float.BYTES;
        ByteBuffer data = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);
        float[] result = new float[count * width];
        for (int i = 0; i < count; i++) {
            for (int component = 0; component < width; component++) {
                result[i * width + component] = data.getFloat(offset + i * stride + component * Float.BYTES);
            }
        }
        return result;
    }

    private static int[] readIndices(JsonObject root, byte[] binary, int accessorIndex) {
        JsonObject accessor = root.getAsJsonArray("accessors").get(accessorIndex).getAsJsonObject();
        JsonObject view = root.getAsJsonArray("bufferViews").get(accessor.get("bufferView").getAsInt()).getAsJsonObject();
        int count = accessor.get("count").getAsInt();
        int componentType = accessor.get("componentType").getAsInt();
        int componentSize = componentType == 5121 ? 1 : componentType == 5123 ? 2 : 4;
        int offset = (view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0)
            + (accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0);
        int stride = view.has("byteStride") ? view.get("byteStride").getAsInt() : componentSize;
        ByteBuffer data = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            int position = offset + i * stride;
            result[i] = switch (componentType) {
                case 5121 -> Byte.toUnsignedInt(data.get(position));
                case 5123 -> Short.toUnsignedInt(data.getShort(position));
                case 5125 -> data.getInt(position);
                default -> throw new IllegalStateException("Unsupported index component " + componentType);
            };
        }
        return result;
    }

    private static int[] ints(JsonArray array) {
        if (array == null) return new int[0];
        int[] result = new int[array.size()];
        for (int i = 0; i < result.length; i++) result[i] = array.get(i).getAsInt();
        return result;
    }

    private static float[] floats(JsonArray array, float[] fallback) {
        if (array == null) return fallback.clone();
        float[] result = new float[array.size()];
        for (int i = 0; i < result.length; i++) result[i] = array.get(i).getAsFloat();
        return result;
    }

    private record Model(Node[] nodes, Mesh[] meshes, int[] sceneRoots, Animation runAnimation, Animation hitAnimation) {}
    private record Node(int mesh, int[] children, float[] translation, float[] rotation, float[] scale) {}
    private record Mesh(float[] positions, float[] normals, float[] uvs, int[] indices) {}
    private record Animation(Channel[] channels, float duration) {}
    private record Channel(int node, String path, float[] timestamps, float[] values, int width) {
        float[] value(int keyframe) {
            float[] result = new float[width];
            System.arraycopy(values, keyframe * width, result, 0, width);
            return result;
        }
    }
    private static final class AnimatedTrs {
        float[] translation;
        float[] rotation;
        float[] scale;
    }
}
