package dev.novautils.mixin;

import dev.novautils.modules.render.Xray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Xray SEE_THROUGH: vanilla's smooth lighting and Fabric's Indigo renderer (flat and smooth) all
 * read block light and corner shading through this cache. Full light and no corner darkening
 * while see-through is on, so the kept blocks aren't drawn black inside the hidden stone.
 */
@Mixin(targets = "net.minecraft.client.renderer.block.BlockModelLighter$Cache")
public abstract class XrayLightCacheMixin {
    @Inject(method = "getLightCoords", at = @At("RETURN"), cancellable = true)
    private void novautils$xrayLight(CallbackInfoReturnable<Integer> cir) {
        if (Xray.forcesBright()) cir.setReturnValue(net.minecraft.util.LightCoordsUtil.FULL_BRIGHT);
    }

    @Inject(method = "getShadeBrightness", at = @At("RETURN"), cancellable = true)
    private void novautils$xrayShade(CallbackInfoReturnable<Float> cir) {
        if (Xray.forcesBright()) cir.setReturnValue(1f);
    }
}
