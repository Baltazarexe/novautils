package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Auto-sneaks when you are about to walk off an edge. */
public class SafeWalk extends Module {
    private boolean forced;

    public SafeWalk() {
        super("SafeWalk", "Stops you from walking off edges.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        Vec3 v = mc.player.getDeltaMovement();
        BlockPos ahead = BlockPos.containing(mc.player.getX() + v.x * 4, mc.player.getY() - 0.5, mc.player.getZ() + v.z * 4);
        boolean edge = mc.player.onGround() && mc.level.getBlockState(ahead).isAir() && mc.level.getBlockState(ahead.below()).isAir();
        if (edge) {
            mc.options.keyShift.setDown(true);
            forced = true;
        } else if (forced) {
            mc.options.keyShift.setDown(false);
            forced = false;
        }
    }

    @Override
    public void onDisable() {
        if (forced) mc.options.keyShift.setDown(false);
        forced = false;
    }
}
