package dev.novautils.modules.world;

import dev.novautils.mixin.DeltaTrackerTimerAccessor;
import dev.novautils.mixin.MinecraftAccessor;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;

/** Speeds up (or slows down) your game clock. */
public class Timer extends Module {
    private final Setting<Double> multiplier = register(Setting.builder(Double.class, "multiplier")
        .description("Game speed multiplier (1.0 = normal).")
        .defaultValue(1.5).range(0.1, 10.0).build());

    private float originalMsPerTick = -1f;

    public Timer() {
        super("Timer", "Changes how fast the game ticks.", Category.WORLD);
    }

    private DeltaTrackerTimerAccessor timer() {
        return (DeltaTrackerTimerAccessor) ((MinecraftAccessor) mc).novautils$getTimer();
    }

    @Override
    public void onEnable() {
        originalMsPerTick = timer().novautils$getMsPerTick();
    }

    @Override
    public void onDisable() {
        if (originalMsPerTick > 0) timer().novautils$setMsPerTick(originalMsPerTick);
        originalMsPerTick = -1f;
    }

    @Override
    public void onTick() {
        if (originalMsPerTick > 0) timer().novautils$setMsPerTick((float) (originalMsPerTick / multiplier.get()));
    }
}
