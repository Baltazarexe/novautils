package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import net.minecraft.world.phys.Vec3;

public class Spider extends Module {
    public Spider() {
        super("Spider", "Climb walls while pushing against them.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (mc.player.horizontalCollision && mc.options.keyUp.isDown()) {
            Vec3 v = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(v.x, 0.2, v.z);
        }
    }
}
