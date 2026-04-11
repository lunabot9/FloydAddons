package floydaddons.not.dogshit.client.util;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class RenderCompat {
    private static final Field TRANSLUCENT_LINES = findField(RenderLayers.class, "LINES_TRANSLUCENT");
    private static final Method ENTITY_CUTOUT_NO_CULL = findMethod(RenderLayers.class, "entityCutoutNoCull", Identifier.class);
    private static final Method LEGACY_ENTITY_CUTOUT_NO_CULL = findMethod(RenderLayer.class, "getEntityCutoutNoCull", Identifier.class);
    private static final Method LEGACY_TEXTURE_FILTER = findMethod(AbstractTexture.class, "setFilter", boolean.class, boolean.class);
    private static final Method LEGACY_LINES = findMethod(RenderLayer.class, "getLines");
    private static final Method LEGACY_DRAW_ENTITY = findMethod(
            InventoryScreen.class,
            "drawEntity",
            DrawContext.class,
            int.class, int.class, int.class, int.class,
            float.class,
            Vector3f.class,
            Quaternionf.class,
            Quaternionf.class,
            LivingEntity.class
    );

    private RenderCompat() {}

    public static RenderLayer getLineLayer() {
        RenderLayer layer = invokeRenderLayerField(TRANSLUCENT_LINES);
        if (layer != null) {
            return layer;
        }

        layer = invokeRenderLayerMethod(LEGACY_LINES);
        if (layer != null) {
            return layer;
        }

        throw new IllegalStateException("No compatible line render layer found");
    }

    public static RenderLayer getEntityCutoutNoCull(Identifier texture) {
        RenderLayer layer = invokeRenderLayerMethod(ENTITY_CUTOUT_NO_CULL, texture);
        if (layer != null) {
            return layer;
        }

        layer = invokeRenderLayerMethod(LEGACY_ENTITY_CUTOUT_NO_CULL, texture);
        return layer != null ? layer : getLineLayer();
    }

    public static void enableLinearFiltering(NativeImageBackedTexture texture) {
        if (LEGACY_TEXTURE_FILTER == null) {
            return;
        }
        try {
            LEGACY_TEXTURE_FILTER.invoke(texture, true, true);
        } catch (ReflectiveOperationException ignored) {}
    }

    public static void drawEntity(
            DrawContext context,
            int x1,
            int y1,
            int x2,
            int y2,
            float size,
            Vector3f offset,
            Quaternionf bodyRotation,
            Quaternionf headRotation,
            LivingEntity entity
    ) {
        if (LEGACY_DRAW_ENTITY != null) {
            try {
                LEGACY_DRAW_ENTITY.invoke(null, context, x1, y1, x2, y2, size, offset, bodyRotation, headRotation, entity);
                return;
            } catch (ReflectiveOperationException ignored) {}
        }

        InventoryScreen.drawEntity(
                context,
                x1,
                y1,
                x2,
                y2,
                Math.max(1, Math.round(size)),
                0.0625f,
                0.0f,
                0.0f,
                entity
        );
    }

    private static RenderLayer invokeRenderLayerMethod(Method method, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            return (RenderLayer) method.invoke(null, args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static RenderLayer invokeRenderLayerField(Field field) {
        if (field == null) {
            return null;
        }
        try {
            return (RenderLayer) field.get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> owner, String name) {
        try {
            return owner.getField(name);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
