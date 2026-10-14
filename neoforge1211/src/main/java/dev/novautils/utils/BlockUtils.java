package dev.novautils.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/** Placing blocks the way a player does: against a real neighbour face, looking at it. */
public final class BlockUtils {
    private BlockUtils() {}

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** Solid, full-cube-ish blocks that are fine to build with (no sand/gravel, no chests/shulkers). */
    public static boolean isBuildingBlock(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem bi)) return false;
        Block b = bi.getBlock();
        if (b instanceof FallingBlock || b instanceof EntityBlock) return false;
        return b.defaultBlockState().isCollisionShapeFullBlock(mc().level, BlockPos.ZERO);
    }

    /** First hotbar slot matching, preferring the held one; -1 if none. */
    public static int findHotbar(Predicate<ItemStack> filter) {
        var inv = mc().player.getInventory();
        int held = inv.selected;
        if (filter.test(inv.getItem(held))) return held;
        for (int i = 0; i < 9; i++) if (filter.test(inv.getItem(i))) return i;
        return -1;
    }

    public static boolean replaceable(BlockPos pos) {
        return mc().level.getBlockState(pos).canBeReplaced();
    }

    /** A neighbour we can click on to put a block at {@code pos}, or null. Prefers the one below. */
    public static Direction placeSide(BlockPos pos) {
        Direction best = null;
        double bestDist = Double.MAX_VALUE;
        Vec3 eyes = mc().player.getEyePosition();
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            var state = mc().level.getBlockState(n);
            if (state.canBeReplaced() || !state.getFluidState().isEmpty()) continue;
            if (d == Direction.DOWN) return d;
            double dist = eyes.distanceToSqr(Vec3.atCenterOf(n));
            if (dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return best;
    }

    /** No entity (you included) in the way of a block at {@code pos}. */
    public static boolean freeOfEntities(BlockPos pos) {
        return mc().level.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(pos),
            e -> e.isAlive() && e.blocksBuilding).isEmpty();
    }

    /**
     * Places the block in hotbar {@code slot} at {@code pos}. With {@code rotate} the server first
     * sees you look at the face you click (silent, your camera doesn't move). Returns false if
     * there is nothing to place against.
     */
    public static boolean place(BlockPos pos, int slot, boolean rotate, boolean swapBack) {
        Direction side = placeSide(pos);
        if (side == null) return false;
        BlockPos against = pos.relative(side);
        Direction face = side.getOpposite();
        Vec3 hit = Vec3.atCenterOf(against).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);

        Runnable doPlace = () -> {
            var mc = mc();
            if (mc.player == null || mc.gameMode == null || !replaceable(pos)) return;
            int previous = mc.player.getInventory().selected;
            if (slot != previous) InvUtils.selectHotbar(slot);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, against, false));
            InvUtils.swing();
            if (swapBack && slot != previous) InvUtils.selectHotbar(previous);
        };

        if (rotate) Rotations.rotate(hit, doPlace);
        else doPlace.run();
        return true;
    }
}
