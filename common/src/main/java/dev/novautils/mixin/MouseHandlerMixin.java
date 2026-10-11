package dev.novautils.mixin;

import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.render.Zoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import dev.novautils.utils.PlatformCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While zoomed, the scroll wheel changes the zoom instead of the hotbar slot. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    /** Mouse buttons can be module keybinds too (side buttons, middle...). */
    @Inject(method = "onButton", at = @At("HEAD"))
    private void novautils$mouseBind(long window, net.minecraft.client.input.MouseButtonInfo info, int action, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        dev.novautils.utils.Keybinds.mouseChanged(info.button(), action != com.mojang.blaze3d.platform.InputConstants.RELEASE);
        if (action == com.mojang.blaze3d.platform.InputConstants.PRESS) ModuleManager.get().onKeyPressed(dev.novautils.utils.Keybinds.mouse(info.button()));
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void novautils$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        Minecraft mc = Minecraft.getInstance();
        if (PlatformCompat.currentScreen(mc) != null || !ModuleManager.get().isActive(Zoom.class)) return;
        if (ModuleManager.get().get(Zoom.class).onScroll(yOffset)) ci.cancel();
    }
}
