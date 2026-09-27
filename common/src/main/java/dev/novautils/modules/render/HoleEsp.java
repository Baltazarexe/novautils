package dev.novautils.modules.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Highlights one-block holes whose four walls are bedrock or obsidian - safe spots to stand in. */
public class HoleEsp extends BlockScanEsp {
    public HoleEsp() {
        super("HoleEsp", "Shows safe one-block holes (bedrock/obsidian walls).", 10, 20);
    }

    private static boolean tough(BlockState s) {
        return s.is(Blocks.BEDROCK) || s.is(Blocks.OBSIDIAN);
    }

    @Override
    protected void scan(BlockPos center, List<AABB> out) {
        int r = radius.get();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -6; y <= 6; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!mc.level.hasChunkAt(pos)) continue;
                    if (!mc.level.getBlockState(pos).isAir() || !mc.level.getBlockState(pos.above()).isAir()) continue;
                    if (!tough(mc.level.getBlockState(pos.below()))) continue;

                    boolean walls = true;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        if (!tough(mc.level.getBlockState(pos.relative(d)))) {
                            walls = false;
                            break;
                        }
                    }
                    if (walls) out.add(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 0.1, pos.getZ() + 1));
                }
            }
        }
    }

    @Override
    protected float[] rgb() {
        return new float[]{0.2f, 1f, 0.4f};
    }
}
