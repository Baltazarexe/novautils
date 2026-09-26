package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.world.phys.Vec3;

/** Sets your horizontal speed (blocks per tick) while you hold a movement key. */
public class Speed extends Module {
    private final Setting<Double> speed = register(Setting.builder(Double.class, "speed")
        .description("Horizontal speed in blocks per tick (sprinting is about 0.28).")
        .defaultValue(0.35).range(0.1, 1.5).build());

    public Speed() {
        super("Speed", "Moves you faster on the ground.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        boolean moving = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
            || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
        if (!moving || mc.player.isShiftKeyDown()) return;

        Vec3 v = mc.player.getDeltaMovement();
        double h = Math.hypot(v.x, v.z);
        if (h < 0.01) return;
        double s = speed.get();
        mc.player.setDeltaMovement(v.x / h * s, v.y, v.z / h * s);
    }
}
