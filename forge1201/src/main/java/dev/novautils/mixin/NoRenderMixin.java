package dev.novautils.mixin;

import dev.novautils.modules.render.NoRender;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender: the fire overlay. */
@Mixin(ScreenEffectRenderer.class)
public abstract class NoRenderMixin {
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void novautils$fire(CallbackInfo ci) {
        if (NoRender.hideFire()) ci.cancel();
    }
}
