package dev.novautils.modules.player;

import dev.novautils.mixin.MultiPlayerGameModeAccessor;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;

/**
 * Mines faster. "no-delay" removes the pause the client inserts between two blocks and is
 * harmless; "speed" multiplies the local breaking progress, which a server that tracks mining
 * itself will reject (you will see the block reappear).
 */
public class FastBreak extends Module {
    private final Setting<Boolean> noDelay = register(Setting.builder(Boolean.class, "no-delay")
        .description("Removes the client's pause between breaking two blocks.")
        .defaultValue(true).build());

    private final Setting<Double> speed = register(Setting.builder(Double.class, "speed")
        .description("Breaking progress multiplier. 1.0 = vanilla; high values need a lenient server.")
        .defaultValue(1.0).range(1.0, 5.0).build());

    public FastBreak() {
        super("FastBreak", "Breaks blocks faster.", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (mc.gameMode == null) return;
        MultiPlayerGameModeAccessor gameMode = (MultiPlayerGameModeAccessor) mc.gameMode;

        if (noDelay.get()) gameMode.novautils$setDestroyDelay(0);

        if (speed.get() > 1.0 && mc.gameMode.isDestroying()) {
            float progress = gameMode.novautils$getDestroyProgress();
            if (progress > 0f) {
                gameMode.novautils$setDestroyProgress(Math.min(1f, progress * speed.get().floatValue()));
            }
        }
    }
}
