package dev.novautils.mixin;

import dev.novautils.modules.render.NoRender;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender's screen-overlay hooks. The fire method is "submitFire" on 26.3, "renderFire" on 26.1.2. */
@Mixin(ScreenEffectRenderer.class)
public abstract class NoRenderMixin {
    @Inject(method = {"submitFire", "renderFire"}, at = @At("HEAD"), cancellable = true, require = 1)
    private static void novautils$fire(CallbackInfo ci) {
        if (NoRender.hideFire()) ci.cancel();
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void novautils$totem(CallbackInfo ci) {
        if (NoRender.hideTotem()) ci.cancel();
    }
}
