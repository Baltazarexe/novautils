package dev.novautils.mixin;

import dev.novautils.modules.render.Xray;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Xray SEE_THROUGH: an ore's face next to a hidden block must still be drawn (vanilla culling). */
@Mixin(Block.class)
public abstract class XrayFaceMixin {
    @Inject(method = "shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z",
        at = @At("HEAD"), cancellable = true)
    private static void novautils$xrayFace(BlockState state, BlockState neighbor, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        if (Xray.hides(neighbor) && !Xray.hides(state)) cir.setReturnValue(true);
    }
}
