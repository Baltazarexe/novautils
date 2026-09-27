package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import net.minecraft.world.phys.Vec3;

public class AirJump extends Module {
    private boolean wasDown;

    public AirJump() {
        super("AirJump", "Jump again while in the air.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        boolean down = mc.options.keyJump.isDown();
        if (down && !wasDown && !mc.player.onGround() && !mc.player.getAbilities().flying) {
            Vec3 v = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(v.x, 0.42, v.z);
            mc.player.resetFallDistance();
        }
        wasDown = down;
    }
}
