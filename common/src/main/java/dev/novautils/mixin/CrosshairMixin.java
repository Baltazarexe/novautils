package dev.novautils.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Crosshair module: skip the vanilla crosshair (the HUD class is Hud on 26.3, Gui before). */
@Pseudo
@Mixin(targets = {"net.minecraft.client.gui.Hud", "net.minecraft.client.gui.Gui"})
public abstract class CrosshairMixin {
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void novautils$crosshair(CallbackInfo ci) {
        if (dev.novautils.modules.render.Crosshair.replacesVanilla()) ci.cancel();
    }
}
