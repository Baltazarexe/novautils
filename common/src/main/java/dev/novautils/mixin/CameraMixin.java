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
    @org.spongepowered.asm.mixin.Shadow
    protected abstract void setPosition(double x, double y, double z);

    @org.spongepowered.asm.mixin.Shadow
    protected abstract void setRotation(float yRot, float xRot);

    /** Freecam: after vanilla placed the camera on the player, move it to the free camera. */
    @Inject(method = "update", at = @At("RETURN"))
    private void novautils$freecam(net.minecraft.client.DeltaTracker deltaTracker, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        var fc = dev.novautils.modules.render.Freecam.active();
        if (fc == null) return;
        var p = fc.frame();
        setPosition(p.x, p.y, p.z);
        setRotation(fc.yaw(), fc.pitch());
    }

    /** Freecam: "detached" so your own body gets rendered where you left it. */
    @Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
    private void novautils$freecamDetached(CallbackInfoReturnable<Boolean> cir) {
        if (dev.novautils.modules.render.Freecam.active() != null) cir.setReturnValue(true);
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void novautils$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        float factor = ModuleManager.get().get(Zoom.class).fovFactor(partialTick);
        if (factor != 1f) cir.setReturnValue(cir.getReturnValue() * factor);
    }
}
