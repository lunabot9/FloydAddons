package floydaddons.not.dogshit.mixin;

import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientWorld.Properties.class)
public interface ClientWorldPropertiesAccessor {
    @Accessor("time") void floydaddons$setTime(long time);
    @Accessor("timeOfDay") void floydaddons$setTimeOfDay(long timeOfDay);
    @Accessor("timeOfDay") long floydaddons$getTimeOfDay();
}
