package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.world.phys.Vec3;

/**
 * Controlled elytra flight with no fireworks: forward moves you where you look, jump goes up,
 * sneak goes down, and with nothing pressed you hover in place. You still have to be gliding
 * (jump in the air with an elytra equipped, like normal). Servers that recompute elytra physics
 * may rubber-band you.
 */
public class ElytraFly extends Module {
    private final Setting<Double> speed = register(Setting.builder(Double.class, "speed")
        .description("Forward speed in blocks per tick.")
        .defaultValue(1.2).range(0.2, 4.0).build());

    private final Setting<Double> vertical = register(Setting.builder(Double.class, "vertical-speed")
        .description("Up/down speed in blocks per tick.")
        .defaultValue(0.6).range(0.1, 2.0).build());

    private final Setting<Boolean> hover = register(Setting.builder(Boolean.class, "hover")
        .description("Stay in place when no key is pressed.")
        .defaultValue(true).build());

    public ElytraFly() {
        super("ElytraFly", "Fly with an elytra without fireworks.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player == null || !mc.player.isFallFlying()) return;

        boolean forward = mc.options.keyUp.isDown();
        boolean up = mc.options.keyJump.isDown();
        boolean down = mc.options.keyShift.isDown();

        Vec3 v;
        if (forward) {
            v = mc.player.getLookAngle().scale(speed.get());
        } else if (hover.get()) {
            v = Vec3.ZERO;
        } else {
            return;
        }

        double y = v.y;
        if (up) y = vertical.get();
        else if (down) y = -vertical.get();
        else if (!forward) y = 0;

        mc.player.setDeltaMovement(v.x, y, v.z);
    }
}
