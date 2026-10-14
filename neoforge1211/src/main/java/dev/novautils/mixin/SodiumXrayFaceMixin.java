package dev.novautils.mixin;

import dev.novautils.modules.render.Xray;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Xray SEE_THROUGH with Sodium: draw every face of the blocks we keep (their neighbours are gone). */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext")
public abstract class SodiumXrayFaceMixin {
    @Inject(method = "isFaceCulled", at = @At("HEAD"), cancellable = true, require = 0)
    private void novautils$xrayFace(Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (Xray.forcesBright()) cir.setReturnValue(false);
    }
}
