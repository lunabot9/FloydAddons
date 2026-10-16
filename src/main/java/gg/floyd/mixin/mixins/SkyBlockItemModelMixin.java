package gg.floyd.mixin.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import gg.floyd.features.impl.render.FloydSkyBlockItemFallbacks;
import gg.floyd.features.impl.render.FloydSkyBlockItemModelPolicy;
import gg.floyd.features.impl.render.FloydSkyBlockPackAssets;
import gg.floyd.features.impl.render.FloydSkyBlockPackDisabler;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin shells use Java so Compose cannot add fields that Mixin rejects.
@Mixin(ItemModelResolver.class)
public abstract class SkyBlockItemModelMixin {
    @WrapOperation(method = "appendItemLayers", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
    private Object replaceSkyBlockItemModel(ItemStack stack, DataComponentType<?> componentType, Operation<Identifier> original) {
        var current = original.call(stack, componentType);
        if (!FloydSkyBlockItemModelPolicy.INSTANCE.shouldReplaceCurrentModel(current,
            FloydSkyBlockPackDisabler.INSTANCE.getEnabled(), stack.isEmpty())) return current;
        var data = FloydSkyBlockItemFallbacks.INSTANCE.getCustomData(stack);
        var id = FloydSkyBlockItemFallbacks.INSTANCE.skyBlockId(data);
        var model = FloydSkyBlockItemModelPolicy.INSTANCE.resolveBaseModel(current, id,
            FloydSkyBlockPackAssets.INSTANCE.getItemModels(),
            data.contains("quiver_arrow") ? Items.ARROW.components().get(DataComponents.ITEM_MODEL) : null);
        return id == null ? model : FloydSkyBlockItemFallbacks.INSTANCE.resolveDynamic(id, stack, data, model);
    }
}
