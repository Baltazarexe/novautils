package dev.novautils.modules.misc;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import dev.novautils.utils.Rotations;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps idle-kick timers from booting you: every so often (with a random wobble so it doesn't look
 * like a metronome) it jumps, swings, turns your head server-side and/or taps sneak.
 */
public class AntiAfk extends Module {
    private final Setting<Integer> interval = register(Setting.builder(Integer.class, "interval-seconds")
        .description("Average seconds between actions.")
        .defaultValue(20).range(3, 300).build());

    private final Setting<Boolean> jump = register(Setting.builder(Boolean.class, "jump")
        .description("Jump.").defaultValue(true).build());
    private final Setting<Boolean> swing = register(Setting.builder(Boolean.class, "swing")
        .description("Swing your arm.").defaultValue(true).build());
    private final Setting<Boolean> look = register(Setting.builder(Boolean.class, "look-around")
        .description("Turn your head (server side only, your view stays).").defaultValue(true).build());
    private final Setting<Boolean> sneak = register(Setting.builder(Boolean.class, "sneak")
        .description("Tap sneak.").defaultValue(false).build());

    private int timer;
    private int sneakTicks;

    public AntiAfk() {
        super("AntiAfk", "Moves a bit now and then so you don't get kicked for being idle.", Category.MISC);
    }

    private void reset() {
        int base = interval.get() * 20;
        timer = base + ThreadLocalRandom.current().nextInt(-base / 4, base / 4 + 1);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        if (sneakTicks > 0) mc.options.keyShift.setDown(false);
        sneakTicks = 0;
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null) return;

        if (sneakTicks > 0 && --sneakTicks == 0) mc.options.keyShift.setDown(false);
        if (timer-- > 0) return;
        reset();

        if (jump.get() && p.onGround()) p.jumpFromGround();
        if (swing.get()) InvUtils.swing();
        if (look.get() && !Rotations.busy()) {
            var r = ThreadLocalRandom.current();
            Rotations.rotate(p.getYRot() + r.nextFloat(-90f, 90f), r.nextFloat(-30f, 30f), null);
        }
        if (sneak.get()) {
            mc.options.keyShift.setDown(true);
            sneakTicks = 4;
        }
    }
}
