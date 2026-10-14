package dev.novautils.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.novautils.modules.render.Xray;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Xray SEE_THROUGH when Sodium builds the chunks instead of vanilla (same three answers as
 * XrayMixin). @Pseudo + require = 0: without Sodium this mixin is simply skipped.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask")
public abstract class SodiumXrayMixin {
    @WrapOperation(method = "execute", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;getRenderShape()Lnet/minecraft/world/level/block/RenderShape;"))
    private RenderShape novautils$xrayShape(BlockState state, Operation<RenderShape> original) {
        return Xray.hides(state) ? RenderShape.INVISIBLE : original.call(state);
    }

    @WrapOperation(method = "execute", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;isSolidRender()Z"))
    private boolean novautils$xraySolid(BlockState state, Operation<Boolean> original) {
        return !Xray.hides(state) && original.call(state);
    }

    @WrapOperation(method = "execute", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/material/FluidState;isEmpty()Z"))
    private boolean novautils$xrayFluid(FluidState fluid, Operation<Boolean> original) {
        return Xray.hidesFluids() || original.call(fluid);
    }
}
