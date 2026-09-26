package dev.novautils.modules.misc;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;

/** Nudges you every so often (a small jump) so idle-kick timers on a server don't boot you. */
public class AntiAfk extends Module {
    private final Setting<Integer> intervalSeconds = register(Setting.builder(Integer.class, "interval-seconds")
        .description("Seconds between each nudge.")
        .defaultValue(45)
        .range(10, 300)
        .build());

    private int timer;

    public AntiAfk() {
        super("AntiAfk", "Periodically jumps so you don't get kicked for being idle.", Category.MISC);
    }

    @Override
    public void onEnable() {
        timer = intervalSeconds.get() * 20;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (timer-- > 0) return;

        mc.player.setJumping(true);
        timer = intervalSeconds.get() * 20;
    }
}
