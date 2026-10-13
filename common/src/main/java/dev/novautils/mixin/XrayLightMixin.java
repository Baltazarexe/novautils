package dev.novautils.mixin;

import dev.novautils.modules.render.Xray;
import net.minecraft.client.renderer.block.BlockModelLighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Xray SEE_THROUGH: the blocks we keep sit inside hidden stone, where the real light is 0, so they
 * would be drawn pitch black. Give their faces full light instead.
 */
@Mixin(BlockModelLighter.class)
public abstract class XrayLightMixin {
    @Inject(method = "getLightCoords", at = @At("RETURN"), cancellable = true)
    private void novautils$xrayLight(CallbackInfoReturnable<Integer> cir) {
        if (Xray.forcesBright()) cir.setReturnValue(net.minecraft.util.LightCoordsUtil.FULL_BRIGHT);
    }
}
