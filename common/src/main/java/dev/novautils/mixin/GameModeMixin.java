package dev.novautils.mixin;

import dev.novautils.utils.MiningGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets module-driven mining survive vanilla's "not holding attack, cancel mining" reset. */
@Mixin(MultiPlayerGameMode.class)
public abstract class GameModeMixin {
    @Inject(method = "stopDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void novautils$keepMining(CallbackInfo ci) {
        if (MiningGuard.active() && !Minecraft.getInstance().options.keyAttack.isDown()) ci.cancel();
    }
}
