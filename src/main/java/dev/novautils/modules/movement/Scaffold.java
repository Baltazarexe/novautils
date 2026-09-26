package dev.novautils.modules.movement;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.utils.InvUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Places a block under your feet whenever there is air there. Picks a block from your hotbar. */
public class Scaffold extends Module {
    private int previousSlot = -1;

    public Scaffold() {
        super("Scaffold", "Automatically places blocks under you.", Category.MOVEMENT);
    }

    @Override
    public void onDisable() {
        if (previousSlot != -1 && mc.player != null) InvUtils.selectHotbar(previousSlot);
        previousSlot = -1;
    }

    private int findBlockSlot() {
        int held = mc.player.getInventory().getSelectedSlot();
        if (mc.player.getInventory().getItem(held).getItem() instanceof BlockItem) return held;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() instanceof BlockItem) return i;
        }
        return -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        BlockPos target = mc.player.blockPosition().below();
        if (!mc.level.getBlockState(target).canBeReplaced()) return;

        int slot = findBlockSlot();
        if (slot == -1) return;
        if (slot != mc.player.getInventory().getSelectedSlot()) {
            if (previousSlot == -1) previousSlot = mc.player.getInventory().getSelectedSlot();
            InvUtils.selectHotbar(slot);
        }

        for (Direction d : Direction.values()) {
            BlockPos neighbor = target.relative(d);
            if (!mc.level.getBlockState(neighbor).isSolid()) continue;

            Direction face = d.getOpposite();
            Vec3 hit = new Vec3(neighbor.getX() + 0.5 + face.getStepX() * 0.5,
                neighbor.getY() + 0.5 + face.getStepY() * 0.5,
                neighbor.getZ() + 0.5 + face.getStepZ() * 0.5);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, neighbor, false));
            mc.player.swing(InteractionHand.MAIN_HAND);
            return;
        }
    }
}
