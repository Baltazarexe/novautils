package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.world.phys.Vec3;

public class LongJump extends Module {
    private final Setting<Double> boost = register(Setting.builder(Double.class, "boost")
        .description("Horizontal speed multiplier applied when you leave the ground.")
        .defaultValue(1.8).range(1.0, 4.0).build());

    private boolean wasOnGround;

    public LongJump() {
        super("LongJump", "Jump much farther.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        Vec3 v = mc.player.getDeltaMovement();
        if (wasOnGround && !mc.player.onGround() && v.y > 0.3) {
            mc.player.setDeltaMovement(v.x * boost.get(), v.y, v.z * boost.get());
        }
        wasOnGround = mc.player.onGround();
    }
}
