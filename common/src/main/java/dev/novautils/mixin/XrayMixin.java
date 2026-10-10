package dev.novautils.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.novautils.modules.render.Xray;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Xray SEE_THROUGH for vanilla chunk building: hidden blocks report "invisible" (no mesh), don't
 * count as solid for the cave-culling graph (or whole sections behind stone would be skipped), and
 * fluids can be left out. Runs on chunk-builder threads, so Xray only reads immutable snapshots.
 */
@Mixin(SectionCompiler.class)
public abstract class XrayMixin {
    private static final String SHAPE = "Lnet/minecraft/world/level/block/state/BlockState;getRenderShape()Lnet/minecraft/world/level/block/RenderShape;";
    private static final String SOLID = "Lnet/minecraft/world/level/block/state/BlockState;isSolidRender()Z";
    private static final String FLUID_EMPTY = "Lnet/minecraft/world/level/material/FluidState;isEmpty()Z";

    /** No corner shadows on what stays visible: its neighbours are hidden, the shading would be wrong. */
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "compile", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/chunk/SectionCompiler;ambientOcclusion:Z"))
    private boolean novautils$xrayNoAo(boolean original) {
        return original && !Xray.forcesBright();
    }

    @WrapOperation(method = "compile", at = @At(value = "INVOKE", target = SHAPE))
    private RenderShape novautils$xrayShape(BlockState state, Operation<RenderShape> original) {
        return Xray.hides(state) ? RenderShape.INVISIBLE : original.call(state);
    }

    @WrapOperation(method = "compile", at = @At(value = "INVOKE", target = SOLID))
    private boolean novautils$xraySolid(BlockState state, Operation<Boolean> original) {
        return !Xray.hides(state) && original.call(state);
    }

    @WrapOperation(method = "compile", at = @At(value = "INVOKE", target = FLUID_EMPTY))
    private boolean novautils$xrayFluid(FluidState fluid, Operation<Boolean> original) {
        return Xray.hidesFluids() || original.call(fluid);
    }
}
