package dev.novautils.modules.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Highlights floor tiles inside 1x2 tunnels (walled on both sides along one axis, roof above). */
public class TunnelEsp extends BlockScanEsp {
    public TunnelEsp() {
        super("TunnelEsp", "Shows 1x2 tunnels.", 12, 24);
    }

    private boolean solid(BlockPos p) {
        return mc.level.getBlockState(p).isSolid();
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
                    if (!solid(pos.below()) || !solid(pos.above(2))) continue;

                    boolean ew = solid(pos.east()) && solid(pos.west()) && solid(pos.above().east()) && solid(pos.above().west());
                    boolean ns = solid(pos.north()) && solid(pos.south()) && solid(pos.above().north()) && solid(pos.above().south());
                    if (ew != ns) out.add(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 0.06, pos.getZ() + 1));
                }
            }
        }
    }

    @Override
    protected float[] rgb() {
        return new float[]{0.3f, 0.6f, 1f};
    }
}
