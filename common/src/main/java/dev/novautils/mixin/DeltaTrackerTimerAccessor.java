package dev.novautils.mixin;

import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DeltaTracker.Timer.class)
public interface DeltaTrackerTimerAccessor {
    @Accessor("msPerTick")
    float novautils$getMsPerTick();

    @Mutable
    @Accessor("msPerTick")
    void novautils$setMsPerTick(float value);
}
