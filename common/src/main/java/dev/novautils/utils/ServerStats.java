package dev.novautils.utils;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;

/**
 * Server TPS, measured the usual way: the server sends the world time every 20 of its ticks, so the
 * real time between two of those packets says how fast it's ticking. Averaged over the last few
 * so one hiccup doesn't swing the number.
 */
public final class ServerStats {
    private ServerStats() {}

    private static final int SAMPLES = 8;
    private static final float[] tps = new float[SAMPLES];
    private static int count, next;
    private static long lastTimePacket = -1, lastGameTime = -1;

    /** ClientPacketListenerMixin. */
    public static void onPacket(Packet<?> packet) {
        if (!(packet instanceof ClientboundSetTimePacket time)) return;
        long now = System.nanoTime();
        long gameTime = time.gameTime();
        if (lastTimePacket > 0 && gameTime > lastGameTime) {
            double seconds = (now - lastTimePacket) / 1e9;
            long ticks = gameTime - lastGameTime;
            if (seconds > 0.05) {
                tps[next] = (float) Math.min(20.0, ticks / seconds);
                next = (next + 1) % SAMPLES;
                count = Math.min(SAMPLES, count + 1);
            }
        }
        lastTimePacket = now;
        lastGameTime = gameTime;
    }

    /** Average TPS, or -1 before there is any data. */
    public static float tps() {
        if (count == 0) return -1;
        float sum = 0;
        for (int i = 0; i < count; i++) sum += tps[i];
        return sum / count;
    }

    /** Seconds since the server last sent the time (a big number = server stopped responding). */
    public static float secondsSinceLastPacket() {
        return lastTimePacket < 0 ? 0 : (System.nanoTime() - lastTimePacket) / 1e9f;
    }

    /** Called on disconnect so a new server starts fresh. */
    public static void reset() {
        count = 0;
        next = 0;
        lastTimePacket = -1;
        lastGameTime = -1;
    }
}
