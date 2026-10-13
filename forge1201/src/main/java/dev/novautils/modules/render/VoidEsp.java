package dev.novautils.modules.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Marks columns where the bottom layer of the world is missing (holes into the void). */
public class VoidEsp extends BlockScanEsp {
    public VoidEsp() {
        super("VoidEsp", "Shows holes in the bottom of the world.", 24, 48);
    }

    @Override
    protected void scan(BlockPos center, List<AABB> out) {
        int r = radius.get();
        int minY = mc.level.getMinBuildHeight();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                BlockPos pos = new BlockPos(center.getX() + x, minY, center.getZ() + z);
                if (!mc.level.hasChunkAt(pos)) continue;
                if (mc.level.getBlockState(pos).isAir()) {
                    out.add(new AABB(pos.getX(), minY, pos.getZ(), pos.getX() + 1, minY + 0.1, pos.getZ() + 1));
                }
            }
        }
    }

    @Override
    protected float[] rgb() {
        return new float[]{0.7f, 0.2f, 1f};
    }
}
