package dev.novautils.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
    @Accessor("destroyDelay")
    int novautils$getDestroyDelay();

    @Mutable
    @Accessor("destroyDelay")
    void novautils$setDestroyDelay(int value);

    @Accessor("destroyProgress")
    float novautils$getDestroyProgress();

    @Mutable
    @Accessor("destroyProgress")
    void novautils$setDestroyProgress(float value);
}
