package dev.novautils.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.novautils.modules.render.Xray;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Xray SEE_THROUGH with Embeddium: its chunk meshing task, same three checks as vanilla. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask")
public abstract class EmbeddiumXrayMixin {
    @WrapOperation(method = "execute", require = 0, remap = false, at = @At(value = "INVOKE", remap = true,
        target = "Lnet/minecraft/world/level/block/state/BlockState;getRenderShape()Lnet/minecraft/world/level/block/RenderShape;"))
    private RenderShape novautils$xrayShape(BlockState state, Operation<RenderShape> original) {
        return Xray.hides(state) ? RenderShape.INVISIBLE : original.call(state);
    }

    @WrapOperation(method = "execute", require = 0, remap = false, at = @At(value = "INVOKE", remap = true,
        target = "Lnet/minecraft/world/level/block/state/BlockState;isSolidRender(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean novautils$xraySolid(BlockState state, BlockGetter level, BlockPos pos, Operation<Boolean> original) {
        return !Xray.hides(state) && original.call(state, level, pos);
    }

    @WrapOperation(method = "execute", require = 0, remap = false, at = @At(value = "INVOKE", remap = true,
        target = "Lnet/minecraft/world/level/material/FluidState;isEmpty()Z"))
    private boolean novautils$xrayFluid(FluidState fluid, Operation<Boolean> original) {
        return Xray.hidesFluids() || original.call(fluid);
    }
}
