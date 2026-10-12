package dev.novautils.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

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

    @Accessor("destroyBlockPos")
    BlockPos novautils$getDestroyBlockPos();

    /** Sends a block action with the right prediction sequence number (PacketMine). */
    @Invoker("startPrediction")
    void novautils$startPrediction(ClientLevel level, PredictiveAction action);
}
