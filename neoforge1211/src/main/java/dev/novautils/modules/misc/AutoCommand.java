package dev.novautils.modules.misc;

import dev.novautils.gui.Toasts;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;

/**
 * Runs a chat command on a timer: type the command (default {@code /rtp}), a delay, and it fires it
 * over and over, stopping after a number of runs or when you turn it off. Handy for travelling a
 * world with a random-teleport command to sample the map (e.g. for a seed cracker like SeedcrackerX
 * fed from StructureEsp/Search finds). Only runs the command you set - nothing is sent anywhere
 * else.
 */
public class AutoCommand extends Module {
    private final Setting<String> command = register(Setting.builder(String.class, "command")
        .description("The command to run, with or without the leading slash.").defaultValue("/rtp").build());

    private final Setting<Double> delay = register(Setting.builder(Double.class, "delay-seconds")
        .description("Seconds to wait between runs.").defaultValue(20.0).range(1.0, 300.0).build());

    private final Setting<Integer> runs = register(Setting.builder(Integer.class, "runs")
        .description("Stop after this many runs (0 = until you turn it off).").defaultValue(0).range(0, 10000).build());

    private final Setting<Boolean> firstImmediately = register(Setting.builder(Boolean.class, "first-now")
        .description("Run once as soon as it's enabled, then wait between the rest.").defaultValue(true).build());

    private int ticks;
    private int done;

    public AutoCommand() {
        super("AutoCommand", "Runs a chat command on a timer (e.g. /rtp every 20s).", Category.MISC);
    }

    /** How many times it has run since being enabled (test hook). */
    public int ranCount() {
        return done;
    }

    public void setCommand(String value) {
        command.set(value);
    }

    @Override
    public void onEnable() {
        done = 0;
        ticks = firstImmediately.get() ? (int) (delay.get() * 20) : 0;
    }

    private void fire() {
        if (mc.getConnection() == null) return;
        String cmd = command.get().trim();
        if (cmd.isEmpty()) {
            setEnabled(false);
            return;
        }
        if (cmd.startsWith("/")) mc.getConnection().sendCommand(cmd.substring(1));
        else mc.getConnection().sendChat(cmd);
        done++;
        if (runs.get() > 0 && done >= runs.get()) {
            Toasts.push("AutoCommand: done (" + done + " runs)", 0xFF66DD77);
            setEnabled(false);
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (++ticks >= (int) (delay.get() * 20)) {
            ticks = 0;
            fire();
        }
    }
}
