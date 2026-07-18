package gg.floyd.features.impl.cosmetic;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Builds lightweight, client-only mob entities and asks Minecraft's own entity renderers to
 * extract their model state. The entities are never added to the level, so this changes only
 * the player's appearance and cannot affect hitboxes, AI, networking, or gameplay.
 */
public final class VanillaMobPlayerModel {
    private static final String MINION_MODEL = "Minion";
    private static final Identifier MINION_TEXTURE = Identifier.fromNamespaceAndPath("floydaddons", "textures/entity/player_model/minion_copper_golem.png");
    private static final Map<Integer, CachedMob> CACHE = new HashMap<>();
    private static final Set<EntityRenderState> MINION_RENDER_STATES = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private static String lastRequestedMobId;
    private static String lastCreatedEntityType;
    private static String lastFailure;
    private static long replacementCount;

    private VanillaMobPlayerModel() {}

    public static EntityRenderState extract(EntityRenderDispatcher dispatcher, AbstractClientPlayer player, float partialTick) {
        String mobId = FloydPlayerModel.selectedVanillaMobIdFor(player.getId());
        if (mobId == null) return null;
        lastRequestedMobId = mobId;

        Level level = player.level();
        CachedMob cached = CACHE.get(player.getId());
        if (cached == null || cached.source != player || cached.level != level || !cached.mobId.equals(mobId)) {
            Entity mob = createMob(level, mobId);
            if (mob == null) {
                lastFailure = "Could not create minecraft:" + mobId;
                return null;
            }
            cached = new CachedMob(player, level, mobId, mob);
            CACHE.put(player.getId(), cached);
            lastCreatedEntityType = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
            lastFailure = null;
        }

        syncEntity(cached, player);
        EntityRenderState state = dispatcher.extractEntity(cached.entity, partialTick);
        if (state != null) {
            MINION_RENDER_STATES.remove(state);
            if (MINION_MODEL.equals(FloydPlayerModel.selectedModelFor(player.getId()))) {
                markMinionState(state);
            }
        }
        replacementCount++;
        return state;
    }

    public static Identifier minionTextureFor(EntityRenderState state) {
        return MINION_RENDER_STATES.contains(state) ? MINION_TEXTURE : null;
    }

    public static Map<String, Object> state() {
        Map<String, Object> state = new HashMap<>();
        state.put("lastRequestedMobId", lastRequestedMobId);
        state.put("lastCreatedEntityType", lastCreatedEntityType);
        state.put("lastFailure", lastFailure);
        state.put("replacementCount", replacementCount);
        state.put("cachedPlayers", CACHE.size());
        return state;
    }

    private static Entity createMob(Level level, String mobId) {
        Identifier id = Identifier.fromNamespaceAndPath("minecraft", mobId);
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
        Entity entity = type.create(level, EntitySpawnReason.COMMAND);
        return entity instanceof Mob ? entity : null;
    }

    private static void syncEntity(CachedMob cached, AbstractClientPlayer player) {
        Entity entity = cached.entity;
        entity.setPos(player.getX(), player.getY(), player.getZ());
        entity.xo = player.xo;
        entity.yo = player.yo;
        entity.zo = player.zo;
        entity.xOld = player.xOld;
        entity.yOld = player.yOld;
        entity.zOld = player.zOld;
        entity.setYRot(player.getYRot());
        entity.yRotO = player.yRotO;
        entity.setXRot(player.getXRot());
        entity.xRotO = player.xRotO;
        entity.setDeltaMovement(player.getDeltaMovement());
        entity.setOnGround(player.onGround());
        entity.setShiftKeyDown(player.isShiftKeyDown());
        entity.setSprinting(player.isSprinting());
        entity.setSwimming(player.isSwimming());
        entity.setInvisible(player.isInvisible());
        entity.tickCount = player.tickCount;

        if (entity instanceof LivingEntity mob) {
            mob.yBodyRot = player.yBodyRot;
            mob.yBodyRotO = player.yBodyRotO;
            mob.yHeadRot = player.yHeadRot;
            mob.yHeadRotO = player.yHeadRotO;
            mob.attackAnim = player.attackAnim;
            mob.oAttackAnim = player.oAttackAnim;
            mob.swinging = player.swinging;
            mob.swingingArm = player.swingingArm;
            mob.swingTime = player.swingTime;

            if (cached.lastAnimationTick != player.tickCount) {
                float speed = player.walkAnimation.speed();
                if (speed > 0.01F) mob.walkAnimation.update(speed, 1.0F, 1.0F);
                else mob.walkAnimation.stop();

                if (mob instanceof EnderDragon dragon) {
                    dragon.oFlapTime = dragon.flapTime;
                    dragon.flapTime += 0.08F + Math.min(0.12F, speed * 0.12F);
                    dragon.flightHistory.record(player.getY(), player.getYRot());
                }
                cached.lastAnimationTick = player.tickCount;
            }
        }
    }

    private static void markMinionState(EntityRenderState state) {
        MINION_RENDER_STATES.add(state);
    }

    private static final class CachedMob {
        private final AbstractClientPlayer source;
        private final Level level;
        private final String mobId;
        private final Entity entity;
        private int lastAnimationTick = Integer.MIN_VALUE;

        private CachedMob(AbstractClientPlayer source, Level level, String mobId, Entity entity) {
            this.source = source;
            this.level = level;
            this.mobId = mobId;
            this.entity = entity;
        }
    }
}
