package com.odtheking.mixin.accessors;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyMapping.Category.class)
public interface KeyMappingCategoryAccessor {
    @Invoker("register")
    static KeyMapping.Category register(String id) {
        throw new UnsupportedOperationException();
    }
}
