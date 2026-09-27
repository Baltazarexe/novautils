package dev.novautils.modules.world;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Mines every breakable block around you, nearest first. Great for clearing an area or tunnelling. */
public class Nuker extends Module {
    private final Setting<Integer> range = register(Setting.builder(Integer.class, "range")
        .description("Reach in blocks (survival reach is about 4).")
        .defaultValue(4).range(1, 6).build());

    private final Setting<Boolean> onlyOres = register(Setting.builder(Boolean.class, "only-ores")
        .description("Only mine ore blocks.")
        .defaultValue(false).build());

    private BlockPos current;

    public Nuker() {
        super("Nuker", "Mines all blocks around you.", Category.WORLD);
    }

    @Override
    public void onDisable() {
        current = null;
        if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
    }

    private boolean valid(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(mc.level, pos) < 0) return false;
        if (!state.getFluidState().isEmpty()) return false;
        if (onlyOres.get()) {
            String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            return path.contains("_ore") || path.equals("ancient_debris");
        }
        return true;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (current != null && (!valid(current) || mc.player.distanceToSqr(Vec3.atCenterOf(current)) > range.get() * range.get())) {
            current = null;
        }

        if (current == null) {
            int r = range.get();
            BlockPos center = mc.player.blockPosition();
            double best = Double.MAX_VALUE;
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos pos = center.offset(x, y, z);
                        double d = mc.player.distanceToSqr(Vec3.atCenterOf(pos));
                        if (d > r * r || d >= best || !valid(pos)) continue;
                        best = d;
                        current = pos.immutable();
                    }
                }
            }
        }

        if (current != null) {
            if (!mc.gameMode.isDestroying()) mc.gameMode.startDestroyBlock(current, Direction.UP);
            mc.gameMode.continueDestroyBlock(current, Direction.UP);
            dev.novautils.utils.InvUtils.swing();
        }
    }
}
