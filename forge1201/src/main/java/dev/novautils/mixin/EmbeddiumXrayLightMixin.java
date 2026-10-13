package dev.novautils.mixin;

import dev.novautils.modules.render.Xray;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Xray SEE_THROUGH with Embeddium: full light on the kept blocks. Embeddium's flat and smooth
 * light pipelines unpack block light, sky light and the corner shade from one packed word each,
 * so all of those channels report "fully lit" while see-through is on.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess")
public abstract class EmbeddiumXrayLightMixin {
    @Inject(method = {"getLightmap", "getEmissiveLightmap"}, at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void novautils$xrayLight(int word, CallbackInfoReturnable<Integer> cir) {
        if (Xray.forcesBright()) cir.setReturnValue(LightTexture.FULL_BRIGHT);
    }

    @Inject(method = {"unpackBL", "unpackSL"}, at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void novautils$xrayChannel(int word, CallbackInfoReturnable<Integer> cir) {
        if (Xray.forcesBright()) cir.setReturnValue(15);
    }

    @Inject(method = "unpackAO", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void novautils$xrayAo(int word, CallbackInfoReturnable<Float> cir) {
        if (Xray.forcesBright()) cir.setReturnValue(1f);
    }
}
