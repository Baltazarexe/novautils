package dev.novautils.mixin;

import dev.novautils.modules.render.Xray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Xray SEE_THROUGH with Sodium: full light on the blocks that stay visible (see XrayLightMixin). */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess")
public abstract class SodiumXrayLightMixin {
    @Inject(method = {"getLightmap", "getEmissiveLightmap"}, at = @At("RETURN"), cancellable = true, require = 0)
    private static void novautils$xrayLight(int word, CallbackInfoReturnable<Integer> cir) {
        if (Xray.forcesBright()) cir.setReturnValue(net.minecraft.util.LightCoordsUtil.FULL_BRIGHT);
    }
}
