package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;

/**
 * Fly the boat you're steering: WASD moves along where you look, jump goes up, sprint goes down,
 * nothing pressed hovers. BoatMixin calls {@link #steer} right after vanilla's own boat control, so
 * the boat moves exactly as set. Anti-kick dips the boat a little every second so vanilla's
 * "vehicle floating" check never kicks you.
 */
public class BoatFly extends Module {
    private final Setting<Double> speed = register(Setting.builder(Double.class, "speed")
        .description("Horizontal speed in blocks per tick.").defaultValue(1.0).range(0.1, 5.0).build());

    private final Setting<Double> vertical = register(Setting.builder(Double.class, "vertical-speed")
        .description("Up (jump) / down (sprint key) speed.").defaultValue(0.4).range(0.05, 2.0).build());

    private final Setting<Boolean> faceView = register(Setting.builder(Boolean.class, "face-camera")
        .description("Turn the boat to where you're looking.").defaultValue(true).build());

    private final Setting<Boolean> antiKick = register(Setting.builder(Boolean.class, "anti-kick")
        .description("Drop a little every 20 ticks so the server doesn't kick you for floating.").defaultValue(true).build());

    private int ticks;

    public BoatFly() {
        super("BoatFly", "Fly with a boat: jump up, sprint key down.", Category.MOVEMENT);
    }

    public void steer(AbstractBoat boat) {
        var p = mc.player;
        if (p == null || p.getVehicle() != boat) return;
        ticks++;

        float yaw = p.getYRot();
        if (faceView.get()) {
            boat.setYRot(yaw);
            boat.yRotO = yaw;
        }
        double fwd = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        double side = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        double rad = Math.toRadians(yaw);
        double x = -Math.sin(rad) * fwd + Math.cos(rad) * side;
        double z = Math.cos(rad) * fwd + Math.sin(rad) * side;
        double len = Math.sqrt(x * x + z * z);
        if (len > 0) {
            x = x / len * speed.get();
            z = z / len * speed.get();
        }
        double y = 0;
        if (mc.options.keyJump.isDown()) y = vertical.get();
        else if (mc.options.keySprint.isDown()) y = -vertical.get();
        if (antiKick.get() && y >= 0 && ticks % 20 == 0) y = -0.04;
        boat.setDeltaMovement(new Vec3(x, y, z));
    }
}
