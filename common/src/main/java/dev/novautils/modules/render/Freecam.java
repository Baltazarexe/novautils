package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.settings.Setting;
import dev.novautils.utils.PlatformCompat;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Leaves your body where it is and lets the camera fly free: WASD, jump/sneak for up/down, sprint
 * for faster. Your character stops moving (its input is cleared - LocalPlayerMixin) and you see it
 * standing there. Purely visual: the server still only sees your real position, so it's for
 * scouting, building checks and looking around corners, not for reaching anything.
 */
public class Freecam extends Module {
    private final Setting<Double> speed = register(Setting.builder(Double.class, "speed")
        .description("Blocks per second.").defaultValue(12.0).range(1.0, 80.0).build());

    private double x, y, z;
    private float yaw, pitch;
    private long lastFrame;

    public Freecam() {
        super("Freecam", "Fly the camera freely while your body stays put.", Category.RENDER);
    }

    public static Freecam active() {
        if (!dev.novautils.NovaUtils.modulesReady) return null;
        Freecam f = ModuleManager.get().get(Freecam.class);
        return f.isEnabled() ? f : null;
    }

    @Override
    public void onEnable() {
        if (mc.player == null) {
            setEnabled(false);
            return;
        }
        Vec3 cam = PlatformCompat.camera(mc).position();
        x = cam.x;
        y = cam.y;
        z = cam.z;
        yaw = mc.player.getYRot();
        pitch = mc.player.getXRot();
        lastFrame = 0;
    }

    /** Entity.turn on the local player while active: the mouse turns the camera instead. */
    public void turn(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch = Mth.clamp(pitch + (float) dy * 0.15f, -90f, 90f);
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    /** CameraMixin, once per frame: move by the keys held for the time since the last frame. */
    public Vec3 frame() {
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        if (PlatformCompat.currentScreen(mc) == null) {
            double f = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
            double s = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
            double u = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
            double r = Math.toRadians(yaw), sin = Math.sin(r), cos = Math.cos(r);
            double mx = s * cos - f * sin, mz = f * cos + s * sin;
            double len = Math.sqrt(mx * mx + mz * mz);
            if (len > 0) { mx /= len; mz /= len; }
            double v = speed.get() * (mc.options.keySprint.isDown() ? 2.5 : 1) * dt;
            x += mx * v;
            z += mz * v;
            y += u * v;
        }
        return new Vec3(x, y, z);
    }
}
