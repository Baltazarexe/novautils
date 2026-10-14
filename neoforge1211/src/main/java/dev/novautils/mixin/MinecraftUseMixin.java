package dev.novautils.mixin;

import dev.novautils.modules.player.AutoTool;
import dev.novautils.modules.world.AirPlace;
import dev.novautils.modules.world.GhostHand;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right-click hooks: GhostHand (use things through walls), then AirPlace (block in the air). */
@Mixin(Minecraft.class)
public abstract class MinecraftUseMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void novautils$airPlace(CallbackInfo ci) {
        if (GhostHand.tryUse() || AirPlace.tryPlace()) ci.cancel();
    }

    /** AutoTool (silent): borrow the best tool just for this tick's attack handling. */
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void novautils$toolBefore(CallbackInfo ci) {
        AutoTool.beforeKeybinds();
    }

    @Inject(method = "handleKeybinds", at = @At("RETURN"))
    private void novautils$toolAfter(CallbackInfo ci) {
        AutoTool.afterKeybinds();
    }
}
