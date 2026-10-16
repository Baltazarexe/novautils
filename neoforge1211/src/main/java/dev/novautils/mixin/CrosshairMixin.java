package dev.novautils.mixin;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Crosshair module: skip the vanilla crosshair. */
@Mixin(Gui.class)
public abstract class CrosshairMixin {
    @Inject(method = "renderCrosshair(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
    private void novautils$crosshair(CallbackInfo ci) {
        if (dev.novautils.modules.render.Crosshair.replacesVanilla()) ci.cancel();
    }
}
