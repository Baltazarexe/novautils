package dev.novautils.modules.player;

import dev.novautils.mixin.MultiPlayerGameModeAccessor;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;

/**
 * Mines faster. "no-delay" removes the 5-tick pause the client adds between two blocks - that
 * alone is a big speed-up and is safe everywhere. "speed" adds extra breaking progress each tick;
 * servers that time mining themselves (most do) will put the block back if it's too high.
 */
public class FastBreak extends Module {
    private final Setting<Boolean> noDelay = register(Setting.builder(Boolean.class, "no-delay")
        .description("Removes the client's pause between breaking two blocks.")
        .defaultValue(true).build());

    private final Setting<Double> speed = register(Setting.builder(Double.class, "speed")
        .description("Breaking speed multiplier. 1.0 = vanilla; above ~1.3 needs a lenient server.")
        .defaultValue(1.0).range(1.0, 3.0).build());

    private float lastProgress;

    public FastBreak() {
        super("FastBreak", "Breaks blocks faster.", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (mc.gameMode == null) return;
        MultiPlayerGameModeAccessor gameMode = (MultiPlayerGameModeAccessor) mc.gameMode;
        if (noDelay.get()) gameMode.novautils$setDestroyDelay(0);

        if (!mc.gameMode.isDestroying()) {
            lastProgress = 0f;
            return;
        }
        float progress = gameMode.novautils$getDestroyProgress();
        if (speed.get() > 1.0 && progress > lastProgress) {
            // add (multiplier - 1) x this tick's vanilla progress: linear, not compounding
            float gained = progress - lastProgress;
            progress = Math.min(1f, progress + gained * (speed.get().floatValue() - 1f));
            gameMode.novautils$setDestroyProgress(progress);
        }
        lastProgress = progress;
    }
}
