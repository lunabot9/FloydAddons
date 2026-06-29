package gg.floyd.mixin.mixins;

import com.mojang.blaze3d.platform.InputConstants;
import gg.floyd.events.InputEvent;
import gg.floyd.features.impl.render.FloydRender;
import gg.floyd.keybind.KeybindSync;
import gg.floyd.mixin.accessors.KeyMappingAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public class KeyBindingMixin {

    @Inject(method = "click", at = @At("HEAD"), cancellable = true)
    private static void onKeyPressed(InputConstants.Key key, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.options != null) {
            KeyMapping fullscreen = minecraft.options.keyFullscreen;
            if (fullscreen != null && key.equals(((KeyMappingAccessor) fullscreen).getBoundKey())) {
                FloydRender.clearBorderlessWindowed(true);
            }
        }
        if (new InputEvent(key).postAndCatch()) ci.cancel();
    }

    /**
     * When the vanilla Controls screen rebinds a key, mirror it back onto the owning FloydAddons
     * setting. The {@link KeybindSync#isSyncing()} guard stops this from looping when FloydAddons is
     * the one that initiated the change.
     */
    @Inject(method = "setKey", at = @At("TAIL"))
    private void floydaddons$onSetKey(InputConstants.Key key, CallbackInfo ci) {
        if (!KeybindSync.isSyncing()) {
            KeybindSync.syncFromBinding((KeyMapping) (Object) this, key);
        }
    }
}
