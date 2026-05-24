package com.odtheking.mixin.mixins;

import com.mojang.blaze3d.platform.InputConstants;
import com.odtheking.odin.keybind.KeybindSync;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public class KeyBindingSyncMixin {

    @Inject(method = "setKey", at = @At("TAIL"))
    private void onSetKey(InputConstants.Key boundKey, CallbackInfo ci) {
        if (KeybindSync.isSyncing()) return;
        KeybindSync.syncFromBinding((KeyMapping) (Object) this, boundKey);
    }
}
