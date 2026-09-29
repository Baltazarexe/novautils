package dev.novautils.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side ("silent") rotations. A module asks to face somewhere and hands over what it wants to
 * do while facing there; the angles go out in the next movement packet (LocalPlayerMixin swaps
 * them in around {@code sendPosition}) and the action runs right after that packet, so the server
 * has already seen you look at the block/mob when the click arrives. Your camera never moves.
 */
public final class Rotations {
    private Rotations() {}

    private static float yaw, pitch;
    private static Runnable action;
    private static boolean pending;
    private static int waited;

    private static boolean applied;
    private static float realYaw, realPitch;

    /** Face (yaw, pitch) on the server, then run {@code then}. Replaces any request not sent yet. */
    public static void rotate(float yaw, float pitch, Runnable then) {
        Rotations.yaw = yaw;
        Rotations.pitch = Mth.clamp(pitch, -90f, 90f);
        Rotations.action = then;
        pending = true;
        waited = 0;
    }

    public static void rotate(Vec3 target, Runnable then) {
        float[] a = anglesTo(target);
        rotate(a[0], a[1], then);
    }

    /** True while a request is waiting for its packet (modules use this to not queue a second one). */
    public static boolean busy() {
        return pending;
    }

    /** {yaw, pitch} from the player's eyes to {@code target}. */
    public static float[] anglesTo(Vec3 target) {
        LocalPlayer p = Minecraft.getInstance().player;
        Vec3 eyes = p.getEyePosition();
        double dx = target.x - eyes.x, dy = target.y - eyes.y, dz = target.z - eyes.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        return new float[]{Mth.wrapDegrees(yaw), pitch};
    }

    /** LocalPlayerMixin, before the movement packet is built. */
    public static void preSend(LocalPlayer p) {
        if (!pending) return;
        realYaw = p.getYRot();
        realPitch = p.getXRot();
        // keep the yaw continuous with the real one so the server sees a short turn
        p.setYRot(realYaw + Mth.wrapDegrees(yaw - realYaw));
        p.setXRot(pitch);
        applied = true;
    }

    /** LocalPlayerMixin, after the movement packet went out. */
    public static void postSend(LocalPlayer p) {
        if (!applied) return;
        p.setYRot(realYaw);
        p.setXRot(realPitch);
        applied = false;
        runPending();
    }

    /** If no movement packet was sent for a while (riding, loading), don't hold the action forever. */
    public static void tick() {
        if (pending && ++waited > 3) runPending();
    }

    private static void runPending() {
        pending = false;
        Runnable r = action;
        action = null;
        if (r != null && Minecraft.getInstance().player != null) r.run();
    }
}
