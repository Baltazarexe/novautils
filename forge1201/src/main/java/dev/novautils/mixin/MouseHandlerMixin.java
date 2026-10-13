package dev.novautils.mixin;

import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.render.Zoom;
import dev.novautils.utils.PlatformCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse buttons as module keybinds; while zoomed, the scroll wheel changes the zoom. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void novautils$mouseBind(long window, int button, int action, int mods, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        dev.novautils.utils.Keybinds.mouseChanged(button, action != com.mojang.blaze3d.platform.InputConstants.RELEASE);
        if (action == com.mojang.blaze3d.platform.InputConstants.PRESS) ModuleManager.get().onKeyPressed(dev.novautils.utils.Keybinds.mouse(button));
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void novautils$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        Minecraft mc = Minecraft.getInstance();
        if (PlatformCompat.currentScreen(mc) != null || !ModuleManager.get().isActive(Zoom.class)) return;
        if (ModuleManager.get().get(Zoom.class).onScroll(yOffset)) ci.cancel();
    }
}
