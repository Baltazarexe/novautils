package dev.novautils.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("rightClickDelay")
    void novautils$setRightClickDelay(int value);

    @Accessor("deltaTracker")
    DeltaTracker.Timer novautils$getTimer();
}
