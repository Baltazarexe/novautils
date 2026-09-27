package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.world.phys.Vec3;

public class HighJump extends Module {
    private final Setting<Double> power = register(Setting.builder(Double.class, "power")
        .description("Upward velocity of a jump (vanilla is 0.42).")
        .defaultValue(0.8).range(0.42, 3.0).build());

    private boolean wasOnGround;

    public HighJump() {
        super("HighJump", "Jump much higher.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        Vec3 v = mc.player.getDeltaMovement();
        if (wasOnGround && !mc.player.onGround() && v.y > 0.3) {
            mc.player.setDeltaMovement(v.x, power.get(), v.z);
        }
        wasOnGround = mc.player.onGround();
    }
}
