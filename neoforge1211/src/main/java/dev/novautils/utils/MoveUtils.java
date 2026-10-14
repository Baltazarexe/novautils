package dev.novautils.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Helpers for the movement modules: which way the keys point, in world space. */
public final class MoveUtils {
    private MoveUtils() {}

    /** True when any WASD key is held. */
    public static boolean moving() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return false;
        Vec2 v = p.input.getMoveVector();
        return v.x != 0 || v.y != 0;
    }

    /**
     * Horizontal unit vector the WASD keys point to, relative to where the player looks.
     * Zero when no key is held.
     */
    public static Vec3 inputDirection() {
        LocalPlayer p = Minecraft.getInstance().player;
        Vec2 in = p.input.getMoveVector(); // x = strafe (left +), y = forward (+)
        double forward = in.y, strafe = in.x;
        if (forward == 0 && strafe == 0) return Vec3.ZERO;
        double yaw = Math.toRadians(p.getYRot());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double x = strafe * cos - forward * sin;
        double z = forward * cos + strafe * sin;
        double len = Math.sqrt(x * x + z * z);
        return new Vec3(x / len, 0, z / len);
    }

    /** Horizontal speed of a movement vector. */
    public static double horizontal(Vec3 v) {
        return Math.sqrt(v.x * v.x + v.z * v.z);
    }
}
