package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Jumps for you at the edge of a block. */
public class Parkour extends Module {
    public Parkour() {
        super("Parkour", "Automatically jumps at the edge of blocks.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        if (!mc.player.onGround() || mc.player.isShiftKeyDown()) return;
        Vec3 v = mc.player.getDeltaMovement();
        if (Math.hypot(v.x, v.z) < 0.05) return;

        BlockPos ahead = BlockPos.containing(mc.player.getX() + v.x * 3, mc.player.getY() - 0.5, mc.player.getZ() + v.z * 3);
        if (mc.level.getBlockState(ahead).isAir() && mc.level.getBlockState(ahead.below()).isAir()) {
            mc.player.setDeltaMovement(v.x, 0.42, v.z);
        }
    }
}
