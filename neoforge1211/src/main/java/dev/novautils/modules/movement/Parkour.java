package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.utils.MoveUtils;
import net.minecraft.world.phys.Vec3;

/** Jumps for you at the last possible moment before you walk off a block. */
public class Parkour extends Module {
    public Parkour() {
        super("Parkour", "Automatically jumps at the edge of blocks.", Category.MOVEMENT);
    }

    @Override
    public Vec3 onMove(Vec3 m) {
        var p = mc.player;
        if (p == null || !p.onGround() || p.isShiftKeyDown() || !MoveUtils.moving()) return m;
        // where we'll be after this step, half a block lower: nothing to stand on = jump now
        var next = p.getBoundingBox().move(m.x, -0.5, m.z).inflate(-0.001, 0, -0.001);
        if (!mc.level.noCollision(p, next)) return m;
        p.jumpFromGround();
        return new Vec3(m.x, p.getDeltaMovement().y, m.z);
    }
}
