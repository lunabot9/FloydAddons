package gg.floyd.mixin.accessors;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the private {@code KeyMapping.Category.register(String)} factory. Not strictly required
 * (the public {@code register(Identifier)} overload is used by {@code KeybindSync}), but kept as an
 * accessor for completeness / future use.
 */
@Mixin(KeyMapping.Category.class)
public interface KeyMappingCategoryAccessor {

    @Invoker("register")
    static KeyMapping.Category invokeRegister(String name) {
        throw new UnsupportedOperationException();
    }
}
