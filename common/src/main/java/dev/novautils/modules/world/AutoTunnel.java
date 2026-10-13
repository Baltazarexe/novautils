package dev.novautils.modules.world;

import dev.novautils.gui.Toasts;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.BlockUtils;
import dev.novautils.utils.InvUtils;
import dev.novautils.utils.MiningGuard;
import dev.novautils.utils.Rotations;
import dev.novautils.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Digs a straight tunnel in the direction you face when you turn it on, walking forward as it
 * goes. Stops by itself in front of lava, water or a drop, fills small holes in the floor with
 * blocks from your hotbar, and can leave a torch every few blocks. Turn it off to stop.
 */
public class AutoTunnel extends Module {
    public enum Size { ONE_BY_TWO, THREE_BY_THREE }

    private final Setting<Size> size = register(Setting.builder(Size.class, "size")
        .description("1 wide 2 tall, or 3 by 3.").defaultValue(Size.ONE_BY_TWO).build());

    private final Setting<Boolean> torches = register(Setting.builder(Boolean.class, "torches")
        .description("Place a torch from your hotbar every few blocks.").defaultValue(true).build());

    private final Setting<Integer> torchEvery = register(Setting.builder(Integer.class, "torch-every")
        .description("Blocks between torches.").defaultValue(9).range(3, 20)
        .visible(() -> torches.get()).build());

    private final Setting<Boolean> fillFloor = register(Setting.builder(Boolean.class, "fill-floor")
        .description("Fill holes in the floor ahead with blocks from your hotbar.").defaultValue(true).build());

    private final Setting<Integer> maxLength = register(Setting.builder(Integer.class, "max-length")
        .description("Stop after this many blocks (0 = no limit).").defaultValue(0).range(0, 1000).build());

    private Direction dir;
    private BlockPos start;
    private BlockPos current;
    private int lastTorchAt;

    public AutoTunnel() {
        super("AutoTunnel", "Digs a straight tunnel where you face, stops at lava and drops.", Category.WORLD);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) {
            setEnabled(false);
            return;
        }
        dir = mc.player.getDirection();
        start = mc.player.blockPosition();
        current = null;
        lastTorchAt = 0;
        mc.player.setYRot(dir.toYRot());
    }

    @Override
    public void onDisable() {
        if (mc.options != null) mc.options.keyUp.setDown(false);
        MiningGuard.release();
        if (mc.gameMode != null && current != null) mc.gameMode.stopDestroyBlock();
        current = null;
    }

    private void stop(String why) {
        Toasts.push("AutoTunnel: " + why, 0xFFFFAA44);
        setEnabled(false);
    }

    /** How far along the tunnel the player is. */
    private int progress() {
        BlockPos p = mc.player.blockPosition();
        return (p.getX() - start.getX()) * dir.getStepX() + (p.getZ() - start.getZ()) * dir.getStepZ();
    }

    /** The tunnel blocks at {@code k} steps ahead of the player, on the tunnel's own line. */
    private List<BlockPos> slice(int k) {
        BlockPos p = mc.player.blockPosition();
        // keep the tunnel on the line it started on, even if you got nudged sideways
        BlockPos onLine = dir.getAxis() == Direction.Axis.X ? new BlockPos(p.getX(), start.getY(), start.getZ())
            : new BlockPos(start.getX(), start.getY(), p.getZ());
        BlockPos center = onLine.relative(dir, k);
        Direction side = dir.getClockWise();
        List<BlockPos> out = new ArrayList<>();
        boolean big = size.get() == Size.THREE_BY_THREE;
        for (int y = big ? 2 : 1; y >= 0; y--) {
            for (int s = big ? -1 : 0; s <= (big ? 1 : 0); s++) out.add(center.relative(side, s).above(y));
        }
        return out;
    }

    private static boolean dangerous(BlockState s) {
        return !s.getFluidState().isEmpty();
    }

    private boolean minable(BlockPos pos) {
        BlockState s = mc.level.getBlockState(pos);
        return !s.isAir() && s.getFluidState().isEmpty() && s.getDestroySpeed(mc.level, pos) >= 0;
    }

    private void mine(BlockPos pos) {
        boolean starting = !pos.equals(current);
        current = pos;
        Direction face = dir.getOpposite();
        Runnable r = () -> {
            if (mc.gameMode == null) return;
            if (starting) mc.gameMode.startDestroyBlock(pos, face);
            else mc.gameMode.continueDestroyBlock(pos, face);
            InvUtils.swing();
        };
        MiningGuard.hold();
        Rotations.rotate(Vec3.atCenterOf(pos), r);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (maxLength.get() > 0 && progress() >= maxLength.get()) {
            stop("done (" + maxLength.get() + " blocks)");
            return;
        }

        // look before digging: lava or water next to the next slice, or a hole under it
        for (BlockPos pos : slice(1)) {
            for (Direction d : Direction.values()) {
                if (dangerous(mc.level.getBlockState(pos.relative(d)))) {
                    stop("liquid ahead at " + pos.relative(d).toShortString());
                    return;
                }
            }
        }
        for (BlockPos pos : slice(1)) {
            if (pos.getY() != mc.player.getBlockY()) continue;
            BlockPos floor = pos.below();
            if (!mc.level.getBlockState(floor).canBeReplaced()) continue;
            int slot = fillFloor.get() ? BlockUtils.findHotbar(BlockUtils::isBuildingBlock) : -1;
            if (slot < 0) {
                stop("hole in the floor ahead");
                return;
            }
            if (!Rotations.busy()) BlockUtils.place(floor, slot, true, true);
            mc.options.keyUp.setDown(false);
            return;
        }

        if (Rotations.busy()) return;
        // dig the next slice (top first, so falling gravel lands where we dig next), then the one after
        for (int k = 1; k <= 2; k++) {
            for (BlockPos pos : slice(k)) {
                if (!minable(pos)) continue;
                if (k == 1) mc.options.keyUp.setDown(false);
                mine(pos);
                if (k == 1) return;
                break;
            }
        }
        current = mc.gameMode.isDestroying() ? current : null;

        // the way is clear: walk
        mc.player.setYRot(dir.toYRot());
        mc.options.keyUp.setDown(true);

        if (torches.get() && progress() - lastTorchAt >= torchEvery.get()) {
            int slot = BlockUtils.findHotbar(s -> s.is(Items.TORCH));
            BlockPos at = mc.player.blockPosition().relative(dir.getOpposite());
            if (slot >= 0 && mc.level.getBlockState(at).isAir() && !mc.level.getBlockState(at.below()).canBeReplaced()) {
                BlockUtils.place(at, slot, true, true);
                lastTorchAt = progress();
            }
        }
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (mc.player == null || dir == null) return;
        for (BlockPos pos : slice(1)) {
            if (!minable(pos)) continue;
            AABB box = new AABB(pos);
            RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, 0x408B9CFF));
        }
    }
}
