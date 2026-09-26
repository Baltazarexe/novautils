package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.world.phys.Vec3;

public class FastClimb extends Module {
    private final Setting<Double> speed = register(Setting.builder(Double.class, "speed")
        .description("Climb speed in blocks per tick.")
        .defaultValue(0.3).range(0.12, 1.0).build());

    public FastClimb() {
        super("FastClimb", "Climb ladders and vines faster.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player == null || !mc.player.onClimbable() || mc.player.isShiftKeyDown()) return;
        if (mc.options.keyUp.isDown() || mc.player.horizontalCollision) {
            Vec3 v = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(v.x, Math.max(v.y, speed.get()), v.z);
        }
    }
}
