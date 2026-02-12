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
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Renders chroma tracer lines, wireframe hitbox outlines, and debug labels for mob ESP entities.
 * Uses MobEspManager.matches() for entity filtering and NpcTracker to skip duplicate armor stands.
 * Shows through walls (no depth test).
 */
public final class MobEspRenderer {

    private static final RenderPipeline XRAY_LINES_PIPELINE = RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of("floydaddons", "pipeline/mob_esp_lines"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build();

    private static final RenderLayer MOB_ESP_LAYER = RenderLayer.of(
            "floydaddons_mob_esp",
            1536,
            XRAY_LINES_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(2.0)))
                    .layering(RenderPhase.NO_LAYERING)
                    .build(false)
    );

    private MobEspRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(MobEspRenderer::onAfterEntities);
    }

    private static void onAfterEntities(WorldRenderContext context) {
        if (!MobEspManager.isEnabled() || !MobEspManager.hasFilters()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null || mc.worldRenderer == null) return;

        boolean debugActive = MobEspManager.isDebugActive();

        // Collect matching entities, resolving armor stands to their mob entities
        List<Entity> targets = new ArrayList<>();
        Set<Integer> addedIds = new HashSet<>();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (!MobEspManager.matches(entity)) continue;

            if (entity instanceof ArmorStandEntity) {
                // Armor stand matched (star mob nametag, name filter, etc.)
                // Try to find the actual mob entity at the same position
                Entity mob = findMobAtPosition(mc, entity);
                if (mob != null && addedIds.add(mob.getId())) {
                    targets.add(mob);
                } else if (mob == null && addedIds.add(entity.getId())) {
                    // No mob found — fall back to the armor stand itself
                    targets.add(entity);
                }
            } else {
                if (addedIds.add(entity.getId())) {
                    targets.add(entity);
                }
            }
        }

        if (targets.isEmpty() && !debugActive) return;

        float tickDelta = mc.getRenderTickCounter().getTickProgress(false);
        Vec3d cameraPos = context.worldState().cameraRenderState.pos;

        BufferBuilderStorage storage = ((WorldRendererAccessor) mc.worldRenderer).getBufferBuilders();
        VertexConsumerProvider.Immediate consumers = storage.getEntityVertexConsumers();

        boolean drawTracers = MobEspManager.isTracersEnabled();
        boolean drawHitboxes = MobEspManager.isHitboxesEnabled();

        MatrixStack.Entry entry = context.matrices().peek();

        // Draw tracers with per-entity colors
        if (drawTracers && !targets.isEmpty()) {
            Vec3d lookVec = mc.player.getRotationVec(tickDelta);
            float sx = (float) (lookVec.x * 2.0);
            float sy = (float) (lookVec.y * 2.0);
            float sz = (float) (lookVec.z * 2.0);

            VertexConsumer tracerBuf = consumers.getBuffer(MOB_ESP_LAYER);
            boolean drew = false;

            for (Entity entity : targets) {
                float[] rgb = resolveEntityColor(entity);
                float r = rgb[0], g = rgb[1], b = rgb[2];

                Vec3d targetPos = entity.getLerpedPos(tickDelta);
                float ex = (float) (targetPos.x - cameraPos.x);
                float ey = (float) (targetPos.y + entity.getHeight() / 2.0 - cameraPos.y);
                float ez = (float) (targetPos.z - cameraPos.z);

                float dx = ex - sx, dy = ey - sy, dz = ez - sz;
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 0.001f) continue;
                float nx = dx / len, ny = dy / len, nz = dz / len;

                tracerBuf.vertex(entry, sx, sy, sz).color(r, g, b, 1.0f).normal(entry, nx, ny, nz);
                tracerBuf.vertex(entry, ex, ey, ez).color(r, g, b, 1.0f).normal(entry, nx, ny, nz);
                drew = true;
            }

            if (drew) {
                consumers.draw(MOB_ESP_LAYER);
            }
        }

        // Draw wireframe hitbox outlines with per-entity colors
        if (drawHitboxes && !targets.isEmpty()) {
            VertexConsumer lineBuf = consumers.getBuffer(MOB_ESP_LAYER);
            boolean drewOutlines = false;

            for (Entity entity : targets) {
                float[] rgb = resolveEntityColor(entity);
                float r = rgb[0], g = rgb[1], b = rgb[2];

                Box aabb = entity.getBoundingBox();
                float x1 = (float) (aabb.minX - cameraPos.x);
                float y1 = (float) (aabb.minY - cameraPos.y);
                float z1 = (float) (aabb.minZ - cameraPos.z);
                float x2 = (float) (aabb.maxX - cameraPos.x);
                float y2 = (float) (aabb.maxY - cameraPos.y);
                float z2 = (float) (aabb.maxZ - cameraPos.z);

                drawBoxOutline(lineBuf, entry, x1, y1, z1, x2, y2, z2, r, g, b, 1.0f);
                drewOutlines = true;
            }

            if (drewOutlines) {
                consumers.draw(MOB_ESP_LAYER);
            }
        }

        // Debug labels
        if (debugActive) {
            renderDebugLabels(context, mc, cameraPos, tickDelta);
        }
    }

    /**
     * Resolves the color for an entity: per-entry color or default ESP color.
     * Returns float[]{r, g, b} in 0..1 range.
     */
    private static float[] resolveEntityColor(Entity entity) {
        int[] colorInfo = MobEspManager.getColorForEntity(entity);
        int color;
        if (colorInfo[1] == 1) {
            // Chroma cycling
            float hue = (float) ((System.currentTimeMillis() % 4000) / 4000.0);
            color = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        } else {
            color = colorInfo[0];
        }
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        return new float[]{r, g, b};
    }

    /**
     * Find the closest non-armor-stand entity at the same X,Z as the given armor stand.
     * Star mob armor stands float above the actual mob entity.
     */
    private static Entity findMobAtPosition(MinecraftClient mc, Entity armorStand) {
        double asX = armorStand.getX();
        double asY = armorStand.getY();
        double asZ = armorStand.getZ();

        Entity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ArmorStandEntity) continue;
            if (entity == mc.player) continue;

            double dx = entity.getX() - asX;
            double dz = entity.getZ() - asZ;
            double horizDistSq = dx * dx + dz * dz;
            if (horizDistSq > 0.01) continue; // within 0.1 blocks horizontal

            double dy = entity.getY() - asY;
            if (dy > 1.0 || dy < -4.0) continue; // mob is at or below the nametag

            double totalDist = horizDistSq + (dy * dy);
            if (totalDist < closestDist) {
                closestDist = totalDist;
                closest = entity;
            }
        }

        return closest;
    }

    private static void drawBoxOutline(VertexConsumer buf, MatrixStack.Entry entry,
                                        float x1, float y1, float z1,
                                        float x2, float y2, float z2,
                                        float r, float g, float b, float a) {
        // Bottom edges
        line(buf, entry, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(buf, entry, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(buf, entry, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(buf, entry, x1, y1, z2, x1, y1, z1, r, g, b, a);
        // Top edges
        line(buf, entry, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(buf, entry, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(buf, entry, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(buf, entry, x1, y2, z2, x1, y2, z1, r, g, b, a);
        // Vertical edges
        line(buf, entry, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(buf, entry, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(buf, entry, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(buf, entry, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void line(VertexConsumer buf, MatrixStack.Entry entry,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float r, float g, float b, float a) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001f) return;
        float nx = dx / len, ny = dy / len, nz = dz / len;
        buf.vertex(entry, x1, y1, z1).color(r, g, b, a).normal(entry, nx, ny, nz);
        buf.vertex(entry, x2, y2, z2).color(r, g, b, a).normal(entry, nx, ny, nz);
    }

    private static void renderDebugLabels(WorldRenderContext context, MinecraftClient mc,
                                           Vec3d cameraPos, float tickDelta) {
        MatrixStack matrices = context.matrices();
        TextRenderer textRenderer = mc.textRenderer;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            double dist = entity.squaredDistanceTo(mc.player);
            if (dist > 2500) continue; // 50 blocks

            Vec3d pos = entity.getLerpedPos(tickDelta);
            float rx = (float) (pos.x - cameraPos.x);
            float ry = (float) (pos.y + entity.getHeight() + 0.5f - cameraPos.y);
            float rz = (float) (pos.z - cameraPos.z);

            String typeId = EntityType.getId(entity.getType()).toString();
            String displayName = entity.getName().getString();
            String cached = NpcTracker.getCachedName(entity);
            boolean matched = MobEspManager.matches(entity);

            String label = typeId + " | " + displayName;
            if (cached != null) label += " | npc=" + cached;
            if (matched) label += " MATCH";

            float distance = (float) Math.sqrt(dist);
            float textScale = Math.max(distance / 10f, 1.0f) * 0.025f;

            matrices.push();
            matrices.peek().translate(rx, ry, rz);
            matrices.peek().rotate(context.worldState().cameraRenderState.orientation);
            matrices.peek().scale(-textScale, -textScale, textScale);

            int textWidth = textRenderer.getWidth(label);
            int color = matched ? 0xFF55FF55 : 0xFFAAAAAA;
            textRenderer.draw(label, -textWidth / 2f, 0, color, false,
                    matrices.peek().getPositionMatrix(), context.consumers(),
                    TextRenderer.TextLayerType.SEE_THROUGH, 0x40000000, 0xF000F0);

            matrices.pop();
        }
    }
}
