package floydaddons.not.dogshit.mixin.mixins;

import floydaddons.not.dogshit.client.FloydAddonsMod;
import floydaddons.not.dogshit.client.features.impl.hiders.FloydModHider;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(value = FabricLoaderImpl.class, remap = false)
public class FloydFabricLoaderMixin {
    @Inject(method = "getAllMods", at = @At("RETURN"), cancellable = true)
    private void floydaddons$hideFromAllMods(CallbackInfoReturnable<List<ModContainer>> cir) {
        if (!FloydModHider.INSTANCE.getEnabled()) return;

        List<ModContainer> original = cir.getReturnValue();
        List<ModContainer> filtered = original.stream()
            .filter(mod -> !FloydAddonsMod.MOD_ID.equals(mod.getMetadata().getId()))
            .toList();
        if (filtered.size() != original.size()) cir.setReturnValue(filtered);
    }

    @Inject(method = "getModContainer", at = @At("HEAD"), cancellable = true)
    private void floydaddons$hideSpecificMod(String id, CallbackInfoReturnable<Optional<ModContainer>> cir) {
        if (FloydModHider.INSTANCE.getEnabled() && FloydAddonsMod.MOD_ID.equals(id)) cir.setReturnValue(Optional.empty());
    }

    @Inject(method = "isModLoaded", at = @At("HEAD"), cancellable = true)
    private void floydaddons$reportNotLoaded(String id, CallbackInfoReturnable<Boolean> cir) {
        if (FloydModHider.INSTANCE.getEnabled() && FloydAddonsMod.MOD_ID.equals(id)) cir.setReturnValue(false);
    }
}
