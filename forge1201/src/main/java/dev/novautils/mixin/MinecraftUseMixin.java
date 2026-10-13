package dev.novautils.mixin;

import dev.novautils.modules.world.AirPlace;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AirPlace: a right-click aimed at nothing places the held block in the air instead. */
@Mixin(Minecraft.class)
public abstract class MinecraftUseMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void novautils$airPlace(CallbackInfo ci) {
        if (AirPlace.tryPlace()) ci.cancel();
    }
}
