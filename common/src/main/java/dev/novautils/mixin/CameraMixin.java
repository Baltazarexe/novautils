package dev.novautils.mixin;

import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.render.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom divides the final FOV instead of touching the FOV option (so nothing is ever saved wrong). */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void novautils$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        float factor = ModuleManager.get().get(Zoom.class).fovFactor(partialTick);
        if (factor != 1f) cir.setReturnValue(cir.getReturnValue() * factor);
    }
}
