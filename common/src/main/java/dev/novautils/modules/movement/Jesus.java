package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import net.minecraft.world.phys.Vec3;

/** Keeps you afloat on the surface of water (hold sneak to sink). */
public class Jesus extends Module {
    public Jesus() {
        super("Jesus", "Walk on water.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.player.isShiftKeyDown()) return;
        if (!mc.player.isInWater()) return;

        Vec3 v = mc.player.getDeltaMovement();
        boolean waterAbove = !mc.level.getFluidState(mc.player.blockPosition().above()).isEmpty();
        mc.player.setDeltaMovement(v.x, waterAbove ? 0.2 : 0.0, v.z);
    }
}
