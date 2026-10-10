package dev.novautils.mixin;

import dev.novautils.modules.render.NoRender;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender: no camera tilt when you get hurt. */
@Mixin(GameRenderer.class)
public abstract class HurtCamMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void novautils$hurtCam(CallbackInfo ci) {
        if (NoRender.hideHurtCam()) ci.cancel();
    }
}
