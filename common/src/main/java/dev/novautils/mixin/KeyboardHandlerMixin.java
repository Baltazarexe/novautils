package dev.novautils.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.novautils.modules.ModuleManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Module keybinds react to the key press itself instead of polling the key state once per tick,
 * so a quick tap that starts and ends between two ticks still toggles the module.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void novautils$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (!dev.novautils.NovaUtils.modulesReady) return;
        if (action == InputConstants.PRESS) ModuleManager.get().onKeyPressed(event.key());
    }
}
