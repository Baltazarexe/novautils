package dev.novautils.modules.render;

import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.EspRender;
import dev.novautils.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Marks floor tiles where hostile mobs can spawn (block light 0, sturdy floor, room to stand), and
 * shows where to put torches: the spot that lights up the most of those tiles, then the best next
 * spot for what's still dark, and so on. Light is simulated like the game does it - a torch gives
 * 14, every block it travels costs at least 1, walls stop it - so it accounts for corners and
 * caves instead of just measuring distance.
 */
public class LightOverlay extends BlockScanEsp {
    /** A torch suggestion and how many dark tiles it would light. */
    public record TorchSpot(BlockPos pos, int lights) {}

    private static final int TORCH_LIGHT = 14;

    private final Setting<Integer> torchSpots = register(Setting.builder(Integer.class, "torch-spots")
        .description("How many torch suggestions to show (best first). 0 = off.")
        .defaultValue(3).range(0, 8).build());

    private final Setting<Integer> torchColor = register(Setting.builder(Integer.class, "torch-color")
        .description("Colour of the torch suggestions.")
        .defaultValue(0xE0FFC830).asColor()
        .visible(() -> torchSpots.get() > 0).build());

    private final List<BlockPos> dark = new ArrayList<>();
    private final List<TorchSpot> spots = new ArrayList<>();

    public LightOverlay() {
        super("LightOverlay", "Shows where mobs can spawn and the best places for torches.", 10, 20);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        dark.clear();
        spots.clear();
    }

    private boolean spawnable(BlockPos pos) {
        if (!mc.level.getBlockState(pos).isAir() || !mc.level.getBlockState(pos.above()).isAir()) return false;
        BlockPos below = pos.below();
        if (!mc.level.getBlockState(below).isFaceSturdy(mc.level, below, Direction.UP)) return false;
        return mc.level.getBrightness(LightLayer.BLOCK, pos) == 0;
    }

    @Override
    protected void scan(BlockPos center, List<AABB> out) {
        dark.clear();
        int r = radius.get();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -5; y <= 5; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!mc.level.hasChunkAt(pos) || !spawnable(pos)) continue;
                    dark.add(pos.immutable());
                    out.add(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 0.04, pos.getZ() + 1));
                }
            }
        }
        findTorchSpots();
    }

    /**
     * Where light from a torch at {@code torch} reaches with level >= 1. Flood fill like the
     * lighting engine: -1 per block (more through blocks that dampen light), opaque blocks stop it.
     */
    private Set<Long> lit(BlockPos torch) {
        Map<Long, Integer> level = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        level.put(torch.asLong(), TORCH_LIGHT);
        queue.add(torch);
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            int l = level.get(p.asLong());
            for (Direction d : Direction.values()) {
                n.setWithOffset(p, d);
                BlockState s = mc.level.getBlockState(n);
                int damp = s.getLightDampening();
                if (damp >= 15) continue;
                int next = l - Math.max(1, damp);
                if (next <= 0) continue;
                long key = n.asLong();
                Integer had = level.get(key);
                if (had != null && had >= next) continue;
                level.put(key, next);
                queue.add(n.immutable());
            }
        }
        return level.keySet();
    }

    private void findTorchSpots() {
        spots.clear();
        if (torchSpots.get() == 0 || dark.isEmpty()) return;

        Set<Long> remaining = new HashSet<>();
        for (BlockPos p : dark) remaining.add(p.asLong());

        for (int k = 0; k < torchSpots.get() && !remaining.isEmpty(); k++) {
            // cheap first pass: rank candidates by how many dark tiles are close enough at all
            List<BlockPos> candidates = new ArrayList<>();
            for (BlockPos p : dark) if (remaining.contains(p.asLong())) candidates.add(p);
            int stride = Math.max(1, candidates.size() / 300);
            List<long[]> ranked = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i += stride) {
                BlockPos c = candidates.get(i);
                int near = 0;
                for (BlockPos t : candidates) if (c.distManhattan(t) < TORCH_LIGHT) near++;
                ranked.add(new long[]{i, near});
            }
            ranked.sort(Comparator.comparingLong((long[] e) -> e[1]).reversed());

            // exact pass on the most promising few: real light flood fill
            TorchSpot best = null;
            Set<Long> bestCovered = null;
            for (int i = 0; i < Math.min(12, ranked.size()); i++) {
                BlockPos c = candidates.get((int) ranked.get(i)[0]);
                Set<Long> covered = new HashSet<>(lit(c));
                covered.retainAll(remaining);
                if (best == null || covered.size() > best.lights()) {
                    best = new TorchSpot(c, covered.size());
                    bestCovered = covered;
                }
            }
            if (best == null || best.lights() == 0) break;
            spots.add(best);
            remaining.removeAll(bestCovered);
            remaining.remove(best.pos().asLong());
        }
    }

    /** Current dark tile count (tests, HUD). */
    public int darkCount() {
        return dark.size();
    }

    /** Current torch suggestions, best first. */
    public List<TorchSpot> torchSpots() {
        return spots;
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        super.onRenderWorld(context);
        if (spots.isEmpty()) return;
        Vec3 cam = context.cameraPos();
        int c = torchColor.get();
        for (int i = 0; i < spots.size(); i++) {
            TorchSpot s = spots.get(i);
            BlockPos p = s.pos();
            // a torch-shaped stick standing where the torch should go
            AABB stick = new AABB(p.getX() + 0.4, p.getY(), p.getZ() + 0.4, p.getX() + 0.6, p.getY() + 0.65, p.getZ() + 0.6);
            AABB tile = new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 0.06, p.getZ() + 1);
            context.poseStack().pushPose();
            context.poseStack().translate(-cam.x, -cam.y, -cam.z);
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), EspRender.filledNoDepth(), (pose, buffer) -> {
                RenderUtils.drawBox(pose, buffer, stick, c);
                RenderUtils.drawBox(pose, buffer, tile, c);
            });
            context.poseStack().popPose();

            Component label = Component.literal("Torch " + (i + 1)).withColor(c & 0xFFFFFF)
                .append(Component.literal(" lights " + s.lights()).withColor(0xAAAAAA));
            RenderUtils.label(context, Vec3.atBottomCenterOf(p).add(0, 1.1, 0), label, 0.9f, true);
        }
    }

    @Override
    protected float[] rgb() {
        return new float[]{1f, 0.15f, 0.15f};
    }
}
