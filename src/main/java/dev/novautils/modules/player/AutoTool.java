package dev.novautils.modules.player;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.utils.InvUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** While you hold attack on a block, switches to the fastest tool in your hotbar, then switches back. */
public class AutoTool extends Module {
    private int previousSlot = -1;

    public AutoTool() {
        super("AutoTool", "Picks the best hotbar tool for the block you mine.", Category.PLAYER);
    }

    @Override
    public void onDisable() {
        restore();
    }

    private void restore() {
        if (previousSlot != -1 && mc.player != null) InvUtils.selectHotbar(previousSlot);
        previousSlot = -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        if (!mc.options.keyAttack.isDown()) {
            restore();
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        int best = mc.player.getInventory().getSelectedSlot();
        float bestSpeed = mc.player.getInventory().getItem(best).getDestroySpeed(state);

        for (int i = 0; i < 9; i++) {
            float speed = mc.player.getInventory().getItem(i).getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }

        if (best != mc.player.getInventory().getSelectedSlot()) {
            if (previousSlot == -1) previousSlot = mc.player.getInventory().getSelectedSlot();
            InvUtils.selectHotbar(best);
        }
    }
}
