package gg.floyd.mixin.accessors;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link GameRenderer}'s private {@link FogRenderer} so the ESP world render can grab the
 * "no fog" buffer ({@code FogRenderer.FogMode.NONE}) and bind it while drawing tracers / ESP boxes.
 * Without this the ESP geometry inherits the world fog, so blindness / darkness / distance fog tints
 * far-away players' boxes toward the (often black) fog color.
 */
@Mixin(GameRenderer.class)
public interface GameRendererFogAccessor {

    @Accessor("fogRenderer")
    FogRenderer floydaddons$getFogRenderer();
}
