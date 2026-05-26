package floydaddons.not.dogshit.mixin.mixins;

import com.mojang.blaze3d.platform.InputConstants;
import floydaddons.not.dogshit.client.events.InputEvent;
import floydaddons.not.dogshit.client.keybind.KeybindSync;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public class KeyBindingMixin {

    @Inject(method = "click", at = @At("HEAD"), cancellable = true)
    private static void onKeyPressed(InputConstants.Key key, CallbackInfo ci) {
        if (new InputEvent(key).postAndCatch()) ci.cancel();
    }

    @Inject(method = "setKey", at = @At("TAIL"))
    private void onSetKey(InputConstants.Key boundKey, CallbackInfo ci) {
        if (KeybindSync.isSyncing()) return;
        KeybindSync.syncFromBinding((KeyMapping) (Object) this, boundKey);
    }
}
