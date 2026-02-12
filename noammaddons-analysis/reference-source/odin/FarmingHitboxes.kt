// SOURCE: https://github.com/odtheking/Odin/blob/main/odinclient/src/main/kotlin/me/odinclient/features/impl/skyblock/FarmingHitboxes.kt
package me.odinclient.features.impl.skyblock

import me.odinclient.mixin.accessors.IBlockAccessor
import me.odinmain.features.Module
import net.minecraft.block.Block

object FarmingHitboxes : Module(
    name = "Farming Hitboxes",
    description = "Expands the hitbox of some crops to a full block."
) {
    // Uses a mixin accessor (IBlockAccessor) to set block bounds to 0,0,0 -> 1,1,1
    // This makes partial blocks (crops, cocoa, nether wart) clickable as full blocks
    fun setFullBlock(block: Block) {
        val accessor = (block as IBlockAccessor)
        accessor.setMinX(0.0)
        accessor.setMinY(0.0)
        accessor.setMinZ(0.0)
        accessor.setMaxX(1.0)
        accessor.setMaxY(1.0)
        accessor.setMaxZ(1.0)
    }
}

// In 1.21.10: Blocks use VoxelShape instead of min/max bounds
// Equivalent approach:
// @Mixin(CropBlock.class)  // or specific crop class
// public class CropHitboxMixin {
//     @Inject(method = "getOutlineShape", at = @At("HEAD"), cancellable = true)
//     private void fullBlockShape(BlockState state, BlockView world, BlockPos pos,
//         ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
//         if (RenderConfig.fullBlockHitboxes) {
//             cir.setReturnValue(VoxelShapes.fullCube());
//         }
//     }
// }
