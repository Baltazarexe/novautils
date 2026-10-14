package dev.novautils.mixin;

import dev.novautils.modules.render.Xray;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Xray SEE_THROUGH: a kept block face next to a hidden block must still be drawn. */
@Mixin(Block.class)
public abstract class XrayFaceMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void novautils$xrayFace(BlockState state, BlockGetter level, BlockPos pos, Direction dir, BlockPos neighborPos,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (!Xray.forcesBright() || Xray.hides(state)) return;
        if (Xray.hides(level.getBlockState(neighborPos))) cir.setReturnValue(true);
    }
}
