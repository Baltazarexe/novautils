package dev.novautils.mixin;

import net.minecraft.client.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Timer.class)
public interface TimerAccessor {
    @Accessor("msPerTick")
    float novautils$getMsPerTick();

    @Mutable
    @Accessor("msPerTick")
    void novautils$setMsPerTick(float value);
}
