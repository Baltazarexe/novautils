package dev.novautils.utils;

/**
 * Vanilla calls {@code stopDestroyBlock()} every tick you're not holding left click, which resets
 * any mining a module started (so Nuker/AutoClicker would never finish a block). Modules call
 * {@link #hold()} each tick they're mining; while held, GameModeMixin skips that reset.
 */
public final class MiningGuard {
    private MiningGuard() {}

    private static int ticks;
    private static int until = -1;

    /** ModuleManager, once per client tick. */
    public static void tick() {
        ticks++;
    }

    public static void hold() {
        until = ticks + 2;
    }

    public static void release() {
        until = -1;
    }

    public static boolean active() {
        return ticks <= until;
    }
}
