package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.SkinConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.spongepowered.asm.mixin.Mixin;

// Dummy mixin to ensure SkinConfig.save() is called on client stop via Fabric callback already registered in FloydAddonsClient; no code needed.
@Mixin(ClientLifecycleEvents.class)
public class SkinConfigSaverMixin { }
