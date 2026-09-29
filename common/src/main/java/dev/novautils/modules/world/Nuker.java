package dev.novautils.modules.world;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import dev.novautils.utils.Rotations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Mines the blocks around you.
 * <ul>
 *   <li>ALL - everything in range.</li>
 *   <li>FLATTEN - only blocks at your feet level and above: levels the ground without digging down.</li>
 *   <li>SMASH - only blocks that break in one hit (grass, flowers, crops, leaves with shears...),
 *       several per tick.</li>
 *   <li>ORES - only ores and ancient debris.</li>
 * </ul>
 * Hits the face that points at you, looks at it server-side, won't mine what you can't see
 * (unless allowed) and never digs out the block you're standing on.
 */
public class Nuker extends Module {
    public enum Mode { ALL, FLATTEN, SMASH, ORES }
    public enum Sort { CLOSEST, TOP_DOWN }

    private final Setting<Mode> mode = register(Setting.builder(Mode.class, "mode")
        .description("Which blocks to mine.")
        .defaultValue(Mode.FLATTEN).build());

    private final Setting<Double> range = register(Setting.builder(Double.class, "range")
        .description("Reach in blocks (survival reach is 4.5).")
        .defaultValue(4.5).range(1.0, 6.0).build());

    private final Setting<Sort> sort = register(Setting.builder(Sort.class, "sort")
        .description("CLOSEST first, or TOP_DOWN so you don't get buried by gravel/sand.")
        .defaultValue(Sort.TOP_DOWN).build());

    private final Setting<Boolean> rotate = register(Setting.builder(Boolean.class, "rotate")
        .description("Face the block while mining (server side only).")
        .defaultValue(true).build());

    private final Setting<Boolean> throughWalls = register(Setting.builder(Boolean.class, "through-walls")
        .description("Mine blocks you can't see.")
        .defaultValue(false).build());

    private final Setting<Integer> perTick = register(Setting.builder(Integer.class, "instant-per-tick")
        .description("One-hit blocks broken per tick (SMASH, and when rotate is off).")
        .defaultValue(4).range(1, 12).build());

    private BlockPos current;
    private Direction currentFace;

    public Nuker() {
        super("Nuker", "Mines blocks around you.", Category.WORLD);
    }

    @Override
    public void onDisable() {
        current = null;
        dev.novautils.utils.MiningGuard.release();
        if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
    }

    private boolean instant(BlockPos pos, BlockState state) {
        return state.getDestroyProgress(mc.player, mc.level, pos) >= 1f;
    }

    private boolean valid(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(mc.level, pos) < 0) return false;
        Vec3 eyes = mc.player.getEyePosition();
        if (eyes.distanceToSqr(Vec3.atCenterOf(pos)) > range.get() * range.get()) return false;

        BlockPos feet = mc.player.blockPosition();
        if (pos.equals(feet.below())) return false; // never the block you stand on

        switch (mode.get()) {
            case FLATTEN -> { if (pos.getY() < feet.getY()) return false; }
            case SMASH -> { if (!instant(pos, state)) return false; }
            case ORES -> {
                String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                if (!path.endsWith("_ore") && !path.equals("ancient_debris")) return false;
            }
            default -> { }
        }
        return throughWalls.get() || visible(pos);
    }

    private boolean visible(BlockPos pos) {
        Vec3 eyes = mc.player.getEyePosition();
        BlockHitResult hit = mc.level.clip(new ClipContext(eyes, Vec3.atCenterOf(pos),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    /** The face of {@code pos} that points toward the eyes. */
    private Direction face(BlockPos pos) {
        Vec3 d = mc.player.getEyePosition().subtract(Vec3.atCenterOf(pos));
        double ax = Math.abs(d.x), ay = Math.abs(d.y), az = Math.abs(d.z);
        if (ay >= ax && ay >= az) return d.y > 0 ? Direction.UP : Direction.DOWN;
        if (ax >= az) return d.x > 0 ? Direction.EAST : Direction.WEST;
        return d.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private List<BlockPos> candidates() {
        int r = (int) Math.ceil(range.get());
        BlockPos center = mc.player.blockPosition();
        List<BlockPos> list = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r + 1, r))) {
            if (valid(pos)) list.add(pos.immutable());
        }
        Vec3 eyes = mc.player.getEyePosition();
        Comparator<BlockPos> byDist = Comparator.comparingDouble(p -> eyes.distanceToSqr(Vec3.atCenterOf(p)));
        list.sort(sort.get() == Sort.TOP_DOWN
            ? Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed().thenComparing(byDist)
            : byDist);
        return list;
    }

    private void mine(BlockPos pos, Direction face, boolean start) {
        Runnable r = () -> {
            if (mc.gameMode == null || !valid(pos)) return;
            if (start) mc.gameMode.startDestroyBlock(pos, face);
            else mc.gameMode.continueDestroyBlock(pos, face);
            InvUtils.swing();
        };
        if (rotate.get()) Rotations.rotate(Vec3.atCenterOf(pos), r);
        else r.run();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null || Rotations.busy()) return;

        // keep going on the block we already started
        if (current != null && valid(current) && mc.gameMode.isDestroying()) {
            dev.novautils.utils.MiningGuard.hold();
            mine(current, currentFace, false);
            return;
        }
        current = null;

        List<BlockPos> list = candidates();
        if (list.isEmpty()) return;

        // one-hit blocks: several per tick when we don't need to turn for each
        int budget = rotate.get() ? 1 : perTick.get();
        for (BlockPos pos : list) {
            if (budget <= 0) return;
            BlockState state = mc.level.getBlockState(pos);
            if (!instant(pos, state)) {
                if (mode.get() == Mode.SMASH) continue;
                if (budget < (rotate.get() ? 1 : perTick.get())) return; // already broke something this tick
                current = pos;
                currentFace = face(pos);
                dev.novautils.utils.MiningGuard.hold();
                mine(pos, currentFace, true);
                return;
            }
            mine(pos, face(pos), true);
            budget--;
        }
    }
}
