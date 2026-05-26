package floydaddons.not.dogshit.mixin.mixins;

import floydaddons.not.dogshit.client.features.impl.render.FloydHubMap;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MapRenderer.class)
public class HubMapRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void floydaddons$applyCustomHubMap(MapId mapId, MapItemSavedData mapItemSavedData, MapRenderState mapRenderState, CallbackInfo ci) {
        FloydHubMap.INSTANCE.applyRenderState(mapId, mapRenderState);
    }
}
