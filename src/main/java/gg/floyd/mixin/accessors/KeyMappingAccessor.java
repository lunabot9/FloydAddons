package gg.floyd.mixin.accessors;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link KeyMapping}'s protected bound-key field so {@code KeybindSync} can read/write the
 * currently bound key without going through {@link KeyMapping#setKey} (which would retrigger the
 * sync injection).
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("key")
    InputConstants.Key getBoundKey();

    @Accessor("key")
    void setBoundKey(InputConstants.Key key);
}
