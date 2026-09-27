package dev.novautils.modules.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Marks floor tiles where hostile mobs can spawn (block light 0, solid floor, room to stand). */
public class LightOverlay extends BlockScanEsp {
    public LightOverlay() {
        super("LightOverlay", "Shows where mobs can spawn.", 10, 20);
    }

    @Override
    protected void scan(BlockPos center, List<AABB> out) {
        int r = radius.get();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -5; y <= 5; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!mc.level.hasChunkAt(pos)) continue;
                    if (!mc.level.getBlockState(pos).isAir()) continue;
                    if (!mc.level.getBlockState(pos.below()).isSolid()) continue;
                    if (!mc.level.getBlockState(pos.above()).isAir()) continue;
                    if (mc.level.getBrightness(LightLayer.BLOCK, pos) > 0) continue;
                    out.add(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 0.04, pos.getZ() + 1));
                }
            }
        }
    }

    @Override
    protected float[] rgb() {
        return new float[]{1f, 0.15f, 0.15f};
    }
}
