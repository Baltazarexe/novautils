package dev.novautils.mixin;

import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.movement.Jesus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Jesus (solid mode): water/lava surfaces get a floor, for you only. */
@Mixin(LiquidBlock.class)
public abstract class LiquidBlockMixin {
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void novautils$jesus(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx,
                                 CallbackInfoReturnable<VoxelShape> cir) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        if (!ModuleManager.get().isActive(Jesus.class)) return;
        VoxelShape floor = ModuleManager.get().get(Jesus.class).floorFor(state, level, pos, ctx);
        if (floor != null) cir.setReturnValue(floor);
    }
}
