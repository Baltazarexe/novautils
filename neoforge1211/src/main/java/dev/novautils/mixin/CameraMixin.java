package dev.novautils.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Freecam. (Zoom goes through Forge's ComputeFov event instead.) */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    /** After vanilla placed the camera on the player, move it to the free camera. */
    @Inject(method = "setup", at = @At("RETURN"))
    private void novautils$freecam(BlockGetter level, Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
        var fc = dev.novautils.modules.render.Freecam.active();
        if (fc == null) return;
        var p = fc.frame();
        setPosition(p.x, p.y, p.z);
        setRotation(fc.yaw(), fc.pitch());
    }

    /** Detached, so your own body gets rendered where you left it. */
    @Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
    private void novautils$freecamDetached(CallbackInfoReturnable<Boolean> cir) {
        if (dev.novautils.modules.render.Freecam.active() != null) cir.setReturnValue(true);
    }
}
